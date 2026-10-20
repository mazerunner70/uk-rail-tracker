#!/usr/bin/env python3
"""Offline demo: load LM fixture, replay harvest corpus, query by serviceGUID."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from schedule_poc.cif_loader import load_lm_schedules
from schedule_poc.db import connect, init_schema
from schedule_poc.harvest import read_harvest_corpus
from schedule_poc.replay import replay_records
from schedule_poc.repository import ScheduleRepository

FIXTURES = ROOT / "fixtures"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--db",
        type=Path,
        default=ROOT / "data" / "demo.db",
        help="SQLite path for the working schedule copy",
    )
    parser.add_argument(
        "--mca",
        type=Path,
        default=FIXTURES / "lm_tiny.mca",
        help="Offline CIF MCA (or tiny LM excerpt)",
    )
    parser.add_argument(
        "--harvest",
        type=Path,
        default=FIXTURES / "harvest_corpus.jsonl",
        help="Harvested Kafka Push Port JSONL corpus",
    )
    parser.add_argument(
        "--fresh",
        action="store_true",
        help="Delete existing DB before running",
    )
    args = parser.parse_args()

    if args.fresh and args.db.exists():
        args.db.unlink()
    args.db.parent.mkdir(parents=True, exist_ok=True)

    # Working copy: load baseline then replay into the same DB file
    conn = connect(args.db)
    init_schema(conn)
    repo = ScheduleRepository(conn)

    guids = load_lm_schedules(args.mca, repo)
    print(f"Loaded {len(guids)} LM journey(s) from {args.mca}")

    records = read_harvest_corpus(args.harvest)
    applied = replay_records(repo, records)
    print(f"Replayed {applied} update item(s) from {args.harvest}")

    print("\nJourneys (query by stable serviceGUID):")
    for journey in repo.list_journeys(toc="LM"):
        print(
            f"  {journey.service_guid}  uid={journey.train_uid}  "
            f"{journey.origin_crs}->{journey.dest_crs}  "
            f"std={journey.origin_std}  status={journey.status}"
        )
        for point in repo.list_calling_points(journey.service_guid):
            print(
                f"    [{point.seq}] {point.tiploc:8} "
                f"arr={point.public_arr or '-':4} dep={point.public_dep or '-':4} "
                f"({point.activity})"
            )
        rid = None
        # Show rid alias if present
        row = conn.execute(
            """
            SELECT alias_value FROM service_aliases
            WHERE service_guid = ? AND alias_type = 'rid'
            """,
            (journey.service_guid,),
        ).fetchone()
        if row:
            rid = row["alias_value"]
            print(f"    alias rid={rid}")
        updates = repo.count_applied_updates(journey.service_guid)
        print(f"    applied_updates={updates}")

    print(f"\nDB path: {args.db}")
    conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
