import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { rm } from "node:fs/promises";
import {
  applyKafkaMessage,
  kafkaOffsetsForTopic,
  openOverlayWriter,
  startOffsetForPartition,
} from "../../src/overlayWriter.js";
import {
  buildNetworkAnswer,
  buildStationPairAnswer,
} from "../../src/trainViews.js";
import {
  CALENDAR_YYMMDD,
  createFixtureDb,
  insertLmJourney,
} from "../timetable-query/helpers.js";

const clock = {
  calendarYyymmdd: () => CALENDAR_YYMMDD,
  nowHhmm: () => "1000",
};

function calendarSsd(): string {
  return `20${CALENDAR_YYMMDD.slice(0, 2)}-${CALENDAR_YYMMDD.slice(2, 4)}-${CALENDAR_YYMMDD.slice(4, 6)}`;
}

describe("overlayWriter", () => {
  const dirs: string[] = [];

  afterEach(async () => {
    while (dirs.length > 0) {
      const dir = dirs.pop();
      if (dir) await rm(dir, { recursive: true, force: true });
    }
  });

  it("updates forecast times and leaves public times", async () => {
    const fixture = await createFixtureDb();
    dirs.push(fixture.dir);
    openOverlayWriter(fixture.db);
    insertLmJourney(fixture.db, { serviceGuid: "guid-1", trainUid: "C16922" });

    applyKafkaMessage(
      fixture.db,
      {
        uR: {
          TS: {
            rid: "202605178765432",
            uid: "C16922",
            ssd: calendarSsd(),
            Location: { tpl: "CREWE", dep: { et: "09:30" } },
          },
        },
      },
      { topic: "darwin.json", partition: 0, offset: 10 },
      { nowIso: () => "2026-11-02T12:00:00.000Z" },
    );

    const point = fixture.db
      .prepare(
        `SELECT public_dep, forecast_dep FROM calling_points
         WHERE service_guid = 'guid-1' AND tiploc = 'CREWE'`,
      )
      .get() as { public_dep: string; forecast_dep: string };
    assert.equal(point.public_dep, "0925");
    assert.equal(point.forecast_dep, "0930");
    assert.equal(kafkaOffsetsForTopic(fixture.db, "darwin.json").get(0), 11);

    const alias = fixture.db
      .prepare(
        `SELECT service_guid FROM service_aliases
         WHERE alias_type = 'rid' AND alias_value = '202605178765432'`,
      )
      .get() as { service_guid: string };
    assert.equal(alias.service_guid, "guid-1");
    const audit = fixture.db
      .prepare(
        `SELECT event_tags FROM applied_updates WHERE service_guid = 'guid-1'`,
      )
      .get() as { event_tags: string };
    const tags = JSON.parse(audit.event_tags) as string[];
    assert.ok(tags.includes("call_forecast"));
    fixture.db.close();
  });

  it("persists platform and train length from TS and overwrites on later update", async () => {
    const fixture = await createFixtureDb();
    dirs.push(fixture.dir);
    openOverlayWriter(fixture.db);
    insertLmJourney(fixture.db, { serviceGuid: "guid-1", trainUid: "C16922" });
    const payload = (plat: string, length: number) => ({
      uR: {
        TS: {
          rid: "r-plat",
          uid: "C16922",
          ssd: calendarSsd(),
          Location: {
            tpl: "CREWE",
            dep: { et: "09:30" },
            plat: { "": plat },
            length,
          },
        },
      },
    });
    applyKafkaMessage(fixture.db, payload("3", 8), {
      topic: "t",
      partition: 0,
      offset: 1,
    });
    let point = fixture.db
      .prepare(
        `SELECT platform, platform_suppressed, train_length FROM calling_points
         WHERE tiploc = 'CREWE'`,
      )
      .get() as {
        platform: string;
        platform_suppressed: number;
        train_length: number;
      };
    assert.equal(point.platform, "3");
    assert.equal(point.platform_suppressed, 0);
    assert.equal(point.train_length, 8);

    applyKafkaMessage(fixture.db, payload("4", 10), {
      topic: "t",
      partition: 0,
      offset: 2,
    });
    point = fixture.db
      .prepare(
        `SELECT platform, train_length FROM calling_points WHERE tiploc = 'CREWE'`,
      )
      .get() as { platform: string; train_length: number };
    assert.equal(point.platform, "4");
    assert.equal(point.train_length, 10);
    fixture.db.close();

    const network = buildNetworkAnswer({
      sqlitePath: fixture.sqlitePath,
      clock,
    });
    assert.ok(!("reason" in network));
    if ("reason" in network) return;
    const stop = network.trains[0]?.stops.find((row) => row.crs === "CRE");
    assert.equal(stop?.platform, "4");
    assert.equal(stop?.trainLength, 10);

    const { buildServiceDetailAnswer } = await import("../../src/trainViews.js");
    const detail = buildServiceDetailAnswer({
      sqlitePath: fixture.sqlitePath,
      clock,
      serviceGuid: "guid-1",
    });
    assert.ok(!("reason" in detail) && !("missing" in detail));
    if ("reason" in detail || "missing" in detail) return;
    assert.equal(detail.trainLength, 10);
    assert.equal(detail.stops[0]?.platform, "4");
    assert.ok(detail.updates.length >= 2);
  });

  it("replaces a second TS forecast for the same stop", async () => {
    const fixture = await createFixtureDb();
    dirs.push(fixture.dir);
    openOverlayWriter(fixture.db);
    insertLmJourney(fixture.db, { serviceGuid: "guid-1", trainUid: "C16922" });
    const payload = (dep: string) => ({
      uR: {
        TS: {
          rid: "r1",
          uid: "C16922",
          ssd: calendarSsd(),
          Location: { tpl: "CREWE", dep: { et: dep } },
        },
      },
    });
    applyKafkaMessage(fixture.db, payload("09:30"), {
      topic: "t",
      partition: 0,
      offset: 1,
    });
    applyKafkaMessage(fixture.db, payload("09:35"), {
      topic: "t",
      partition: 0,
      offset: 2,
    });
    const point = fixture.db
      .prepare(
        `SELECT forecast_dep FROM calling_points WHERE tiploc = 'CREWE'`,
      )
      .get() as { forecast_dep: string };
    assert.equal(point.forecast_dep, "0935");
    fixture.db.close();
  });

  it("sets service_day.cancelled from schedule can and shows on pair view", async () => {
    const fixture = await createFixtureDb();
    dirs.push(fixture.dir);
    openOverlayWriter(fixture.db);
    insertLmJourney(fixture.db, { serviceGuid: "guid-1", trainUid: "C16922" });

    applyKafkaMessage(
      fixture.db,
      {
        uR: {
          schedule: {
            rid: "r1",
            uid: "C16922",
            ssd: calendarSsd(),
            can: true,
          },
        },
      },
      { topic: "t", partition: 0, offset: 3 },
    );
    const day = fixture.db
      .prepare(`SELECT cancelled FROM service_day WHERE train_uid = 'C16922'`)
      .get() as { cancelled: number };
    assert.equal(day.cancelled, 1);
    fixture.db.close();

    const pair = buildStationPairAnswer({
      sqlitePath: fixture.sqlitePath,
      clock,
      earlierCrs: "CRE",
      laterCrs: "BHM",
    });
    assert.ok(!("reason" in pair));
    if (!("reason" in pair)) {
      assert.ok(pair.trains.some((t) => t.status === "cancelled"));
    }
  });

  it("clears cancelled when can is false", async () => {
    const fixture = await createFixtureDb();
    dirs.push(fixture.dir);
    openOverlayWriter(fixture.db);
    insertLmJourney(fixture.db, {
      serviceGuid: "guid-1",
      trainUid: "C16922",
      cancelled: true,
    });
    applyKafkaMessage(
      fixture.db,
      {
        uR: {
          schedule: {
            rid: "r1",
            uid: "C16922",
            ssd: calendarSsd(),
            can: false,
          },
        },
      },
      { topic: "t", partition: 0, offset: 4 },
    );
    const cleared = fixture.db
      .prepare(`SELECT cancelled FROM service_day WHERE train_uid = 'C16922'`)
      .get() as { cancelled: number };
    assert.equal(cleared.cancelled, 0);
    fixture.db.close();
  });

  it("deactivates a journey and marks problem on the network view", async () => {
    const fixture = await createFixtureDb();
    dirs.push(fixture.dir);
    openOverlayWriter(fixture.db);
    insertLmJourney(fixture.db, { serviceGuid: "guid-1", trainUid: "C16922" });
    applyKafkaMessage(
      fixture.db,
      {
        uR: {
          deactivated: { rid: "r1", uid: "C16922", ssd: calendarSsd() },
        },
      },
      { topic: "t", partition: 0, offset: 5 },
    );
    const journey = fixture.db
      .prepare(`SELECT status FROM journeys WHERE service_guid = 'guid-1'`)
      .get() as { status: string };
    assert.equal(journey.status, "deactivated");
    fixture.db.close();

    const network = buildNetworkAnswer({
      sqlitePath: fixture.sqlitePath,
      clock,
    });
    assert.ok(!("reason" in network));
    if (!("reason" in network)) {
      assert.equal(network.trains[0]?.problem, true);
      assert.equal(network.trains[0]?.statusLabel, "deactivated");
    }
  });

  it("binds by rid after the first service_day hit", async () => {
    const fixture = await createFixtureDb();
    dirs.push(fixture.dir);
    openOverlayWriter(fixture.db);
    insertLmJourney(fixture.db, { serviceGuid: "guid-1", trainUid: "C16922" });
    applyKafkaMessage(
      fixture.db,
      {
        uR: {
          TS: {
            rid: "rid-only-later",
            uid: "C16922",
            ssd: calendarSsd(),
            Location: { tpl: "CREWE", dep: { et: "09:40" } },
          },
        },
      },
      { topic: "t", partition: 0, offset: 6 },
    );
    applyKafkaMessage(
      fixture.db,
      {
        uR: {
          TS: {
            rid: "rid-only-later",
            Location: { tpl: "CREWE", dep: { et: "09:41" } },
          },
        },
      },
      { topic: "t", partition: 0, offset: 7 },
    );
    const point = fixture.db
      .prepare(`SELECT forecast_dep FROM calling_points WHERE tiploc = 'CREWE'`)
      .get() as { forecast_dep: string };
    assert.equal(point.forecast_dep, "0941");
    fixture.db.close();
  });

  it("advances offset on unknown UID without creating a journey", async () => {
    const fixture = await createFixtureDb();
    dirs.push(fixture.dir);
    openOverlayWriter(fixture.db);
    insertLmJourney(fixture.db, { serviceGuid: "guid-1", trainUid: "C16922" });
    applyKafkaMessage(
      fixture.db,
      {
        uR: {
          TS: {
            rid: "x",
            uid: "Y99999",
            ssd: "260517",
            Location: { tpl: "CREWE", dep: { et: "08:01" } },
          },
        },
      },
      { topic: "t", partition: 1, offset: 99 },
    );
    assert.equal(kafkaOffsetsForTopic(fixture.db, "t").get(1), 100);
    const journeys = fixture.db
      .prepare(`SELECT COUNT(*) AS n FROM journeys`)
      .get() as { n: number };
    assert.equal(journeys.n, 1);
    const updates = fixture.db
      .prepare(`SELECT COUNT(*) AS n FROM applied_updates`)
      .get() as { n: number };
    assert.equal(updates.n, 0);
    fixture.db.close();
  });

  it("rolls back forecast and offset when a statement fails", async () => {
    const fixture = await createFixtureDb();
    dirs.push(fixture.dir);
    openOverlayWriter(fixture.db);
    insertLmJourney(fixture.db, { serviceGuid: "guid-1", trainUid: "C16922" });

    const original = fixture.db.prepare.bind(fixture.db);
    let boom = false;
    fixture.db.prepare = ((sql: string) => {
      const stmt = original(sql);
      if (boom && sql.includes("INSERT INTO applied_updates")) {
        return {
          run: () => {
            throw new Error("forced failure");
          },
        } as ReturnType<typeof original>;
      }
      return stmt;
    }) as typeof fixture.db.prepare;

    boom = true;
    assert.throws(() =>
      applyKafkaMessage(
        fixture.db,
        {
          uR: {
            TS: {
              rid: "r1",
              uid: "C16922",
              ssd: calendarSsd(),
              Location: { tpl: "CREWE", dep: { et: "09:50" } },
            },
          },
        },
        { topic: "t", partition: 0, offset: 20 },
      ),
    );

    fixture.db.prepare = original;
    const point = fixture.db
      .prepare(`SELECT forecast_dep FROM calling_points WHERE tiploc = 'CREWE'`)
      .get() as { forecast_dep: string | null };
    assert.equal(point.forecast_dep, null);
    assert.equal(kafkaOffsetsForTopic(fixture.db, "t").size, 0);
    fixture.db.close();
  });

  it("chooses seek offsets from saved rows or the live end", () => {
    assert.equal(startOffsetForPartition(12, 100), 12);
    assert.equal(startOffsetForPartition(undefined, 100), 100);
  });
});
