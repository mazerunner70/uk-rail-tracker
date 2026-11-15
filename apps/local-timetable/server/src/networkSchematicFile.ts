import { readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import type BetterSqlite3 from "better-sqlite3";
import {
  diagramFromOctLayoutResult,
  metroLayoutEngine,
  runMilpLayout,
} from "./metroLayoutBridge.js";
import {
  buildLayoutInput,
  layoutDiagram,
  type DiagramBody,
  type SchematicJourney,
} from "./networkSchematic.js";
import { NETWORK_SCHEMATIC_FILENAME } from "./paths.js";
import { openTimetableReader } from "./sqliteReader.js";
import { loadStationGeo } from "./stationGeo.js";
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

type JourneyBundle = {
  journeys: SchematicJourney[];
  loadDay: string;
  hdSequence: string;
};

function readJourneys(sqlitePath: string): JourneyBundle | FailureBody {
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
    return { journeys: journeysFrom(rows), loadDay, hdSequence };
  } finally {
    db.close();
  }
}

/** Sync geo/schematic layout (tests / METRO_LAYOUT_ENGINE=geo). */
export function buildNetworkDiagramGeo(sqlitePath: string): NetworkDiagram | FailureBody {
  const bundle = readJourneys(sqlitePath);
  if ("reason" in bundle) return bundle;
  return {
    ...layoutDiagram(bundle.journeys, { geo: loadStationGeo() }),
    loadDay: bundle.loadDay,
    hdSequence: bundle.hdSequence,
  };
}

/** @deprecated Prefer buildNetworkDiagramGeo or writeNetworkDiagram; kept for callers. */
export function buildNetworkDiagram(sqlitePath: string): NetworkDiagram | FailureBody {
  return buildNetworkDiagramGeo(sqlitePath);
}

async function buildNetworkDiagramMilp(
  sqlitePath: string,
): Promise<NetworkDiagram | FailureBody> {
  const bundle = readJourneys(sqlitePath);
  if ("reason" in bundle) return bundle;
  const input = buildLayoutInput(bundle.journeys, { geo: loadStationGeo() });
  const oct = await runMilpLayout(input);
  return {
    ...diagramFromOctLayoutResult(oct),
    loadDay: bundle.loadDay,
    hdSequence: bundle.hdSequence,
  };
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

/**
 * Build and atomically write network-schematic.json.
 * Uses PuLP+HiGHS when METRO_LAYOUT_ENGINE=milp (default);
 * uses the in-process geo schematic when METRO_LAYOUT_ENGINE=geo.
 */
export async function writeNetworkDiagram(sqlitePath: string): Promise<void> {
  const engine = metroLayoutEngine();
  const built =
    engine === "geo"
      ? buildNetworkDiagramGeo(sqlitePath)
      : await buildNetworkDiagramMilp(sqlitePath);
  if ("reason" in built) {
    throw new Error(`network schematic was not built: ${built.reason}`);
  }
  writeAtomic(networkSchematicPath(sqlitePath), built);
}

/**
 * Read a previously written schematic. Does not run the MILP (long-running).
 * Returns { reason: "missing" } when the file is absent.
 */
export function readNetworkDiagram(sqlitePath: string): NetworkDiagram | FailureBody {
  const path = networkSchematicPath(sqlitePath);
  const stored = readStored(path);
  if (stored) return stored;
  const opened = openTimetableReader(sqlitePath);
  if (!opened.ok) return { reason: opened.reason };
  opened.db.close();
  return { reason: "missing" };
}
