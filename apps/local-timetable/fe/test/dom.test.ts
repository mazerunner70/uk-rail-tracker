import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { createInitialViewModel } from "../src/controller.js";
import { isMobileViewport, renderApp, schedulePick } from "../src/dom.js";
import { stationName } from "../src/stationNames.js";
import type { ScreenViewModel } from "../src/controller.js";
import type { NetworkDiagram } from "../src/types.js";

const londonAfternoon = new Date("2026-11-06T13:00:00Z");

function render(vm: ScreenViewModel, now = londonAfternoon): HTMLElement {
  const root = document.createElement("div");
  renderApp(root, vm, now);
  return root;
}

function text(root: ParentNode, testId: string): string {
  return root.querySelector(`[data-testid="${testId}"]`)?.textContent ?? "";
}

function heldDiagram(lines: string[][]): NetworkDiagram {
  const stations: NetworkDiagram["stations"] = [];
  const corridors: NetworkDiagram["corridors"] = [];
  let x = 40;
  for (const line of lines) {
    const row = line.map((crs, index) => {
      const station = { crs, x: x + index * 80, y: 80, rank: index, lane: 0 };
      stations.push(station);
      return station;
    });
    for (let index = 1; index < row.length; index++) {
      const from = row[index - 1]!;
      const to = row[index]!;
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
    x += line.length * 80 + 40;
  }
  return { loadDay: "20261106", hdSequence: "1", stations, corridors, width: x, height: 160 };
}

describe("network page", () => {
  it("paints the three regions, status words, and problem rows", () => {
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.loadSucceeded = true;
    vm.diagramHeld = heldDiagram([["EUS", "MKC"], ["CRE", "STA"]]);
    vm.networkHeld = {
      calendarDate: "251102",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        {
          serviceGuid: "on-time-1",
          originCrs: "EUS",
          destCrs: "MKC",
          reachedCrs: "EUS",
          nextCrs: "MKC",
          scheduledTime: "1310",
          problem: false,
          statusLabel: "on time",
          stops: [
            { crs: "EUS", arr: null, dep: "1310" },
            { crs: "MKC", arr: "1400", dep: null },
          ],
        },
        {
          serviceGuid: "late-1",
          originCrs: "CRE",
          destCrs: "STA",
          reachedCrs: "CRE",
          nextCrs: "STA",
          scheduledTime: "1310",
          problem: true,
          statusLabel: "delayed",
          eventTags: ["delayed", "call_forecast"],
          stops: [
            { crs: "CRE", arr: null, dep: "1310" },
            { crs: "STA", arr: "1400", dep: null },
          ],
        },
      ],
    };
    const root = render(vm);
    assert.match(root.textContent ?? "", /JOURNEY CONTROL CENTRE/);
    assert.doesNotMatch(text(root, "network-overview"), /This panel has no data/);
    assert.match(text(root, "network-overview"), /EUS/);
    assert.match(text(root, "network-overview"), /Main line/);
    assert.ok(root.querySelector('[data-testid="network-map"]'));
    assert.ok(root.querySelector('[data-testid="map-reset"]'));
    assert.match(text(root, "network-overview"), /Whole view/);
    assert.match(text(root, "network-overview"), /Scroll to zoom. Hold Shift and drag to move./);
    const badge = root.querySelector('.station-mark[data-crs="EUS"] .station-label');
    assert.equal(badge?.textContent, "EUS");
    assert.equal(
      root.querySelector('[data-service="on-time-1"]')?.getAttribute("data-at-station"),
      "true",
    );
    assert.equal(
      root.querySelector('[data-service="late-1"]')?.getAttribute("data-at-station"),
      "true",
    );
    assert.match(text(root, "network-list"), /From/);
    assert.match(text(root, "network-list"), /To/);
    assert.match(text(root, "network-list"), /At/);
    assert.match(text(root, "network-list"), /Time/);
    assert.doesNotMatch(text(root, "network-list"), /Headcode/);
    assert.match(text(root, "network-list"), /on time/);
    assert.match(text(root, "network-list"), /delayed/);
    assert.match(text(root, "network-list"), /EUS/);
    assert.match(text(root, "network-list"), /1310/);
    assert.match(text(root, "network-list"), /no data/);
    assert.match(text(root, "disruptions"), /Disruption summary|delayed/);
    assert.match(text(root, "disruption-summary"), /delayed/);
    assert.match(text(root, "disruption-summary"), /1/);
    assert.doesNotMatch(text(root, "disruption-summary"), /on-time-1/);
    assert.match(text(root, "disruption-trend"), /This panel has no data/);
    assert.match(text(root, "incident-logs"), /late-1/);
    assert.match(text(root, "footer-active"), /2/);
    assert.match(text(root, "footer-on-time"), /1 \(50%\)/);
    assert.match(text(root, "footer-delayed"), /1/);
    assert.match(text(root, "footer-cancelled"), /no data/);
    assert.match(text(root, "footer-updates-per-min"), /no data/);
    assert.equal(root.querySelectorAll(".board > *").length, 2);
    assert.ok(root.querySelector('[data-testid="board-secondary"]'));
    assert.ok(root.querySelector('[data-testid="board-secondary"] [data-testid="live-schedules"]'));
    assert.ok(root.querySelector('[data-testid="board-secondary"] [data-testid="disruptions"]'));
    assert.match(text(root, "network-overview"), /Use \+\/− to zoom/);

    vm.searchText = "eus";
    const filtered = render(vm);
    assert.match(text(filtered, "disruptions"), /No interesting events/);
    assert.doesNotMatch(text(filtered, "incident-logs"), /late-1/);
    assert.match(text(filtered, "incident-logs"), /No problems/);
  });

  it("detects mobile viewports for the network-first layout", () => {
    assert.equal(isMobileViewport({
      matchMedia: (query: string) => ({
        matches: query.includes("max-width: 720px"),
        media: query,
        onchange: null,
        addListener() {},
        removeListener() {},
        addEventListener() {},
        removeEventListener() {},
        dispatchEvent() { return false; },
      }),
    }), true);
    assert.equal(isMobileViewport({
      matchMedia: () => ({
        matches: false,
        media: "",
        onchange: null,
        addListener() {},
        removeListener() {},
        addEventListener() {},
        removeEventListener() {},
        dispatchEvent() { return false; },
      }),
    }), false);
  });

  it("zooms the map and returns to the whole network", () => {
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.loadSucceeded = true;
    vm.diagramHeld = heldDiagram([["EUS", "MKC"], ["CRE", "STA"]]);
    vm.networkHeld = {
      calendarDate: "251102",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        {
          serviceGuid: "on-time-1",
          originCrs: "EUS",
          destCrs: "MKC",
          problem: false,
          stops: [
            { crs: "EUS", arr: null, dep: "1200" },
            { crs: "MKC", arr: "1400", dep: null },
          ],
        },
      ],
    };
    const root = render(vm);
    root.querySelector<HTMLButtonElement>('[data-testid="map-reset"]')?.click();
    const svg = root.querySelector<SVGSVGElement>('[data-testid="network-map"]');
    assert.ok(svg);
    const whole = svg.getAttribute("viewBox");
    svg.dispatchEvent(new WheelEvent("wheel", { deltaY: -120, bubbles: true, cancelable: true }));
    const zoomed = svg.getAttribute("viewBox");
    assert.notEqual(zoomed, whole);
    const wholeWidth = Number(whole?.split(/\s+/)[2]);
    const zoomedWidth = Number(zoomed?.split(/\s+/)[2]);
    assert.ok(zoomedWidth < wholeWidth);
    root.querySelector<HTMLButtonElement>('[data-testid="map-reset"]')?.click();
    assert.equal(svg.getAttribute("viewBox"), whole);
  });

  it("pans only while Shift is held and shows a hover tip", () => {
    const originalMatchMedia = window.matchMedia;
    window.matchMedia = ((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener() {},
      removeListener() {},
      addEventListener() {},
      removeEventListener() {},
      dispatchEvent() { return false; },
    })) as typeof window.matchMedia;
    try {
      const vm = createInitialViewModel();
      vm.phase = "loaded";
      vm.loadSucceeded = true;
      vm.diagramHeld = heldDiagram([["EUS", "MKC"], ["CRE", "STA"]]);
      vm.networkHeld = {
        calendarDate: "251102",
        generation: "g1",
        revision: 0,
        full: true,
        removed: [],
        trains: [
          {
            serviceGuid: "on-time-1",
            originCrs: "EUS",
            destCrs: "MKC",
            problem: false,
            stops: [
              { crs: "EUS", arr: null, dep: "1310" },
              { crs: "MKC", arr: "1400", dep: null },
            ],
          },
        ],
      };
      const root = render(vm);
      const svg = root.querySelector<SVGSVGElement>('[data-testid="network-map"]');
      const tip = root.querySelector<HTMLElement>('[data-testid="map-tooltip"]');
      const station = root.querySelector(".station-mark");
      assert.ok(svg && tip && station);
      assert.equal(tip.hidden, true);
      station.dispatchEvent(new PointerEvent("pointerover", { bubbles: true }));
      assert.equal(tip.hidden, false);
      assert.equal(tip.textContent, "London Euston");
      assert.equal(station.querySelector("text")?.textContent, "EUS");
      assert.equal(stationName("ZZZ"), "ZZZ");
      const train = root.querySelector('[data-testid="train-marker"]');
      assert.ok(train);
      train.dispatchEvent(new PointerEvent("pointerover", { bubbles: true }));
      assert.equal(tip.textContent, "EUSMKC-1310");
      assert.equal(train.getAttribute("data-tip"), "EUSMKC-1310");
      svg.dispatchEvent(new PointerEvent("pointerdown", { button: 0, bubbles: true, pointerId: 1 }));
      assert.equal(svg.classList.contains("is-panning"), false);
      svg.dispatchEvent(new PointerEvent("pointerdown", {
        button: 0,
        bubbles: true,
        shiftKey: true,
        pointerId: 2,
      }));
      assert.equal(svg.classList.contains("is-panning"), true);
      assert.equal(svg.classList.contains("is-shift"), true);
      window.dispatchEvent(new KeyboardEvent("keyup", { key: "Shift" }));
      assert.equal(svg.classList.contains("is-panning"), false);
      assert.equal(svg.classList.contains("is-shift"), false);
    } finally {
      window.matchMedia = originalMatchMedia;
    }
  });

  it("allows drag pan on the map in a mobile viewport", () => {
    const originalMatchMedia = window.matchMedia;
    window.matchMedia = ((query: string) => ({
      matches: query.includes("max-width: 720px") || query.includes("pointer: coarse"),
      media: query,
      onchange: null,
      addListener() {},
      removeListener() {},
      addEventListener() {},
      removeEventListener() {},
      dispatchEvent() { return false; },
    })) as typeof window.matchMedia;
    try {
      const vm = createInitialViewModel();
      vm.phase = "loaded";
      vm.loadSucceeded = true;
      vm.diagramHeld = heldDiagram([["EUS", "MKC"]]);
      vm.networkHeld = {
        calendarDate: "251102",
        generation: "g1",
        revision: 0,
        full: true,
        removed: [],
        trains: [],
      };
      const root = render(vm);
      assert.ok(root.querySelector(".shell.is-mobile"));
      assert.ok(root.querySelector(".page.is-mobile"));
      const svg = root.querySelector<SVGSVGElement>('[data-testid="network-map"]');
      assert.ok(svg);
      svg.dispatchEvent(new PointerEvent("pointerdown", {
        button: 0,
        bubbles: true,
        pointerId: 7,
        clientX: 40,
        clientY: 40,
      }));
      assert.equal(svg.classList.contains("is-panning"), true);
    } finally {
      window.matchMedia = originalMatchMedia;
    }
  });

  it("lists the live trains for a clicked station", () => {
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.loadSucceeded = true;
    vm.diagramHeld = heldDiagram([["EUS", "MKC"], ["CRE", "STA"]]);
    vm.networkHeld = {
      calendarDate: "261106",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        {
          serviceGuid: "via-mkc",
          originCrs: "EUS",
          destCrs: "MKC",
          scheduledTime: "1400",
          problem: false,
          stops: [
            { crs: "EUS", arr: null, dep: "1200" },
            { crs: "MKC", arr: "1400", dep: null },
          ],
        },
        {
          serviceGuid: "via-rug",
          originCrs: "BHM",
          destCrs: "RUG",
          scheduledTime: "1410",
          problem: false,
          stops: [
            { crs: "BHM", arr: null, dep: "1210" },
            { crs: "RUG", arr: "1410", dep: null },
          ],
        },
      ],
    };
    vm.scheduleFocus = { kind: "station", crs: "MKC" };
    const stationView = render(vm);
    assert.equal(stationView.querySelector(".station-mark.is-selected")?.getAttribute("data-crs"), "MKC");
    assert.match(text(stationView, "schedule-focus"), /Trains through Milton Keynes Central/);
    assert.deepEqual(
      Array.from(stationView.querySelectorAll('[data-testid="network-list"] tbody tr')).map((row) => row.getAttribute("data-service")),
      ["via-mkc"],
    );
    assert.match(text(stationView, "disruptions"), /No interesting events/);
    const station = stationView.querySelector(".station-mark");
    assert.ok(station);
    assert.equal(schedulePick(station)?.kind, "station");
  });

  it("shows a route preview card when a train is hovered", () => {
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.loadSucceeded = true;
    vm.diagramHeld = heldDiagram([["EUS", "WFJ", "MKC", "BHM"]]);
    vm.networkHeld = {
      calendarDate: "261106",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        {
          serviceGuid: "1M45",
          originCrs: "EUS",
          destCrs: "BHM",
          reachedCrs: "MKC",
          scheduledTime: "1530",
          problem: true,
          statusLabel: "delayed",
          delayMinutes: 14,
          stops: [
            { crs: "EUS", arr: null, dep: "1200" },
            { crs: "MKC", arr: "1400", dep: "1405" },
            { crs: "BHM", arr: "1600", dep: null },
          ],
        },
      ],
    };
    vm.routePreview = { serviceGuid: "1M45" };
    const root = render(vm);
    assert.ok(root.querySelector(".network-map.has-route-preview"));
    assert.match(text(root, "route-preview-card"), /SELECTED TRAIN: EUSBHM-1200/);
    assert.match(text(root, "route-preview-card"), /Delayed \+14m/);
    assert.ok(root.querySelector(".schedule-row.is-preview"));
  });
});

