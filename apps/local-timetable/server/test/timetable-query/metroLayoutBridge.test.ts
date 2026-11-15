import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  applyOctLayoutJson,
  diagramFromOctLayoutResult,
  metroLayoutEngine,
  metroLayoutTimeLimitSec,
} from "../../src/metroLayoutBridge.js";
import {
  buildLayoutInput,
  LayoutDegreeError,
  OCT_CELL_PX,
} from "../../src/networkSchematic.js";

describe("metroLayoutBridge fixtures", () => {
  it("builds a diagram from octilinear JSON", () => {
    const diagram = applyOctLayoutJson({
      stations: [
        { crs: "EUS", x: 0, y: 0, rank: 0, lane: 0 },
        { crs: "BHM", x: 2, y: 1, rank: 1, lane: 0 },
        { crs: "CRE", x: 4, y: 2, rank: 2, lane: 0 },
      ],
      edges: [
        { from: "BHM", to: "EUS" },
        { from: "BHM", to: "CRE" },
      ],
    });
    assert.equal(diagram.stations.length, 3);
    assert.equal(diagram.corridors.length, 2);
    const eus = diagram.stations.find((station) => station.crs === "EUS");
    const cre = diagram.stations.find((station) => station.crs === "CRE");
    assert.ok(eus && cre);
    assert.ok(cre.x > eus.x);
    assert.ok(diagram.width > OCT_CELL_PX);
  });

  it("diagramFromOctLayoutResult matches applyOctLayoutJson", () => {
    const raw = {
      stations: [
        { crs: "A", x: 1, y: 1, rank: 0, lane: 0 },
        { crs: "B", x: 2, y: 1, rank: 1, lane: 0 },
      ],
      edges: [{ from: "A", to: "B" }],
    };
    assert.deepEqual(diagramFromOctLayoutResult(raw), applyOctLayoutJson(raw));
  });

  it("reads engine and time-limit env helpers", () => {
    assert.ok(metroLayoutEngine() === "geo" || metroLayoutEngine() === "milp");
    assert.ok(metroLayoutTimeLimitSec() > 0);
  });
});

describe("buildLayoutInput", () => {
  it("emits geo-seeded nodes, reduced edges, and line hops", () => {
    const input = buildLayoutInput([
      { stops: [{ crs: "EUS" }, { crs: "BHM" }, { crs: "CRE" }] },
      { stops: [{ crs: "EUS" }, { crs: "BHM" }, { crs: "CRE" }] },
      { stops: [{ crs: "BHM" }, { crs: "NMP" }] },
    ]);
    assert.ok(input.nodes.some((node) => node.crs === "EUS"));
    assert.ok(input.edges.some((edge) => edge.from === "BHM" && edge.to === "EUS")
      || input.edges.some((edge) => edge.from === "EUS" && edge.to === "BHM"));
    assert.ok(input.lines.length >= 1);
    // Duplicate journeys collapse to one linename path.
    const names = new Set(input.lines.map((hop) => hop.linename));
    assert.ok(names.size >= 1);
    assert.ok(names.size <= 2);
  });

  it("throws LayoutDegreeError when a station exceeds degree 8", () => {
    const hub = "HUB";
    const spokes = ["A", "B", "C", "D", "E", "F", "G", "H", "I"];
    const journeys = spokes.map((crs) => ({
      stops: [{ crs: hub }, { crs }],
    }));
    assert.throws(() => buildLayoutInput(journeys), LayoutDegreeError);
  });
});
