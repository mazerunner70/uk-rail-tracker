"""Orchestrate MCA extract → layout → network-schematic.json."""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from .layout_input import build_layout_input, schematic_lines_from_stop_lists
from .mca_extract import extract_and_stamp_mca, mca_day_from_last_modified
from .mca_journeys import iter_mca_journeys
from .schematic_post import diagram_from_oct_coords

HERE = Path(__file__).resolve().parent
# Lambda image: layout/ sits beside schematic_process/. Repo: …/server/lambda/schematic_process.
if (HERE.parent / "layout" / "metro_layout.py").is_file():
    SERVER_ROOT = HERE.parent
else:
    SERVER_ROOT = HERE.parents[2]
DEFAULT_METRO_LAYOUT = SERVER_ROOT / "layout" / "metro_layout.py"
DEFAULT_GEO = SERVER_ROOT / "data" / "station-geo.json"


@dataclass
class ProcessResult:
    mca_day: str
    mca_path: Path
    schematic: dict
    hd_sequence: str
    etag: str | None = None
    source: str | None = None


def _run_metro_layout(layout_input: dict, time_limit_sec: float, script: Path) -> dict:
    proc = subprocess.run(
        [sys.executable, str(script), "--time-limit-sec", str(time_limit_sec)],
        input=json.dumps(layout_input),
        text=True,
        capture_output=True,
        check=False,
    )
    if proc.stderr:
        print(proc.stderr, file=sys.stderr, end="")
    if proc.returncode != 0:
        raise RuntimeError(
            f"metro_layout.py exited {proc.returncode}: {proc.stderr.strip() or proc.stdout.strip()}"
        )
    json_line = None
    for line in proc.stdout.splitlines():
        stripped = line.strip()
        if stripped.startswith("{") and stripped.endswith("}"):
            json_line = stripped
    if not json_line:
        raise RuntimeError("metro_layout.py produced no JSON on stdout")
    return json.loads(json_line)


def process_mca_file(
    mca_path: Path,
    *,
    mca_day: str,
    time_limit_sec: float = 840.0,
    metro_layout_script: Path | None = None,
    geo_path: Path | None = None,
    work_dir: Path | None = None,
) -> ProcessResult:
    parsed = iter_mca_journeys(mca_path)
    geo = geo_path or DEFAULT_GEO
    layout_in = build_layout_input(parsed.stop_lists, geo_path=geo)
    if not layout_in["nodes"]:
        raise RuntimeError("no LM journeys found for layout")
    script = metro_layout_script or DEFAULT_METRO_LAYOUT
    oct_result = _run_metro_layout(layout_in, time_limit_sec, script)
    lines = schematic_lines_from_stop_lists(parsed.stop_lists, geo_path=geo)
    schematic = diagram_from_oct_coords(
        oct_result["stations"],
        oct_result["edges"],
        lines,
        load_day=mca_day,
        hd_sequence=parsed.hd_sequence,
    )
    if work_dir:
        # Keep a copy under work_dir for upload staging
        staged = work_dir / mca_path.name
        if staged.resolve() != mca_path.resolve():
            shutil.copy2(mca_path, staged)
            mca_path = staged
    return ProcessResult(
        mca_day=mca_day,
        mca_path=mca_path,
        schematic=schematic,
        hd_sequence=parsed.hd_sequence,
    )


def process_zip_file(
    zip_path: Path,
    *,
    last_modified: datetime | str,
    out_dir: Path,
    time_limit_sec: float = 840.0,
    metro_layout_script: Path | None = None,
    geo_path: Path | None = None,
) -> ProcessResult:
    mca_day = mca_day_from_last_modified(last_modified)
    out_dir.mkdir(parents=True, exist_ok=True)
    mca_path = extract_and_stamp_mca(zip_path, out_dir, mca_day)
    return process_mca_file(
        mca_path,
        mca_day=mca_day,
        time_limit_sec=time_limit_sec,
        metro_layout_script=metro_layout_script,
        geo_path=geo_path,
        work_dir=out_dir,
    )


def write_manifest(
    path: Path,
    *,
    etag: str,
    source: str,
    mca_day: str,
    mca_key: str,
    schematic_key: str,
    hd_sequence: str,
) -> None:
    payload = {
        "etag": etag,
        "source": source,
        "mcaDay": mca_day,
        "mcaKey": mca_key,
        "schematicKey": schematic_key,
        "hdSequence": hd_sequence,
        "completedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
