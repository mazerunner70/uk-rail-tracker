import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { rm } from "node:fs/promises";
import { openTimetableReader } from "../../src/sqliteReader.js";
import { createFixtureDb, insertLmJourney } from "../timetable-query/helpers.js";

describe("busy_timeout", () => {
  const dirs: string[] = [];

  afterEach(async () => {
    while (dirs.length > 0) {
      const dir = dirs.pop();
      if (dir) await rm(dir, { recursive: true, force: true });
    }
  });

  it("sets busy_timeout on a reader", async () => {
    const fixture = await createFixtureDb();
    dirs.push(fixture.dir);
    insertLmJourney(fixture.db);
    fixture.db.close();

    const opened = openTimetableReader(fixture.sqlitePath);
    assert.equal(opened.ok, true);
    if (!opened.ok) return;
    const timeout = opened.db.pragma("busy_timeout", { simple: true }) as number;
    assert.equal(timeout, 5000);
    opened.db.close();
  });
});
