import assert from "node:assert/strict";
import { join } from "node:path";
import { describe, it } from "node:test";
import { createLoadController } from "../../src/start.js";
import type { OverlayHooks } from "../../src/start.js";
import {
  LM_SCHEDULE,
  makeMca,
  tempDir,
  waitUntil,
  writeMca,
} from "../mca-load/helpers.js";

function fakeOverlay(): OverlayHooks & {
  pauses: number;
  resumes: number;
  pauseGate: Promise<void>;
  releasePause: () => void;
} {
  let releasePause = (): void => {};
  const pauseGate = new Promise<void>((resolve) => {
    releasePause = resolve;
  });
  const hooks = {
    pauses: 0,
    resumes: 0,
    pauseGate,
    releasePause,
    pause: async () => {
      hooks.pauses += 1;
      await pauseGate;
    },
    resume: async () => {
      hooks.resumes += 1;
    },
  };
  return hooks;
}

describe("load hand-off overlay hooks", () => {
  it("does not openReplacement until pause resolves", async () => {
    const dir = await tempDir();
    await writeMca(dir, "full-20261027.mca", makeMca({ schedules: LM_SCHEDULE }));
    const sqlitePath = join(dir, "timetable.sqlite");
    const overlay = fakeOverlay();
    const controller = createLoadController({
      mcaDir: dir,
      sqlitePath,
      calendarDate: "20261027",
      overlay,
    });
    controller.start();
    assert.equal(overlay.pauses, 1);
    assert.equal(overlay.resumes, 0);
    assert.equal(controller.progress().state, "running");
    overlay.releasePause();
    await waitUntil(controller, (state) => state === "succeeded");
    assert.equal(overlay.resumes, 1);
  });

  it("resumes after header refusal", async () => {
    const dir = await tempDir();
    await writeMca(dir, "full-20261027.mca", "not-an-hd-record\n");
    const sqlitePath = join(dir, "timetable.sqlite");
    const overlay = fakeOverlay();
    overlay.releasePause();
    const controller = createLoadController({
      mcaDir: dir,
      sqlitePath,
      calendarDate: "20261027",
      overlay,
    });
    controller.start();
    await waitUntil(controller, (state) => state === "failed");
    assert.equal(overlay.pauses, 1);
    assert.equal(overlay.resumes, 1);
  });

  it("resumes when MCA is missing", async () => {
    const dir = await tempDir();
    const overlay = fakeOverlay();
    overlay.releasePause();
    const controller = createLoadController({
      mcaDir: dir,
      sqlitePath: join(dir, "timetable.sqlite"),
      calendarDate: "20261027",
      overlay,
    });
    controller.start();
    await waitUntil(controller, (state) => state === "failed");
    assert.equal(overlay.resumes, 1);
  });

  it("stays paused after write failure that deletes the file", async () => {
    const dir = await tempDir();
    await writeMca(dir, "full-20261027.mca", makeMca({ schedules: LM_SCHEDULE }));
    const sqlitePath = join(dir, "timetable.sqlite");
    const overlay = fakeOverlay();
    overlay.releasePause();
    const controller = createLoadController({
      mcaDir: dir,
      sqlitePath,
      calendarDate: "20261027",
      overlay,
      openDatabase: () => {
        throw new Error("forced write failure");
      },
    });
    controller.start();
    await waitUntil(controller, (state) => state === "failed");
    assert.equal(overlay.pauses, 1);
    assert.equal(overlay.resumes, 0);
  });
});
