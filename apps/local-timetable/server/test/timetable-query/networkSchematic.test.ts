import assert from "node:assert/strict";
import { existsSync, readFileSync, unlinkSync } from "node:fs";
import { describe, it } from "node:test";
import {
  layoutDiagram,
  linePathsFromJourneys,
  type StationLayout,
} from "../../src/networkSchematic.js";
import {
  networkSchematicPath,
  readNetworkDiagram,
  writeNetworkDiagram,
} from "../../src/networkSchematicFile.js";
import { indexStationGeo, loadStationGeo, type GeoIndex } from "../../src/stationGeo.js";
import { createFixtureDb, insertLmJourney } from "./helpers.js";

/** Matches server DOT_MIN_DIST (r=5 + 4px gap). */
const DOT_MIN_DIST_TEST = 14;

function stops(crs: string[]): { crs: string }[] {
  return crs.map((code) => ({ crs: code }));
}

function journey(crs: string[]): { stops: { crs: string }[] } {
  return { stops: stops(crs) };
}

function byCrs(stations: readonly StationLayout[]): Map<string, StationLayout> {
  return new Map(stations.map((station) => [station.crs, station]));
}

function between(station: StationLayout, left: StationLayout, right: StationLayout): boolean {
  const dx = right.x - left.x;
  const dy = right.y - left.y;
  const len2 = dx * dx + dy * dy;
  if (len2 === 0) return false;
  const t = ((station.x - left.x) * dx + (station.y - left.y) * dy) / len2;
  return t > 0.05 && t < 0.95;
}

function offSegment(station: StationLayout, left: StationLayout, right: StationLayout): boolean {
  const dx = right.x - left.x;
  const dy = right.y - left.y;
  const len = Math.hypot(dx, dy);
  if (len === 0) return false;
  const cross = Math.abs((station.x - left.x) * dy - (station.y - left.y) * dx) / len;
  return cross > 10;
}