describe("route detail page", () => {
  it("renders the windowed strip, entity pane, and side panes for a tracked train", () => {
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.loadSucceeded = true;
    vm.page = "route";
    vm.diagramHeld = heldDiagram([["EUS", "WFJ", "MKC", "NMP", "BHM"]]);
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
        {
          serviceGuid: "1M45",
          originCrs: "EUS",
          destCrs: "BHM",
          reachedCrs: "NMP",
          scheduledTime: "1530",
          problem: true,
          statusLabel: "delayed",
          delayMinutes: 14,
          stops: [
            { crs: "EUS", arr: null, dep: "1200" },
            { crs: "MKC", arr: "1330", dep: "1335" },
            { crs: "BHM", arr: "1600", dep: null },
          ],
        },
        {
          serviceGuid: "2A08",
          originCrs: "EUS",
          destCrs: "BHM",
          problem: false,
          statusLabel: "on time",
          stops: [
            { crs: "EUS", arr: null, dep: "1100" },
            { crs: "WFJ", arr: "1200", dep: "1205" },
            { crs: "BHM", arr: "1400", dep: null },
          ],
        },
        {
          serviceGuid: "AM11",
          originCrs: "EUS",
          destCrs: "BHM",
          problem: true,
          statusLabel: "delayed",
          stops: [
            { crs: "EUS", arr: null, dep: "0900" },
            { crs: "WFJ", arr: "0930", dep: "0935" },
            { crs: "BHM", arr: "1030", dep: null },
          ],
        },
      ],
    };
    const root = render(vm);
    assert.match(root.textContent ?? "", /Route & Live Track/);
    assert.match(text(root, "selected-route"), /Tracking train 1M45/);
    assert.match(text(root, "route-stats"), /Visible trains:/);
    assert.ok(root.querySelector('[data-testid="route-strip"]'));
    assert.match(text(root, "selected-entity-card"), /EUSBHM-1200/);
    assert.doesNotMatch(text(root, "selected-entity-card"), /SELECTED TRAIN: 1M45/);
    assert.match(text(root, "selected-entity-card"), /Current Location:/);
    assert.match(text(root, "selected-entity-card"), /Train length:/);
    assert.ok(root.querySelector('[data-testid="journey-stops"]'));
    assert.ok(root.querySelector('[data-testid="journey-updates"]'));
    assert.ok(root.querySelector('[data-testid="route-other-trains"]'));
    assert.match(text(root, "route-other-trains"), /EUSBHM-1100/);
    assert.doesNotMatch(text(root, "route-other-trains"), /2A08|AM11/);
    assert.equal(root.querySelectorAll('[data-service="AM11"]').length, 0);
    assert.ok(root.querySelector('[data-testid="route-stations"]'));
    assert.ok(root.querySelector('[data-testid="open-network"]'));
  });

  it("shows between-station location with journey percent and schedule platforms", () => {
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.loadSucceeded = true;
    vm.page = "route";
    vm.diagramHeld = heldDiagram([["EUS", "WFJ", "MKC", "BHM"]]);
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
        {
          serviceGuid: "1M45",
          originCrs: "EUS",
          destCrs: "BHM",
          reachedCrs: "EUS",
          nextCrs: "MKC",
          scheduledTime: "1300",
          problem: false,
          statusLabel: "on time",
          stops: [
            { crs: "EUS", arr: null, dep: "1000", platform: "1" },
            { crs: "MKC", arr: "1200", dep: "1205", platform: "4" },
            { crs: "BHM", arr: "1400", dep: null },
          ],
        },
      ],
    };
    vm.serviceDetailHeld = {
      serviceGuid: "1M45",
      originCrs: "EUS",
      destCrs: "BHM",
      reachedCrs: "EUS",
      nextCrs: "MKC",
      scheduledTime: "1300",
      problem: false,
      statusLabel: "on time",
      delayMinutes: null,
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
          publicArr: "1200",
          publicDep: "1205",
          forecastArr: "1200",
          forecastDep: "1205",
          platform: "4",
          platformSuppressed: false,
          trainLength: null,
        },
        {
          crs: "BHM",
          publicArr: "1400",
          publicDep: null,
          forecastArr: "1400",
          forecastDep: null,
          platform: null,
          platformSuppressed: false,
          trainLength: null,
        },
      ],
      updates: [
        {
          id: 1,
          updateType: "TS",
          eventTags: ["platform_set"],
          appliedAt: "2026-11-21T12:00:00.000Z",
        },
      ],
      eventTags: ["platform_set"],
    };
    const root = render(vm, new Date("2026-11-06T11:00:00Z"));
    const card = text(root, "selected-entity-card");
    assert.match(card, /SELECTED TRAIN: EUSBHM-1000/);
    assert.match(card, /1205 Milton Keynes Central to Birmingham New Street/);
    assert.doesNotMatch(card, /London Euston to Birmingham/);
    assert.match(card, /→/);
    assert.match(card, /\(\d+%\)/);
    assert.match(card, /Train length: 8/);
    assert.match(text(root, "journey-stops"), /4/);
    assert.match(text(root, "journey-updates"), /platform/);
  });

  it("shows schedule Plat from stop platform instead of hardcoded no data", () => {
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.loadSucceeded = true;
    vm.page = "network";
    vm.diagramHeld = heldDiagram([["EUS", "MKC", "BHM"]]);
    vm.networkHeld = {
      calendarDate: "261106",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        {
          serviceGuid: "1M45",
          originCrs: "EUS",
          destCrs: "BHM",
          reachedCrs: "MKC",
          scheduledTime: "1400",
          problem: false,
          statusLabel: "on time",
          stops: [
            { crs: "EUS", arr: null, dep: "1200" },
            { crs: "MKC", arr: "1400", dep: "1405", platform: "3" },
            { crs: "BHM", arr: "1600", dep: null },
          ],
        },
      ],
    };
    const root = render(vm, new Date("2026-11-06T14:02:00Z"));
    const row = root.querySelector('[data-testid="schedule-row"]');
    assert.ok(row);
    assert.match(row?.textContent ?? "", /3/);
    assert.doesNotMatch(row?.textContent ?? "", /no data/);
  });

  it("shows Focus beside Show all when a station schedule focus is set", () => {
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.loadSucceeded = true;
    vm.page = "network";
    vm.diagramHeld = heldDiagram([["EUS", "MKC", "BHM"]]);
    vm.scheduleFocus = { kind: "station", crs: "MKC" };
    vm.networkHeld = {
      calendarDate: "261106",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [],
    };
    const root = render(vm);
    assert.ok(root.querySelector('[data-testid="focus-route"]'));
    assert.ok(root.querySelector('[data-testid="clear-schedule-focus"]'));
    assert.match(text(root, "schedule-focus"), /Trains through/);
  });

  it("renders a station-tracked route with selected station pane", () => {
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.loadSucceeded = true;
    vm.page = "route";
    vm.diagramHeld = heldDiagram([["EUS", "WFJ", "MKC", "NMP", "BHM"]]);
    vm.routeTrack = {
      kind: "station",
      crs: "MKC",
      lastWindowKey: "at:MKC",
    };
    vm.networkHeld = {
      calendarDate: "261106",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [],
    };
    const root = render(vm);
    assert.match(text(root, "selected-route"), /Tracking station/);
    assert.match(text(root, "selected-entity-card"), /SELECTED STATION/);
    assert.match(text(root, "selected-entity-card"), /MKC/);
  });
});

