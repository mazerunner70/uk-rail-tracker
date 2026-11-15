import type BetterSqlite3 from "better-sqlite3";
import type { EventTag } from "./eventTags.js";
import type { LondonClock } from "./londonClock.js";
import { defaultLondonClock } from "./londonClock.js";
import {
  openTimetableReader,
  type OpenDatabase,
  type ReadFailureReason,
} from "./sqliteReader.js";
import {
  reachedAndNextCrs,
  scheduledHhmm,
  type StopRow,
} from "./stopTime.js";

export type FailureBody = { reason: ReadFailureReason };

export type NetworkStop = {
  crs?: string;
  arr: string | null;
  dep: string | null;
};

export type StatusLabel = "cancelled" | "deactivated" | "delayed" | "on time";

export type NetworkTrainRow = {
  serviceGuid: string;
  originCrs: string;
  destCrs: string;
  reachedCrs: string | null;
  nextCrs: string | null;
  scheduledTime: string | null;
  problem: boolean;
  statusLabel: StatusLabel;
  delayMinutes: number | null;
  stops: NetworkStop[];
  eventTags: EventTag[];
};

export type RemovedTrain = {
  serviceGuid: string;
  trainUid: string;
  eventTags: EventTag[];
};

export type NetworkAnswer = {
  calendarDate: string;
  trains: NetworkTrainRow[];
  generation: string;
  revision: number;
  full: boolean;
  removed: RemovedTrain[];
};

export type PairTrainRow = {
  serviceGuid: string | null;
  trainUid?: string;
  cancellationLabel?: string;
  status: string;
  reachedCrs: string | null;
  nextCrs: string | null;
  issue: boolean;
};

export type StationPairAnswer = {
  calendarDate: string;
  earlierCrs: string;
  laterCrs: string;
  trains: PairTrainRow[];
};

type PointRow = {
  seq: number;
  crs: string | null;
  public_arr: string | null;
  public_dep: string | null;
  forecast_arr: string | null;
  forecast_dep: string | null;
};

function trackedHhmm(forecast: string | null, published: string | null): string | null {
  return forecast ?? published;
}

function toTrackedStop(point: PointRow): NetworkStop {
  const stop: NetworkStop = {
    arr: trackedHhmm(point.forecast_arr, point.public_arr),
    dep: trackedHhmm(point.forecast_dep, point.public_dep),
  };
  if (point.crs) {
    return { ...stop, crs: point.crs };
  }
  return stop;
}

function mapStopRow(row: PointRow): StopRow {
  return {
    seq: row.seq,
    crs: row.crs,
    publicArr: row.public_arr,
    publicDep: row.public_dep,
    forecastArr: row.forecast_arr,
    forecastDep: row.forecast_dep,
  };
}

function minutes(hhmm: string): number {
  return Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(2, 4));
}

function latenessMinutes(forecast: string, published: string): number {
  let late = minutes(forecast) - minutes(published);
  if (late < -12 * 60) late += 24 * 60;
  if (late > 12 * 60) late -= 24 * 60;
  return late;
}

function delayMinutesFromPoints(points: PointRow[]): number | null {
  let maxLate: number | null = null;
  for (const point of points) {
    const pairs: Array<[string | null, string | null]> = [
      [point.forecast_arr, point.public_arr],
      [point.forecast_dep, point.public_dep],
    ];
    for (const [forecast, published] of pairs) {
      if (!forecast || !published || forecast === published) continue;
      const late = latenessMinutes(forecast, published);
      if (maxLate === null || late > maxLate) maxLate = late;
    }
  }
  return maxLate;
}

function hasForecastMismatch(points: PointRow[]): boolean {
  return delayMinutesFromPoints(points) !== null;
}

function journeyHasProblem(status: string, points: PointRow[]): boolean {
  return status === "deactivated" || hasForecastMismatch(points);
}

function journeyStatusLabel(status: string, points: PointRow[]): StatusLabel {
  if (status === "deactivated") {
    return "deactivated";
  }
  if (hasForecastMismatch(points)) {
    return "delayed";
  }
  return "on time";
}

function loadCallingPoints(
  db: BetterSqlite3.Database,
  serviceGuid: string,
): PointRow[] {
  return db
    .prepare(
      `SELECT seq, crs, public_arr, public_dep, forecast_arr, forecast_dep
       FROM calling_points WHERE service_guid = ? ORDER BY seq ASC`,
    )
    .all(serviceGuid) as PointRow[];
}

export function pairMatchesCallingPoints(
  points: Array<{ crs: string | null | undefined }>,
  earlierCrs: string,
  laterCrs: string,
): boolean {
  let earlierIndex = -1;
  for (let i = 0; i < points.length; i++) {
    if (points[i]?.crs === earlierCrs) {
      earlierIndex = i;
      break;
    }
  }
  if (earlierIndex === -1) {
    return false;
  }
  for (let j = earlierIndex + 1; j < points.length; j++) {
    if (points[j]?.crs === laterCrs) {
      return true;
    }
  }
  return false;
}

