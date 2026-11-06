import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { markersOn, placeTrain, stationBadgeOffset } from "../src/networkMap.js";
import type { NetworkDiagram, NetworkStop, NetworkTrain } from "../src/types.js";

function stop(crs: string, arr: string | null, dep: string | null): NetworkStop {
  return { crs, arr, dep };
}

function train(
  serviceGuid: string,
  originCrs: string,
  destCrs: string,
  stops: NetworkStop[],
): NetworkTrain {
  return { serviceGuid, originCrs, destCrs, problem: false, stops };
}

describe("placeTrain", () => {
  it("puts a train halfway along the hop it is on", () => {
    const place = placeTrain(
      [stop("CRE", null, "1000"), stop("STA", "1100", "1105")],
      "1030",
    );
    assert.deepEqual(place, {
      atStation: false,
      fromCrs: "CRE",
      toCrs: "STA",
      fraction: 0.5,
    });
  });

  it("keeps a dwelling train on the station it has reached", () => {
    const place = placeTrain(
      [stop("CRE", null, "0900"), stop("STA", "1000", "1010"), stop("BHM", "1100", null)],
      "1005",
    );
    assert.deepEqual(place, {
      atStation: true,
      crs: "STA",
      towardCrs: "BHM",
      fromCrs: "CRE",
    });
  });

  it("keeps a terminated train on the destination", () => {
    const place = placeTrain(
      [stop("CRE", null, "0900"), stop("BHM", "1000", null)],
      "1100",
    );
    assert.deepEqual(place, {
      atStation: true,
      crs: "BHM",
      towardCrs: null,
      fromCrs: "CRE",
    });
  });

  it("keeps a train that has not left on the origin", () => {
    const place = placeTrain(
      [stop("EUS", null, "1400"), stop("MKC", "1500", null)],
      "1300",
    );
    assert.deepEqual(place, {
      atStation: true,
      crs: "EUS",
      towardCrs: "MKC",
      fromCrs: null,
    });
  });

  it("uses the next-day minute rule after midnight", () => {
    const place = placeTrain(
      [stop("EUS", null, "2300"), stop("MKC", "0030", null)],
      "0015",
    );
    assert.ok(place && !place.atStation);
    if (!place || place.atStation) return;
    assert.ok(Math.abs(place.fraction - 75 / 90) < 1e-9);
  });
});

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

