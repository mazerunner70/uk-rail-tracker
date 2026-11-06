import { existsSync } from "node:fs";
import { mkdtemp, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { setTimeout as delay } from "node:timers/promises";
import type BetterSqlite3 from "better-sqlite3";
import type { LoadController } from "../../src/start.js";

const require = createRequire(import.meta.url);
const BetterSqlite = require("better-sqlite3") as typeof import("better-sqlite3");

const here = dirname(fileURLToPath(import.meta.url));

export const HOST_SQLITE = resolve(here, "../../../db/timetable.sqlite");
export const FIXTURES_DIR = resolve(here, "../fixtures");

export function assertThrowawaySqlite(sqlitePath: string): void {
  if (resolve(sqlitePath) === HOST_SQLITE) {
    throw new Error("tests must not use the host timetable.sqlite path");
  }
}

export async function tempDir(prefix = "mca-load-"): Promise<string> {
  return mkdtemp(join(tmpdir(), prefix));
}

export function openDb(sqlitePath: string): BetterSqlite3.Database {
  assertThrowawaySqlite(sqlitePath);
  const db = new BetterSqlite(sqlitePath);
  db.pragma("foreign_keys = ON");
  return db;
}

/** RSPS5046 HD: cols 3–32 description, 33–39 current ref, 40–46 last ref, 47 F/U. */
export function makeHdRecord(options: {
  currentRef: string;
  lastRef?: string;
  update?: "F" | "U";
}): string {
  const description = "TPS.UCFCATE.PDTEST1".padEnd(30, " ").slice(0, 30);
  const current = options.currentRef.padEnd(7, " ").slice(0, 7);
  const last = (options.lastRef ?? "TTIS000").padEnd(7, " ").slice(0, 7);
  const update = options.update ?? "U";
  const tail = "011026021026".padEnd(33, " ").slice(0, 33);
  return `HD${description}${current}${last}${update}${tail}`.padEnd(80, " ").slice(0, 80);
}
export function makeHd(sequence: string): string {
  const seq = sequence.replace(/\D/g, "").padStart(3, "0").slice(-3);
  return makeHdRecord({ currentRef: `TTIS${seq}` });
}
export function makeBadHd(): string {
  return makeHdRecord({ currentRef: "DFTTISD" });
}

export const TIP_CREWE =
  "TICREWE  00124300JCREWE                     421402835CRECREWE";
export const TIP_BHAM =
  "TIBHAMNWS40112700EBIRMINGHAM NEW STREET     656302810BHMBIRMINGHAM N ST";
export function makeBs(options: {
  uid?: string;
  from?: string;
  to?: string;
  daysRun?: string;
  stp?: string;
}): string {
  const uid = (options.uid ?? "C16922").padEnd(6, " ").slice(0, 6);
  const from = options.from ?? "261027";
  const to = options.to ?? "261027";
  const daysRun = (options.daysRun ?? "1111111").padEnd(7, "0").slice(0, 7);
  const stp = (options.stp ?? "P").slice(0, 1);
  const rest = " PXX1G453012122300000 EMU506 110      S S          ";
  return `BSN${uid}${from}${to}${daysRun}${rest}${stp}`.padEnd(80, " ").slice(0, 80);
}

export function makeBx(toc: string): string {
  return `BX         ${toc}`.padEnd(80, " ").slice(0, 80);
}

export const LO_CREWE = "LOCREWE   0925 09257  FL     TB";
export const LT_BHAM = "LTBHAMNWS 1025 10255     TF";

export function makeMca(options: {
  hd?: string;
  tips?: string[];
  schedules?: string[];
}): string {
  const parts = [
    options.hd ?? makeHd("991"),
    ...(options.tips ?? [TIP_CREWE, TIP_BHAM]),
    ...(options.schedules ?? []),
    "ZZ",
  ];
  return `${parts.join("\n")}\n`;
}

export const LM_SCHEDULE = [makeBs({}), makeBx("LM"), LO_CREWE, LT_BHAM];
export const VT_SCHEDULE = [
  makeBs({ uid: "C20000" }),
  makeBx("VT"),
  LO_CREWE,
  LT_BHAM,
];

export async function writeMca(dir: string, filename: string, text: string): Promise<string> {
  const path = join(dir, filename);
  await writeFile(path, text, "utf8");
  return path;
}

export async function waitUntil(
  controller: LoadController,
  predicate: (state: string) => boolean,
  timeoutMs = 5000,
): Promise<void> {
  const startAt = Date.now();
  while (!predicate(controller.progress().state)) {
    if (Date.now() - startAt > timeoutMs) {
      throw new Error(
        `timeout waiting for load state; last=${controller.progress().state} ${controller.progress().sentence}`,
      );
    }
    await delay(15);
  }
}

export function hostSqliteUntouched(): boolean {
  return !existsSync(HOST_SQLITE) || resolve(HOST_SQLITE) === HOST_SQLITE;
}
