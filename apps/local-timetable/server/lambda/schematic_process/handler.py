"""AWS Lambda entry: S3 zip / manual mcaS3Uri → artifacts + ordered SSM apply."""

from __future__ import annotations

import json
import os
import tempfile
import urllib.parse
from datetime import datetime
from pathlib import Path
from typing import Any

import boto3

from .pipeline import process_mca_file, process_zip_file, write_manifest
from .mca_extract import mca_day_from_last_modified

s3 = boto3.client("s3")
ssm = boto3.client("ssm")


def _env(name: str, default: str | None = None) -> str:
    value = os.environ.get(name, default)
    if value is None or value == "":
        raise RuntimeError(f"missing env {name}")
    return value


def _parse_s3_uri(uri: str) -> tuple[str, str]:
    if not uri.startswith("s3://"):
        raise ValueError(f"not an s3 uri: {uri}")
    rest = uri[5:]
    bucket, _, key = rest.partition("/")
    if not bucket or not key:
        raise ValueError(f"bad s3 uri: {uri}")
    return bucket, key


def _latest_manifest_etag(artifact_bucket: str, _prefix: str) -> str | None:
    try:
        obj = s3.get_object(Bucket=artifact_bucket, Key="artifacts/latest/manifest.json")
        data = json.loads(obj["Body"].read())
        etag = data.get("etag")
        return str(etag) if etag else None
    except Exception:
        return None


def _get_object_meta(bucket: str, key: str) -> tuple[str, datetime]:
    head = s3.head_object(Bucket=bucket, Key=key)
    etag = str(head["ETag"]).strip('"')
    last_modified = head["LastModified"]
    return etag, last_modified


