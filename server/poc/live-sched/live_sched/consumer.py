"""One-hour Kafka consumer: CSV archive + LM apply + DB change log."""

from __future__ import annotations

import logging
import time
from collections.abc import Callable, Iterable, Iterator, Mapping
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from live_sched.apply import apply_payload
from live_sched.config import (
    DEFAULT_CHANGE_LOG_PATH,
    DEFAULT_CSV_PATH,
    DEFAULT_DURATION_SECONDS,
    KafkaSettings,
)
from live_sched.csv_archive import append_message
from live_sched.kafka_classify import decode_kafka_value
from live_sched.repository import ScheduleRepository

log = logging.getLogger(__name__)

Clock = Callable[[], float]
RawMessage = bytes | str | dict[str, Any]


@dataclass(frozen=True)
class ConsumedMessage:
    raw: RawMessage
    offset: int | None = None
    partition: int | None = None
    topic: str | None = None


@dataclass
class ConsumerRunResult:
    messages_seen: int
    applied: int
    ignored: int
    elapsed_seconds: float
    stopped_reason: str


def process_message_stream(
    messages: Iterable[ConsumedMessage | RawMessage],
    repo: ScheduleRepository,
    *,
    csv_path: str | Path = DEFAULT_CSV_PATH,
    change_log_path: str | Path = DEFAULT_CHANGE_LOG_PATH,
    duration_seconds: float = DEFAULT_DURATION_SECONDS,
    clock: Clock | None = None,
) -> ConsumerRunResult:
    """
    Process a message iterable for up to ``duration_seconds``.

    Every message is appended to the CSV. Decodable Push Port payloads are
    applied for matching LM journeys. Unit tests inject fixtures; the live
    path wraps a Kafka poll loop. Ctrl+C stops the loop and returns the
    counts gathered so far.
    """
    now = clock or time.monotonic
    started = now()
    messages_seen = 0
    applied_total = 0
    ignored_total = 0
    stopped_reason = "source_exhausted"
    iterator = iter(messages)

    try:
        for item in iterator:
            elapsed = now() - started
            if elapsed >= duration_seconds:
                stopped_reason = "max_duration"
                break

            if isinstance(item, ConsumedMessage):
                msg = item
                raw = item.raw
            else:
                msg = ConsumedMessage(raw=item, offset=messages_seen)
                raw = item

            if isinstance(raw, bytes):
                payload_text = raw.decode("utf-8", errors="replace")
            elif isinstance(raw, str):
                payload_text = raw
            else:
                import json

                payload_text = json.dumps(raw, ensure_ascii=False)

            received_at = datetime.now(timezone.utc).isoformat()
            append_message(
                csv_path,
                received_at=received_at,
                offset=msg.offset,
                partition=msg.partition,
                topic=msg.topic,
                payload=payload_text,
            )
            messages_seen += 1

            decoded = decode_kafka_value(raw)
            if decoded is None:
                ignored_total += 1
            else:
                result = apply_payload(
                    repo,
                    decoded,
                    source_offset=msg.offset,
                    change_log_path=change_log_path,
                )
                applied_total += result.applied
                ignored_total += result.ignored
            _remember_offset(repo, msg)

            elapsed = now() - started
            if elapsed >= duration_seconds:
                stopped_reason = "max_duration"
                break
    except KeyboardInterrupt:
        stopped_reason = "keyboard_interrupt"
    finally:
        close = getattr(iterator, "close", None)
        if close is not None:
            close()

    return ConsumerRunResult(
        messages_seen=messages_seen,
        applied=applied_total,
        ignored=ignored_total,
        elapsed_seconds=now() - started,
        stopped_reason=stopped_reason,
    )


def start_offsets_for_partitions(
    partition_ids: Iterable[int],
    saved: Mapping[int, int],
    *,
    live_offset: int,
) -> list[tuple[int, int]]:
    """Next read offset per partition. Unsaved partitions use ``live_offset``."""
    return [
        (partition_id, saved[partition_id] if partition_id in saved else live_offset)
        for partition_id in sorted(partition_ids)
    ]


def _remember_offset(repo: ScheduleRepository, msg: ConsumedMessage) -> None:
    if msg.topic is None or msg.partition is None or msg.offset is None:
        return
    repo.save_kafka_next_offset(msg.topic, msg.partition, msg.offset + 1)


def iter_kafka_messages(
    settings: KafkaSettings,
    repo: ScheduleRepository,
    *,
    topic_format: str = "json",
    poll_timeout: float = 1.0,
) -> Iterator[ConsumedMessage]:
    """Yield messages from RDM Darwin PubSub until the caller stops iterating.

    Partitions with a row in ``kafka_offsets`` resume at that next offset.
    Partitions with no row start at the live end of the log.
    """
    from confluent_kafka import OFFSET_END, Consumer, TopicPartition

    topic = settings.topic_for_format(topic_format)
    consumer = Consumer(settings.consumer_config())
    metadata = consumer.list_topics(topic, timeout=15)
    topic_meta = metadata.topics.get(topic)
    if topic_meta is None or topic_meta.error is not None:
        error = topic_meta.error if topic_meta is not None else "unknown"
        consumer.close()
        raise RuntimeError(f"Could not describe topic {topic}: {error}")
    starts = start_offsets_for_partitions(
        topic_meta.partitions.keys(),
        repo.kafka_offsets_for_topic(topic),
        live_offset=OFFSET_END,
    )
    consumer.assign(
        [TopicPartition(topic, partition_id, offset) for partition_id, offset in starts]
    )
    for partition_id, offset in starts:
        if offset == OFFSET_END:
            log.info("partition %s of %s: live (no saved offset)", partition_id, topic)
        else:
            log.info("partition %s of %s: resume at offset %s", partition_id, topic, offset)
    try:
        while True:
            msg = consumer.poll(poll_timeout)
            if msg is None:
                continue
            if msg.error():
                log.warning("kafka poll error: %s", msg.error())
                continue
            value = msg.value()
            if value is None:
                continue
            yield ConsumedMessage(
                raw=value,
                offset=msg.offset(),
                partition=msg.partition(),
                topic=msg.topic(),
            )
    finally:
        consumer.close()


def run_live_consumer(
    repo: ScheduleRepository,
    settings: KafkaSettings,
    *,
    duration_seconds: float = DEFAULT_DURATION_SECONDS,
    csv_path: str | Path = DEFAULT_CSV_PATH,
    change_log_path: str | Path = DEFAULT_CHANGE_LOG_PATH,
    topic_format: str = "json",
) -> ConsumerRunResult:
    """Run the live Kafka consumer for a fixed duration (default 1 hour)."""
    stream = iter_kafka_messages(settings, repo, topic_format=topic_format)
    return process_message_stream(
        stream,
        repo,
        csv_path=csv_path,
        change_log_path=change_log_path,
        duration_seconds=duration_seconds,
    )
