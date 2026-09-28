#!/usr/bin/env python3
"""Stream Darwin PubSub Kafka messages and log timestamps and update types."""

from __future__ import annotations

import argparse
import io
import json
import logging
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Literal, cast

import fastavro  # pyright: ignore[reportMissingImports]
from confluent_kafka import Consumer, Message  # pyright: ignore[reportMissingImports]

from config import load_settings
from consumer_utils import (
    StartMode,
    add_start_mode_args,
    assign_topic,
    poll_message,
    record_offset,
    resolve_start_mode,
)
from offset_store import offset_store_for_format

logging.basicConfig(
    format="%(asctime)s %(levelname)s %(message)s",
    level=logging.INFO,
)
log = logging.getLogger(__name__)

Format = Literal["json", "xml", "avro"]

_TS_ATTR = re.compile(r'\bts="([^"]+)"')

UPDATE_TYPES = frozenset(
    {
        "schedule",
        "deactivated",
        "association",
        "scheduleFormations",
        "TS",
        "formationLoading",
        "serviceLoading",
        "trainOrder",
        "OW",
        "trainAlert",
        "trackingID",
        "alarm",
        "stationInformation",
    }
)
_WRAPPER_KEYS = ("uR", "sR")


@dataclass(frozen=True)
class MessageInfo:
    timestamp: str | None
    update_types: list[str]


@dataclass(frozen=True)
class TimestampConsumerArgs:
    format: Format
    from_beginning: bool
    live: bool
    reset_offsets: bool
    max_messages: int | None


def _to_iso(value: object) -> str | None:
    if value is None:
        return None
    if isinstance(value, (int, float)):
        seconds = value / 1000 if value > 1_000_000_000_000 else value
        return datetime.fromtimestamp(seconds, tz=timezone.utc).isoformat()
    if isinstance(value, str):
        text = value.strip()
        if not text:
            return None
        try:
            return datetime.fromisoformat(text.replace("Z", "+00:00")).isoformat()
        except ValueError:
            return None
    return None


def _update_types_from_body(body: dict[str, object]) -> list[str]:
    return sorted(key for key in body if key in UPDATE_TYPES)


def _update_types_from_payload(payload: dict[str, object]) -> list[str]:
    for wrapper in _WRAPPER_KEYS:
        body = payload.get(wrapper)
        if isinstance(body, dict):
            return _update_types_from_body(cast(dict[str, object], body))
    return []


def _local_xml_tag(tag: str) -> str:
    if "}" in tag:
        return tag.rsplit("}", 1)[-1]
    if ":" in tag:
        return tag.rsplit(":", 1)[-1]
    return tag


def _parse_json_payload(raw: bytes) -> dict[str, object] | None:
    try:
        envelope = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return None
    if not isinstance(envelope, dict):
        return None

    typed_envelope = cast(dict[str, object], envelope)
    inner = typed_envelope.get("bytes")
    if isinstance(inner, str):
        try:
            payload = json.loads(inner)
        except json.JSONDecodeError:
            payload = None
        if isinstance(payload, dict):
            return cast(dict[str, object], payload)
    return typed_envelope


def _timestamp_from_payload(payload: dict[str, object]) -> str | None:
    for key in ("ts", "timestamp"):
        if key in payload:
            return _to_iso(payload[key])
    return None


def _extract_json_info(raw: bytes) -> MessageInfo:
    payload = _parse_json_payload(raw)
    if payload is None:
        return MessageInfo(timestamp=None, update_types=[])

    ts = _timestamp_from_payload(payload)
    if ts is None:
        try:
            envelope = cast(dict[str, object], json.loads(raw.decode("utf-8")))
        except (UnicodeDecodeError, json.JSONDecodeError):
            envelope = {}
        for key in ("timestamp", "ts"):
            if key in envelope:
                ts = _to_iso(envelope[key])
                break

    return MessageInfo(timestamp=ts, update_types=_update_types_from_payload(payload))


def _extract_xml_info(raw: bytes) -> MessageInfo:
    text = raw.decode("utf-8")
    ts: str | None = None
    match = _TS_ATTR.search(text)
    if match:
        ts = _to_iso(match.group(1))

    update_types: list[str] = []
    try:
        root = ET.fromstring(text)
        if ts is None and "ts" in root.attrib:
            ts = _to_iso(root.attrib["ts"])
        for elem in root.iter():
            local = _local_xml_tag(elem.tag)
            if local in _WRAPPER_KEYS:
                update_types = sorted(
                    _local_xml_tag(child.tag)
                    for child in elem
                    if _local_xml_tag(child.tag) in UPDATE_TYPES
                )
                break
    except ET.ParseError:
        pass

    return MessageInfo(timestamp=ts, update_types=update_types)


