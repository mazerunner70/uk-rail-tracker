"""Append every Kafka message to the review CSV under output/."""

from __future__ import annotations

import csv
from pathlib import Path

CSV_HEADER = ("received_at", "offset", "partition", "topic", "payload")


def ensure_csv(path: str | Path) -> Path:
    out = Path(path)
    out.parent.mkdir(parents=True, exist_ok=True)
    if not out.exists() or out.stat().st_size == 0:
        with out.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.writer(handle)
            writer.writerow(CSV_HEADER)
    return out


def append_message(
    path: str | Path,
    *,
    received_at: str,
    offset: int | None,
    partition: int | None,
    topic: str | None,
    payload: str,
) -> None:
    out = ensure_csv(path)
    with out.open("a", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(
            [
                received_at,
                "" if offset is None else offset,
                "" if partition is None else partition,
                topic or "",
                payload,
            ]
        )
