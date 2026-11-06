import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { reachedAndNextCrs, scheduledHhmm, type StopRow } from "../../src/stopTime.js";

describe("stopTime reached and next", () => {
  it("uses the first stop as next when nothing is at or before now", () => {
    const result = reachedAndNextCrs(
      [
        {
          seq: 1,
          crs: "CRE",
          publicArr: null,
          publicDep: "1200",
          forecastArr: null,
          forecastDep: null,
        },
        {
          seq: 2,
          crs: "BHM",
          publicArr: "1300",
          publicDep: "1300",
          forecastArr: null,
          forecastDep: null,
        },
      ],
      "1000",
    );
    assert.equal(result.reachedCrs, null);
    assert.equal(result.nextCrs, "CRE");
  });

  it("picks the latest stop at or before now", () => {
    const result = reachedAndNextCrs(
      [
        {
          seq: 1,
          crs: "CRE",
          publicArr: null,
          publicDep: "0900",
          forecastArr: null,
          forecastDep: null,
        },
        {
          seq: 2,
          crs: "BHM",
          publicArr: "1000",
          publicDep: "1000",
          forecastArr: null,
          forecastDep: null,
        },
      ],
      "1015",
    );
    assert.equal(result.reachedCrs, "BHM");
    assert.equal(result.nextCrs, null);
  });
});

function stop(partial: Partial<StopRow> & Pick<StopRow, "seq" | "crs">): StopRow {
  return {
    publicArr: null,
    publicDep: null,
    forecastArr: null,
    forecastDep: null,
    ...partial,
  };
}

describe("scheduledHhmm", () => {
  it("uses the next stop's time while the journey is still running", () => {
    const points = [
      stop({ seq: 1, crs: "CRE", publicDep: "0900" }),
      stop({ seq: 2, crs: "BHM", publicArr: "1000", publicDep: "1005" }),
    ];
    assert.equal(scheduledHhmm(points, "0930"), "1005");
  });

  it("prefers the next stop's forecast over its public time", () => {
    const points = [
      stop({ seq: 1, crs: "CRE", publicDep: "0900" }),
      stop({
        seq: 2,
        crs: "BHM",
        publicArr: "1000",
        publicDep: "1005",
        forecastDep: "1030",
      }),
    ];
    assert.equal(scheduledHhmm(points, "0930"), "1030");
  });

  it("uses the terminus arrival once the journey has ended", () => {
    const points = [
      stop({ seq: 1, crs: "CRE", publicDep: "0900" }),
      stop({
        seq: 2,
        crs: "BHM",
        publicArr: "1000",
        publicDep: "1005",
        forecastArr: "1010",
        forecastDep: "1012",
      }),
    ];
    assert.equal(scheduledHhmm(points, "1100"), "1010");
  });
});