def _decode_avro_record(raw: bytes) -> dict[str, object] | None:
    payload = raw[5:] if len(raw) > 5 and raw[0] == 0 else raw
    bio = io.BytesIO(payload)
    record: object | None
    try:
        record = fastavro.schemaless_reader(
            bio, {"type": "record", "name": "Darwin", "fields": []}
        )
    except Exception:
        try:
            _ = bio.seek(0)
            records = list(fastavro.reader(bio))
            record = records[0] if records else None
        except Exception:
            return None
    if isinstance(record, dict):
        return cast(dict[str, object], record)
    return None


def _extract_avro_info(raw: bytes) -> MessageInfo:
    record = _decode_avro_record(raw)
    if record is None:
        return MessageInfo(timestamp=None, update_types=[])

    ts = _timestamp_from_payload(record)
    inner = record.get("bytes")
    if isinstance(inner, str):
        try:
            payload = json.loads(inner)
        except json.JSONDecodeError:
            payload = None
        if isinstance(payload, dict):
            typed_payload = cast(dict[str, object], payload)
            if ts is None:
                ts = _timestamp_from_payload(typed_payload)
            return MessageInfo(
                timestamp=ts,
                update_types=_update_types_from_payload(typed_payload),
            )

    return MessageInfo(timestamp=ts, update_types=_update_types_from_payload(record))


def extract_message_info(fmt: Format, msg: Message) -> MessageInfo:
    raw = msg.value()
    if raw is None:
        return MessageInfo(timestamp=None, update_types=[])

    info = {
        "json": _extract_json_info,
        "xml": _extract_xml_info,
        "avro": _extract_avro_info,
    }[fmt](raw)

    if info.timestamp:
        return info

    kafka_ts = msg.timestamp()[1]
    if kafka_ts and kafka_ts > 0:
        return MessageInfo(timestamp=_to_iso(kafka_ts), update_types=info.update_types)
    return info


def _format_update_types(update_types: list[str]) -> str:
    if update_types:
        return ",".join(update_types)
    return "-"


def consume_timestamps(
    fmt: Format,
    *,
    start_mode: StartMode,
    reset_offsets: bool,
    max_messages: int | None,
) -> None:
    settings = load_settings()
    topic = settings.topic_for_format(fmt)
    offset_store = offset_store_for_format(fmt)

    if reset_offsets:
        offset_store.clear_topic(topic)
        log.info("Cleared saved offsets for %s", topic)

    config = settings.consumer_config()
    config["auto.offset.reset"] = (
        "earliest" if start_mode is StartMode.BEGINNING else "latest"
    )
    config["enable.auto.commit"] = "false"

    consumer = Consumer(config)
    assignment = assign_topic(
        consumer,
        topic,
        start_mode=start_mode,
        offset_store=offset_store,
    )
    log.info("Offset store: %s", offset_store.path)
    for partition_id, _, label in assignment:
        log.info("Partition %s starting at %s", partition_id, label)

    received = 0
    group_auth_warned = False
    try:
        while max_messages is None or received < max_messages:
            msg, group_auth_warned = poll_message(
                consumer, group_auth_warned=group_auth_warned
            )
            if msg is None:
                continue

            info = extract_message_info(fmt, msg)
            received += 1
            record_offset(offset_store, msg)
            types_label = _format_update_types(info.update_types)
            if info.timestamp:
                log.info("%s %s", info.timestamp, types_label)
            else:
                log.warning(
                    "partition=%s offset=%s — no timestamp found; types=%s",
                    msg.partition(),
                    msg.offset(),
                    types_label,
                )
    except KeyboardInterrupt:
        log.info("Stopped by user")
    finally:
        consumer.close()
        log.info("Logged %d message(s)", received)


def _parse_args(argv: list[str] | None = None) -> TimestampConsumerArgs:
    parser = argparse.ArgumentParser(
        description="Log message timestamps and Push Port update types from a Darwin PubSub Kafka topic."
    )
    _ = parser.add_argument(
        "format",
        choices=["json", "xml", "avro"],
        help="Topic encoding to consume",
    )
    add_start_mode_args(parser)
    _ = parser.add_argument(
        "--max-messages",
        type=int,
        help="Stop after N messages (default: run until Ctrl+C)",
    )
    ns = parser.parse_args(argv)
    return TimestampConsumerArgs(
        format=cast(Format, ns.format),
        from_beginning=bool(ns.from_beginning),
        live=bool(ns.live),
        reset_offsets=bool(ns.reset_offsets),
        max_messages=ns.max_messages,
    )


def main() -> None:
    args = _parse_args()
    consume_timestamps(
        args.format,
        start_mode=resolve_start_mode(
            from_beginning=args.from_beginning,
            live=args.live,
        ),
        reset_offsets=args.reset_offsets,
        max_messages=args.max_messages,
    )


if __name__ == "__main__":
    main()
