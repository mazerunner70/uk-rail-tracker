import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { rm } from "node:fs/promises";
import {
  applyPolledMessage,
  drainPolledMessages,
  seekOffsetsFromSaved,
  shouldSkipOffset,
} from "../../src/kafkaConsumer.js";
import { openOverlayWriter } from "../../src/overlayWriter.js";
import {
  CALENDAR_YYMMDD,
  createFixtureDb,
  insertLmJourney,
} from "../timetable-query/helpers.js";

describe("kafkaConsumer helpers", () => {
  const dirs: string[] = [];

  afterEach(async () => {
    while (dirs.length > 0) {
      const dir = dirs.pop();
      if (dir) await rm(dir, { recursive: true, force: true });
    }
  });

  it("skips offsets below the stored next offset", () => {
    assert.equal(shouldSkipOffset(10, 9), true);
    assert.equal(shouldSkipOffset(10, 10), false);
    assert.equal(shouldSkipOffset(undefined, 0), false);
  });

  it("seeks from saved offsets or the live end", () => {
    const seeks: Array<{ partition: number; offset: string }> = [];
    seekOffsetsFromSaved(
      {
        seek: (input) => {
          seeks.push({ partition: input.partition, offset: input.offset });
        },
      },
      "darwin.json",
      [
        { partition: 0, high: 100 },
        { partition: 1, high: 50 },
      ],
      new Map([[0, 12]]),
    );
    assert.deepEqual(seeks, [
      { partition: 0, offset: "12" },
      { partition: 1, offset: "50" },
    ]);
  });

  it("drains injected messages onto the writer", async () => {
    const fixture = await createFixtureDb();
    dirs.push(fixture.dir);
    openOverlayWriter(fixture.db);
    insertLmJourney(fixture.db, { serviceGuid: "guid-1", trainUid: "C16922" });
    const ssd = `20${CALENDAR_YYMMDD.slice(0, 2)}-${CALENDAR_YYMMDD.slice(2, 4)}-${CALENDAR_YYMMDD.slice(4, 6)}`;

    async function* messages() {
      yield {
        topic: "t",
        partition: 0,
        offset: 1,
        value: Buffer.from(
          JSON.stringify({
            uR: {
              TS: {
                rid: "r1",
                uid: "C16922",
                ssd,
                Location: { tpl: "CREWE", dep: { et: "09:44" } },
              },
            },
          }),
        ),
      };
    }

    await drainPolledMessages(fixture.db, messages());
    const point = fixture.db
      .prepare(`SELECT forecast_dep FROM calling_points WHERE tiploc = 'CREWE'`)
      .get() as { forecast_dep: string };
    assert.equal(point.forecast_dep, "0944");
    applyPolledMessage(fixture.db, {
      topic: "t",
      partition: 0,
      offset: 0,
      value: Buffer.from("{}"),
    });
    fixture.db.close();
  });
});
