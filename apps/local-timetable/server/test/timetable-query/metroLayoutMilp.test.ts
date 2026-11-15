import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { describe, it } from "node:test";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { runMilpLayout } from "../../src/metroLayoutBridge.js";
import { buildLayoutInput, diagramFromOctCoords } from "../../src/networkSchematic.js";

const HERE = dirname(fileURLToPath(import.meta.url));
const SCRIPT = join(HERE, "../../layout/metro_layout.py");

function pulpHighsPython(): string | null {
  const candidates = [
    process.env.METRO_LAYOUT_PYTHON,
    "/tmp/metro-layout-venv/bin/python",
    "python3",
  ].filter((value): value is string => Boolean(value));
  for (const python of candidates) {
    const probe = spawnSync(python, ["-c", "import pulp, highspy"], { encoding: "utf8" });
    if (probe.status === 0) return python;
  }
  return null;
}

describe("PuLP+HiGHS metro layout", () => {
  it("lays out a tiny graph with octilinear coords and rough geography", async (t) => {
    const python = pulpHighsPython();
    if (!python) {
      t.skip("pulp/highspy not installed (pip install -r layout/requirements.txt)");
      return;
    }
    const input = buildLayoutInput([
      {
        stops: [
          { crs: "EUS" },
          { crs: "BHM" },
          { crs: "CRE" },
        ],
      },
      { stops: [{ crs: "BHM" }, { crs: "NMP" }] },
    ]);
    // Force geo-like seed so EUS is south-east of BHM in the input.
    const byCrs = new Map(input.nodes.map((node) => [node.crs, node]));
    if (byCrs.has("EUS") && byCrs.has("BHM") && byCrs.has("CRE")) {
      byCrs.get("BHM")!.x = 40;
      byCrs.get("BHM")!.y = 40;
      byCrs.get("EUS")!.x = 100;
      byCrs.get("EUS")!.y = 100;
      byCrs.get("CRE")!.x = 10;
      byCrs.get("CRE")!.y = 10;
      if (byCrs.has("NMP")) {
        byCrs.get("NMP")!.x = 70;
        byCrs.get("NMP")!.y = 50;
      }
    }
    const seeded = {
      ...input,
      nodes: [...byCrs.values()],
    };
    const result = await runMilpLayout(seeded, {
      scriptPath: SCRIPT,
      timeLimitSec: 30,
      pythonPath: python,
    });
    assert.ok(result.stations.length >= 3);
    assert.ok(result.edges.length >= 2);
    const stations = new Map(result.stations.map((station) => [station.crs, station]));
    const eus = stations.get("EUS");
    const bhm = stations.get("BHM");
    assert.ok(eus && bhm);
    assert.ok(eus.x >= bhm.x, "EUS should be east of BHM");
    assert.ok(eus.y >= bhm.y, "EUS should be south of BHM (y-down)");
    const diagram = diagramFromOctCoords(result.stations, result.edges);
    assert.ok(diagram.corridors.length >= 2);
    assert.ok(diagram.width > 0);
  });
});
