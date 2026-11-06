import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { createInitialViewModel } from "../src/controller.js";
import { renderApp, schedulePick } from "../src/dom.js";
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
      trains: [
        {
          serviceGuid: "on-time-1",
          originCrs: "EUS",
          destCrs: "MKC",
          reachedCrs: "EUS",
          nextCrs: "MKC",
          scheduledTime: "1400",
          problem: false,
          stops: [
            { crs: "EUS", arr: null, dep: "1200" },
            { crs: "MKC", arr: "1400", dep: null },
          ],
        },
        {
          serviceGuid: "late-1",
          originCrs: "CRE",
          destCrs: "STA",
          reachedCrs: "STA",
          nextCrs: null,
          scheduledTime: "1200",
          problem: true,
          stops: [
            { crs: "CRE", arr: null, dep: "1100" },
            { crs: "STA", arr: "1250", dep: "1330" },
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
      "false",
    );
    assert.equal(
      root.querySelector('[data-service="late-1"]')?.getAttribute("data-at-station"),
      "true",
    );
    assert.match(text(root, "network-list"), /on time/);
    assert.match(text(root, "network-list"), /problem/);
    assert.match(text(root, "network-list"), /EUS/);
    assert.match(text(root, "network-list"), /1200/);
    assert.match(text(root, "network-list"), /1400/);
    assert.match(text(root, "network-list"), /no data/);
    assert.match(text(root, "disruptions"), /late-1/);
    assert.match(text(root, "disruptions"), /problem/);
    assert.doesNotMatch(text(root, "disruptions"), /on-time-1/);
    assert.match(text(root, "disruption-trend"), /This panel has no data/);
    assert.match(text(root, "incident-logs"), /late-1/);
    assert.match(text(root, "footer-active"), /2/);
    assert.match(text(root, "footer-on-time"), /1 \(50%\)/);
    assert.match(text(root, "footer-delayed"), /1/);
    assert.match(text(root, "footer-cancelled"), /no data/);
    assert.equal(root.querySelectorAll(".board > *").length, 3);
  });

  it("zooms the map and returns to the whole network", () => {
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.loadSucceeded = true;
    vm.diagramHeld = heldDiagram([["EUS", "MKC"], ["CRE", "STA"]]);
    vm.networkHeld = {
      calendarDate: "251102",
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
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.loadSucceeded = true;
    vm.diagramHeld = heldDiagram([["EUS", "MKC"], ["CRE", "STA"]]);
    vm.networkHeld = {
      calendarDate: "251102",
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
    assert.equal(tip.textContent, train.getAttribute("data-service"));
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
  });

  it("lists the live trains for a clicked station or arrow", () => {
    const vm = createInitialViewModel();
    vm.phase = "loaded";
    vm.loadSucceeded = true;
    vm.diagramHeld = heldDiagram([["EUS", "MKC"], ["CRE", "STA"]]);
    vm.networkHeld = {
      calendarDate: "261106",
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
    const station = stationView.querySelector(".station-mark");
    assert.ok(station);
    assert.equal(schedulePick(station)?.kind, "station");

    const arrow = stationView.querySelector(".train-marker");
    assert.ok(arrow);
    const pick = schedulePick(arrow);
    assert.equal(pick?.kind, "services");
    vm.scheduleFocus = pick;
    const arrowView = render(vm);
    const selected = arrowView.querySelector(".train-marker.is-selected");
    assert.equal(selected?.getAttribute("data-service"), arrow.getAttribute("data-service"));
    assert.match(text(arrowView, "schedule-focus"), /Trains on this arrow/);
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
      trains: [{ serviceGuid: "kept", problem: false }],
    };
    vm.pairHeld = {
      calendarDate: "251102",
      earlierCrs: "CRE",
      laterCrs: "BHM",
      trains: [
        { serviceGuid: "run-1", status: "published", reachedCrs: "CRE", nextCrs: "BHM", issue: true },
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
    assert.match(text(root, "pair-list"), /issue/);
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
