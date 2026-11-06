import { unlinkSync } from "node:fs";
import { createRequire } from "node:module";
import type BetterSqlite3 from "better-sqlite3";
import type { PreparedJourney } from "./cif.js";
import { resolveServiceDays, type ScheduleWindow } from "./serviceDays.js";
import { SCHEMA_SQL } from "./sqliteSchema.js";

const require = createRequire(import.meta.url);
const BetterSqlite = require("better-sqlite3") as typeof import("better-sqlite3");

export type SqliteDatabase = BetterSqlite3.Database;
export type OpenDatabase = (path: string) => SqliteDatabase;

export type ReplacementSession = {
  sqlitePath: string;
  insertJourney: (journey: PreparedJourney) => void;
  finalize: (meta: { loadDay: string; hdSequence: string }) => void;
  abortMissing: () => void;
};

function defaultOpen(path: string): SqliteDatabase {
  return new BetterSqlite(path);
}

/** Bulk replace: one commit at the end, WAL, relaxed sync during the load. */
function tuneDatabaseForBulkLoad(db: SqliteDatabase): void {
  db.pragma("journal_mode = WAL");
  db.pragma("synchronous = NORMAL");
  db.pragma("foreign_keys = ON");
}

function rollbackQuietly(db: SqliteDatabase): void {
  try {
    db.exec("ROLLBACK");
  } catch {
    // already rolled back or never began
  }
}

function unlinkIfPresent(path: string): void {
  try {
    unlinkSync(path);
  } catch (err) {
    const code = (err as NodeJS.ErrnoException).code;
    if (code !== "ENOENT") {
      throw err;
    }
  }
}

export function openReplacement(
  sqlitePath: string,
  deps: { openDatabase?: OpenDatabase; afterUnlink?: () => void } = {},
): ReplacementSession {
  unlinkIfPresent(sqlitePath);
  deps.afterUnlink?.();
  const open = deps.openDatabase ?? defaultOpen;
  let db: SqliteDatabase | undefined;
  try {
    db = open(sqlitePath);
    tuneDatabaseForBulkLoad(db);
    db.exec(SCHEMA_SQL);
    db.exec("BEGIN IMMEDIATE");
  } catch (err) {
    if (db) {
      rollbackQuietly(db);
      try {
        db.close();
      } catch {
        // best-effort
      }
    }
    unlinkIfPresent(sqlitePath);
    throw err;
  }

  const insertJourneyStmt = db.prepare(`
    INSERT INTO journeys (
      service_guid, train_uid, run_date, date_runs_to, days_run,
      origin_crs, dest_crs, origin_std, toc, stp_indicator, status
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);
  const insertPointStmt = db.prepare(`
    INSERT INTO calling_points (
      service_guid, seq, tiploc, crs, public_arr, public_dep,
      forecast_arr, forecast_dep, activity
    ) VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, ?)
  `);
  const insertMetaStmt = db.prepare(
    `INSERT INTO feed_meta (key, value) VALUES (?, ?)`,
  );
  const insertDayStmt = db.prepare(`
    INSERT INTO service_day (train_uid, calendar_date, service_guid, cancelled)
    VALUES (?, ?, ?, ?)
  `);

  let closed = false;
  let committed = false;

  const closeQuietly = (): void => {
    if (closed) {
      return;
    }
    closed = true;
    try {
      db.close();
    } catch {
      // close is best-effort on abort
    }
  };

  return {
    sqlitePath,
    insertJourney: (journey) => {
      if (journey.toc !== "LM") {
        return;
      }
      insertJourneyStmt.run(
        journey.serviceGuid,
        journey.trainUid,
        journey.runDate,
        journey.dateRunsTo,
        journey.daysRun,
        journey.originCrs,
        journey.destCrs,
        journey.originStd,
        journey.toc,
        journey.stpIndicator,
        journey.status,
      );
      for (const point of journey.callingPoints) {
        insertPointStmt.run(
          journey.serviceGuid,
          point.seq,
          point.tiploc,
          point.crs,
          point.publicArr,
          point.publicDep,
          point.activity,
        );
      }
    },
    finalize: (meta) => {
      const rows = db
        .prepare(
          `SELECT service_guid, train_uid, run_date, date_runs_to, days_run, stp_indicator
           FROM journeys`,
        )
        .all() as Array<{
        service_guid: string;
        train_uid: string;
        run_date: string;
        date_runs_to: string;
        days_run: string;
        stp_indicator: string;
      }>;
      const schedules: ScheduleWindow[] = rows.map((row) => ({
        serviceGuid: row.service_guid,
        trainUid: row.train_uid,
        dateRunsFrom: row.run_date,
        dateRunsTo: row.date_runs_to,
        daysRun: row.days_run,
        stpIndicator: row.stp_indicator,
      }));
      const days = resolveServiceDays(schedules);
      for (const day of days) {
        insertDayStmt.run(
          day.trainUid,
          day.calendarDate,
          day.serviceGuid,
          day.cancelled ? 1 : 0,
        );
      }
      insertMetaStmt.run("load_day", meta.loadDay);
      insertMetaStmt.run("hd_sequence", meta.hdSequence);
      db.exec("COMMIT");
      committed = true;
      closeQuietly();
    },
    abortMissing: () => {
      if (!committed) {
        rollbackQuietly(db);
      }
      closeQuietly();
      unlinkIfPresent(sqlitePath);
    },
  };
}

export function replaceFromJourneys(
  sqlitePath: string,
  journeys: PreparedJourney[],
  meta: { loadDay: string; hdSequence: string },
  deps?: { openDatabase?: OpenDatabase; afterUnlink?: () => void },
): void {
  const session = openReplacement(sqlitePath, deps);
  try {
    for (const journey of journeys) {
      session.insertJourney(journey);
    }
    session.finalize(meta);
  } catch (err) {
    session.abortMissing();
    throw err;
  }
}