function readGeneration(db: BetterSqlite3.Database): string {
  const rows = db
    .prepare(
      `SELECT key, value FROM feed_meta
       WHERE key IN ('load_day', 'hd_sequence', 'loaded_at')`,
    )
    .all() as Array<{ key: string; value: string }>;
  const map = new Map(rows.map((row) => [row.key, row.value]));
  return `${map.get("load_day") ?? ""}|${map.get("hd_sequence") ?? ""}|${map.get("loaded_at") ?? ""}`;
}

function readRevision(db: BetterSqlite3.Database): number {
  const row = db
    .prepare(`SELECT MAX(id) AS max_id FROM applied_updates`)
    .get() as { max_id: number | null };
  return row.max_id ?? 0;
}

function parseEventTagsJson(raw: string | null): EventTag[] {
  if (!raw) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter((tag): tag is EventTag => typeof tag === "string");
  } catch {
    return [];
  }
}

function loadEventTagsByGuid(db: BetterSqlite3.Database): Map<string, EventTag[]> {
  const info = db.pragma(`table_info(applied_updates)`) as Array<{ name: string }>;
  if (!info.some((col) => col.name === "event_tags")) {
    return new Map();
  }
  const rows = db
    .prepare(
      `SELECT service_guid, event_tags FROM applied_updates
       WHERE service_guid IS NOT NULL`,
    )
    .all() as Array<{ service_guid: string; event_tags: string | null }>;
  const byGuid = new Map<string, Set<EventTag>>();
  for (const row of rows) {
    let set = byGuid.get(row.service_guid);
    if (!set) {
      set = new Set();
      byGuid.set(row.service_guid, set);
    }
    for (const tag of parseEventTagsJson(row.event_tags)) {
      set.add(tag);
    }
  }
  const out = new Map<string, EventTag[]>();
  for (const [guid, set] of byGuid) {
    out.set(guid, [...set]);
  }
  return out;
}

function toNetworkTrain(
  db: BetterSqlite3.Database,
  row: {
    service_guid: string;
    status: string;
    origin_crs: string;
    dest_crs: string;
  },
  nowHhmm: string,
  eventTagsByGuid: Map<string, EventTag[]>,
): NetworkTrainRow {
  const points = loadCallingPoints(db, row.service_guid);
  const stops = points.map(mapStopRow);
  const { reachedCrs, nextCrs } = reachedAndNextCrs(stops, nowHhmm);
  const problem = journeyHasProblem(row.status, points);
  return {
    serviceGuid: row.service_guid,
    originCrs: row.origin_crs,
    destCrs: row.dest_crs,
    reachedCrs,
    nextCrs,
    scheduledTime: scheduledHhmm(stops, nowHhmm),
    problem,
    statusLabel: journeyStatusLabel(row.status, points),
    delayMinutes: delayMinutesFromPoints(points),
    stops: points.map(toTrackedStop),
    eventTags: eventTagsByGuid.get(row.service_guid) ?? [],
  };
}

export type TrainViewDeps = {
  sqlitePath: string;
  clock?: LondonClock;
  openDatabase?: OpenDatabase;
  since?: number;
  generation?: string;
};

