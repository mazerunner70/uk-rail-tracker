import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { rm } from "node:fs/promises";
import { applyKafkaMessage, openOverlayWriter } from "../../src/overlayWriter.js";
import { buildNetworkAnswer } from "../../src/trainViews.js";
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

describe("network delta query", () => {
  const dirs: string[] = [];

  afterEach(async () => {
    while (dirs.length > 0) {
      const dir = dirs.pop();
      if (dir) await rm(dir, { recursive: true, force: true });
    }
  });

  async function seeded() {
    const fixture = await createFixtureDb();
    dirs.push(fixture.dir);
    openOverlayWriter(fixture.db);
    insertLmJourney(fixture.db, { serviceGuid: "guid-1", trainUid: "C16922" });
    insertLmJourney(fixture.db, { serviceGuid: "guid-2", trainUid: "C16923" });
    fixture.db
      .prepare(`INSERT INTO feed_meta (key, value) VALUES (?, ?)`)
      .run("load_day", "20261102");
    fixture.db
      .prepare(`INSERT INTO feed_meta (key, value) VALUES (?, ?)`)
      .run("hd_sequence", "001");
    fixture.db
      .prepare(`INSERT INTO feed_meta (key, value) VALUES (?, ?)`)
      .run("loaded_at", "2026-11-02T10:00:00.000Z");
    return fixture;
  }

  it("returns only guids with applied_updates above since", async () => {
    const fixture = await seeded();
    applyKafkaMessage(
      fixture.db,
      {
        uR: {
          TS: {
            rid: "r1",
            uid: "C16922",
            ssd: calendarSsd(),
            Location: { tpl: "CREWE", dep: { et: "09:40" } },
          },
        },
      },
      { topic: "t", partition: 0, offset: 1 },
    );
    fixture.db.close();

    const full = buildNetworkAnswer({ sqlitePath: fixture.sqlitePath, clock });
    assert.ok(!("reason" in full));
    if ("reason" in full) return;
    assert.equal(full.full, true);
    assert.equal(full.trains.length, 2);

    const delta = buildNetworkAnswer({
      sqlitePath: fixture.sqlitePath,
      clock,
      since: 0,
      generation: full.generation,
    });
    assert.ok(!("reason" in delta));
    if ("reason" in delta) return;
    assert.equal(delta.full, false);
    assert.equal(delta.trains.length, 1);
    assert.equal(delta.trains[0]?.serviceGuid, "guid-1");
    assert.equal(delta.removed.length, 0);
  });

  it("puts cancelled guids in removed", async () => {
    const fixture = await seeded();
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
      { topic: "t", partition: 0, offset: 2 },
    );
    const generation = "20261102|001|2026-11-02T10:00:00.000Z";
    fixture.db.close();
    const delta = buildNetworkAnswer({
      sqlitePath: fixture.sqlitePath,
      clock,
      since: 0,
      generation,
    });
    assert.ok(!("reason" in delta));
    if ("reason" in delta) return;
    assert.equal(delta.trains.length, 0);
    assert.deepEqual(delta.removed, [
      { serviceGuid: "guid-1", trainUid: "C16922", eventTags: ["cancelled"] },
    ]);
  });

  it("returns full when generation mismatches", async () => {
    const fixture = await seeded();
    fixture.db.close();
    const answer = buildNetworkAnswer({
      sqlitePath: fixture.sqlitePath,
      clock,
      since: 0,
      generation: "other",
    });
    assert.ok(!("reason" in answer));
    if ("reason" in answer) return;
    assert.equal(answer.full, true);
    assert.equal(answer.trains.length, 2);
  });

  it("returns empty delta when since equals max id", async () => {
    const fixture = await seeded();
    applyKafkaMessage(
      fixture.db,
      {
        uR: {
          TS: {
            rid: "r1",
            uid: "C16922",
            ssd: calendarSsd(),
            Location: { tpl: "CREWE", dep: { et: "09:40" } },
          },
        },
      },
      { topic: "t", partition: 0, offset: 3 },
    );
    const max = fixture.db
      .prepare(`SELECT MAX(id) AS id FROM applied_updates`)
      .get() as { id: number };
    const generation = "20261102|001|2026-11-02T10:00:00.000Z";
    fixture.db.close();
    const delta = buildNetworkAnswer({
      sqlitePath: fixture.sqlitePath,
      clock,
      since: max.id,
      generation,
    });
    assert.ok(!("reason" in delta));
    if ("reason" in delta) return;
    assert.equal(delta.full, false);
    assert.equal(delta.trains.length, 0);
    assert.equal(delta.removed.length, 0);
  });
});
