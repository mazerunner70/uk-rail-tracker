#!/usr/bin/env python3
"""Consume Darwin PubSub Kafka messages (json, xml, or avro topic)."""

from __future__ import annotations

import argparse
import io
import json
import logging
import sys
import xml.dom.minidom

from confluent_kafka import Consumer, TopicPartition

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
from setup_log import (
    print_consumption_mode,
    print_env_config,
    print_kafka_client_config,
    print_topic_discovery,
)

logging.basicConfig(
    format="%(asctime)s %(levelname)s %(message)s",
    level=logging.INFO,
)
log = logging.getLogger(__name__)


def _decode_key(raw: bytes | None) -> str | None:
    if raw is None:
        return None
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError:
        return raw.hex()


def _format_json_payload(raw: bytes) -> str:
    text = raw.decode("utf-8")
    try:
        return json.dumps(json.loads(text), indent=2, ensure_ascii=False)
    except json.JSONDecodeError:
        return text


def _format_xml_payload(raw: bytes) -> str:
    text = raw.decode("utf-8")
    try:
        return xml.dom.minidom.parseString(text).toprettyxml(indent="  ")
    except Exception:  # noqa: BLE001 — fall back to raw XML
        return text


def _format_avro_payload(raw: bytes) -> str:
    """Best-effort Avro decode without Schema Registry (RDM may use raw Avro)."""
    import fastavro

    payload = raw
    if len(raw) > 5 and raw[0] == 0:
        # Confluent wire format: magic byte + 4-byte schema id.
        payload = raw[5:]

    bio = io.BytesIO(payload)
    try:
        record = fastavro.schemaless_reader(bio, {"type": "record", "name": "Darwin", "fields": []})
        return json.dumps(record, indent=2, default=str)
    except Exception as first_error:
        try:
            bio.seek(0)
            records = list(fastavro.reader(bio))
            if len(records) == 1:
                return json.dumps(records[0], indent=2, default=str)
            return json.dumps(records, indent=2, default=str)
        except Exception:
            return (
                f"<binary avro, {len(raw)} bytes; could not decode without schema>\n"
                f"hex preview: {raw[:64].hex()}{'…' if len(raw) > 64 else ''}\n"
                f"first decode error: {first_error}"
            )


def _format_message(fmt: str, msg) -> str:
    raw = msg.value()
    if raw is None:
        return "<empty>"

    if fmt == "json":
        return _format_json_payload(raw)
    if fmt == "xml":
        return _format_xml_payload(raw)
    if fmt == "avro":
        return _format_avro_payload(raw)
    raise ValueError(f"Unknown format: {fmt}")


def _partition_watermarks(
    consumer: Consumer, topic: str, partition_ids: list[int]
) -> list[dict[str, int]]:
    rows: list[dict[str, int]] = []
    for partition_id in partition_ids:
        low, high = consumer.get_watermark_offsets(
            TopicPartition(topic, partition_id), timeout=15
        )
        rows.append(
            {
                "partition": partition_id,
                "low_offset": low,
                "high_offset": high,
                "retained_messages": max(0, high - low),
            }
        )
    return rows


def consume(
    fmt: str,
    *,
    start_mode: StartMode,
    reset_offsets: bool,
    max_messages: int | None,
    verbose: bool,
) -> None:
    settings = load_settings()
    topic = settings.topic_for_format(fmt)
    offset_store = offset_store_for_format(fmt)

    if reset_offsets:
        offset_store.clear_topic(topic)
        log.info("Cleared saved offsets for %s", topic)

    if verbose:
        print_env_config(settings, fmt=fmt)

    config = settings.consumer_config()
    config["auto.offset.reset"] = (
        "earliest" if start_mode is StartMode.BEGINNING else "latest"
    )
    config["enable.auto.commit"] = "false"

    if verbose:
        print_kafka_client_config(config)
        print_consumption_mode(
            from_beginning=start_mode is StartMode.BEGINNING,
            use_group=False,
        )
        log.info("Offset store: %s", offset_store.path)

    log.info("Creating Kafka consumer and connecting to %s", settings.bootstrap_servers)
    consumer = Consumer(config)
    assignment = assign_topic(
        consumer,
        topic,
        start_mode=start_mode,
        offset_store=offset_store,
    )
    partition_ids = [row[0] for row in assignment]
    if verbose:
        watermarks = _partition_watermarks(consumer, topic, partition_ids)
        print_topic_discovery(
            topic,
            partition_ids,
            from_beginning=start_mode is StartMode.BEGINNING,
            watermarks=watermarks,
        )
    for partition_id, _start_offset, label in assignment:
        log.info("Partition %s starting at %s", partition_id, label)
    log.info(
        "Ready — assigned %s (%s) partitions %s; waiting for messages …",
        topic,
        fmt,
        partition_ids,
    )
    if max_messages:
        log.info("Will stop after %d message(s)", max_messages)

    received = 0
    group_auth_warned = False
    try:
        while max_messages is None or received < max_messages:
            msg, group_auth_warned = poll_message(
                consumer, group_auth_warned=group_auth_warned
            )
            if msg is None:
                continue

            received += 1
            record_offset(offset_store, msg)
            key = _decode_key(msg.key())
            headers = {k: v.decode("utf-8", errors="replace") for k, v in (msg.headers() or [])}
            payload = _format_message(fmt, msg)

            print(
                f"\n--- message {received} ---\n"
                f"topic={msg.topic()} partition={msg.partition()} offset={msg.offset():,}\n"
                f"timestamp={msg.timestamp()[1]} key={key}\n"
                f"headers={headers or '{}'}\n"
                f"{payload}"
            )
            sys.stdout.flush()
    except KeyboardInterrupt:
        log.info("Stopped by user")
    finally:
        consumer.close()
        log.info("Received %d message(s)", received)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Stream messages from a Darwin PubSub Kafka topic."
    )
    parser.add_argument(
        "format",
        choices=["json", "xml", "avro"],
        help="Topic encoding to consume (must match KAFKA_TOPIC_* in .env)",
    )
    add_start_mode_args(parser)
    parser.add_argument(
        "--max-messages",
        type=int,
        help="Stop after N messages (default: run until Ctrl+C)",
    )
    parser.add_argument(
        "--quiet",
        "-q",
        action="store_true",
        help="Suppress setup diagnostics (config explanations)",
    )
    args = parser.parse_args()
    consume(
        args.format,
        start_mode=resolve_start_mode(
            from_beginning=args.from_beginning,
            live=args.live,
        ),
        reset_offsets=args.reset_offsets,
        max_messages=args.max_messages,
        verbose=not args.quiet,
    )


if __name__ == "__main__":
    main()
