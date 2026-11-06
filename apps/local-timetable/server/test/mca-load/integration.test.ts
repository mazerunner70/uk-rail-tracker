import assert from "node:assert/strict";
import { join, resolve } from "node:path";
import { describe, it } from "node:test";
import { createLoadController } from "../../src/start.js";
import {
  HOST_SQLITE,
  LM_SCHEDULE,
  VT_SCHEDULE,
  makeHd,
  makeMca,
  openDb,
  tempDir,
  waitUntil,
  writeMca,
} from "./helpers.js";

describe("mca-load integration", () => {
  it("start then progress then throwaway sqlite has LM rows only", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    assert.notEqual(resolve(sqlitePath), HOST_SQLITE);
    await writeMca(
      dir,
      "full-20261027.mca",
      makeMca({
        hd: makeHd("991"),
        schedules: [...VT_SCHEDULE, ...LM_SCHEDULE],
      }),
    );
    const controller = createLoadController({
      mcaDir: dir,
      sqlitePath,
      calendarDate: "20261027",
    });
    const started = controller.start();
    assert.ok(started.state === "running" || started.state === "succeeded");
    await waitUntil(controller, (state) => state === "succeeded");
    const done = controller.progress();
    assert.equal(done.state, "succeeded");
    assert.equal(done.howFar, "100");
    const db = openDb(sqlitePath);
    const journeys = db.prepare("SELECT toc, service_guid FROM journeys").all() as Array<{
      toc: string;
      service_guid: string;
    }>;
    const kafka = db.prepare("SELECT COUNT(*) AS n FROM kafka_offsets").get() as { n: number };
    const applied = db.prepare("SELECT COUNT(*) AS n FROM applied_updates").get() as {
      n: number;
    };
    const forecasts = db
      .prepare("SELECT forecast_arr, forecast_dep FROM calling_points")
      .all() as Array<{ forecast_arr: string | null; forecast_dep: string | null }>;
    db.close();
    assert.equal(journeys.length, 1);
    assert.equal(journeys[0]?.toc, "LM");
    assert.equal(journeys[0]?.service_guid, "C16922_261027_P");
    assert.equal(kafka.n, 0);
    assert.equal(applied.n, 0);
    for (const row of forecasts) {
      assert.equal(row.forecast_arr, null);
      assert.equal(row.forecast_dep, null);
    }
  });
});
