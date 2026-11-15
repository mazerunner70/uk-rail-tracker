import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  segmentSeverity,
  spinePath,
  stripLayout,
  trainEnds,
  trainsOnSpine,
} from "../src/routeStrip.js";
import type { NetworkDiagram, NetworkStop, NetworkTrain } from "../src/types.js";

function stop(crs: string, arr: string | null, dep: string | null): NetworkStop {
  return { crs, arr, dep };
}

function train(
  serviceGuid: string,
  originCrs: string,
  destCrs: string,
  stops: NetworkStop[],
  extra: Partial<NetworkTrain> = {},
): NetworkTrain {
  return { serviceGuid, originCrs, destCrs, problem: false, stops, ...extra };
}

function lineDiagram(codes: readonly string[]): NetworkDiagram {
  const stations = codes.map((crs, index) => ({
    crs,
    x: 40 + index * 100,
    y: 80,
    rank: index,
    lane: 0,
  }));
  const corridors = [];
  for (let index = 1; index < stations.length; index++) {
    const from = stations[index - 1]!;
    const to = stations[index]!;
    corridors.push({
      from: from.crs,
      to: to.crs,
      via: [],
      points: [
        { x: from.x, y: from.y },
        { x: to.x, y: to.y },
      ],
      color: "#7dffa8",
    });
  }
  return {
    loadDay: "20261106",
    hdSequence: "1",
    stations,
    corridors,
    width: 40 + codes.length * 100,
    height: 160,
  };
}

describe("spinePath", () => {
  it("includes intermediate stations the train does not call at", () => {
    const diagram = lineDiagram(["EUS", "WFJ", "MKC", "NMP", "RUG", "COV", "BHM"]);
    assert.deepEqual(spinePath(diagram, "EUS", "BHM"), [
      "EUS",
      "WFJ",
      "MKC",
      "NMP",
      "RUG",
      "COV",
      "BHM",
    ]);
  });

  it("falls back to ends when the diagram has no path", () => {
    const diagram = lineDiagram(["EUS", "MKC"]);
    assert.deepEqual(spinePath(diagram, "EUS", "BHM"), ["EUS", "BHM"]);
  });
});

describe("trainsOnSpine", () => {
  const diagram = lineDiagram(["EUS", "WFJ", "MKC", "BHM"]);
  const spine = spinePath(diagram, "EUS", "BHM");

  it("keeps trains currently on a spine hop", () => {
    const on = train("a", "EUS", "BHM", [
      stop("EUS", null, "1000"),
      stop("MKC", "1100", "1105"),
      stop("BHM", "1200", null),
    ]);
    const off = train("b", "CRE", "STA", [
      stop("CRE", null, "1000"),
      stop("STA", "1100", null),
    ]);
    const found = trainsOnSpine([on, off], spine, "1030");
    assert.deepEqual(
      found.map((row) => row.serviceGuid),
      ["a"],
    );
  });
});

describe("segmentSeverity", () => {
  it("marks a hop minor when a delayed train is on it", () => {
    const spine = ["EUS", "MKC", "BHM"];
    const delayed = train(
      "late",
      "EUS",
      "BHM",
      [stop("EUS", null, "1000"), stop("MKC", "1100", "1105"), stop("BHM", "1200", null)],
      { problem: true, statusLabel: "delayed" },
    );
    assert.deepEqual(segmentSeverity(spine, [delayed], "1030"), ["minor", "on-time"]);
  });

  it("marks severe for deactivated trains at a station", () => {
    const spine = ["EUS", "MKC", "BHM"];
    const dead = train(
      "dead",
      "EUS",
      "BHM",
      [stop("EUS", null, "0900"), stop("MKC", "1000", "1010"), stop("BHM", "1100", null)],
      { problem: true, statusLabel: "deactivated" },
    );
    assert.deepEqual(segmentSeverity(spine, [dead], "1005"), ["severe", "severe"]);
  });
});

describe("placeOnSpine / stripLayout", () => {
  it("projects a mid-hop train onto the strip and reports selected progress", () => {
    const spine = ["EUS", "WFJ", "MKC", "BHM"];
    const service = train("1M45", "EUS", "BHM", [
      stop("EUS", null, "1000"),
      stop("MKC", "1200", "1205"),
      stop("BHM", "1300", null),
    ]);
    const layout = stripLayout(spine, [service], "1100", "1M45");
    assert.equal(layout.stations.length, 4);
    assert.equal(layout.segments.length, 3);
    assert.equal(layout.markers.length, 1);
    assert.ok(layout.selectedProgress !== null);
    assert.ok(Math.abs((layout.selectedProgress ?? 0) - 1 / 3) < 1e-9);
  });

  it("reads origin and dest from stops when fields are missing", () => {
    const ends = trainEnds({
      serviceGuid: "x",
      problem: false,
      stops: [stop("EUS", null, "1000"), stop("BHM", "1200", null)],
    });
    assert.deepEqual(ends, { originCrs: "EUS", destCrs: "BHM" });
  });
});
