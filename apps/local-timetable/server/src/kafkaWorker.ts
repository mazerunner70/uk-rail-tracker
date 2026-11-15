import { existsSync } from "node:fs";
import { createRequire } from "node:module";
import { parentPort, workerData } from "node:worker_threads";
import type BetterSqlite3 from "better-sqlite3";
import { startLiveConsumer, type LiveConsumer } from "./kafkaConsumer.js";
import { readKafkaSettings } from "./kafkaSettings.js";
import { openOverlayWriter } from "./overlayWriter.js";

const require = createRequire(import.meta.url);
const BetterSqlite = require("better-sqlite3") as typeof import("better-sqlite3");

export type WorkerInbound = { type: "pause" } | { type: "resume" };
export type WorkerOutbound =
  | { type: "paused" }
  | { type: "resumed" }
  | { type: "idle"; reason: "not-configured" | "missing-db" };

type WorkerData = { sqlitePath: string };

const data = workerData as WorkerData;
if (!parentPort) {
  throw new Error("kafkaWorker must run as a Worker thread");
}
const port = parentPort;

let paused = true;
let db: BetterSqlite3.Database | null = null;
let live: LiveConsumer | null = null;
let starting: Promise<void> | null = null;

function post(message: WorkerOutbound): void {
  port.postMessage(message);
}

function closeDb(): void {
  if (db) {
    try {
      db.close();
    } catch {
      // already closed
    }
    db = null;
  }
}

async function stopLive(): Promise<void> {
  if (live) {
    const current = live;
    live = null;
    await current.stop();
  }
}

async function pauseConsumer(): Promise<void> {
  paused = true;
  if (starting) {
    await starting.catch(() => undefined);
  }
  await stopLive();
  closeDb();
  post({ type: "paused" });
}

async function resumeConsumer(): Promise<void> {
  paused = false;
  const settings = readKafkaSettings();
  if (!settings.ok) {
    console.error("kafka overlay idle: KAFKA_* settings are missing or empty");
    post({ type: "idle", reason: "not-configured" });
    return;
  }
  if (!existsSync(data.sqlitePath)) {
    console.error("kafka overlay idle: timetable sqlite is missing", data.sqlitePath);
    post({ type: "idle", reason: "missing-db" });
    return;
  }

  closeDb();
  db = new BetterSqlite(data.sqlitePath);
  openOverlayWriter(db);

  starting = (async () => {
    await stopLive();
    if (paused || !db) {
      return;
    }
    live = await startLiveConsumer({
      db,
      settings: settings.settings,
      isPaused: () => paused,
    });
  })();

  try {
    await starting;
  } catch (err) {
    const text = err instanceof Error ? err.message : String(err);
    console.error("kafka overlay consumer failed to start", text);
    closeDb();
    post({ type: "idle", reason: "not-configured" });
    return;
  } finally {
    starting = null;
  }

  if (!paused) {
    post({ type: "resumed" });
  }
}

port.on("message", (message: WorkerInbound) => {
  if (message.type === "pause") {
    void pauseConsumer();
    return;
  }
  if (message.type === "resume") {
    void resumeConsumer();
  }
});
