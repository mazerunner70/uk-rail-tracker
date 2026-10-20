"""Append short markdown entries for each database change."""

from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path


def append_db_change(
    path: str | Path,
    *,
    update_type: str,
    service_guid: str,
    lines: list[str],
) -> None:
    """Append one markdown entry describing a single applied DB change."""
    out = Path(path)
    out.parent.mkdir(parents=True, exist_ok=True)
    if not out.exists():
        out.write_text("# Database change log\n\n", encoding="utf-8")
    when = datetime.now(timezone.utc).isoformat()
    body = "\n".join(f"- {line}" for line in lines)
    block = f"## {when} — `{update_type}` `{service_guid}`\n{body}\n\n"
    with out.open("a", encoding="utf-8") as handle:
        handle.write(block)
