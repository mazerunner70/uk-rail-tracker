import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  adjacentGraph,
  journeysThroughStation,
  mergeSpine,
  mergedStationWindow,
  sideBranchesAtWindow,
  windowAroundMergedSpine,
} from "../src/journeySpine.js";
import { WINDOW_RADIUS } from "../src/routeStrip.js";
import type { NetworkTrain } from "../src/types.js";

function train(guid: string, crsList: string[]): NetworkTrain {
  return {
    serviceGuid: guid,
    problem: false,
    stops: crsList.map((crs) => ({ crs, arr: null, dep: "1200" })),
  };
}

describe("journeysThroughStation / adjacentGraph", () => {
  it("keeps only journeys that call at S", () => {
    const trains = [
      train("a", ["HML", "APS", "KGL"]),
      train("b", ["EUS", "WFJ", "MKC"]),
    ];
    assert.deepEqual(journeysThroughStation(trains, "aps"), [
      ["HML", "APS", "KGL"],
    ]);
  });

  it("builds undirected adjacency from consecutive stops", () => {
    const g = adjacentGraph([["HML", "APS", "KGL"]]);
    assert.deepEqual([...(g.get("APS") ?? [])].sort(), ["HML", "KGL"]);
  });
});

describe("mergeSpine", () => {
  it("inserts a stop one journey skips", () => {
    const { spine } = mergeSpine(
      [
        ["HML", "APS"],
        ["HML", "XOE", "APS"],
      ],
      "APS",
    );
    assert.deepEqual(spine, ["HML", "XOE", "APS"]);
  });

  it("keeps majority path on spine and records minority fork", () => {
    const { spine, forks } = mergeSpine(
      [
        ["APS", "KGL", "WFJ", "BUS"],
        ["APS", "KGL", "WFJ", "BUS"],
        ["APS", "KGL", "WFJ", "SAA"],
      ],
      "APS",
    );
    assert.ok(spine.includes("BUS"));
    assert.equal(spine.includes("SAA"), false);
    assert.ok(
      forks.some((f) => f.atCrs === "WFJ" && f.nextCrs === "SAA"),
      `expected fork WFJ->SAA, got ${JSON.stringify(forks)}`,
    );
  });

  it("merges both sides around S", () => {
    const { spine } = mergeSpine(
      [
        ["CED", "TRI", "APS", "KGL", "WFJ"],
        ["TRI", "BKM", "APS", "KGL", "WFJ", "BUS"],
      ],
      "APS",
    );
    assert.ok(spine.indexOf("CED") < spine.indexOf("TRI"));
    assert.ok(spine.indexOf("TRI") < spine.indexOf("APS"));
    assert.ok(spine.indexOf("APS") < spine.indexOf("KGL"));
    assert.ok(spine.includes("BKM") || spine.includes("TRI"));
    assert.ok(spine.includes("BUS") || spine.includes("WFJ"));
  });

  it("does not mirror when journeys run both directions through S", () => {
    const north = ["CED", "TRI", "BKM", "XOE", "HML", "APS", "KGL", "WFJ", "BUS", "EUS"];
    const south = [...north].reverse();
    const { spine } = mergeSpine([north, south, north, south], "APS");
    const at = spine.indexOf("APS");
    const left = spine.slice(Math.max(0, at - 5), at);
    const right = spine.slice(at + 1, at + 6);
    assert.notEqual(left.join("|"), [...right].reverse().join("|"));
    assert.ok(right.includes("KGL") || left.includes("KGL"));
    assert.ok(right.includes("HML") || left.includes("HML"));
    assert.equal(new Set(spine).size, spine.length, "spine CRS must be unique");
  });
});

describe("windowAroundMergedSpine", () => {
  it("continues past a mid-spine station up to radius", () => {
    const spine = ["A", "B", "C", "D", "E", "S", "F", "G", "H", "I", "J", "K"];
    const window = windowAroundMergedSpine(spine, "S", 5);
    assert.deepEqual(window, ["A", "B", "C", "D", "E", "S", "F", "G", "H", "I", "J"]);
  });

  it("shortens only when the spine ends", () => {
    const spine = ["S", "A", "B"];
    assert.deepEqual(windowAroundMergedSpine(spine, "S", 5), ["S", "A", "B"]);
  });
});

describe("sideBranchesAtWindow / mergedStationWindow", () => {
  it("attaches a side branch at a junction in the window", () => {
    const journeys = [
      ["APS", "KGL", "WFJ", "BUS", "EUS"],
      ["APS", "KGL", "WFJ", "BUS", "EUS"],
      ["APS", "KGL", "WFJ", "SAA"],
    ];
    const { spine, forks } = mergeSpine(journeys, "APS");
    const window = windowAroundMergedSpine(spine, "APS", WINDOW_RADIUS);
    const graph = adjacentGraph(journeys);
    const branches = sideBranchesAtWindow({
      spineWindow: window,
      fullSpine: spine,
      graph,
      forks,
      selectedCrs: "APS",
    });
    const saa = branches.find((b) => b.stations.includes("SAA"));
    assert.ok(saa, `expected SAA branch, got ${JSON.stringify(branches)}`);
    assert.equal(saa!.junctionCrs, "WFJ");
  });

  it("mergedStationWindow returns null when no journeys through S", () => {
    assert.equal(
      mergedStationWindow([train("x", ["EUS", "MKC"])], "APS"),
      null,
    );
  });

  it("mergedStationWindow builds window past former line-style clamp", () => {
    const trains = [
      train("a", ["CED", "TRI", "BKM", "HML", "APS", "KGL", "WFJ", "BUS", "EUS"]),
      train("b", ["TRI", "BKM", "HML", "APS", "KGL", "WFJ", "SAA"]),
    ];
    const merged = mergedStationWindow(trains, "APS");
    assert.ok(merged);
    assert.ok(merged!.window.includes("APS"));
    // Past WFJ when within ±5 of APS on merged spine
    assert.ok(
      merged!.window.includes("BUS") || merged!.sideBranches.some((b) => b.stations.includes("SAA")),
    );
  });
});
