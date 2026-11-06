import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { openTimetableReader } from "../../src/sqliteReader.js";
import {
  CALENDAR_YYMMDD,
  createFixtureDb,
  insertLmJourney,
} from "./helpers.js";

describe("sqliteReader", () => {
  it("reports missing when the file does not exist", () => {
    const result = openTimetableReader("/tmp/does-not-exist-timetable.sqlite");
    assert.equal(result.ok, false);
    if (result.ok) {
      return;
    }
    assert.equal(result.reason, "missing");
  });

  it("opens a current-schema fixture", async () => {
    const { sqlitePath, db } = await createFixtureDb();
    insertLmJourney(db);
    db.close();
    const result = openTimetableReader(sqlitePath);
    assert.equal(result.ok, true);
    result.ok && result.db.close();
  });

  it("reports older-schema when a required column is absent", async () => {
    const { sqlitePath, db } = await createFixtureDb();
    db.exec("DROP TABLE calling_points");
    db.exec(
      "CREATE TABLE calling_points (service_guid TEXT, seq INTEGER PRIMARY KEY)",
    );
    db.close();
    const result = openTimetableReader(sqlitePath);
    assert.equal(result.ok, false);
    if (result.ok) {
      return;
    }
    assert.equal(result.reason, "older-schema");
  });
});

describe("trainViews network", () => {
  it("lists LM journeys for the calendar day", async () => {
    const { sqlitePath, db } = await createFixtureDb();
    insertLmJourney(db);
    db.close();
    const { buildNetworkAnswer } = await import("../../src/trainViews.js");
    const answer = buildNetworkAnswer({
      sqlitePath,
      clock: {
        calendarYyymmdd: () => CALENDAR_YYMMDD,
        nowHhmm: () => "1000",
      },
    });
    assert.ok(!("reason" in answer));
    if ("reason" in answer) {
      return;
    }
    assert.equal(answer.trains.length, 1);
    assert.equal(answer.trains[0]?.serviceGuid, "lm-guid-1");
    assert.equal(answer.trains[0]?.originCrs, "CRE");
    assert.equal(answer.trains[0]?.destCrs, "BHM");
    assert.equal(answer.trains[0]?.scheduledTime, "1025");
    assert.deepEqual(answer.trains[0]?.stops, [
      { crs: "CRE", arr: null, dep: "0925" },
      { crs: "BHM", arr: "1025", dep: "1025" },
    ]);
    assert.equal("graph" in answer, false);
    assert.equal("fraction" in answer.trains[0]!, false);
    assert.equal("atStation" in answer.trains[0]!, false);
  });

  it("tracks forecast times and omits a crs when the stop has none", async () => {
    const { sqlitePath, db } = await createFixtureDb();
    insertLmJourney(db, { forecastMismatch: true });
    db.prepare(
      `INSERT INTO calling_points (
        service_guid, seq, tiploc, crs, public_arr, public_dep, forecast_arr, forecast_dep, activity
      ) VALUES ('lm-guid-1', 3, 'PASSONLY', NULL, '1040', '1042', NULL, NULL, 'call')`,
    ).run();
    db.close();
    const { buildNetworkAnswer } = await import("../../src/trainViews.js");
    const answer = buildNetworkAnswer({
      sqlitePath,
      clock: {
        calendarYyymmdd: () => CALENDAR_YYMMDD,
        nowHhmm: () => "1000",
      },
    });
    assert.ok(!("reason" in answer));
    if ("reason" in answer) return;
    const stops = answer.trains[0]?.stops ?? [];
    assert.equal(stops[1]?.arr, "1030");
    assert.equal(stops[1]?.dep, "1030");
    assert.equal(stops[2]?.arr, "1040");
    assert.equal(stops[2]?.dep, "1042");
    assert.equal(Object.hasOwn(stops[2] ?? {}, "crs"), false);
  });

  it("marks deactivated journeys as problems", async () => {
    const { sqlitePath, db } = await createFixtureDb();
    insertLmJourney(db, { status: "deactivated" });
    db.close();
    const { buildNetworkAnswer } = await import("../../src/trainViews.js");
    const answer = buildNetworkAnswer({
      sqlitePath,
      clock: {
        calendarYyymmdd: () => CALENDAR_YYMMDD,
        nowHhmm: () => "1000",
      },
    });
    assert.ok(!("reason" in answer));
    if ("reason" in answer) {
      return;
    }
    assert.equal(answer.trains[0]?.problem, true);
  });
});

describe("trainViews pair", () => {
  it("returns an empty list when codes match no calling point", async () => {
    const { sqlitePath, db } = await createFixtureDb();
    insertLmJourney(db);
    db.close();
    const { buildStationPairAnswer } = await import("../../src/trainViews.js");
    const answer = buildStationPairAnswer({
      sqlitePath,
      earlierCrs: "XXX",
      laterCrs: "YYY",
      clock: {
        calendarYyymmdd: () => CALENDAR_YYMMDD,
        nowHhmm: () => "1000",
      },
    });
    assert.ok(!("reason" in answer));
    if ("reason" in answer) {
      return;
    }
    assert.equal(answer.trains.length, 0);
  });

  it("includes a cancellation row on the pair view", async () => {
    const { sqlitePath, db } = await createFixtureDb();
    insertLmJourney(db, { cancelled: true });
    db.close();
    const { buildStationPairAnswer } = await import("../../src/trainViews.js");
    const answer = buildStationPairAnswer({
      sqlitePath,
      earlierCrs: "CRE",
      laterCrs: "BHM",
      clock: {
        calendarYyymmdd: () => CALENDAR_YYMMDD,
        nowHhmm: () => "1000",
      },
    });
    assert.ok(!("reason" in answer));
    if ("reason" in answer) {
      return;
    }
    assert.equal(answer.trains.length, 1);
    assert.equal(answer.trains[0]?.serviceGuid, null);
    assert.equal(answer.trains[0]?.issue, true);
  });

  it("does not change sqlite bytes on a train ask", async () => {
    const { sqlitePath, db } = await createFixtureDb();
    insertLmJourney(db);
    db.close();
    const before = (await import("./helpers.js")).fileBytes(sqlitePath);
    const { readOnlyTrainAsk } = await import("../../src/trainViews.js");
    readOnlyTrainAsk({
      sqlitePath,
      earlierCrs: "CRE",
      laterCrs: "BHM",
      clock: {
        calendarYyymmdd: () => CALENDAR_YYMMDD,
        nowHhmm: () => "1000",
      },
    });
    const after = (await import("./helpers.js")).fileBytes(sqlitePath);
    assert.equal(before.compare(after), 0);
  });
});