describe("layoutDiagram", () => {
  const spine = [
    journey(["EUS", "HALT", "MKC", "RUG", "STA", "CRE"]),
    journey(["MKC", "RUG", "STA"]),
    journey(["RUG", "NMP"]),
    journey(["MKC", "RUG"]),
  ];

  it("draws every calling point, with the main line down-left and a branch off it", () => {
    const map = layoutDiagram(spine);
    const stations = byCrs(map.stations);
    const eus = stations.get("EUS");
    const halt = stations.get("HALT");
    const cre = stations.get("CRE");
    const mkc = stations.get("MKC");
    const rug = stations.get("RUG");
    const nmp = stations.get("NMP");
    assert.ok(eus && halt && cre && mkc && rug && nmp);
    assert.ok(cre.x > eus.x);
    assert.ok(cre.y < eus.y);
    assert.equal(eus.lane, 0);
    assert.equal(halt.lane, 0);
    assert.ok(between(halt, eus, mkc));
    assert.equal(offSegment(halt, eus, mkc), false);
    assert.notEqual(nmp.lane, 0);
    assert.deepEqual(layoutDiagram(spine), map);
    const branch = map.corridors.find((corridor) => corridor.from === "NMP" || corridor.to === "NMP");
    assert.ok(branch);
    assert.ok(branch.points.length >= 8);
    let sharpest = 0;
    for (let index = 1; index < branch.points.length - 1; index++) {
      const prev = branch.points[index - 1]!;
      const point = branch.points[index]!;
      const next = branch.points[index + 1]!;
      const inward = Math.atan2(point.y - prev.y, point.x - prev.x);
      const outward = Math.atan2(next.y - point.y, next.x - point.x);
      let turn = outward - inward;
      if (turn > Math.PI) turn -= Math.PI * 2;
      if (turn < -Math.PI) turn += Math.PI * 2;
      sharpest = Math.max(sharpest, Math.abs(turn));
    }
    assert.ok(sharpest < Math.PI / 6);
  });

  it("keeps Harrow on the single Euston to Watford line", () => {
    const map = layoutDiagram([
      journey(["EUS", "WFJ"]),
      journey(["EUS", "HRW", "WFJ"]),
    ]);
    const stations = byCrs(map.stations);
    const eus = stations.get("EUS");
    const wfj = stations.get("WFJ");
    const hrw = stations.get("HRW");
    assert.ok(eus && wfj && hrw);
    assert.equal(
      map.corridors.some((corridor) => corridor.from === "EUS" && corridor.to === "WFJ"),
      false,
    );
    assert.ok(between(hrw, eus, wfj));
    assert.equal(offSegment(hrw, eus, wfj), false);
  });

  it("keeps Apsley on the line between Kings Langley and Hemel", () => {
    const map = layoutDiagram([
      journey(["KGL", "HML"]),
      journey(["KGL", "APS", "HML"]),
    ]);
    const stations = byCrs(map.stations);
    const kgl = stations.get("KGL");
    const hml = stations.get("HML");
    const aps = stations.get("APS");
    assert.ok(kgl && hml && aps);
    assert.equal(
      map.corridors.some((corridor) => corridor.from === "HML" && corridor.to === "KGL"),
      false,
    );
    assert.ok(between(aps, kgl, hml));
    assert.equal(offSegment(aps, kgl, hml), false);
  });

  it("draws a station that only one train calls at", () => {
    const map = layoutDiagram([
      journey(["AAA", "BBB"]),
      journey(["AAA", "BBB"]),
      journey(["AAA", "BBB"]),
      journey(["AAA", "CCC", "BBB"]),
    ]);
    const stations = byCrs(map.stations);
    const aaa = stations.get("AAA");
    const bbb = stations.get("BBB");
    const ccc = stations.get("CCC");
    assert.ok(aaa && bbb && ccc);
    assert.equal(
      map.corridors.some((corridor) => corridor.from === "AAA" && corridor.to === "BBB"),
      false,
    );
    assert.ok(between(ccc, aaa, bbb));
    assert.equal(offSegment(ccc, aaa, bbb), false);
  });

  it("forks the Abbey line at Watford and leaves Apsley on the main line", () => {
    const map = layoutDiagram([
      journey(["WFJ", "KGL", "APS", "HML"]),
      journey(["WFJ", "WFN", "SAA"]),
      journey(["EUS", "HRW", "WFJ"]),
      journey(["WFJ", "APS"]),
      journey(["KGL", "HML"]),
    ]);
    const stations = byCrs(map.stations);
    const aps = stations.get("APS");
    const saa = stations.get("SAA");
    const kgl = stations.get("KGL");
    const hml = stations.get("HML");
    assert.ok(aps && saa && kgl && hml);
    assert.ok(between(aps, kgl, hml));
    assert.equal(offSegment(aps, kgl, hml), false);
    assert.equal(aps.lane, 0);
    assert.notEqual(saa.lane, 0);
    assert.ok(aps.x !== saa.x || aps.y !== saa.y);
    assert.equal(
      map.corridors.some((corridor) => corridor.from === "APS" && corridor.to === "SAA"),
      false,
    );
    assert.equal(
      map.corridors.some((corridor) => corridor.from === "HML" && corridor.to === "KGL"),
      false,
    );
  });
});

