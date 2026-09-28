"""Local offset persistence — resume consumption without Kafka consumer groups."""

from __future__ import annotations

import json
import os
import tempfile
from datetime import datetime, timezone
from pathlib import Path

DEFAULT_OFFSET_DIR = Path(__file__).resolve().parent / ".offsets"


class OffsetStore:
    """Track next offset to read per topic partition in a JSON file."""

    def __init__(self, path: Path) -> None:
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._data: dict[str, dict[str, object]] = self._load()

    def _load(self) -> dict[str, dict[str, object]]:
        if not self.path.exists():
            return {}
        with self.path.open(encoding="utf-8") as handle:
            return json.load(handle)

    def _flush(self) -> None:
        payload = json.dumps(self._data, indent=2, sort_keys=True)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp_path = tempfile.mkstemp(
            dir=self.path.parent,
            prefix=f".{self.path.stem}.",
            suffix=".tmp",
        )
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                handle.write(payload)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(tmp_path, self.path)
        except Exception:
            try:
                os.unlink(tmp_path)
            except OSError:
                pass
            raise

    def next_offset(self, topic: str, partition: int) -> int | None:
        topic_state = self._data.get(topic)
        if not topic_state:
            return None
        partitions = topic_state.get("partitions", {})
        value = partitions.get(str(partition))
        return int(value) if value is not None else None

    def save_next_offset(self, topic: str, partition: int, next_offset: int) -> None:
        topic_state = self._data.setdefault(
            topic,
            {"partitions": {}, "updated_at": None},
        )
        partitions = topic_state.setdefault("partitions", {})
        partitions[str(partition)] = next_offset
        topic_state["updated_at"] = datetime.now(timezone.utc).isoformat()
        self._flush()

    def clear_topic(self, topic: str) -> None:
        if topic in self._data:
            del self._data[topic]
            self._flush()

    def path_for_format(self, fmt: str) -> Path:
        return self.path


def offset_store_for_format(fmt: str, *, offset_dir: Path | None = None) -> OffsetStore:
    base = offset_dir or DEFAULT_OFFSET_DIR
    return OffsetStore(base / f"{fmt}.json")
