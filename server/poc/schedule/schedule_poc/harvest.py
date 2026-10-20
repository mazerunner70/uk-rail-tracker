"""Kafka harvest with injectable stop rule: 20 LM events OR 2 hours."""

from __future__ import annotations

import json
import time
from collections.abc import Callable, Iterable, Iterator
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from schedule_poc.kafka_classify import decode_kafka_value, is_lm_relevant

DEFAULT_MAX_LM_EVENTS = 20
DEFAULT_MAX_DURATION_SECONDS = 2 * 60 * 60  # 2 hours


@dataclass(frozen=True)
class HarvestRecord:
    offset: int
    payload: dict[str, Any]


@dataclass
class HarvestResult:
    records: list[HarvestRecord]
    stopped_reason: str
    lm_event_count: int
    elapsed_seconds: float


Clock = Callable[[], float]
MessageSource = Iterable[bytes | str | dict[str, Any]]


def should_stop_harvest(
    *,
    lm_event_count: int,
    elapsed_seconds: float,
    max_lm_events: int = DEFAULT_MAX_LM_EVENTS,
    max_duration_seconds: float = DEFAULT_MAX_DURATION_SECONDS,
) -> str | None:
    """
    Return a stop reason when the harvest boundary is hit, else None.

    Whichever limit is reached first wins.
    """
    if lm_event_count >= max_lm_events:
        return "max_lm_events"
    if elapsed_seconds >= max_duration_seconds:
        return "max_duration"
    return None


def harvest_lm_events(
    messages: MessageSource,
    *,
    known_lm_uids: set[str],
    max_lm_events: int = DEFAULT_MAX_LM_EVENTS,
    max_duration_seconds: float = DEFAULT_MAX_DURATION_SECONDS,
    clock: Clock | None = None,
) -> HarvestResult:
    """
    Consume messages, keep LM-relevant Push Port payloads, stop on boundary.

    ``clock`` defaults to ``time.monotonic`` so unit tests can inject a fake clock.
    """
    now = clock or time.monotonic
    started = now()
    kept: list[HarvestRecord] = []
    scanned = 0
    stopped_reason = "source_exhausted"

    for raw in messages:
        elapsed = now() - started
        reason = should_stop_harvest(
            lm_event_count=len(kept),
            elapsed_seconds=elapsed,
            max_lm_events=max_lm_events,
            max_duration_seconds=max_duration_seconds,
        )
        if reason:
            stopped_reason = reason
            break

        payload = decode_kafka_value(raw)
        scanned += 1
        if payload is None:
            continue
        if not is_lm_relevant(payload, known_lm_uids=known_lm_uids):
            continue
        kept.append(HarvestRecord(offset=scanned - 1, payload=payload))

        elapsed = now() - started
        reason = should_stop_harvest(
            lm_event_count=len(kept),
            elapsed_seconds=elapsed,
            max_lm_events=max_lm_events,
            max_duration_seconds=max_duration_seconds,
        )
        if reason:
            stopped_reason = reason
            break

    return HarvestResult(
        records=kept,
        stopped_reason=stopped_reason,
        lm_event_count=len(kept),
        elapsed_seconds=now() - started,
    )


def write_harvest_corpus(path: str | Path, records: Iterable[HarvestRecord]) -> None:
    out = Path(path)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(
                json.dumps({"offset": record.offset, "payload": record.payload})
                + "\n"
            )


def read_harvest_corpus(path: str | Path) -> list[HarvestRecord]:
    records: list[HarvestRecord] = []
    with Path(path).open(encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            row = json.loads(line)
            records.append(
                HarvestRecord(offset=int(row["offset"]), payload=row["payload"])
            )
    return records


def iter_fixture_messages(path: str | Path) -> Iterator[dict[str, Any]]:
    """Yield payload dicts from a JSONL harvest corpus (offline demo/tests)."""
    for record in read_harvest_corpus(path):
        yield record.payload