describe("layoutDiagram with geo", () => {
  const wcmlGeo: GeoIndex = indexStationGeo([
    { crs: "EUS", lat: 51.52837626, lon: -0.134558479 },
    { crs: "KGL", lat: 51.70635749, lon: -0.438410982 },
    { crs: "APS", lat: 51.73252418, lon: -0.462924369 },
    { crs: "HML", lat: 51.74233574, lon: -0.490764101 },
    { crs: "WFJ", lat: 51.66352959, lon: -0.396519634 },
  ]);

  it("keeps north-up geography so Euston is east and south of Kings Langley", () => {
    const map = layoutDiagram(
      [
        journey(["EUS", "WFJ", "KGL", "APS", "HML"]),
        journey(["EUS", "KGL", "HML"]),
        journey(["KGL", "APS", "HML"]),
      ],
      { geo: wcmlGeo },
    );
    const stations = byCrs(map.stations);
    const eus = stations.get("EUS");
    const kgl = stations.get("KGL");
    assert.ok(eus && kgl);
    assert.ok(eus.x > kgl.x);
    assert.ok(eus.y > kgl.y);
    assert.ok(map.corridors.every((corridor) => corridor.points.length === 2));
  });

  it("keeps Apsley east and south of Hemel after nudges", () => {
    const map = layoutDiagram(
      [journey(["KGL", "APS", "HML"]), journey(["KGL", "HML"]), journey(["APS", "HML"])],
      { geo: wcmlGeo },
    );
    const stations = byCrs(map.stations);
    const aps = stations.get("APS");
    const hml = stations.get("HML");
    assert.ok(aps && hml);
    assert.ok(aps.x > hml.x - 8);
    assert.ok(aps.y > hml.y - 8);
  });

  it("nudges near-identical stations apart along neighbour-average direction", () => {
    const geo = indexStationGeo([
      { crs: "HUB", lat: 52.5, lon: -1.9 },
      { crs: "AAA", lat: 52.51, lon: -1.9 },
      { crs: "BBB", lat: 52.5100001, lon: -1.9000001 },
    ]);
    const map = layoutDiagram(
      [journey(["HUB", "AAA"]), journey(["HUB", "BBB"]), journey(["AAA", "BBB"])],
      { geo },
    );
    const stations = byCrs(map.stations);
    const aaa = stations.get("AAA");
    const bbb = stations.get("BBB");
    const hub = stations.get("HUB");
    assert.ok(aaa && bbb && hub);
    assert.ok(Math.hypot(bbb.x - aaa.x, bbb.y - aaa.y) >= DOT_MIN_DIST_TEST - 0.05);
    // Both remain roughly north of the hub (smaller y than hub on SVG).
    assert.ok(aaa.y < hub.y);
    assert.ok(bbb.y < hub.y);
  });

  it("keeps a finite position when one CRS is missing from geo", () => {
    const geo = indexStationGeo([
      { crs: "BHM", lat: 52.4778, lon: -1.8986 },
      { crs: "CRE", lat: 53.0890, lon: -2.4320 },
    ]);
    const map = layoutDiagram([journey(["BHM", "ZZZ", "CRE"])], { geo });
    const stations = byCrs(map.stations);
    const zzz = stations.get("ZZZ");
    assert.ok(zzz);
    assert.ok(Number.isFinite(zzz.x) && Number.isFinite(zzz.y));
    assert.equal(map.corridors.length >= 1, true);
  });

  it("drops A-C, A-D, and A-E when a longer collinear path covers them", () => {
    const geo = indexStationGeo([
      { crs: "A", lat: 52.0, lon: -2.0 },
      { crs: "B", lat: 52.1, lon: -1.8 },
      { crs: "C", lat: 52.2, lon: -1.6 },
      { crs: "D", lat: 52.3, lon: -1.4 },
      { crs: "E", lat: 52.4, lon: -1.2 },
    ]);
    const map = layoutDiagram(
      [
        journey(["A", "B", "C"]),
        journey(["C", "D", "E"]),
        journey(["A", "C"]),
        journey(["A", "D"]),
        journey(["A", "E"]),
        journey(["B", "E"]),
      ],
      { geo },
    );
    const pairs = map.corridors.map((corridor) => `${corridor.from}-${corridor.to}`);
    assert.equal(pairs.includes("A-C") || pairs.includes("C-A"), false);
    assert.equal(pairs.includes("A-D") || pairs.includes("D-A"), false);
    assert.equal(pairs.includes("A-E") || pairs.includes("E-A"), false);
    assert.equal(pairs.includes("B-E") || pairs.includes("E-B"), false);
    assert.ok(pairs.includes("A-B") || pairs.includes("B-A"));
    assert.ok(pairs.includes("B-C") || pairs.includes("C-B"));
    assert.ok(pairs.includes("C-D") || pairs.includes("D-C"));
    assert.ok(pairs.includes("D-E") || pairs.includes("E-D"));
  });

  it("drops long skips on a single journey for any span", () => {
    const map = layoutDiagram([
      journey(["A", "B", "C", "D", "E"]),
      journey(["A", "C"]),
      journey(["A", "D"]),
      journey(["A", "E"]),
      journey(["B", "E"]),
    ]);
    const pairs = map.corridors.map((corridor) => `${corridor.from}-${corridor.to}`);
    assert.equal(pairs.includes("A-C") || pairs.includes("C-A"), false);
    assert.equal(pairs.includes("A-D") || pairs.includes("D-A"), false);
    assert.equal(pairs.includes("A-E") || pairs.includes("E-A"), false);
    assert.equal(pairs.includes("B-E") || pairs.includes("E-B"), false);
    assert.deepEqual(pairs.sort(), ["A-B", "B-C", "C-D", "D-E"]);
  });

  it("keeps a Droitwich bridge when skip-chord drops would isolate Worcester", () => {
    // Real LM pattern: some trains run WOS–DTW / WOF–DTW direct; others call both
    // Worcester stations so markSkippedPairs drops both directs and disconnects HFD.
    const map = layoutDiagram([
      journey(["HFD", "WOF", "WOS"]),
      journey(["WOF", "WOS"]),
      journey(["WOS", "DTW", "BHM"]),
      journey(["WOF", "DTW", "BHM"]),
      journey(["WOS", "WOF", "DTW", "BHM"]),
      journey(["WOF", "WOS", "DTW", "BHM"]),
    ]);
    const pairs = map.corridors.map((corridor) => `${corridor.from}-${corridor.to}`);
    const has = (a: string, b: string) =>
      pairs.includes(`${a}-${b}`) || pairs.includes(`${b}-${a}`);
    assert.ok(has("WOS", "DTW") || has("WOF", "DTW"));
    assert.ok(has("DTW", "BHM"));
    assert.ok(has("HFD", "WOF") || has("HFD", "WOS"));
  });
});

