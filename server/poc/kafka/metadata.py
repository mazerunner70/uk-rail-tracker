#!/usr/bin/env python3
"""Inspect Darwin PubSub Kafka topic metadata (offsets, retention, consumer lag)."""

from __future__ import annotations

import argparse
import sys
from typing import Any

from confluent_kafka import Consumer, TopicPartition
from confluent_kafka.admin import AdminClient, ConfigResource

from config import KafkaSettings, load_settings


def _describe_topic(admin: AdminClient, topic: str) -> dict[str, Any] | None:
    futures = admin.describe_topics([topic], request_timeout=15)
    result = futures.get(topic)
    if result is None:
        return None
    if result.error is not None:
        raise RuntimeError(f"describe_topics({topic}): {result.error}")
    return {
        "name": result.name,
        "partitions": [
            {
                "id": partition.id,
                "leader": partition.leader,
                "replicas": len(partition.replicas),
                "isrs": len(partition.isrs),
            }
            for partition in result.partitions
        ],
    }


def _topic_configs(admin: AdminClient, topic: str) -> dict[str, str]:
    resource = ConfigResource(ConfigResource.Type.TOPIC, topic)
    futures = admin.describe_configs([resource], request_timeout=15)
    config_result = futures[resource].result()
    return {
        entry.name: entry.value
        for entry in config_result.values()
        if entry.value is not None
    }


def _partition_watermarks(
    consumer: Consumer, topic: str, partition_ids: list[int]
) -> list[dict[str, int]]:
    rows: list[dict[str, int]] = []
    for partition_id in partition_ids:
        tp = TopicPartition(topic, partition_id)
        low, high = consumer.get_watermark_offsets(tp, timeout=15)
        rows.append(
            {
                "partition": partition_id,
                "low_offset": low,
                "high_offset": high,
                "retained_messages": max(0, high - low),
            }
        )
    return rows


def _consumer_lag(
    consumer: Consumer, topic: str, partition_ids: list[int]
) -> list[dict[str, int | None]]:
    assigned = [TopicPartition(topic, pid) for pid in partition_ids]
    consumer.assign(assigned)
    committed = {
        (meta.topic, meta.partition): meta.offset
        for meta in consumer.committed(assigned, timeout=15)
    }

    rows: list[dict[str, int | None]] = []
    for partition_id in partition_ids:
        tp = TopicPartition(topic, partition_id)
        low, high = consumer.get_watermark_offsets(tp, timeout=15)
        committed_offset = committed.get((topic, partition_id))
        if committed_offset is None or committed_offset < 0:
            lag = None
        else:
            lag = max(0, high - committed_offset)
        rows.append(
            {
                "partition": partition_id,
                "committed_offset": committed_offset,
                "high_offset": high,
                "lag": lag,
            }
        )
    consumer.unassign()
    return rows


def print_topic_metadata(settings: KafkaSettings, topic: str, group_id: str) -> None:
    admin = AdminClient(settings.consumer_config(group_id=group_id))
    consumer = Consumer(settings.consumer_config(group_id=group_id))

    try:
        description = _describe_topic(admin, topic)
        if description is None:
            print(f"Topic not found or inaccessible: {topic}", file=sys.stderr)
            sys.exit(1)

        partition_ids = [row["id"] for row in description["partitions"]]
        watermarks = _partition_watermarks(consumer, topic, partition_ids)
        lag_rows = _consumer_lag(consumer, topic, partition_ids)

        try:
            configs = _topic_configs(admin, topic)
        except Exception as exc:  # noqa: BLE001 — POC tool
            configs = {}
            print(f"Note: could not read topic configs ({exc})", file=sys.stderr)

        total_retained = sum(row["retained_messages"] for row in watermarks)
        total_lag = sum(row["lag"] or 0 for row in lag_rows)

        print(f"Topic: {topic}")
        print(f"Consumer group: {group_id}")
        print(f"Partitions: {len(partition_ids)}")
        if "retention.ms" in configs:
            print(f"Retention (ms): {configs['retention.ms']}")
        if "retention.bytes" in configs:
            print(f"Retention (bytes): {configs['retention.bytes']}")
        print()
        print("Partition watermarks (messages currently retained in the log):")
        for row in watermarks:
            print(
                f"  partition {row['partition']:>2}: "
                f"low={row['low_offset']:,} high={row['high_offset']:,} "
                f"retained={row['retained_messages']:,}"
            )
        print(f"  total retained (all partitions): {total_retained:,}")
        print()
        print("Consumer group lag (unread messages for this group):")
        for row in lag_rows:
            committed = row["committed_offset"]
            committed_display = "not committed" if committed is None or committed < 0 else f"{committed:,}"
            lag = row["lag"]
            lag_display = "n/a (no committed offset yet)" if lag is None else f"{lag:,}"
            print(
                f"  partition {row['partition']:>2}: "
                f"committed={committed_display} high={row['high_offset']:,} lag={lag_display}"
            )
        print(f"  total lag (all partitions): {total_lag:,}")
        print()
        print(
            "Notes:\n"
            "  - Kafka is a retained log, not a traditional queue. 'Retained' is how many\n"
            "    messages still exist in the topic right now (subject to retention policy).\n"
            "  - 'Lag' is how far behind your consumer group is. With auto.offset.reset=latest\n"
            "    and a new group, lag stays near zero until you connect; live messages then\n"
            "    stream in as they arrive.\n"
            "  - Use consume.py to receive messages in real time."
        )
    finally:
        consumer.close()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Show Darwin PubSub Kafka topic metadata (offsets and lag)."
    )
    parser.add_argument(
        "format",
        choices=["json", "xml", "avro", "all"],
        help="Which configured topic to inspect",
    )
    parser.add_argument(
        "--group-id",
        help="Consumer group to measure lag for (default: KAFKA_GROUP_ID from .env)",
    )
    args = parser.parse_args()

    settings = load_settings()
    group_id = args.group_id or settings.group_id

    if args.format == "all":
        topics = [
            ("json", settings.topic_json),
            ("xml", settings.topic_xml),
            ("avro", settings.topic_avro),
        ]
        configured = [(fmt, topic) for fmt, topic in topics if topic]
        if not configured:
            print("No topics configured in .env", file=sys.stderr)
            sys.exit(1)
        for index, (fmt, topic) in enumerate(configured):
            if index:
                print("\n" + "=" * 72 + "\n")
            print(f"[{fmt}]")
            print_topic_metadata(settings, topic, group_id)
        return

    topic = settings.topic_for_format(args.format)
    print_topic_metadata(settings, topic, group_id)


if __name__ == "__main__":
    main()