export function buildNetworkAnswer(deps: TrainViewDeps): NetworkAnswer | FailureBody {
  const clock = deps.clock ?? defaultLondonClock();
  const calendarDate = clock.calendarYyymmdd();
  const opened = openTimetableReader(deps.sqlitePath, {
    openDatabase: deps.openDatabase,
  });
  if (!opened.ok) {
    return { reason: opened.reason };
  }
  const db = opened.db;
  try {
    const generation = readGeneration(db);
    const revision = readRevision(db);
    const nowHhmm = clock.nowHhmm();
    const eventTagsByGuid = loadEventTagsByGuid(db);
    const wantFull =
      deps.since === undefined ||
      deps.generation === undefined ||
      deps.generation !== generation;

    if (wantFull) {
      const rows = db
        .prepare(
          `SELECT j.service_guid, j.status, j.origin_crs, j.dest_crs
           FROM service_day sd
           JOIN journeys j ON j.service_guid = sd.service_guid
           WHERE sd.calendar_date = ? AND sd.cancelled = 0 AND j.toc = 'LM'`,
        )
        .all(calendarDate) as Array<{
          service_guid: string;
          status: string;
          origin_crs: string;
          dest_crs: string;
        }>;
      return {
        calendarDate,
        trains: rows.map((row) => toNetworkTrain(db, row, nowHhmm, eventTagsByGuid)),
        generation,
        revision,
        full: true,
        removed: [],
      };
    }

    const since = deps.since ?? 0;
    const guids = db
      .prepare(
        `SELECT DISTINCT service_guid FROM applied_updates
         WHERE id > ? AND service_guid IS NOT NULL`,
      )
      .all(since) as Array<{ service_guid: string }>;

    const trains: NetworkTrainRow[] = [];
    const removed: RemovedTrain[] = [];

    for (const { service_guid } of guids) {
      const journey = db
        .prepare(
          `SELECT service_guid, train_uid, status, origin_crs, dest_crs
           FROM journeys WHERE service_guid = ? AND toc = 'LM'`,
        )
        .get(service_guid) as
        | {
            service_guid: string;
            train_uid: string;
            status: string;
            origin_crs: string;
            dest_crs: string;
          }
        | undefined;
      if (!journey) {
        continue;
      }
      const day = db
        .prepare(
          `SELECT cancelled FROM service_day
           WHERE train_uid = ? AND calendar_date = ? AND service_guid = ?`,
        )
        .get(journey.train_uid, calendarDate, journey.service_guid) as
        | { cancelled: number }
        | undefined;
      if (day?.cancelled === 1) {
        removed.push({
          serviceGuid: journey.service_guid,
          trainUid: journey.train_uid,
          eventTags: eventTagsByGuid.get(journey.service_guid) ?? [],
        });
        continue;
      }
      trains.push(toNetworkTrain(db, journey, nowHhmm, eventTagsByGuid));
    }

    return {
      calendarDate,
      trains,
      generation,
      revision: revision === 0 ? since : revision,
      full: false,
      removed,
    };
  } finally {
    db.close();
  }
}

export function buildStationPairAnswer(
  deps: TrainViewDeps & { earlierCrs: string; laterCrs: string },
): StationPairAnswer | FailureBody {
  const clock = deps.clock ?? defaultLondonClock();
  const calendarDate = clock.calendarYyymmdd();
  const { earlierCrs, laterCrs } = deps;
  const opened = openTimetableReader(deps.sqlitePath, {
    openDatabase: deps.openDatabase,
  });
  if (!opened.ok) {
    return { reason: opened.reason };
  }
  const db = opened.db;
  try {
    const journeys = db
      .prepare(`SELECT service_guid, train_uid, status FROM journeys WHERE toc = 'LM'`)
      .all() as { service_guid: string; train_uid: string; status: string }[];

    const nowHhmm = clock.nowHhmm();
    const trains: PairTrainRow[] = [];

    for (const journey of journeys) {
      const points = loadCallingPoints(db, journey.service_guid);
      if (!pairMatchesCallingPoints(points, earlierCrs, laterCrs)) {
        continue;
      }
      const day = db
        .prepare(
          `SELECT cancelled FROM service_day
           WHERE train_uid = ? AND calendar_date = ? AND service_guid = ?`,
        )
        .get(journey.train_uid, calendarDate, journey.service_guid) as
        | { cancelled: number }
        | undefined;
      if (!day || day.cancelled === 1) {
        continue;
      }
      const { reachedCrs, nextCrs } = reachedAndNextCrs(
        points.map(mapStopRow),
        nowHhmm,
      );
      const problem = journeyHasProblem(journey.status, points);
      trains.push({
        serviceGuid: journey.service_guid,
        status: journeyStatusLabel(journey.status, points),
        reachedCrs,
        nextCrs,
        issue: problem,
      });
    }

    const cancelledRows = db
      .prepare(
        `SELECT train_uid, service_guid FROM service_day
         WHERE calendar_date = ? AND cancelled = 1`,
      )
      .all(calendarDate) as { train_uid: string; service_guid: string | null }[];

    for (const row of cancelledRows) {
      const journey = journeys.find((j) => j.train_uid === row.train_uid);
      if (!journey) {
        continue;
      }
      const points = loadCallingPoints(db, journey.service_guid);
      if (!pairMatchesCallingPoints(points, earlierCrs, laterCrs)) {
        continue;
      }
      trains.push({
        serviceGuid: null,
        trainUid: journey.train_uid,
        cancellationLabel: "cancelled",
        status: "cancelled",
        reachedCrs: null,
        nextCrs: null,
        issue: true,
      });
    }

    return { calendarDate, earlierCrs, laterCrs, trains };
  } finally {
    db.close();
  }
}

export function isFailureBody(
  body: NetworkAnswer | StationPairAnswer | FailureBody,
): body is FailureBody {
  return "reason" in body;
}

/** Read-only check: no writes; used in tests for NFR3. */
export function readOnlyTrainAsk(
  deps: TrainViewDeps & { earlierCrs?: string; laterCrs?: string },
): void {
  if (deps.earlierCrs && deps.laterCrs) {
    buildStationPairAnswer({
      ...deps,
      earlierCrs: deps.earlierCrs,
      laterCrs: deps.laterCrs,
    });
  } else {
    buildNetworkAnswer(deps);
  }
}
