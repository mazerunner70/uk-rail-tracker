import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { planOverlayWrite } from "../../src/overlayPlan.js";

describe("planOverlayWrite", () => {
  it("plans TS forecasts only", () => {
    const write = planOverlayWrite(
      "TS",
      {
        rid: "r1",
        uid: "C16922",
        ssd: "2026-05-17",
        Location: { tpl: "CREWE", dep: { et: "09:26" } },
      },
      "guid-1",
    );
    assert.equal(write.kind, "TS");
    if (write.kind === "TS") {
      assert.deepEqual(write.forecasts, [
        {
          tiploc: "CREWE",
          forecastArr: null,
          forecastDep: "0926",
          platform: null,
          platformSuppressed: null,
          trainLength: null,
        },
      ]);
      assert.ok(write.tags.includes("call_forecast"));
    }
  });

  it("plans schedule cancel and audit-only", () => {
    const cancel = planOverlayWrite(
      "schedule",
      { rid: "r1", uid: "C16922", ssd: "2026-05-17", can: true },
      "guid-1",
    );
    assert.equal(cancel.kind, "schedule");
    if (cancel.kind === "schedule") {
      assert.equal(cancel.cancel, "cancel");
      assert.deepEqual(cancel.tags, ["cancelled"]);
    }
    const audit = planOverlayWrite(
      "schedule",
      { rid: "r1", uid: "C16922", ssd: "2026-05-17", toc: "LM" },
      "guid-1",
    );
    assert.equal(audit.kind, "schedule");
    if (audit.kind === "schedule") {
      assert.equal(audit.cancel, "unchanged");
      assert.deepEqual(audit.tags, []);
    }
  });

  it("plans deactivated and miss", () => {
    const deactivated = planOverlayWrite(
      "deactivated",
      { rid: "r1", uid: "C16922", ssd: "2026-05-17" },
      "guid-1",
    );
    assert.equal(deactivated.kind, "deactivated");
    if (deactivated.kind === "deactivated") {
      assert.deepEqual(deactivated.tags, ["deactivated"]);
    }
    const miss = planOverlayWrite(
      "TS",
      { rid: "r1", uid: "Y99999", ssd: "2026-05-17" },
      null,
    );
    assert.equal(miss.kind, "miss");
  });
});
