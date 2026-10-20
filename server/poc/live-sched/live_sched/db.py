"""SQLite schema and connection helpers for the live-sched POC."""

from __future__ import annotations

import sqlite3
from pathlib import Path

SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS journeys (
    service_guid TEXT PRIMARY KEY,
    train_uid TEXT NOT NULL,
    run_date TEXT NOT NULL,
    date_runs_to TEXT NOT NULL,
    days_run TEXT NOT NULL,
    origin_crs TEXT NOT NULL,
    dest_crs TEXT NOT NULL,
    origin_std TEXT NOT NULL,
    toc TEXT NOT NULL,
    stp_indicator TEXT NOT NULL DEFAULT 'P',
    status TEXT NOT NULL DEFAULT 'published',
    UNIQUE (train_uid, run_date, stp_indicator)
);

CREATE TABLE IF NOT EXISTS calling_points (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    service_guid TEXT NOT NULL REFERENCES journeys(service_guid) ON DELETE CASCADE,
    seq INTEGER NOT NULL,
    tiploc TEXT NOT NULL,
    crs TEXT,
    public_arr TEXT,
    public_dep TEXT,
    forecast_arr TEXT,
    forecast_dep TEXT,
    activity TEXT NOT NULL DEFAULT 'call',
    UNIQUE (service_guid, seq)
);

CREATE TABLE IF NOT EXISTS service_day (
    train_uid TEXT NOT NULL,
    calendar_date TEXT NOT NULL,
    service_guid TEXT REFERENCES journeys(service_guid) ON DELETE CASCADE,
    cancelled INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (train_uid, calendar_date)
);

CREATE TABLE IF NOT EXISTS applied_updates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    service_guid TEXT REFERENCES journeys(service_guid),
    update_type TEXT NOT NULL,
    rid TEXT,
    payload_json TEXT NOT NULL,
    applied_at TEXT NOT NULL,
    source_offset INTEGER
);

CREATE TABLE IF NOT EXISTS service_aliases (
    alias_type TEXT NOT NULL,
    alias_value TEXT NOT NULL,
    service_guid TEXT NOT NULL REFERENCES journeys(service_guid) ON DELETE CASCADE,
    PRIMARY KEY (alias_type, alias_value)
);

CREATE TABLE IF NOT EXISTS feed_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS kafka_offsets (
    topic TEXT NOT NULL,
    partition INTEGER NOT NULL,
    next_offset INTEGER NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (topic, partition)
);

CREATE INDEX IF NOT EXISTS idx_journeys_uid_date ON journeys(train_uid, run_date);
CREATE INDEX IF NOT EXISTS idx_service_day_guid ON service_day(service_guid);
CREATE INDEX IF NOT EXISTS idx_aliases_guid ON service_aliases(service_guid);
CREATE INDEX IF NOT EXISTS idx_calling_guid ON calling_points(service_guid);
"""


def connect(db_path: str | Path) -> sqlite3.Connection:
    """Open a SQLite connection with foreign keys enabled."""
    path = Path(db_path)
    if str(path) != ":memory:":
        path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(path))
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_schema(conn: sqlite3.Connection) -> None:
    """Create POC tables if they do not exist."""
    conn.executescript(SCHEMA_SQL)
    conn.commit()