describe("station geo file", () => {
  it("loads CRS coordinates from the packaged geo table", () => {
    const geo = loadStationGeo();
    assert.ok(geo.size > 2000);
    const bhm = geo.get("BHM");
    assert.ok(bhm);
    assert.ok(bhm.lat > 52 && bhm.lat < 53);
    assert.ok(bhm.lon < -1 && bhm.lon > -2);
  });
});

describe("network schematic file", () => {
  it("writes the diagram beside the sqlite and rebuilds it when the file is missing", async () => {
    const { sqlitePath, db } = await createFixtureDb();
    insertLmJourney(db);
    db.prepare("INSERT INTO feed_meta (key, value) VALUES ('load_day', '20261027')").run();
    db.prepare("INSERT INTO feed_meta (key, value) VALUES ('hd_sequence', '007')").run();
    db.close();

    await writeNetworkDiagram(sqlitePath);
    const path = networkSchematicPath(sqlitePath);
    assert.equal(existsSync(path), true);
    const written = JSON.parse(readFileSync(path, "utf8")) as {
      loadDay: string;
      hdSequence: string;
      stations: { crs: string }[];
      lines: { id: string; stations: string[] }[];
    };
    assert.equal(written.loadDay, "20261027");
    assert.equal(written.hdSequence, "007");
    assert.ok(written.stations.some((station) => station.crs === "CRE"));
    assert.ok(written.stations.some((station) => station.crs === "BHM"));
    assert.ok(Array.isArray(written.lines));
    assert.ok(written.lines.length > 0);

    unlinkSync(path);
    const missing = readNetworkDiagram(sqlitePath);
    assert.deepEqual(missing, { reason: "missing" });
    assert.equal(existsSync(path), false);
  });
});

describe("linePathsFromJourneys", () => {
  it("emits an ordered junction-to-junction line including intermediates", () => {
    const lines = linePathsFromJourneys([
      journey(["EUS", "WFJ", "APS", "HML", "BKM", "MKC", "CRE"]),
      journey(["EUS", "WFJ", "APS", "HML", "MKC"]),
    ]);
    assert.ok(lines.length >= 1);
    const throughAps = lines.find((line) => line.stations.includes("APS"));
    assert.ok(throughAps);
    const idx = throughAps!.stations.indexOf("APS");
    assert.ok(idx > 0);
    assert.ok(idx < throughAps!.stations.length - 1);
    assert.ok(throughAps!.stations.includes("HML"));
    assert.ok(throughAps!.stations.includes("WFJ") || throughAps!.stations.includes("EUS"));
  });

  it("layoutDiagram includes the same lines field", () => {
    const map = layoutDiagram([
      journey(["EUS", "APS", "HML", "MKC"]),
    ]);
    assert.ok(map.lines.length >= 1);
    assert.ok(map.lines.some((line) => line.stations.includes("APS")));
  });
});
