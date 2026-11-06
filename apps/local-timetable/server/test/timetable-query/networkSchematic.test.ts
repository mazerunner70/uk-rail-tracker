import assert from "node:assert/strict";
import { existsSync, readFileSync, unlinkSync } from "node:fs";
import { describe, it } from "node:test";
import { layoutDiagram, type StationLayout } from "../../src/networkSchematic.js";
import {
  networkSchematicPath,
  readNetworkDiagram,
  writeNetworkDiagram,
} from "../../src/networkSchematicFile.js";
import { createFixtureDb, insertLmJourney } from "./helpers.js";

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

describe("network schematic file", () => {
  it("writes the diagram beside the sqlite and rebuilds it when the file is missing", async () => {
    const { sqlitePath, db } = await createFixtureDb();
    insertLmJourney(db);
    db.prepare("INSERT INTO feed_meta (key, value) VALUES ('load_day', '20261027')").run();
    db.prepare("INSERT INTO feed_meta (key, value) VALUES ('hd_sequence', '007')").run();
    db.close();

    writeNetworkDiagram(sqlitePath);
    const path = networkSchematicPath(sqlitePath);
    assert.equal(existsSync(path), true);
    const written = JSON.parse(readFileSync(path, "utf8")) as {
      loadDay: string;
      hdSequence: string;
      stations: { crs: string }[];
    };
    assert.equal(written.loadDay, "20261027");
    assert.equal(written.hdSequence, "007");
    assert.ok(written.stations.some((station) => station.crs === "CRE"));
    assert.ok(written.stations.some((station) => station.crs === "BHM"));

    unlinkSync(path);
    const rebuilt = readNetworkDiagram(sqlitePath);
    assert.equal("reason" in rebuilt, false);
    if ("reason" in rebuilt) return;
    assert.equal(rebuilt.hdSequence, "007");
    assert.equal(existsSync(path), true);
  });
});
