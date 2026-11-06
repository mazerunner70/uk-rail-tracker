import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { describe, it } from "node:test";
import { networkSchematicPath } from "../../src/networkSchematicFile.js";
import { createLoadController } from "../../src/start.js";
import { locateFailureSentence } from "../../src/loadSentences.js";
import {
  FIXTURES_DIR,
  HOST_SQLITE,
  LM_SCHEDULE,
  makeMca,
  openDb,
  tempDir,
  waitUntil,
  writeMca,
} from "./helpers.js";

describe("start and progress", () => {
  it("returns running at once and does not wait for the replace", async () => {
    const dir = await tempDir();
    await writeMca(dir, "full-20261027.mca", makeMca({ schedules: LM_SCHEDULE }));
    const sqlitePath = join(dir, "timetable.sqlite");
    let released = false;
    let release = (): void => {};
    const gate = new Promise<void>((resolve) => {
      release = () => {
        released = true;
        resolve();
      };
    });
    const controller = createLoadController({
      mcaDir: dir,
      sqlitePath,
      calendarDate: "20261027",
      beforeWork: () => gate,
    });
    const first = controller.start();
    assert.equal(first.state, "running");
    assert.equal(released, false);
    release();
    await waitUntil(controller, (state) => state !== "running");
  });

  it("progress does not start a load", async () => {
    const dir = await tempDir();
    const controller = createLoadController({
      mcaDir: dir,
      sqlitePath: join(dir, "timetable.sqlite"),
      calendarDate: "20261027",
    });
    const before = controller.progress();
    assert.equal(before.state, "idle");
    const again = controller.progress();
    assert.equal(again.state, "idle");
  });

  it("start while running returns the current progress", async () => {
    const dir = await tempDir();
    await writeMca(dir, "full-20261027.mca", makeMca({ schedules: LM_SCHEDULE }));
    const sqlitePath = join(dir, "timetable.sqlite");
    let release = (): void => {};
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const controller = createLoadController({
      mcaDir: dir,
      sqlitePath,
      calendarDate: "20261027",
      beforeWork: () => gate,
    });
    const first = controller.start();
    const second = controller.start();
    assert.equal(first.state, "running");
    assert.equal(second.state, "running");
    assert.equal(second.sentence, first.sentence);
    release();
    await waitUntil(controller, (state) => state !== "running");
  });

  it("start after success begins a new replace", async () => {
    const dir = await tempDir();
    await writeMca(dir, "full-20261027.mca", makeMca({ schedules: LM_SCHEDULE }));
    const sqlitePath = join(dir, "timetable.sqlite");
    const controller = createLoadController({
      mcaDir: dir,
      sqlitePath,
      calendarDate: "20261027",
    });
    controller.start();
    await waitUntil(controller, (state) => state === "succeeded");
    const again = controller.start();
    assert.ok(again.state === "running" || again.state === "succeeded");
    await waitUntil(controller, (state) => state === "succeeded");
  });

  it("missing MCA fails and leaves sqlite unchanged", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    const marker = "keep-me";
    const { writeFile, readFile: read } = await import("node:fs/promises");
    await writeFile(sqlitePath, marker);
    const controller = createLoadController({
      mcaDir: dir,
      sqlitePath,
      calendarDate: "20261027",
    });
    controller.start();
    await waitUntil(controller, (state) => state === "failed");
    assert.equal(
      controller.progress().sentence,
      locateFailureSentence("no-match", "20261027"),
    );
    assert.equal(await read(sqlitePath, "utf8"), marker);
    assert.notEqual(sqlitePath, HOST_SQLITE);
  });

  it("duplicate day's file fails and leaves sqlite unchanged", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    const { writeFile, readFile: read } = await import("node:fs/promises");
    await writeFile(sqlitePath, "old");
    await writeMca(dir, "a-20261027.mca", makeMca({ schedules: LM_SCHEDULE }));
    await writeMca(dir, "b-20261027.mca", makeMca({ schedules: LM_SCHEDULE }));
    const controller = createLoadController({
      mcaDir: dir,
      sqlitePath,
      calendarDate: "20261027",
    });
    controller.start();
    await waitUntil(controller, (state) => state === "failed");
    assert.equal(
      controller.progress().sentence,
      locateFailureSentence("duplicate", "20261027"),
    );
    assert.equal(await read(sqlitePath, "utf8"), "old");
  });

  it("full-extract HD loads and keeps the RJTTF sequence", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    const line = "HDTPS.UCFCATE.PD1107191907112108DFTTISS       FA190711300912".padEnd(
      80,
      " ",
    );
    await writeMca(
      dir,
      "RJTTF007MCA-20261027.mca",
      makeMca({ hd: line, schedules: LM_SCHEDULE }),
    );
    const controller = createLoadController({
      mcaDir: dir,
      sqlitePath,
      calendarDate: "20261027",
    });
    controller.start();
    await waitUntil(controller, (state) => state === "succeeded");
    const db = openDb(sqlitePath);
    try {
      const row = db
        .prepare("SELECT value FROM feed_meta WHERE key = 'hd_sequence'")
        .get() as { value: string };
      assert.equal(row.value, "007");
    } finally {
      db.close();
    }
    const schematicPath = networkSchematicPath(sqlitePath);
    assert.equal(existsSync(schematicPath), true);
    const schematic = JSON.parse(await readFile(schematicPath, "utf8")) as { hdSequence: string };
    assert.equal(schematic.hdSequence, "007");
  });

  it("bad HD fails before unlink", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    const { writeFile, readFile: read } = await import("node:fs/promises");
    await writeFile(sqlitePath, "old");
    await writeMca(
      dir,
      "full-20261027.mca",
      makeMca({ hd: "BSNC169222610272610271111111", schedules: LM_SCHEDULE }),
    );
    const controller = createLoadController({
      mcaDir: dir,
      sqlitePath,
      calendarDate: "20261027",
    });
    controller.start();
    await waitUntil(controller, (state) => state === "failed");
    assert.equal(controller.progress().sentence, "The MCA header was refused.");
    assert.equal(await read(sqlitePath, "utf8"), "old");
  });

  it("reads the synthetic fixture file from test/fixtures", async () => {
    const text = await readFile(join(FIXTURES_DIR, "valid-lm.mca"), "utf8");
    assert.ok(text.startsWith("HD"));
    assert.ok(text.includes("BX         LM"));
  });
});
