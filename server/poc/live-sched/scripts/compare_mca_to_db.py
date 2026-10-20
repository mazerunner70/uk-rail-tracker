#!/usr/bin/env python3
"""Compare MCA schedule records with rows in live_sched.sqlite.

Counts CIF records in the schedule file the same way ``live-sched load`` does
(LM TOC, resolvable origin/dest CRS, origin public departure), then prints
SQLite ``journeys`` / ``calling_points`` / ``service_day`` totals and a simple
persistable-vs-loaded delta.

Usage (from package root or anywhere):

  .venv/bin/python scripts/compare_mca_to_db.py
  .venv/bin/python scripts/compare_mca_to_db.py --mca /path/to/MCA --db data/live_sched.sqlite
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

PACKAGE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PACKAGE_ROOT))

from live_sched.config import DEFAULT_DB_PATH, resolve_schedule_path  # noqa: E402
from live_sched.identity import normalize_hhmm  # noqa: E402

LM_TOC = "LM"


@dataclass
class _Location:
    tiploc: str
    crs: str | None
    public_dep: str | None


@dataclass
class _Draft:
    train_uid: str = ""
    date_from: str = ""
    stp: str = "P"
    toc: str = ""
    locations: list[_Location] = field(default_factory=list)


def _slice(line: str, start: int, end: int) -> str:
    return line.ljust(end)[start:end].strip()


def _public_dep(line: str) -> str | None:
    public = _slice(line, 15, 19)
    if public and public[0].isdigit():
        return normalize_hhmm(public)
    working = _slice(line, 10, 14)
    if working and working[0].isdigit():
        return normalize_hhmm(working)
    return None


def _parse_ti(line: str) -> tuple[str, str | None]:
    tiploc = _slice(line, 2, 9)
    crs = _slice(line, 53, 56) or None
    if crs and (len(crs) != 3 or not crs.isalpha()):
        crs = None
    return tiploc, crs


def _classify_flush(
    draft: _Draft, tip_crs: dict[str, str]
) -> tuple[str, str | None]:
    """Return (bucket, service_guid_or_None) matching loader persist rules."""
    if draft.toc != LM_TOC:
        return "non_lm", None
    if not draft.train_uid or not draft.locations:
        return "lm_skipped_incomplete", None
    origin = draft.locations[0]
    dest = draft.locations[-1]
    origin_crs = origin.crs or tip_crs.get(origin.tiploc)
    dest_crs = dest.crs or tip_crs.get(dest.tiploc)
    if not origin_crs or not dest_crs:
        return "lm_skipped_no_crs", None
    if not origin.public_dep:
        return "lm_skipped_no_origin_dep", None
    guid = (
        f"SVC-{origin_crs}{dest_crs}{draft.date_from}"
        f"{normalize_hhmm(origin.public_dep)}"
    )
    return "lm_persistable", guid


def scan_mca(mca_path: Path) -> dict[str, object]:
    tip_crs: dict[str, str] = {}
    draft: _Draft | None = None
    code_counts: Counter[str] = Counter()
    buckets: Counter[str] = Counter()
    guids: list[str] = []
    lines = 0

    with mca_path.open("rb") as handle:
        for raw in handle:
            lines += 1
            line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
            if len(line) < 2:
                continue
            rec = line[:2]
            code_counts[rec] += 1
            if rec == "TI":
                tiploc, crs = _parse_ti(line)
                if tiploc and crs:
                    tip_crs[tiploc] = crs
                continue
            if rec == "BS":
                if draft is not None:
                    bucket, guid = _classify_flush(draft, tip_crs)
                    buckets[bucket] += 1
                    if guid:
                        guids.append(guid)
                draft = _Draft(
                    train_uid=_slice(line, 3, 9),
                    date_from=_slice(line, 9, 15),
                    stp=_slice(line, 79, 80) or "P",
                )
                continue
            if draft is None:
                continue
            if rec == "BX":
                draft.toc = _slice(line, 11, 13)
                continue
            if rec == "LO":
                tiploc = _slice(line, 2, 9)
                draft.locations.append(
                    _Location(
                        tiploc=tiploc,
                        crs=tip_crs.get(tiploc),
                        public_dep=_public_dep(line),
                    )
                )
                continue
            if rec in ("LI", "LT"):
                tiploc = _slice(line, 2, 9)
                draft.locations.append(
                    _Location(tiploc=tiploc, crs=tip_crs.get(tiploc), public_dep=None)
                )
                continue
            if rec == "ZZ":
                break

    if draft is not None:
        bucket, guid = _classify_flush(draft, tip_crs)
        buckets[bucket] += 1
        if guid:
            guids.append(guid)

    return {
        "lines": lines,
        "code_counts": dict(sorted(code_counts.items())),
        "buckets": dict(buckets),
        "persistable_guids": guids,
        "unique_persistable_guids": len(set(guids)),
    }


def scan_db(db_path: Path) -> dict[str, int]:
    conn = sqlite3.connect(str(db_path))
    try:
        def count(sql: str) -> int:
            row = conn.execute(sql).fetchone()
            return int(row[0]) if row else 0

        return {
            "journeys": count("SELECT COUNT(*) FROM journeys"),
            "journeys_lm": count("SELECT COUNT(*) FROM journeys WHERE toc = 'LM'"),
            "calling_points": count("SELECT COUNT(*) FROM calling_points"),
            "service_day": count("SELECT COUNT(*) FROM service_day"),
            "service_day_cancelled": count(
                "SELECT COUNT(*) FROM service_day WHERE cancelled = 1"
            ),
        }
    finally:
        conn.close()


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Compare MCA schedule counts with live-sched SQLite rows."
    )
    parser.add_argument(
        "--mca",
        type=Path,
        default=None,
        help="MCA path (default: SCHEDULE_DIR/SCHEDULE_FILE from workspace .env)",
    )
    parser.add_argument(
        "--db",
        type=Path,
        default=DEFAULT_DB_PATH,
        help=f"SQLite path (default: {DEFAULT_DB_PATH})",
    )
    args = parser.parse_args()
    mca = args.mca if args.mca else resolve_schedule_path()
    db = args.db

    if not mca.is_file():
        print(f"MCA not found: {mca}", file=sys.stderr)
        return 1
    if not db.is_file():
        print(f"SQLite DB not found: {db}", file=sys.stderr)
        return 1

    mca_stats = scan_mca(mca)
    db_stats = scan_db(db)
    buckets = mca_stats["buckets"]  # type: ignore[assignment]
    persistable = int(buckets.get("lm_persistable", 0))  # type: ignore[union-attr]
    unique_guids = int(mca_stats["unique_persistable_guids"])  # type: ignore[arg-type]
    journeys = db_stats["journeys_lm"]
    dupes = persistable - unique_guids

    print(f"MCA:  {mca}")
    print(f"DB:   {db}")
    print()
    print("=== Schedule file ===")
    print(f"  lines (incl. blank/trailer):     {mca_stats['lines']}")
    print(f"  BS records:                     {mca_stats['code_counts'].get('BS', 0)}")  # type: ignore[union-attr]
    print(f"  BX / LO / LI / LT:              "
          f"{mca_stats['code_counts'].get('BX', 0)} / "  # type: ignore[union-attr]
          f"{mca_stats['code_counts'].get('LO', 0)} / "
          f"{mca_stats['code_counts'].get('LI', 0)} / "
          f"{mca_stats['code_counts'].get('LT', 0)}")
    print(f"  LM persistable schedules:       {persistable}")
    print(f"  unique service_guid (file):     {unique_guids}"
          + (f"  ({dupes} duplicate guid(s))" if dupes else ""))
    for key in (
        "non_lm",
        "lm_skipped_incomplete",
        "lm_skipped_no_crs",
        "lm_skipped_no_origin_dep",
    ):
        n = int(buckets.get(key, 0))  # type: ignore[union-attr]
        if n:
            print(f"  {key}: {n}")

    print()
    print("=== SQLite ===")
    print(f"  journeys (all / LM):            {db_stats['journeys']} / {journeys}")
    print(f"  calling_points:                 {db_stats['calling_points']}")
    print(f"  service_day (all / cancelled):  "
          f"{db_stats['service_day']} / {db_stats['service_day_cancelled']}")

    print()
    print("=== Compare (LM persistable ↔ journeys) ===")
    delta = journeys - unique_guids
    print(f"  unique persistable guids (file): {unique_guids}")
    print(f"  LM journeys (db):                {journeys}")
    print(f"  delta (db − file unique):        {delta}")
    if delta == 0:
        print("  OK: counts match.")
        return 0
    print("  MISMATCH: reload or inspect skip reasons above.")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
