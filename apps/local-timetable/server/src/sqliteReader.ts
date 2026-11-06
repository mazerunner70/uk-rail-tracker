import { accessSync, constants } from "node:fs";
import { createRequire } from "node:module";
import type BetterSqlite3 from "better-sqlite3";
import { TABLE_NAMES } from "./sqliteSchema.js";

const require = createRequire(import.meta.url);
const BetterSqlite = require("better-sqlite3") as typeof import("better-sqlite3");

export type ReadFailureReason = "missing" | "deleted" | "locked" | "older-schema";

export type OpenReaderResult =
  | { ok: true; db: BetterSqlite3.Database }
  | { ok: false; reason: ReadFailureReason };

export type OpenDatabase = (path: string) => BetterSqlite3.Database;

const REQUIRED_COLUMNS: Record<string, readonly string[]> = {
  journeys: ["service_guid", "train_uid", "toc", "status"],
  calling_points: [
    "service_guid",
    "seq",
    "crs",
    "public_arr",
    "public_dep",
    "forecast_arr",
    "forecast_dep",
  ],
  service_day: ["train_uid", "calendar_date", "service_guid", "cancelled"],
};

function defaultOpen(path: string): BetterSqlite3.Database {
  return new BetterSqlite(path, { readonly: true, fileMustExist: true });
}

function fileMissing(path: string): boolean {
  try {
    accessSync(path, constants.F_OK);
    return false;
  } catch (err) {
    const code = (err as NodeJS.ErrnoException).code;
    return code === "ENOENT";
  }
}

function schemaIsCurrent(db: BetterSqlite3.Database): boolean {
  for (const table of TABLE_NAMES) {
    const row = db
      .prepare(
        `SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?`,
      )
      .get(table) as { name: string } | undefined;
    if (!row) {
      return false;
    }
    const required = REQUIRED_COLUMNS[table];
    if (!required) {
      continue;
    }
    const info = db.pragma(`table_info(${table})`) as { name: string }[];
    const names = new Set(info.map((col) => col.name));
    for (const col of required) {
      if (!names.has(col)) {
        return false;
      }
    }
  }
  return true;
}

export function openTimetableReader(
  sqlitePath: string,
  deps: { openDatabase?: OpenDatabase } = {},
): OpenReaderResult {
  if (fileMissing(sqlitePath)) {
    return { ok: false, reason: "missing" };
  }
  try {
    accessSync(sqlitePath, constants.R_OK);
  } catch (err) {
    const code = (err as NodeJS.ErrnoException).code;
    if (code === "ENOENT") {
      return { ok: false, reason: "deleted" };
    }
    return { ok: false, reason: "locked" };
  }

  const open = deps.openDatabase ?? defaultOpen;
  let db: BetterSqlite3.Database | undefined;
  try {
    db = open(sqlitePath);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    if (/SQLITE_BUSY|database is locked/i.test(message)) {
      return { ok: false, reason: "locked" };
    }
    if (/no such file|ENOENT/i.test(message)) {
      return { ok: false, reason: "missing" };
    }
    return { ok: false, reason: "locked" };
  }

  if (!schemaIsCurrent(db)) {
    db.close();
    return { ok: false, reason: "older-schema" };
  }
  return { ok: true, db };
}
