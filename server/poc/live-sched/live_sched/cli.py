"""CLI entrypoints: load MCA → DB+docs; run 1h live consumer."""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

from live_sched.cfa_apply import process_watch_folder
from live_sched.cfa_report import format_outcome
from live_sched.cif_loader import load_lm_schedules
from live_sched.config import (
    DEFAULT_CHANGE_LOG_PATH,
    DEFAULT_CSV_PATH,
    DEFAULT_DB_PATH,
    DEFAULT_DOCS_DIR,
    DEFAULT_DURATION_SECONDS,
    load_kafka_settings,
    resolve_cfa_watch_dir,
    resolve_feed_sequence_seed,
    resolve_schedule_path,
)
from live_sched.consumer import run_live_consumer
from live_sched.db import connect, init_schema
from live_sched.docs_gen import write_schedule_code_docs
from live_sched.repository import ScheduleRepository

log = logging.getLogger(__name__)


def cmd_load(args: argparse.Namespace) -> int:
    mca = Path(args.mca) if args.mca else resolve_schedule_path(args.env)
    if not mca.is_file():
        log.error("MCA file not found: %s", mca)
        return 1
    db_path = Path(args.db)
    if db_path.exists() and str(db_path) != ":memory:":
        # Full MCA load replaces the plan; drop the old file so schema matches.
        db_path.unlink()
    conn = connect(db_path)
    init_schema(conn)
    repo = ScheduleRepository(conn)
    result = load_lm_schedules(mca, repo)
    summary, algorithms = write_schedule_code_docs(
        args.docs,
        result.code_counts,
        journeys_loaded=len(result.service_guids),
    )
    log.info(
        "loaded %d LM journeys from %s → %s",
        len(result.service_guids),
        mca,
        args.db,
    )
    log.info("wrote %s and %s", summary, algorithms)
    conn.close()
    return 0


def cmd_cfa_update(args: argparse.Namespace) -> int:
    """One-shot: scan watch folder for CFA update zips; never wipe the DB."""
    db_path = Path(args.db)
    conn = connect(db_path)
    init_schema(conn)
    repo = ScheduleRepository(conn)
    watch = Path(args.watch) if args.watch else resolve_cfa_watch_dir(args.env)
    seed = args.seed or resolve_feed_sequence_seed(args.env)
    result = process_watch_folder(watch, repo, seed=seed)
    for outcome in result.outcomes:
        for line in format_outcome(outcome):
            print(line)
        print("---")
    conn.close()
    return int(result.exit_code)


def cmd_consume(args: argparse.Namespace) -> int:
    conn = connect(args.db)
    init_schema(conn)
    repo = ScheduleRepository(conn)
    try:
        settings = load_kafka_settings(args.env)
    except RuntimeError as exc:
        log.error("%s", exc)
        conn.close()
        return 1
    duration = (
        float(args.duration)
        if args.duration is not None
        else float(DEFAULT_DURATION_SECONDS)
    )
    log.info(
        "starting Kafka consumer for %.0fs (csv=%s, changes=%s)",
        duration,
        args.csv,
        args.change_log,
    )
    result = run_live_consumer(
        repo,
        settings,
        duration_seconds=duration,
        csv_path=args.csv,
        change_log_path=args.change_log,
        topic_format=args.format,
    )
    log.info(
        "stopped (%s): messages=%d applied=%d ignored=%d elapsed=%.1fs",
        result.stopped_reason,
        result.messages_seen,
        result.applied,
        result.ignored,
        result.elapsed_seconds,
    )
    conn.close()
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="live-sched",
        description="Live schedule POC (MCA LM load + CFA daily update + Kafka apply)",
    )
    parser.add_argument(
        "-v",
        "--verbose",
        action="store_true",
        help="DEBUG logging",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    load_p = sub.add_parser("load", help="Parse MCA, persist LM schedules, write docs")
    load_p.add_argument(
        "--mca",
        default=None,
        help=(
            "MCA file path. Overrides SCHEDULE_DIR and SCHEDULE_FILE "
            "from the workspace-root .env"
        ),
    )
    load_p.add_argument(
        "--env",
        default=None,
        help="Path to .env (default: workspace-root .env)",
    )
    load_p.add_argument(
        "--db",
        default=str(DEFAULT_DB_PATH),
        help=f"SQLite path (default: {DEFAULT_DB_PATH})",
    )
    load_p.add_argument(
        "--docs",
        default=str(DEFAULT_DOCS_DIR),
        help=f"Docs output directory (default: {DEFAULT_DOCS_DIR})",
    )
    load_p.set_defaults(func=cmd_load)

    cons_p = sub.add_parser(
        "consume",
        help="Run Kafka consumer for 1 hour (or --duration) and apply LM updates",
    )
    cons_p.add_argument(
        "--db",
        default=str(DEFAULT_DB_PATH),
        help=f"SQLite path (default: {DEFAULT_DB_PATH})",
    )
    cons_p.add_argument(
        "--env",
        default=None,
        help="Path to .env (default: workspace-root .env)",
    )
    cons_p.add_argument(
        "--duration",
        type=float,
        default=None,
        help=(
            f"Seconds to run (default {DEFAULT_DURATION_SECONDS}; "
            "use a short value for local dry-runs)"
        ),
    )
    cons_p.add_argument(
        "--csv",
        default=str(DEFAULT_CSV_PATH),
        help=f"Kafka message CSV (default: {DEFAULT_CSV_PATH})",
    )
    cons_p.add_argument(
        "--change-log",
        default=str(DEFAULT_CHANGE_LOG_PATH),
        help=f"DB change markdown log (default: {DEFAULT_CHANGE_LOG_PATH})",
    )
    cons_p.add_argument(
        "--format",
        choices=("json", "xml", "avro"),
        default="json",
        help="Topic format / KAFKA_TOPIC_* key (default: json)",
    )
    cons_p.set_defaults(func=cmd_consume)

    cfa_p = sub.add_parser(
        "cfa-update",
        help="One-shot: apply CFA daily update zips from a watch folder (no DB wipe)",
    )
    cfa_p.add_argument(
        "--db",
        default=str(DEFAULT_DB_PATH),
        help=f"SQLite path (default: {DEFAULT_DB_PATH})",
    )
    cfa_p.add_argument(
        "--env",
        default=None,
        help="Path to .env (default: workspace-root .env)",
    )
    cfa_p.add_argument(
        "--watch",
        default=None,
        help="Watch folder of update zips (default: CFA_WATCH_DIR from .env)",
    )
    cfa_p.add_argument(
        "--seed",
        default=None,
        help="Bootstrap previous-load code when DB has none (default: FEED_SEQUENCE_SEED or 990)",
    )
    cfa_p.set_defaults(func=cmd_cfa_update)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    logging.basicConfig(
        format="%(asctime)s %(levelname)s %(message)s",
        level=logging.DEBUG if args.verbose else logging.INFO,
    )
    return int(args.func(args))


if __name__ == "__main__":
    sys.exit(main())
