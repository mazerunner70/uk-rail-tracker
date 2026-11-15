import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  detectEventTags,
  hhmmDigits,
  isLaterThanBooked,
} from "../../src/eventTags.js";

describe("hhmmDigits / isLaterThanBooked", () => {
  it("normalises Darwin times", () => {
    assert.equal(hhmmDigits("09:50"), "0950");
    assert.equal(hhmmDigits("09:50:00"), "0950");
    assert.equal(hhmmDigits("0951"), "0951");
    assert.equal(hhmmDigits(""), null);
  });

  it("detects lateness with overnight wrap", () => {
    assert.equal(isLaterThanBooked("1000", "0950"), true);
    assert.equal(isLaterThanBooked("0950", "0950"), false);
    assert.equal(isLaterThanBooked("0940", "0950"), false);
    assert.equal(isLaterThanBooked("0005", "2350"), true);
  });
});

describe("detectEventTags", () => {
  it("tags arrived, departed, and call_forecast", () => {
    const tags = detectEventTags("TS", {
      Location: [
        { tpl: "CREWE", arr: { at: "09:50" }, dep: { et: "09:52" } },
        { tpl: "STAFFRD", arr: { et: "10:10" } },
      ],
    });
    assert.ok(tags.includes("arrived"));
    assert.ok(tags.includes("call_forecast"));
    assert.ok(!tags.includes("departed"));
  });

  it("tags departed when dep.at is present", () => {
    const tags = detectEventTags("TS", {
      Location: { tpl: "CREWE", dep: { at: "09:51" } },
    });
    assert.deepEqual(tags, ["departed"]);
  });

  it("tags pass_forecast and passed_location", () => {
    const forecast = detectEventTags("TS", {
      Location: { tpl: "WMBY", wtp: "13:06", pass: { et: "13:06" } },
    });
    assert.ok(forecast.includes("pass_forecast"));
    assert.ok(!forecast.includes("passed_location"));

    const actual = detectEventTags("TS", {
      Location: { tpl: "WMBY", wtp: "13:06", pass: { at: "13:07" } },
    });
    assert.ok(actual.includes("passed_location"));
    assert.ok(!actual.includes("pass_forecast"));
    assert.ok(actual.includes("delayed"));
  });

  it("tags delayed from LateReason and booked mismatch", () => {
    const reason = detectEventTags("TS", { LateReason: "100", Location: [] });
    assert.deepEqual(reason, ["delayed"]);

    const late = detectEventTags("TS", {
      Location: {
        tpl: "CREWE",
        ptd: "09:26",
        dep: { et: "09:40" },
      },
    });
    assert.ok(late.includes("delayed"));
    assert.ok(late.includes("call_forecast"));
  });

  it("tags actual_removed, platform, and length", () => {
    const tags = detectEventTags("TS", {
      Location: {
        tpl: "CREWE",
        arr: { atRemoved: true },
        plat: { "": "3" },
        platsup: true,
        length: 8,
      },
    });
    assert.ok(tags.includes("actual_removed"));
    assert.ok(tags.includes("platform_set"));
    assert.ok(tags.includes("platform_suppressed"));
    assert.ok(tags.includes("length_reported"));
  });

  it("tags schedule cancel and clear", () => {
    assert.deepEqual(
      detectEventTags("schedule", { can: true }),
      ["cancelled"],
    );
    assert.deepEqual(
      detectEventTags("schedule", { can: false }),
      ["cancel_cleared"],
    );
    assert.deepEqual(detectEventTags("schedule", { toc: "LM" }), []);
  });

  it("tags deactivated", () => {
    assert.deepEqual(detectEventTags("deactivated", { rid: "r1" }), [
      "deactivated",
    ]);
  });
});
