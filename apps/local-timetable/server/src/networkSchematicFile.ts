import { readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import type BetterSqlite3 from "better-sqlite3";
import { layoutDiagram, type DiagramBody, type SchematicJourney } from "./networkSchematic.js";
import { NETWORK_SCHEMATIC_FILENAME } from "./paths.js";
import { openTimetableReader } from "./sqliteReader.js";
import type { FailureBody } from "./trainViews.js";

export type NetworkDiagram = DiagramBody & {
  loadDay: string;
  hdSequence: string;
};

type CallingRow = {
  service_guid: string;
  crs: string | null;
};

export function networkSchematicPath(sqlitePath: string): string {
  return join(dirname(sqlitePath), NETWORK_SCHEMATIC_FILENAME);
}

function metaValue(db: BetterSqlite3.Database, key: string): string {
  const row = db.prepare("SELECT value FROM feed_meta WHERE key = ?").get(key) as
    | { value: string }
    | undefined;
  return row?.value ?? "";
}

function journeysFrom(rows: readonly CallingRow[]): SchematicJourney[] {
  const byGuid = new Map<string, { crs?: string }[]>();
  for (const row of rows) {
    const stops = byGuid.get(row.service_guid) ?? [];
    stops.push(row.crs ? { crs: row.crs } : {});
    byGuid.set(row.service_guid, stops);
  }
  return [...byGuid.values()].map((stops) => ({ stops }));
}

export function buildNetworkDiagram(sqlitePath: string): NetworkDiagram | FailureBody {
  const opened = openTimetableReader(sqlitePath);
  if (!opened.ok) return { reason: opened.reason };
  const db = opened.db;
  try {
    const loadDay = metaValue(db, "load_day");
    const hdSequence = metaValue(db, "hd_sequence");
    const rows = db
      .prepare(
        `SELECT cp.service_guid, cp.crs
         FROM calling_points cp
         JOIN journeys j ON j.service_guid = cp.service_guid
         WHERE j.toc = 'LM'
         ORDER BY cp.service_guid, cp.seq`,
      )
      .all() as CallingRow[];
    return { ...layoutDiagram(journeysFrom(rows)), loadDay, hdSequence };
  } finally {
    db.close();
  }
}

function isDiagram(value: unknown): value is NetworkDiagram {
  if (typeof value !== "object" || value === null) return false;
  const row = value as NetworkDiagram;
  return Array.isArray(row.stations) && Array.isArray(row.corridors)
    && typeof row.width === "number"
    && typeof row.height === "number"
    && typeof row.loadDay === "string"
    && typeof row.hdSequence === "string";
}

function readStored(path: string): NetworkDiagram | null {
  try {
    const parsed: unknown = JSON.parse(readFileSync(path, "utf8"));
    return isDiagram(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function writeAtomic(path: string, diagram: NetworkDiagram): void {
  const tmp = `${path}.${process.pid}.tmp`;
  writeFileSync(tmp, JSON.stringify(diagram));
  renameSync(tmp, path);
}

export function writeNetworkDiagram(sqlitePath: string): void {
  const built = buildNetworkDiagram(sqlitePath);
  if ("reason" in built) {
    console.error("network schematic was not written", built.reason);
    return;
  }
  try {
    writeAtomic(networkSchematicPath(sqlitePath), built);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error("network schematic was not written", message);
  }
}

export function readNetworkDiagram(sqlitePath: string): NetworkDiagram | FailureBody {
  const path = networkSchematicPath(sqlitePath);
  const stored = readStored(path);
  if (stored) return stored;
  const built = buildNetworkDiagram(sqlitePath);
  if ("reason" in built) return built;
  try {
    writeAtomic(path, built);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error("network schematic was not written", message);
  }
  return built;
}
