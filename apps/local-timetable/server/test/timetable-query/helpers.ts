import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import type BetterSqlite3 from "better-sqlite3";
import { SCHEMA_SQL } from "../../src/sqliteSchema.js";
import { assertThrowawaySqlite, openDb, tempDir } from "../mca-load/helpers.js";

const require = createRequire(import.meta.url);
const BetterSqlite = require("better-sqlite3") as typeof import("better-sqlite3");

export const CALENDAR_YYMMDD = "261102";
export const CRS_CREWE = "CRE";
export const CRS_BHAM = "BHM";

export async function createFixtureDb(): Promise<{
  dir: string;
  sqlitePath: string;
  db: BetterSqlite3.Database;
}> {
  const dir = await tempDir("timetable-query-");
  const sqlitePath = `${dir}/timetable.sqlite`;
  assertThrowawaySqlite(sqlitePath);
  const db = new BetterSqlite(sqlitePath);
  db.exec(SCHEMA_SQL);
  return { dir, sqlitePath, db };
}

export function insertLmJourney(
  db: BetterSqlite3.Database,
  options: {
    serviceGuid?: string;
    trainUid?: string;
    status?: string;
    cancelled?: boolean;
    forecastMismatch?: boolean;
  } = {},
): void {
  const serviceGuid = options.serviceGuid ?? "lm-guid-1";
  const trainUid = options.trainUid ?? "C16922";
  const status = options.status ?? "published";
  db.prepare(
    `INSERT INTO journeys (
      service_guid, train_uid, run_date, date_runs_to, days_run,
      origin_crs, dest_crs, origin_std, toc, stp_indicator, status
    ) VALUES (?, ?, '261027', '261027', '1111111', 'CRE', 'BHM', '0925', 'LM', 'P', ?)`,
  ).run(serviceGuid, trainUid, status);
  db.prepare(
    `INSERT INTO calling_points (
      service_guid, seq, tiploc, crs, public_arr, public_dep, forecast_arr, forecast_dep, activity
    ) VALUES (?, 1, 'CREWE', 'CRE', NULL, '0925', NULL, NULL, 'call')`,
  ).run(serviceGuid);
  db.prepare(
    `INSERT INTO calling_points (
      service_guid, seq, tiploc, crs, public_arr, public_dep, forecast_arr, forecast_dep, activity
    ) VALUES (?, 2, 'BHAMNWS', 'BHM', '1025', '1025', ?, ?, 'call')`,
  ).run(
    serviceGuid,
    options.forecastMismatch ? "1030" : null,
    options.forecastMismatch ? "1030" : null,
  );
  db.prepare(
    `INSERT INTO service_day (train_uid, calendar_date, service_guid, cancelled)
     VALUES (?, ?, ?, ?)`,
  ).run(trainUid, CALENDAR_YYMMDD, serviceGuid, options.cancelled ? 1 : 0);
}

export function fileBytes(path: string): Buffer {
  return readFileSync(path);
}
