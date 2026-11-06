import type BetterSqlite3 from "better-sqlite3";
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

export type NetworkTrainRow = {
  serviceGuid: string;
  originCrs: string;
  destCrs: string;
  reachedCrs: string | null;
  nextCrs: string | null;
  scheduledTime: string | null;
  problem: boolean;
  stops: NetworkStop[];
};

export type NetworkAnswer = {
  calendarDate: string;
  trains: NetworkTrainRow[];
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

function journeyHasProblem(
  status: string,
  points: PointRow[],
): boolean {
  if (status === "deactivated") {
    return true;
  }
  for (const point of points) {
    if (
      point.forecast_arr &&
      point.public_arr &&
      point.forecast_arr !== point.public_arr
    ) {
      return true;
    }
    if (
      point.forecast_dep &&
      point.public_dep &&
      point.forecast_dep !== point.public_dep
    ) {
      return true;
    }
  }
  return false;
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

function pairMatchesCallingPoints(
  points: PointRow[],
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

export type TrainViewDeps = {
  sqlitePath: string;
  clock?: LondonClock;
  openDatabase?: OpenDatabase;
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
    const rows = db
      .prepare(
        `SELECT j.service_guid, j.status, j.origin_crs, j.dest_crs
         FROM service_day sd
         JOIN journeys j ON j.service_guid = sd.service_guid
         WHERE sd.calendar_date = ? AND sd.cancelled = 0 AND j.toc = 'LM'`,
      )
      .all(calendarDate) as {
        service_guid: string;
        status: string;
        origin_crs: string;
        dest_crs: string;
      }[];

    const nowHhmm = clock.nowHhmm();
    const trains: NetworkTrainRow[] = rows.map((row) => {
      const points = loadCallingPoints(db, row.service_guid);
      const stops = points.map(mapStopRow);
      const { reachedCrs, nextCrs } = reachedAndNextCrs(stops, nowHhmm);
      return {
        serviceGuid: row.service_guid,
        originCrs: row.origin_crs,
        destCrs: row.dest_crs,
        reachedCrs,
        nextCrs,
        scheduledTime: scheduledHhmm(stops, nowHhmm),
        problem: journeyHasProblem(row.status, points),
        stops: points.map(toTrackedStop),
      };
    });
    return { calendarDate, trains };
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
        status: journey.status,
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