describe("station pair page", () => {
  it("opens in the same shell without a network list", () => {
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.page = "pair";
    vm.pairEarlier = "CRE";
    vm.pairLater = "BHM";
    vm.networkHeld = {
      calendarDate: "251102",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [{ serviceGuid: "kept", problem: false }],
    };
    vm.pairHeld = {
      calendarDate: "251102",
      earlierCrs: "CRE",
      laterCrs: "BHM",
      trains: [
        { serviceGuid: "run-1", status: "delayed", reachedCrs: "CRE", nextCrs: "BHM", issue: true },
        {
          serviceGuid: null,
          trainUid: "C9",
          status: "cancelled",
          cancellationLabel: "cancelled",
          issue: true,
        },
      ],
    };
    const root = render(vm);
    assert.match(root.textContent ?? "", /WEST COAST SOUTH CONTROL/);
    assert.match(text(root, "pair-list"), /delayed/);
    assert.match(text(root, "pair-list"), /cancelled/);
    assert.match(text(root, "pair-list"), /C9/);
    assert.match(text(root, "berth-schematic"), /This panel has no data/);
    assert.match(text(root, "tile-running"), /1/);
    assert.match(text(root, "tile-problems"), /1/);
    assert.match(text(root, "tile-cancelled"), /1/);
    assert.match(text(root, "tile-ppm"), /no data/);
    assert.equal(root.querySelector('[data-testid="network-list"]'), null);
    assert.ok(root.querySelector('[data-testid="open-network"]'));
  });
});
