import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { placeTrain } from "../src/networkMap.js";
import {
  corridorThroughStation,
  segmentSeverity,
  spinePath,
  stationAggregates,
  stripLayout,
  trainEnds,
  trainsOnSpine,
  windowAroundPlace,
  windowAroundStation,
  windowKeyForPlace,
  WINDOW_RADIUS,
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
    assert.ok(layout.segments.every((segment) => segment.severity === "on-time"));
  });

  it("reads origin and dest from stops when fields are missing", () => {
    const ends = trainEnds({
      serviceGuid: "x",
      problem: false,
      stops: [stop("EUS", null, "1000"), stop("BHM", "1200", null)],
    });
    assert.deepEqual(ends, { originCrs: "EUS", destCrs: "BHM" });
  });

  it("stacks markers per location, not globally across the strip", () => {
    const spine = ["APS", "KGL", "WFJ"];
    // At 1015: early train is KGL→WFJ; late train is APS→KGL. Same side,
    // different places — both must sit at the base offset (not stack 0 then 1).
    const nearWfj = train("near-wfj", "APS", "WFJ", [
      stop("APS", null, "0900"),
      stop("KGL", "0940", "0945"),
      stop("WFJ", "1100", null),
    ]);
    const apsToKgl = train("aps-kgl", "APS", "WFJ", [
      stop("APS", null, "1000"),
      stop("KGL", "1100", "1105"),
      stop("WFJ", "1200", null),
    ]);
    const layout = stripLayout(spine, [nearWfj, apsToKgl], "1015", null);
    assert.equal(layout.markers.length, 2);
    const baseOffset = 28;
    for (const marker of layout.markers) {
      assert.equal(Math.abs(marker.y - 80), baseOffset);
      assert.equal(marker.above, true);
    }
    assert.notEqual(layout.markers[0]!.progress, layout.markers[1]!.progress);
  });

  it("still stacks trains that share the same place", () => {
    const spine = ["APS", "KGL", "WFJ"];
    const first = train("a", "APS", "WFJ", [
      stop("APS", null, "1000"),
      stop("KGL", "1100", "1105"),
      stop("WFJ", "1200", null),
    ]);
    const second = train("b", "APS", "WFJ", [
      stop("APS", null, "1005"),
      stop("KGL", "1105", "1110"),
      stop("WFJ", "1205", null),
    ]);
    const layout = stripLayout(spine, [first, second], "1030", null);
    assert.equal(layout.markers.length, 2);
    const offsets = layout.markers
      .map((marker) => Math.abs(marker.y - 80))
      .sort((a, b) => a - b);
    assert.deepEqual(offsets, [28, 42]);
  });
});

describe("windowAroundPlace / windowAroundStation", () => {
  const codes = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M"];
  const diagram = lineDiagram(codes);
  const spine = spinePath(diagram, "A", "M");

  it("windows ±5 around an at-station place", () => {
    const place = placeTrain(
      [
        stop("A", null, "0900"),
        stop("G", "1000", "1005"),
        stop("M", "1200", null),
      ],
      "1002",
    );
    assert.ok(place?.atStation);
    assert.equal(WINDOW_RADIUS, 5);
    assert.deepEqual(windowAroundPlace(spine, place!), [
      "B",
      "C",
      "D",
      "E",
      "F",
      "G",
      "H",
      "I",
      "J",
      "K",
      "L",
    ]);
    assert.equal(windowKeyForPlace(place!), "at:G");
  });

  it("windows five either side of a between-stations hop", () => {
    const place = placeTrain(
      [
        stop("A", null, "0900"),
        stop("G", "1100", "1105"),
        stop("M", "1300", null),
      ],
      "1000",
    );
    assert.ok(place && !place.atStation);
    const window = windowAroundPlace(spine, place!);
    assert.equal(window[0], "A");
    assert.ok(window.includes(place!.fromCrs));
    assert.ok(window.includes(place!.toCrs));
    assert.ok(window.length <= 2 + WINDOW_RADIUS * 2);
    assert.match(windowKeyForPlace(place!), /^between:/);
  });

  it("windows ±5 around a tracked station on its corridor", () => {
    assert.deepEqual(windowAroundStation(diagram, "G"), [
      "B",
      "C",
      "D",
      "E",
      "F",
      "G",
      "H",
      "I",
      "J",
      "K",
      "L",
    ]);
    // Hop fallback walks ±WINDOW_RADIUS from G (not full A–M).
    assert.deepEqual(corridorThroughStation(diagram, "G"), [
      "B",
      "C",
      "D",
      "E",
      "F",
      "G",
      "H",
      "I",
      "J",
      "K",
      "L",
    ]);
  });

  it("prefers schematic lines over hop rank/BFS when present", () => {
    const withLines = {
      ...diagram,
      lines: [
        { id: "noise", stations: ["ACG", "TYS", "SRI"] },
        { id: "wcml", stations: ["EUS", "APS", "HML", "BKM", "TRI", "LBZ", "MKC"] },
      ],
    };
    assert.deepEqual(corridorThroughStation(withLines, "APS"), [
      "EUS",
      "APS",
      "HML",
      "BKM",
      "TRI",
      "LBZ",
      "MKC",
    ]);
    assert.deepEqual(windowAroundStation(withLines, "APS"), [
      "EUS",
      "APS",
      "HML",
      "BKM",
      "TRI",
      "LBZ",
      "MKC",
    ]);
  });

  it("aggregates trains at and approaching stations", () => {
    const atG = train("at", "A", "M", [
      stop("A", null, "0900"),
      stop("G", "1000", "1010"),
      stop("M", "1200", null),
    ], { statusLabel: "delayed", problem: true });
    const towardG = train("app", "A", "M", [
      stop("A", null, "0900"),
      stop("G", "1100", "1105"),
      stop("M", "1300", null),
    ], { statusLabel: "on time" });
    const rows = stationAggregates(["F", "G", "H"], [atG, towardG], "1005");
    const g = rows.find((row) => row.crs === "G");
    assert.ok(g);
    assert.equal(g!.atCount, 1);
    assert.equal(g!.approachingCount, 1);
    assert.equal(g!.worstStatus, "delayed");
  });
});
