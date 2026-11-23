import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  createInitialViewModel,
  markerVisibleTrains,
  rebuildRouteLive,
} from "../src/controller.js";
import {
  buildRouteLiveModel,
  normalizeRouteTrain,
  placementStopsFromRows,
  rowsForTrain,
} from "../src/routeLiveModel.js";
import { placeTrain } from "../src/networkMap.js";
import type { NetworkDiagram, NetworkTrain, ServiceDetail } from "../src/types.js";

function diagram(line: string[]): NetworkDiagram {
  const stations = line.map((crs, index) => ({
    crs,
    x: 40 + index * 80,
    y: 80,
    rank: index,
    lane: 0,
  }));
  const corridors = [];
  for (let i = 1; i < stations.length; i++) {
    const from = stations[i - 1]!;
    const to = stations[i]!;
    corridors.push({
      from: from.crs,
      to: to.crs,
      via: [] as string[],
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
    width: 400,
    height: 160,
  };
}

function train(overrides: Partial<NetworkTrain> & { serviceGuid: string }): NetworkTrain {
  return {
    problem: false,
    statusLabel: "on time",
    ...overrides,
  };
}

describe("placementStopsFromRows", () => {
  it("prefers forecast times for placeTrain", () => {
    const rows = rowsForTrain(
      train({
        serviceGuid: "1M45",
        stops: [
          { crs: "EUS", arr: null, dep: "1000" },
          { crs: "MKC", arr: "1200", dep: "1205" },
        ],
      }),
      {
        serviceGuid: "1M45",
        originCrs: "EUS",
        destCrs: "MKC",
        reachedCrs: "EUS",
        nextCrs: "MKC",
        scheduledTime: "1000",
        problem: false,
        statusLabel: "on time",
        delayMinutes: null,
        trainLength: null,
        stops: [
          {
            crs: "EUS",
            publicArr: null,
            publicDep: "1000",
            forecastArr: null,
            forecastDep: "1000",
            platform: null,
            platformSuppressed: false,
            trainLength: null,
          },
          {
            crs: "MKC",
            publicArr: "1200",
            publicDep: "1205",
            forecastArr: "1230",
            forecastDep: "1235",
            platform: null,
            platformSuppressed: false,
            trainLength: null,
          },
        ],
        updates: [],
        eventTags: [],
      },
    );
    const placed = placementStopsFromRows(rows);
    assert.equal(placed[1]?.arr, "1230");
    assert.equal(placed[1]?.dep, "1235");
    const publicPlace = placeTrain(
      [
        { crs: "EUS", arr: null, dep: "1000" },
        { crs: "MKC", arr: "1200", dep: "1205" },
      ],
      "1215",
    );
    const forecastPlace = placeTrain(placed, "1215");
    // Public: already arrived MKC (arr 1200). Forecast arr 1230 → still en route.
    assert.ok(publicPlace?.atStation);
    assert.equal(publicPlace?.atStation && publicPlace.crs, "MKC");
    assert.ok(forecastPlace && !forecastPlace.atStation);
    assert.equal(forecastPlace?.atStation === false && forecastPlace.fromCrs, "EUS");
  });
});

describe("normalizeRouteTrain", () => {
  it("shares place and journeyProgress for strip and card fields", () => {
    const held = train({
      serviceGuid: "1M45",
      originCrs: "EUS",
      destCrs: "BHM",
      stops: [
        { crs: "EUS", arr: null, dep: "1000" },
        { crs: "MKC", arr: "1200", dep: "1205" },
        { crs: "BHM", arr: "1400", dep: null },
      ],
    });
    const live = normalizeRouteTrain(held, "1100", ["EUS", "MKC", "BHM"]);
    assert.ok(live.place && !live.place.atStation);
    assert.ok(live.journeyProgress !== null);
    assert.ok(live.windowProgress !== null);
    assert.equal(live.place?.atStation === false && live.place.fromCrs, "EUS");
    assert.equal(live.place?.atStation === false && live.place.toCrs, "MKC");
  });
});

describe("buildRouteLiveModel / rebuildRouteLive", () => {
  it("puts the same selected place on strip markers and the entity card", () => {
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.page = "route";
    vm.diagramHeld = diagram(["EUS", "WFJ", "MKC", "BHM"]);
    vm.routeTrack = {
      kind: "train",
      serviceGuid: "1M45",
      lastWindowKey: null,
    };
    vm.networkHeld = {
      calendarDate: "261106",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        train({
          serviceGuid: "1M45",
          originCrs: "EUS",
          destCrs: "BHM",
          scheduledTime: "1300",
          stops: [
            { crs: "EUS", arr: null, dep: "1000" },
            { crs: "MKC", arr: "1200", dep: "1205" },
            { crs: "BHM", arr: "1400", dep: null },
          ],
        }),
        train({
          serviceGuid: "2A08",
          originCrs: "EUS",
          destCrs: "BHM",
          scheduledTime: "1200",
          stops: [
            { crs: "EUS", arr: null, dep: "0900" },
            { crs: "WFJ", arr: "1000", dep: "1005" },
            { crs: "BHM", arr: "1200", dep: null },
          ],
        }),
      ],
    };
    rebuildRouteLive(vm, "1100");
    const model = vm.routeLive;
    assert.ok(model);
    assert.ok(model!.selected);
    assert.equal(model!.selected!.serviceGuid, "1M45");
    assert.equal(model!.layout.selectedProgress, model!.selected!.journeyProgress);
    const marker = model!.layout.markers.find((m) => m.serviceGuid === "1M45");
    assert.ok(marker);
    assert.ok(model!.trainsOnStrip.some((t) => t.serviceGuid === "1M45"));
    assert.equal(model!.selected!.place?.atStation, false);
  });

  it("moves selected placement when service detail forecasts lag public times", () => {
    const held = train({
      serviceGuid: "1M45",
      originCrs: "EUS",
      destCrs: "BHM",
      scheduledTime: "1300",
      stops: [
        { crs: "EUS", arr: null, dep: "1000" },
        { crs: "MKC", arr: "1100", dep: "1105" },
        { crs: "BHM", arr: "1300", dep: null },
      ],
    });
    const detail: ServiceDetail = {
      serviceGuid: "1M45",
      originCrs: "EUS",
      destCrs: "BHM",
      reachedCrs: "EUS",
      nextCrs: "MKC",
      scheduledTime: "1300",
      problem: true,
      statusLabel: "delayed",
      delayMinutes: 20,
      trainLength: 8,
      stops: [
        {
          crs: "EUS",
          publicArr: null,
          publicDep: "1000",
          forecastArr: null,
          forecastDep: "1000",
          platform: "1",
          platformSuppressed: false,
          trainLength: 8,
        },
        {
          crs: "MKC",
          publicArr: "1100",
          publicDep: "1105",
          forecastArr: "1120",
          forecastDep: "1125",
          platform: "4",
          platformSuppressed: false,
          trainLength: null,
        },
        {
          crs: "BHM",
          publicArr: "1300",
          publicDep: null,
          forecastArr: "1320",
          forecastDep: null,
          platform: null,
          platformSuppressed: false,
          trainLength: null,
        },
      ],
      updates: [],
      eventTags: ["delayed"],
    };
    const publicLive = normalizeRouteTrain(held, "1110", ["EUS", "MKC", "BHM"]);
    const detailLive = normalizeRouteTrain(held, "1110", ["EUS", "MKC", "BHM"], detail);
    // Public arr MKC 1100 → past arrival; forecast arr 1120 → still approaching MKC.
    assert.ok(publicLive.place?.atStation || (publicLive.place && !publicLive.place.atStation));
    assert.ok(detailLive.place && !detailLive.place.atStation);
    assert.equal(detailLive.place?.atStation === false && detailLive.place.toCrs, "MKC");
    assert.equal(detailLive.delayMinutes, 20);
    assert.equal(detailLive.trainLength, 8);

    const model = buildRouteLiveModel(
      "1110",
      {
        window: ["EUS", "MKC", "BHM"],
        windowKey: "at:EUS",
        selectedServiceGuid: "1M45",
      },
      [held],
      markerVisibleTrains,
      held,
      detail,
    );
    assert.ok(model?.selected);
    assert.equal(model!.selected!.delayMinutes, 20);
    assert.equal(model!.selected!.journeyProgress, model!.layout.selectedProgress);
  });
});
