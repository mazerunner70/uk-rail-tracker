"""Shared consumer helpers for assign, poll, and local offset resume."""

from __future__ import annotations

import argparse
import logging
from enum import Enum

from confluent_kafka import Consumer, KafkaError, KafkaException, Message, TopicPartition
from confluent_kafka import OFFSET_BEGINNING, OFFSET_END

from offset_store import OffsetStore

log = logging.getLogger(__name__)


class StartMode(str, Enum):
    RESUME = "resume"
    BEGINNING = "beginning"
    LIVE = "live"


def _is_group_auth_error(err: KafkaError) -> bool:
    return err.code() == KafkaError.GROUP_AUTHORIZATION_FAILED


def assign_topic(
    consumer: Consumer,
    topic: str,
    *,
    start_mode: StartMode,
    offset_store: OffsetStore | None,
) -> list[tuple[int, int | None, str]]:
    """Assign all partitions. Returns (partition, start_offset, start_label) per row."""
    metadata = consumer.list_topics(topic, timeout=15)
    topic_meta = metadata.topics.get(topic)
    if topic_meta is None or topic_meta.error is not None:
        error = topic_meta.error if topic_meta else "unknown"
        raise RuntimeError(f"Could not describe topic {topic}: {error}")

    assignment: list[TopicPartition] = []
    summary: list[tuple[int, int | None, str]] = []

    for partition_id in sorted(topic_meta.partitions.keys()):
        start_offset: int
        label: str

        if start_mode is StartMode.BEGINNING:
            start_offset = OFFSET_BEGINNING
            label = "earliest retained"
        elif start_mode is StartMode.LIVE:
            start_offset = OFFSET_END
            label = "live (end offset)"
        else:
            saved = offset_store.next_offset(topic, partition_id) if offset_store else None
            if saved is not None:
                start_offset = saved
                label = f"saved offset {saved:,}"
            else:
                start_offset = OFFSET_END
                label = "live (no saved offset)"

        assignment.append(TopicPartition(topic, partition_id, start_offset))
        summary.append(
            (
                partition_id,
                None if start_offset in (OFFSET_BEGINNING, OFFSET_END) else start_offset,
                label,
            )
        )

    consumer.assign(assignment)
    return summary


def poll_message(
    consumer: Consumer,
    *,
    group_auth_warned: bool,
) -> tuple[Message | None, bool]:
    """Poll once; returns (message, group_auth_warned). Message is None if idle/error handled."""
    msg = consumer.poll(1.0)
    if msg is None:
        return None, group_auth_warned
    if not msg.error():
        return msg, group_auth_warned

    err = msg.error()
    if _is_group_auth_error(err):
        if not group_auth_warned:
            log.warning(
                "Group authorization probe failed (non-fatal) — continuing"
            )
            group_auth_warned = True
        return None, group_auth_warned
    if err.code() == KafkaError._PARTITION_EOF:
        return None, group_auth_warned
    raise KafkaException(err)


def record_offset(offset_store: OffsetStore | None, msg: Message) -> None:
    if offset_store is None:
        return
    offset_store.save_next_offset(msg.topic(), msg.partition(), msg.offset() + 1)


def add_start_mode_args(parser: argparse.ArgumentParser) -> None:
    group = parser.add_mutually_exclusive_group()
    group.add_argument(
        "--from-beginning",
        action="store_true",
        help="Start at earliest retained offset (ignores saved offsets)",
    )
    group.add_argument(
        "--live",
        action="store_true",
        help="Only new messages from now (ignores saved offsets)",
    )
    parser.add_argument(
        "--reset-offsets",
        action="store_true",
        help="Delete saved offsets for this topic/format before starting",
    )


def resolve_start_mode(
    *,
    from_beginning: bool,
    live: bool,
) -> StartMode:
    if from_beginning:
        return StartMode.BEGINNING
    if live:
        return StartMode.LIVE
    return StartMode.RESUME
