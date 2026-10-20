"""Persist and gate the 3-digit CIF feed/update sequence."""

from __future__ import annotations

import re
import sqlite3
from pathlib import Path

FEED_SEQUENCE_KEY = "feed_sequence"
DEFAULT_SEED = "990"

_DIGITS_RE = re.compile(r"(\d{3})")


def format_sequence(value: int | str) -> str:
    """Normalise a sequence to a zero-padded 3-digit string (000–999)."""
    if isinstance(value, str):
        digits = "".join(ch for ch in value if ch.isdigit())
        if len(digits) < 3:
            raise ValueError(f"sequence must be 3 digits, got {value!r}")
        n = int(digits[-3:])
    else:
        n = int(value)
    return f"{n % 1000:03d}"


def next_sequence(previous: str) -> str:
    """Expected next code: previous + 1, modulo 1000."""
    return format_sequence(int(format_sequence(previous)) + 1)


def get_feed_sequence(
    conn: sqlite3.Connection,
    *,
    seed: str = DEFAULT_SEED,
) -> str:
    """Return stored previous-load code, seeding the DB when absent."""
    row = conn.execute(
        "SELECT value FROM feed_meta WHERE key = ?",
        (FEED_SEQUENCE_KEY,),
    ).fetchone()
    if row is None:
        seeded = format_sequence(seed)
        conn.execute(
            "INSERT INTO feed_meta (key, value) VALUES (?, ?)",
            (FEED_SEQUENCE_KEY, seeded),
        )
        conn.commit()
        return seeded
    return format_sequence(str(row["value"] if hasattr(row, "keys") else row[0]))


def set_feed_sequence(conn: sqlite3.Connection, sequence: str) -> None:
    """Persist the last successfully applied 3-digit code."""
    value = format_sequence(sequence)
    conn.execute(
        """
        INSERT INTO feed_meta (key, value) VALUES (?, ?)
        ON CONFLICT(key) DO UPDATE SET value = excluded.value
        """,
        (FEED_SEQUENCE_KEY, value),
    )
    conn.commit()


def parse_hd_sequence(hd_line: str) -> str:
    """
    Read the 3-digit feed sequence from an HD (CIF header) record.

    Uses the Current File Reference field (CIF columns 33–39 / 0-based [32:39]).
    Raises when that field has no three-digit run (for example ``DFTTISD``).
    Callers that have the CFA member name should then use
    :func:`sequence_from_cfa_member`.
    """
    line = hd_line.rstrip("\r\n")
    if len(line) < 2 or line[:2] != "HD":
        raise ValueError("CFA header is missing or not an HD record")
    padded = line.ljust(80)
    current_ref = padded[32:39].strip()
    match = _DIGITS_RE.search(current_ref)
    if not match:
        raise ValueError(
            "HD Current File Reference has no 3-digit sequence "
            f"(got {current_ref!r})"
        )
    return format_sequence(match.group(1))


def sequence_from_cfa_member(name: str) -> str:
    """
    Read the 3-digit feed code from a CFA member name.

    ``RJTTC991CFA.txt`` and ``RJTTC991.CFA`` both yield ``991``. The outer
    zip name is not used.
    """
    stem = Path(name).name
    # Drop the extension, then a trailing CFA type token (RJTTC991CFA.txt).
    stem = Path(stem).stem
    bare = re.sub(r"CFA$", "", stem, flags=re.IGNORECASE)
    match = re.search(r"(\d{3})$", bare)
    if not match:
        raise ValueError(f"CFA member name has no 3-digit sequence: {name!r}")
    return format_sequence(match.group(1))


def resolve_feed_sequence(hd_line: str, member_name: str | None = None) -> str:
    """HD Current File Reference when it has digits, else the CFA member name."""
    try:
        return parse_hd_sequence(hd_line)
    except ValueError as exc:
        if member_name is None or "no 3-digit sequence" not in str(exc):
            raise
        return sequence_from_cfa_member(member_name)
