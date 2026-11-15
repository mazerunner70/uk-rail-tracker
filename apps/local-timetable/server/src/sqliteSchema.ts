/**
 * Seven-table SQLite shape copied from the existing store (column names and types).
 * This unit owns the SQL. It does not import live-sched or schedule_poc.
 */
export const SCHEMA_SQL = `
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
    source_offset INTEGER,
    event_tags TEXT NOT NULL DEFAULT '[]'
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
`;

export const TABLE_NAMES = [
  "journeys",
  "calling_points",
  "service_day",
  "service_aliases",
  "feed_meta",
  "kafka_offsets",
  "applied_updates",
] as const;

export const OVERLAY_TABLES = ["kafka_offsets", "applied_updates"] as const;

/** Upgrade existing DBs created before event_tags existed. */
export function ensureAppliedUpdatesEventTags(db: {
  pragma: (source: string) => unknown;
  exec: (source: string) => unknown;
}): void {
  const cols = db.pragma(`table_info(applied_updates)`) as Array<{ name: string }>;
  if (cols.some((col) => col.name === "event_tags")) {
    return;
  }
  db.exec(
    `ALTER TABLE applied_updates ADD COLUMN event_tags TEXT NOT NULL DEFAULT '[]'`,
  );
}
