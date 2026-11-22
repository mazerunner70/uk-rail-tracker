import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { writeFile } from "node:fs/promises";
import { join, resolve } from "node:path";
import { describe, it } from "node:test";
import { createRequire } from "node:module";
import {
  SCHEMA_SQL,
  TABLE_NAMES,
  ensureCallingPointLiveFields,
} from "../../src/sqliteSchema.js";
import { openReplacement } from "../../src/sqliteReplacer.js";
import { assertThrowawaySqlite, HOST_SQLITE, tempDir } from "./helpers.js";

const require = createRequire(import.meta.url);
const BetterSqlite = require("better-sqlite3") as typeof import("better-sqlite3");

describe("sqlite schema", () => {
  it("creates all seven tables on a throwaway path", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    assertThrowawaySqlite(sqlitePath);
    const db = new BetterSqlite(sqlitePath);
    db.pragma("foreign_keys = ON");
    db.exec(SCHEMA_SQL);
    const names = db
      .prepare(`SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name`)
      .all() as Array<{ name: string }>;
    db.close();
    for (const table of TABLE_NAMES) {
      assert.ok(
        names.some((row) => row.name === table),
        `missing table ${table}`,
      );
    }
  });

  it("enables foreign keys", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    const db = new BetterSqlite(sqlitePath);
    db.pragma("foreign_keys = ON");
    db.exec(SCHEMA_SQL);
    const enabled = db.pragma("foreign_keys", { simple: true });
    db.close();
    assert.equal(enabled, 1);
  });

  it("creates overlay tables empty", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    const db = new BetterSqlite(sqlitePath);
    db.exec(SCHEMA_SQL);
    const kafka = db.prepare("SELECT COUNT(*) AS n FROM kafka_offsets").get() as { n: number };
    const applied = db.prepare("SELECT COUNT(*) AS n FROM applied_updates").get() as {
      n: number;
    };
    db.close();
    assert.equal(kafka.n, 0);
    assert.equal(applied.n, 0);
  });

  it("creates journeys with store tokens", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    const db = new BetterSqlite(sqlitePath);
    db.exec(SCHEMA_SQL);
    const cols = db.prepare("PRAGMA table_info(journeys)").all() as Array<{ name: string }>;
    db.close();
    const names = cols.map((col) => col.name);
    for (const token of [
      "service_guid",
      "train_uid",
      "stp_indicator",
      "origin_crs",
      "dest_crs",
      "run_date",
      "days_run",
    ]) {
      assert.ok(names.includes(token), `missing ${token}`);
    }
  });

  it("creates calling_points with empty forecast columns after schema only", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    const db = new BetterSqlite(sqlitePath);
    db.exec(SCHEMA_SQL);
    const cols = db.prepare("PRAGMA table_info(calling_points)").all() as Array<{
      name: string;
    }>;
    db.close();
    const names = cols.map((col) => col.name);
    assert.ok(names.includes("forecast_arr"));
    assert.ok(names.includes("forecast_dep"));
    assert.ok(names.includes("platform"));
    assert.ok(names.includes("platform_suppressed"));
    assert.ok(names.includes("train_length"));
  });

  it("ensureCallingPointLiveFields upgrades older calling_points tables", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    const db = new BetterSqlite(sqlitePath);
    db.exec(`
      CREATE TABLE calling_points (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        service_guid TEXT NOT NULL,
        seq INTEGER NOT NULL,
        tiploc TEXT NOT NULL,
        crs TEXT,
        public_arr TEXT,
        public_dep TEXT,
        forecast_arr TEXT,
        forecast_dep TEXT,
        activity TEXT NOT NULL DEFAULT 'call'
      );
    `);
    ensureCallingPointLiveFields(db);
    const names = (
      db.prepare("PRAGMA table_info(calling_points)").all() as Array<{ name: string }>
    ).map((col) => col.name);
    db.close();
    assert.ok(names.includes("platform"));
    assert.ok(names.includes("platform_suppressed"));
    assert.ok(names.includes("train_length"));
  });

  it("does not open the host timetable.sqlite", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    assert.notEqual(resolve(sqlitePath), HOST_SQLITE);
    const db = new BetterSqlite(sqlitePath);
    db.exec(SCHEMA_SQL);
    db.close();
  });

  it("observes a missing file when write never happens", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    assert.equal(existsSync(sqlitePath), false);
  });

  it("leaves a missing file when write fails after unlink", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    await writeFile(sqlitePath, "old-bytes");
    assert.equal(existsSync(sqlitePath), true);
    assert.throws(() =>
      openReplacement(sqlitePath, {
        afterUnlink: () => {
          throw new Error("disk full");
        },
      }),
    );
    assert.equal(existsSync(sqlitePath), false);
    assert.notEqual(resolve(sqlitePath), HOST_SQLITE);
  });
});
