import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { writeFile } from "node:fs/promises";
import { join, resolve } from "node:path";
import { describe, it } from "node:test";
import type { PreparedJourney } from "../../src/cif.js";
import { replaceFromJourneys } from "../../src/sqliteReplacer.js";
import { assertThrowawaySqlite, HOST_SQLITE, openDb, tempDir } from "./helpers.js";

function journey(overrides: Partial<PreparedJourney> = {}): PreparedJourney {
  return {
    serviceGuid: "C16922_261027_P",
    trainUid: "C16922",
    runDate: "261027",
    dateRunsTo: "261027",
    daysRun: "1111111",
    originCrs: "CRE",
    destCrs: "BHM",
    originStd: "0925",
    toc: "LM",
    stpIndicator: "P",
    status: "published",
    callingPoints: [
      {
        seq: 0,
        tiploc: "CREWE",
        crs: "CRE",
        publicArr: null,
        publicDep: "0925",
        activity: "origin",
      },
      {
        seq: 1,
        tiploc: "BHAMNWS",
        crs: "BHM",
        publicArr: "1025",
        publicDep: null,
        activity: "destination",
      },
    ],
    ...overrides,
  };
}

describe("SqliteReplacer", () => {
  it("keeps London Midland journeys", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    assertThrowawaySqlite(sqlitePath);
    replaceFromJourneys(sqlitePath, [journey()], {
      loadDay: "20261027",
      hdSequence: "991",
    });
    const db = openDb(sqlitePath);
    const rows = db.prepare("SELECT toc, service_guid FROM journeys").all() as Array<{
      toc: string;
      service_guid: string;
    }>;
    db.close();
    assert.equal(rows.length, 1);
    assert.equal(rows[0]?.toc, "LM");
    assert.equal(rows[0]?.service_guid, "C16922_261027_P");
  });

  it("drops non-LM journeys", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    replaceFromJourneys(
      sqlitePath,
      [
        journey({
          toc: "VT",
          trainUid: "C20000",
          serviceGuid: "C20000_261027_P",
        }),
        journey(),
      ],
      { loadDay: "20261027", hdSequence: "991" },
    );
    const db = openDb(sqlitePath);
    const rows = db.prepare("SELECT train_uid FROM journeys").all() as Array<{
      train_uid: string;
    }>;
    db.close();
    assert.deepEqual(
      rows.map((row) => row.train_uid),
      ["C16922"],
    );
  });

  it("creates overlay tables with zero rows", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    replaceFromJourneys(sqlitePath, [journey()], {
      loadDay: "20261027",
      hdSequence: "991",
    });
    const db = openDb(sqlitePath);
    const kafka = db.prepare("SELECT COUNT(*) AS n FROM kafka_offsets").get() as { n: number };
    const applied = db.prepare("SELECT COUNT(*) AS n FROM applied_updates").get() as {
      n: number;
    };
    db.close();
    assert.equal(kafka.n, 0);
    assert.equal(applied.n, 0);
  });

  it("leaves forecast columns empty", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    replaceFromJourneys(sqlitePath, [journey()], {
      loadDay: "20261027",
      hdSequence: "991",
    });
    const db = openDb(sqlitePath);
    const points = db
      .prepare("SELECT forecast_arr, forecast_dep FROM calling_points")
      .all() as Array<{ forecast_arr: string | null; forecast_dep: string | null }>;
    db.close();
    assert.equal(points.length, 2);
    for (const point of points) {
      assert.equal(point.forecast_arr, null);
      assert.equal(point.forecast_dep, null);
    }
  });

  it("stores unknown calling-point CRS as null", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    replaceFromJourneys(
      sqlitePath,
      [
        journey({
          callingPoints: [
            {
              seq: 0,
              tiploc: "CREWE",
              crs: "CRE",
              publicArr: null,
              publicDep: "0925",
              activity: "origin",
            },
            {
              seq: 1,
              tiploc: "UNKNOWN",
              crs: null,
              publicArr: "1000",
              publicDep: "1001",
              activity: "call",
            },
            {
              seq: 2,
              tiploc: "BHAMNWS",
              crs: "BHM",
              publicArr: "1025",
              publicDep: null,
              activity: "destination",
            },
          ],
        }),
      ],
      { loadDay: "20261027", hdSequence: "991" },
    );
    const db = openDb(sqlitePath);
    const unknown = db
      .prepare("SELECT crs FROM calling_points WHERE tiploc = ?")
      .get("UNKNOWN") as { crs: string | null };
    db.close();
    assert.equal(unknown.crs, null);
  });

  it("stores load facts in feed_meta and never secrets", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    replaceFromJourneys(sqlitePath, [journey()], {
      loadDay: "20261027",
      hdSequence: "991",
    });
    const db = openDb(sqlitePath);
    const rows = db.prepare("SELECT key, value FROM feed_meta").all() as Array<{
      key: string;
      value: string;
    }>;
    db.close();
    const map = Object.fromEntries(rows.map((row) => [row.key, row.value]));
    assert.equal(map.load_day, "20261027");
    assert.equal(map.hd_sequence, "991");
    assert.equal("kafka_password" in map, false);
    assert.equal("darwin_key" in map, false);
  });

  it("write-fail after unlink leaves a missing file", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    await writeFile(sqlitePath, "previous");
    assert.equal(existsSync(sqlitePath), true);
    assert.throws(() =>
      replaceFromJourneys(
        sqlitePath,
        [journey()],
        {
          loadDay: "20261027",
          hdSequence: "991",
        },
        {
          afterUnlink: () => {
            throw new Error("write failed");
          },
        },
      ),
    );
    assert.equal(existsSync(sqlitePath), false);
  });

  it("never unlinks the host timetable.sqlite", async () => {
    const dir = await tempDir();
    const sqlitePath = join(dir, "timetable.sqlite");
    assert.notEqual(resolve(sqlitePath), HOST_SQLITE);
    replaceFromJourneys(sqlitePath, [journey()], {
      loadDay: "20261027",
      hdSequence: "991",
    });
    assert.notEqual(resolve(sqlitePath), HOST_SQLITE);
  });
});