describe("markersOn", () => {
  const diagram = lineDiagram(["EUS", "MKC", "RUG", "STA", "CRE"]);

  it("places a moving train on the stored line and keeps the stored stations", () => {
    const map = markersOn(
      diagram,
      [
        train("spine", "EUS", "CRE", [
          stop("EUS", null, "1200"),
          stop("MKC", "1230", "1232"),
          stop("RUG", "1300", "1302"),
          stop("STA", "1330", "1332"),
          stop("CRE", "1400", null),
        ]),
        train("mid", "MKC", "RUG", [
          stop("MKC", null, "1000"),
          stop("RUG", "1100", "1105"),
        ]),
      ],
      "1030",
    );
    assert.deepEqual(map.stations, diagram.stations);
    assert.deepEqual(map.corridors, diagram.corridors);
    const mkc = diagram.stations.find((station) => station.crs === "MKC");
    const rug = diagram.stations.find((station) => station.crs === "RUG");
    const eus = diagram.stations.find((station) => station.crs === "EUS");
    assert.ok(mkc && rug && eus);
    const mid = map.markers.find((marker) => marker.serviceGuid === "mid");
    assert.ok(mid);
    assert.equal(mid.atStation, false);
    assert.ok(Math.abs(mid.x - (mkc.x + rug.x) / 2) < 1e-6);
    assert.ok(Math.abs(mid.y - (mkc.y + rug.y) / 2) < 1e-6);
    const standing = map.markers.find((marker) => marker.serviceGuid === "spine");
    assert.ok(standing);
    assert.equal(standing.atStation, true);
    assert.ok(Math.abs(standing.x - eus.x) < 1e-6);
    assert.ok(Math.abs(standing.y - eus.y) < 1e-6);
  });

  it("uses one arrow when every train at a station leaves the same way", () => {
    const map = markersOn(
      diagram,
      [
        train("spine", "EUS", "MKC", [stop("EUS", null, "1200"), stop("MKC", "1300", null)]),
        train("also", "EUS", "MKC", [stop("EUS", null, "1500"), stop("MKC", "1600", null)]),
      ],
      "1030",
    );
    const eus = map.stations.find((station) => station.crs === "EUS");
    const mkc = map.stations.find((station) => station.crs === "MKC");
    assert.ok(eus && mkc);
    const atEus = map.markers.filter(
      (marker) => marker.atStation && marker.x === eus.x && marker.y === eus.y,
    );
    assert.equal(atEus.length, 1);
    assert.equal(atEus[0]?.serviceGuid, "also,spine");
    assert.ok(Math.abs((atEus[0]?.angle ?? 0) - Math.atan2(mkc.y - eus.y, mkc.x - eus.x)) < 1e-6);
  });

  it("shows at most two opposite departure arrows at a station", () => {
    const map = markersOn(
      diagram,
      [
        train("down", "STA", "RUG", [stop("STA", null, "1200"), stop("RUG", "1300", null)]),
        train("up", "STA", "CRE", [stop("STA", null, "1200"), stop("CRE", "1300", null)]),
        train("up-2", "STA", "CRE", [stop("STA", null, "1210"), stop("CRE", "1310", null)]),
      ],
      "1030",
    );
    const sta = map.stations.find((station) => station.crs === "STA");
    assert.ok(sta);
    const atSta = map.markers.filter(
      (marker) => marker.atStation && marker.x === sta.x && marker.y === sta.y,
    );
    assert.equal(atSta.length, 2);
    const turn = Math.atan2(
      Math.sin(atSta[0]!.angle - atSta[1]!.angle),
      Math.cos(atSta[0]!.angle - atSta[1]!.angle),
    );
    assert.ok(Math.abs(Math.abs(turn) - Math.PI) < 1e-6);
    const onward = atSta.find((marker) => marker.serviceGuid.includes("up"));
    const back = atSta.find((marker) => marker.serviceGuid.includes("down"));
    assert.equal(onward?.serviceGuid, "up,up-2");
    assert.equal(back?.serviceGuid, "down");
  });

  it("puts a train on the Harrow line", () => {
    const diagram: NetworkDiagram = {
      loadDay: "20261106",
      hdSequence: "1",
      stations: [
        { crs: "EUS", x: 0, y: 0, rank: 0, lane: 0 },
        { crs: "HRW", x: 50, y: 40, rank: 0.5, lane: 1 },
        { crs: "WFJ", x: 100, y: 0, rank: 1, lane: 0 },
      ],
      corridors: [
        {
          from: "EUS",
          to: "WFJ",
          via: [],
          points: [
            { x: 0, y: 0 },
            { x: 100, y: 0 },
          ],
          color: "#7dffa8",
        },
        {
          from: "EUS",
          to: "WFJ",
          via: ["HRW"],
          points: [
            { x: 0, y: 0 },
            { x: 50, y: 40 },
            { x: 100, y: 0 },
          ],
          color: "#f0b45a",
        },
      ],
      width: 200,
      height: 100,
    };
    const map = markersOn(
      diagram,
      [train("via", "EUS", "WFJ", [
        stop("EUS", null, "1000"),
        stop("HRW", "1030", "1032"),
        stop("WFJ", "1100", null),
      ])],
      "1015",
    );
    const marker = map.markers.find((item) => item.serviceGuid === "via");
    assert.ok(marker);
    assert.ok(Math.abs(marker.x - 25) < 1);
    assert.ok(Math.abs(marker.y - 20) < 1);
  });

  it("puts branch badges on the opposite side of the main line", () => {
    const spine = stationBadgeOffset(0);
    const branch = stationBadgeOffset(1);
    assert.ok(spine.x * branch.x + spine.y * branch.y < 0);
  });
});