def _download(bucket: str, key: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    s3.download_file(bucket, key, str(dest))


def _upload(path: Path, bucket: str, key: str) -> None:
    if path.suffix == ".json":
        s3.upload_file(
            str(path), bucket, key, ExtraArgs={"ContentType": "application/json"}
        )
    elif path.suffix == ".txt":
        s3.upload_file(
            str(path), bucket, key, ExtraArgs={"ContentType": "text/plain"}
        )
    else:
        s3.upload_file(str(path), bucket, key)


def _trigger_ssm_apply(artifact_prefix: str) -> str | None:
    instance_id = os.environ.get("EC2_INSTANCE_ID", "").strip()
    if not instance_id:
        print("EC2_INSTANCE_ID unset; skip SSM apply", flush=True)
        return None
    region = os.environ.get("AWS_REGION") or os.environ.get("AWS_DEFAULT_REGION") or "eu-west-2"
    artifact_bucket = _env("ARTIFACT_BUCKET")
    script = (
        "set -euo pipefail\n"
        f"export ARTIFACT_BUCKET={artifact_bucket!s}\n"
        f"export ARTIFACT_PREFIX={artifact_prefix!s}\n"
        f"export AWS_REGION={region!s}\n"
        "bash /opt/urt-lt/apply-mca-artifacts.sh\n"
    )
    resp = ssm.send_command(
        InstanceIds=[instance_id],
        DocumentName="AWS-RunShellScript",
        Comment="urt-lt apply mca artifacts",
        Parameters={"commands": script.splitlines()},
        TimeoutSeconds=3600,
    )
    return resp["Command"]["CommandId"]


def _process_and_publish(
    *,
    local_path: Path,
    is_zip: bool,
    last_modified: datetime | str,
    etag: str,
    source: str,
) -> dict[str, Any]:
    artifact_bucket = _env("ARTIFACT_BUCKET")
    time_limit = float(os.environ.get("METRO_LAYOUT_TIME_LIMIT_SEC", "840"))
    with tempfile.TemporaryDirectory(prefix="urt-lam-") as tmp:
        tmp_path = Path(tmp)
        if is_zip:
            result = process_zip_file(
                local_path,
                last_modified=last_modified,
                out_dir=tmp_path / "out",
                time_limit_sec=time_limit,
            )
        else:
            import re
            import shutil

            mca_day = mca_day_from_last_modified(last_modified)
            stem = re.sub(r"-\d{8}$", "", local_path.stem)
            stamped = tmp_path / "out" / f"{stem}-{mca_day}.txt"
            stamped.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(local_path, stamped)
            result = process_mca_file(
                stamped,
                mca_day=mca_day,
                time_limit_sec=time_limit,
            )

        day = result.mca_day
        prefix = f"artifacts/{day}"
        mca_key = f"{prefix}/{result.mca_path.name}"
        schematic_key = f"{prefix}/network-schematic.json"
        manifest_key = f"{prefix}/manifest.json"
        latest_manifest_key = "artifacts/latest/manifest.json"

        schematic_path = tmp_path / "network-schematic.json"
        schematic_path.write_text(json.dumps(result.schematic) + "\n", encoding="utf-8")
        manifest_path = tmp_path / "manifest.json"
        write_manifest(
            manifest_path,
            etag=etag,
            source=source,
            mca_day=day,
            mca_key=mca_key,
            schematic_key=schematic_key,
            hd_sequence=result.hd_sequence,
        )

        _upload(result.mca_path, artifact_bucket, mca_key)
        _upload(schematic_path, artifact_bucket, schematic_key)
        _upload(manifest_path, artifact_bucket, manifest_key)
        _upload(manifest_path, artifact_bucket, latest_manifest_key)

        command_id = _trigger_ssm_apply(prefix)
        return {
            "ok": True,
            "mcaDay": day,
            "mcaKey": mca_key,
            "schematicKey": schematic_key,
            "stations": len(result.schematic.get("stations", [])),
            "corridors": len(result.schematic.get("corridors", [])),
            "lines": len(result.schematic.get("lines", [])),
            "ssmCommandId": command_id,
        }


def handler(event: dict[str, Any], _context: Any) -> dict[str, Any]:
    artifact_bucket = _env("ARTIFACT_BUCKET")
    print(f"schematic handler start artifact_bucket={artifact_bucket}", flush=True)

    # Manual invoke
    if isinstance(event, dict) and event.get("mcaS3Uri"):
        bucket, key = _parse_s3_uri(str(event["mcaS3Uri"]))
        print(f"manual invoke s3://{bucket}/{key}", flush=True)
        etag, last_modified = _get_object_meta(bucket, key)
        with tempfile.TemporaryDirectory(prefix="urt-dl-") as tmp:
            local = Path(tmp) / Path(key).name
            print("downloading…", flush=True)
            _download(bucket, key, local)
            print(f"downloaded bytes={local.stat().st_size}", flush=True)
            is_zip = key.lower().endswith(".zip") or local.suffix.lower() == ".zip"
            print("process_and_publish (extract + MILP; may take many minutes)…", flush=True)
            out = _process_and_publish(
                local_path=local,
                is_zip=is_zip,
                last_modified=last_modified,
                etag=etag,
                source=f"s3://{bucket}/{key}",
            )
            print(f"done {json.dumps(out)}", flush=True)
            return out

    # S3 event
    records = event.get("Records") if isinstance(event, dict) else None
    if not records:
        raise ValueError("expected S3 Records or mcaS3Uri")

    results = []
    for record in records:
        bucket = record["s3"]["bucket"]["name"]
        key = urllib.parse.unquote_plus(record["s3"]["object"]["key"])
        etag, last_modified = _get_object_meta(bucket, key)
        prev = _latest_manifest_etag(artifact_bucket, "artifacts")
        if prev and prev == etag:
            results.append({"ok": True, "skipped": True, "reason": "etag-unchanged", "etag": etag})
            continue
        with tempfile.TemporaryDirectory(prefix="urt-dl-") as tmp:
            local = Path(tmp) / Path(key).name
            _download(bucket, key, local)
            results.append(
                _process_and_publish(
                    local_path=local,
                    is_zip=True,
                    last_modified=last_modified,
                    etag=etag,
                    source=f"s3://{bucket}/{key}",
                )
            )
    return {"results": results}
