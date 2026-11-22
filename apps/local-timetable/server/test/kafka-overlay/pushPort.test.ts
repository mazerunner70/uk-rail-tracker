import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { describe, it } from "node:test";
import { fileURLToPath } from "node:url";
import {
  decodePushPort,
  extractSsd,
  locationForecasts,
  scheduleCancelFlag,
  updateItems,
} from "../../src/pushPort.js";

const here = dirname(fileURLToPath(import.meta.url));
const FIXTURE = resolve(
  here,
  "../../../../../server/poc/live-sched/fixtures/kafka_payloads.jsonl",
);

describe("decodePushPort", () => {
  it("decodes the live-sched fixture shapes", () => {
    const lines = readFileSync(FIXTURE, "utf8")
      .trim()
      .split("\n")
      .map((line) => JSON.parse(line) as { payload: object });
    const first = decodePushPort(lines[0]!.payload);
    assert.ok(first);
    assert.equal(updateItems(first!).length, 1);
    assert.equal(updateItems(first!)[0]?.type, "TS");

    const schedule = decodePushPort(lines[2]!.payload);
    assert.equal(updateItems(schedule!)[0]?.type, "schedule");

    const deactivated = decodePushPort(lines[4]!.payload);
    assert.equal(updateItems(deactivated!)[0]?.type, "deactivated");

    const ignored = decodePushPort(lines[5]!.payload);
    assert.equal(updateItems(ignored!).length, 0);
  });

  it("unwraps a string bytes field", () => {
    const inner = {
      uR: { TS: { rid: "1", uid: "C16922", ssd: "2026-05-17" } },
    };
    const payload = decodePushPort(JSON.stringify({ bytes: JSON.stringify(inner) }));
    assert.deepEqual(updateItems(payload!), [
      {
        type: "TS",
        item: { rid: "1", uid: "C16922", ssd: "2026-05-17" },
      },
    ]);
  });

  it("returns null for bad JSON", () => {
    assert.equal(decodePushPort("{"), null);
  });

  it("normalises ssd and forecast times", () => {
    const item = {
      rid: "r",
      uid: "C16922",
      ssd: "2026-05-17",
      Location: {
        tpl: "CREWE",
        arr: { et: "09:50" },
        dep: { at: "09:51:00" },
      },
    };
    assert.equal(extractSsd(item), "260517");
    assert.deepEqual(locationForecasts(item), [
      {
        tiploc: "CREWE",
        forecastArr: "0950",
        forecastDep: "0951",
        platform: null,
        platformSuppressed: null,
        trainLength: null,
      },
    ]);
  });

  it("reads Location lists and cancel flags", () => {
    const item = {
      Location: [
        { tpl: "CREWE", dep: { et: "09:26" } },
        { tpl: "STAFFRD", arr: { wet: "09:50" } },
      ],
      can: "true",
    };
    assert.equal(locationForecasts(item).length, 2);
    assert.equal(scheduleCancelFlag(item), "cancel");
    assert.equal(scheduleCancelFlag({ can: false }), "clear");
    assert.equal(scheduleCancelFlag({}), "unchanged");
  });
});
