import type { NetworkFooter, PairCounts, ScheduleFocus, ScreenViewModel } from "./controller.js";
import { stationName } from "./stationNames.js";
import {
  calendarLabelFor,
  disruptionMessage,
  formatLondonClock,
  networkFooter,
  networkListMessage,
  isCancellationRow,
  pairCounts,
  pairListMessage,
  problemTrains,
  systemAlertsMessage,
  londonHhmm,
  visibleNetworkTrains,
  visiblePairTrains,
} from "./controller.js";
import {
  clampCamera,
  formatViewBox,
  frameCamera,
  panCamera,
  sameFrame,
  screenScale,
  stackOffset,
  zoomCamera,
  type Camera,
} from "./mapCamera.js";
import {
  markersOn,
  stationBadgeOffset,
  type MarkerLayout,
  type Schematic,
  type StationLayout,
} from "./networkMap.js";
import type { NetworkTrain, PairTrain } from "./types.js";

function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  attrs: Record<string, string> = {},
  children: (Node | string)[] = [],
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === "class") node.className = v;
    else if (k === "value" && "value" in node) (node as HTMLInputElement).value = v;
    else node.setAttribute(k, v);
  }
  for (const child of children) {
    node.append(typeof child === "string" ? document.createTextNode(child) : child);
  }
  return node;
}

function panel(title: string, testId: string, body: (Node | string)[]): HTMLElement {
  return el("section", { class: "panel", "data-testid": testId, "aria-label": title }, [
    el("h2", {}, [title]),
    el("div", { class: "panel-body" }, body),
  ]);
}

function noDataPanel(title: string, testId: string): HTMLElement {
  return panel(title, testId, [el("p", { class: "nodata" }, ["This panel has no data"])]);
}

function statusWord(problem: boolean): HTMLElement {
  return el("span", { class: problem ? "status problem" : "status ok" }, [
    problem ? "problem" : "on time",
  ]);
}

function textCell(text: string, className = "mono"): HTMLElement {
  return el("td", { class: className }, [text]);
}

function intervalField(vm: ScreenViewModel): HTMLElement {
  return el("label", { class: "field" }, [
    "Interval (seconds)",
    el("input", {
      "data-testid": "interval-input",
      type: "number",
      min: "1",
      step: "1",
      value: String(vm.intervalSeconds),
    }),
  ]);
}

function stationFields(vm: ScreenViewModel): HTMLElement {
  return el("div", { class: "stations" }, [
    el("label", { class: "field" }, [
      "Earlier station",
      el("input", {
        "data-testid": "earlier-crs",
        maxlength: "3",
        value: vm.pairEarlier,
        class: "mono",
      }),
    ]),
    el("button", { "data-testid": "swap-stations", type: "button" }, ["Swap"]),
    el("label", { class: "field" }, [
      "Later station",
      el("input", {
        "data-testid": "later-crs",
        maxlength: "3",
        value: vm.pairLater,
        class: "mono",
      }),
    ]),
  ]);
}

function header(vm: ScreenViewModel, title: string): HTMLElement {
  return el("header", { class: "topbar" }, [
    el("p", { class: "brand" }, [
      el("span", { class: "brand-london" }, ["london"]),
      " ",
      el("span", { class: "brand-midland" }, ["midland"]),
    ]),
    el("h1", {}, [title]),
    el("div", { class: "clock-block" }, [
      el("p", { class: "mono clock", "data-testid": "clock" }, [formatLondonClock()]),
      el("p", { "data-testid": "service-date" }, [calendarLabelFor(vm)]),
      el("p", { class: "zone" }, ["Europe/London"]),
    ]),
  ]);
}

function statusBar(vm: ScreenViewModel): HTMLElement {
  return el("p", { "data-testid": "status-bar", id: "status-bar", class: "status-bar" }, [
    vm.commsLost ? "comms is lost" : vm.statusLine,
  ]);
}

function loadProgressText(vm: ScreenViewModel): string {
  if (vm.loadProgress) {
    const howFar = vm.loadProgress.howFar ? ` (${vm.loadProgress.howFar}%)` : "";
    return `${vm.loadProgress.state}: ${vm.loadProgress.sentence}${howFar}`;
  }
  return vm.emptyMessage || "Press Start load";
}

function renderEmpty(vm: ScreenViewModel): HTMLElement {
  return el("section", { class: "empty-board" }, [
    intervalField(vm),
    el("button", { "data-testid": "load-button", type: "button" }, ["Start load"]),
    el("p", { "data-testid": "load-progress" }, [loadProgressText(vm)]),
  ]);
}

function scheduleTable(vm: ScreenViewModel, now: Date): HTMLElement {
  const table = el("table", { "data-testid": "network-list" }, [
    el("caption", { class: "sr-only" }, ["Live schedules"]),
    el("thead", {}, [
      el("tr", {}, [
        el("th", {}, ["Train"]),
        el("th", {}, ["Origin"]),
        el("th", {}, ["Destination"]),
        el("th", {}, ["Reached"]),
        el("th", {}, ["Next"]),
        el("th", {}, ["Scheduled"]),
        el("th", {}, ["Status"]),
        el("th", {}, ["Headcode"]),
        el("th", {}, ["Plat"]),
      ]),
    ]),
  ]);
  const body = el("tbody");
  for (const train of visibleNetworkTrains(vm, now)) {
    body.append(scheduleRow(train));
  }
  table.append(body);
  return table;
}

function scheduledCell(time: string | null | undefined): HTMLElement {
  if (!time) return textCell("no data", "nodata");
  return textCell(time);
}

function scheduleRow(train: NetworkTrain): HTMLElement {
  return el("tr", { "data-service": train.serviceGuid }, [
    textCell(train.serviceGuid),
    textCell(train.originCrs ?? ""),
    textCell(train.destCrs ?? ""),
    textCell(train.reachedCrs ?? ""),
    textCell(train.nextCrs ?? ""),
    scheduledCell(train.scheduledTime),
    el("td", {}, [statusWord(train.problem)]),
    textCell("no data", "nodata"),
    textCell("no data", "nodata"),
  ]);
}

function problemList(trains: NetworkTrain[]): HTMLElement {
  const list = el("ul", { class: "problem-list" });
  for (const train of trains) {
    list.append(
      el("li", {}, [
        el("span", { class: "mono" }, [train.serviceGuid]),
        " ",
        el("span", { class: "mono" }, [train.originCrs ?? ""]),
        " ",
        el("span", { class: "mono" }, [train.destCrs ?? ""]),
        " ",
        statusWord(true),
      ]),
    );
  }
  return list;
}

function scheduleFocusNote(vm: ScreenViewModel): HTMLElement | null {
  const focus = vm.scheduleFocus;
  if (!focus) return null;
  const label = focus.kind === "station"
    ? `Trains through ${stationName(focus.crs)}`
    : "Trains on this arrow";
  return el("p", { class: "schedule-focus", "data-testid": "schedule-focus" }, [
    label,
    " ",
    el("button", { type: "button", "data-testid": "clear-schedule-focus" }, ["Show all"]),
  ]);
}

export function schedulePick(target: Element): ScheduleFocus | null {
  const station = target.closest(".station-mark");
  if (station) {
    const crs = station.getAttribute("data-crs") ?? "";
    return crs ? { kind: "station", crs } : null;
  }
  const train = target.closest(".train-marker");
  if (!train) return null;
  const serviceGuids = (train.getAttribute("data-service") ?? "").split(",").filter((id) => id.length > 0);
  return serviceGuids.length > 0 ? { kind: "services", serviceGuids } : null;
}

function messageOr(message: string, filled: HTMLElement): (Node | string)[] {
  if (message) return [el("p", { class: "panel-message" }, [message])];
  return [filled];
}

function onTimeText(footer: NetworkFooter): string {
  if (!footer.ready) return "no data";
  if (footer.onTimePercent === null) return String(footer.onTime);
  return `${footer.onTime} (${footer.onTimePercent}%)`;
}

function footerTile(label: string, value: string, testId: string): HTMLElement {
  return el("div", { class: "tile", "data-testid": testId }, [
    el("span", { class: "tile-label" }, [label]),
    el("strong", {}, [value]),
  ]);
}

function networkFooterBar(footer: NetworkFooter): HTMLElement {
  const active = footer.ready ? String(footer.active) : "no data";
  const delayed = footer.ready ? String(footer.delayed) : "no data";
  return el("footer", { class: "board-footer", "data-testid": "network-footer" }, [
    footerTile("Active", active, "footer-active"),
    footerTile("On time", onTimeText(footer), "footer-on-time"),
    footerTile("Delayed", delayed, "footer-delayed"),
    footerTile("Cancelled", "no data", "footer-cancelled"),
  ]);
}

function svgEl<K extends keyof SVGElementTagNameMap>(
  tag: K,
  attrs: Record<string, string>,
): SVGElementTagNameMap[K] {
  const node = document.createElementNS("http://www.w3.org/2000/svg", tag);
  for (const [key, value] of Object.entries(attrs)) node.setAttribute(key, value);
  return node;
}

function legendItem(swatch: string, label: string): HTMLElement {
  return el("li", {}, [el("span", { class: swatch }, []), label]);
}

function mapLegend(): HTMLElement {
  return el("ul", { class: "map-legend" }, [
    legendItem("swatch spine", "Main line"),
    legendItem("swatch branch", "Branch"),
    legendItem("swatch on-time", "On time"),
    legendItem("swatch problem", "Problem"),
  ]);
}

let mapCamera: Camera | null = null;
let mapResize: ResizeObserver | null = null;
let mapKeys: AbortController | null = null;

function pathD(points: { x: number; y: number }[]): string {
  return points
    .map((point, index) => `${index === 0 ? "M" : "L"}${point.x.toFixed(1)} ${point.y.toFixed(1)}`)
    .join(" ");
}

function attrNumber(node: Element, name: string): number {
  return Number(node.getAttribute(name));
}

function readCamera(svg: SVGSVGElement, map: Schematic): Camera {
  const raw = svg.getAttribute("viewBox");
  if (!raw) return frameCamera(map.width, map.height, 0, 0);
  const [x, y, width, height] = raw.split(/[\s,]+/).map(Number);
  if ([x, y, width, height].every((value) => Number.isFinite(value)) && width! > 0 && height! > 0) {
    return { x: x!, y: y!, width: width!, height: height! };
  }
  return frameCamera(map.width, map.height, 0, 0);
}

function fittedCamera(svg: SVGSVGElement, map: Schematic): Camera {
  const rect = svg.getBoundingClientRect();
  return frameCamera(map.width, map.height, rect.width, rect.height);
}

function svgGroups(svg: SVGSVGElement, selector: string): SVGGElement[] {
  return Array.from(svg.querySelectorAll(selector)).filter((node) => node instanceof SVGGElement);
}

function paintFrame(svg: SVGSVGElement, map: Schematic, camera: Camera): void {
  svg.setAttribute("viewBox", formatViewBox(camera));
  const scale = screenScale(camera.width, svg.getBoundingClientRect().width);
  for (const mark of svgGroups(svg, ".station-mark")) {
    const x = attrNumber(mark, "data-x");
    const y = attrNumber(mark, "data-y");
    mark.setAttribute("transform", `translate(${x.toFixed(1)} ${y.toFixed(1)}) scale(${scale.toFixed(4)})`);
  }
  for (const train of svgGroups(svg, ".train-marker")) {
    const x = attrNumber(train, "data-x");
    const y = attrNumber(train, "data-y");
    const angle = attrNumber(train, "data-angle");
    const shift = stackOffset(
      attrNumber(train, "data-stack"),
      attrNumber(train, "data-stack-size"),
      train.getAttribute("data-at-station") === "true",
      angle,
    );
    const degrees = (angle * 180) / Math.PI;
    const tx = x + shift.x * scale;
    const ty = y + shift.y * scale;
    train.setAttribute(
      "transform",
      `translate(${tx.toFixed(1)} ${ty.toFixed(1)}) rotate(${degrees.toFixed(1)}) scale(${scale.toFixed(4)})`,
    );
  }
  cullStationBadges(svg);
}

function overlaps(left: DOMRect, right: DOMRect): boolean {
  const pad = 8;
  return (
    left.left - pad < right.right &&
    left.right + pad > right.left &&
    left.top - pad < right.bottom &&
    left.bottom + pad > right.top
  );
}

function badgeRank(mark: Element): number {
  const lane = attrNumber(mark, "data-lane");
  const rank = attrNumber(mark, "data-rank");
  return (lane === 0 ? 0 : 1000) + rank;
}

function compareBadges(left: Element, right: Element): number {
  const rank = badgeRank(left) - badgeRank(right);
  if (rank !== 0) return rank;
  return (left.getAttribute("data-crs") ?? "").localeCompare(right.getAttribute("data-crs") ?? "");
}

function cullStationBadges(svg: SVGSVGElement): void {
  const marks = svgGroups(svg, ".station-mark");
  for (const mark of marks) mark.querySelector(".station-badge")?.classList.remove("is-crowded");
  marks.sort(compareBadges);
  const placed: DOMRect[] = [];
  for (const mark of marks) {
    const badge = mark.querySelector(".station-badge");
    if (!badge) continue;
    const box = badge.getBoundingClientRect();
    if (box.width === 0 || box.height === 0) return;
    if (placed.some((other) => overlaps(other, box))) badge.classList.add("is-crowded");
    else placed.push(box);
  }
}

function zoomLimits(svg: SVGSVGElement, map: Schematic): { fit: Camera; min: number; max: number } {
  const fit = fittedCamera(svg, map);
  return { fit, min: fit.width / 16, max: fit.width };
}

function wheelFactor(deltaY: number, deltaMode: number): number {
  const pixels = deltaMode === 1 ? deltaY * 16 : deltaMode === 2 ? deltaY * 400 : deltaY;
  const capped = Math.max(-180, Math.min(180, pixels));
  return Math.exp(capped * 0.0018);
}

function commitCamera(svg: SVGSVGElement, map: Schematic, camera: Camera): void {
  const fit = zoomLimits(svg, map).fit;
  mapCamera = sameFrame(camera, fit) ? null : camera;
  paintFrame(svg, map, camera);
}

function zoomAt(svg: SVGSVGElement, map: Schematic, factor: number, anchorX: number, anchorY: number): void {
  const current = readCamera(svg, map);
  const limits = zoomLimits(svg, map);
  const next = clampCamera(
    zoomCamera(current, factor, anchorX, anchorY, limits.min, limits.max),
    map.width,
    map.height,
  );
  commitCamera(svg, map, next);
}

function zoomAboutCenter(svg: SVGSVGElement, map: Schematic, factor: number): void {
  const current = readCamera(svg, map);
  zoomAt(svg, map, factor, current.x + current.width / 2, current.y + current.height / 2);
}

function showWholeNetwork(svg: SVGSVGElement, map: Schematic): void {
  mapCamera = null;
  paintFrame(svg, map, fittedCamera(svg, map));
}

function panByPixels(svg: SVGSVGElement, map: Schematic, dx: number, dy: number): void {
  const rect = svg.getBoundingClientRect();
  if (rect.width < 2 || rect.height < 2) return;
  const current = readCamera(svg, map);
  const next = clampCamera(
    panCamera(current, dx * (current.width / rect.width), dy * (current.height / rect.height)),
    map.width,
    map.height,
  );
  mapCamera = next;
  paintFrame(svg, map, next);
}

type MapDrag = { active: boolean; id: number; x: number; y: number };

function tipText(target: Element): string | null {
  const station = target.closest(".station-mark");
  if (station) return stationName(station.getAttribute("data-crs") ?? "");
  const train = target.closest(".train-marker");
  if (!train) return null;
  const ids = (train.getAttribute("data-service") ?? "").split(",").filter((id) => id.length > 0);
  if (ids.length === 0) return null;
  return ids.join("\n");
}

function placeTip(tip: HTMLElement, ev: PointerEvent): void {
  const frame = tip.parentElement;
  if (!frame) return;
  const bounds = frame.getBoundingClientRect();
  let x = ev.clientX - bounds.left + 12;
  let y = ev.clientY - bounds.top + 14;
  tip.style.left = `${x}px`;
  tip.style.top = `${y}px`;
  const tipBox = tip.getBoundingClientRect();
  if (tipBox.right > bounds.right - 4) x -= tipBox.width + 24;
  if (tipBox.bottom > bounds.bottom - 4) y -= tipBox.height + 28;
  tip.style.left = `${Math.max(4, x)}px`;
  tip.style.top = `${Math.max(4, y)}px`;
}

function syncTip(tip: HTMLElement | null, ev: PointerEvent, panning: boolean): void {
  if (!tip) return;
  const target = ev.target instanceof Element ? ev.target : null;
  const text = !ev.shiftKey && !panning && target ? tipText(target) : null;
  if (!text) {
    tip.hidden = true;
    return;
  }
  tip.textContent = text;
  tip.hidden = false;
  placeTip(tip, ev);
}

function bindMapGestures(svg: SVGSVGElement, map: Schematic): void {
  mapKeys?.abort();
  mapKeys = new AbortController();
  const signal = mapKeys.signal;
  const frame = svg.parentElement;
  const tip = frame?.querySelector<HTMLElement>('[data-testid="map-tooltip"]') ?? null;
  const drag: MapDrag = { active: false, id: -1, x: 0, y: 0 };

  function stopPan(): void {
    drag.active = false;
    svg.classList.remove("is-panning");
  }

  svg.addEventListener(
    "wheel",
    (ev) => {
      ev.preventDefault();
      const rect = svg.getBoundingClientRect();
      const current = readCamera(svg, map);
      const mx = rect.width ? (ev.clientX - rect.left) / rect.width : 0.5;
      const my = rect.height ? (ev.clientY - rect.top) / rect.height : 0.5;
      zoomAt(svg, map, wheelFactor(ev.deltaY, ev.deltaMode), current.x + mx * current.width, current.y + my * current.height);
    },
    { passive: false },
  );

  window.addEventListener("keydown", (ev) => {
    if (ev.key !== "Shift" || ev.repeat) return;
    svg.classList.add("is-shift");
    if (tip) tip.hidden = true;
  }, { signal });
  window.addEventListener("keyup", (ev) => {
    if (ev.key !== "Shift") return;
    svg.classList.remove("is-shift");
    stopPan();
  }, { signal });

  svg.addEventListener("pointerdown", (ev) => {
    if (ev.button !== 0 || !ev.shiftKey) return;
    drag.active = true;
    drag.id = ev.pointerId;
    drag.x = ev.clientX;
    drag.y = ev.clientY;
    svg.classList.add("is-shift");
    svg.classList.add("is-panning");
    if (tip) tip.hidden = true;
    if (typeof svg.setPointerCapture === "function") svg.setPointerCapture(ev.pointerId);
  });
  svg.addEventListener("pointermove", (ev) => {
    if (ev.shiftKey) svg.classList.add("is-shift");
    else if (!drag.active) svg.classList.remove("is-shift");
    if (drag.active && drag.id === ev.pointerId && ev.shiftKey) {
      panByPixels(svg, map, ev.clientX - drag.x, ev.clientY - drag.y);
      drag.x = ev.clientX;
      drag.y = ev.clientY;
    } else if (drag.active && drag.id === ev.pointerId) {
      stopPan();
    }
    syncTip(tip, ev, drag.active);
  });
  const endDrag = (ev: PointerEvent) => {
    if (!drag.active || drag.id !== ev.pointerId) return;
    stopPan();
  };
  svg.addEventListener("pointerup", endDrag);
  svg.addEventListener("pointercancel", endDrag);
  svg.addEventListener("pointerover", (ev) => {
    syncTip(tip, ev, drag.active);
  });
  svg.addEventListener("pointerout", (ev) => {
    if (ev.relatedTarget instanceof Node && svg.contains(ev.relatedTarget)) return;
    if (tip) tip.hidden = true;
  });

  svg.addEventListener("keydown", (ev) => {
    if (ev.key === "+" || ev.key === "=") {
      ev.preventDefault();
      zoomAboutCenter(svg, map, 1 / 1.35);
    } else if (ev.key === "-" || ev.key === "_") {
      ev.preventDefault();
      zoomAboutCenter(svg, map, 1.35);
    } else if (ev.key === "0" || ev.key === "Home") {
      ev.preventDefault();
      showWholeNetwork(svg, map);
    }
  });

  frame?.querySelector('[data-testid="map-zoom-in"]')?.addEventListener("click", () => {
    zoomAboutCenter(svg, map, 1 / 1.35);
  });
  frame?.querySelector('[data-testid="map-zoom-out"]')?.addEventListener("click", () => {
    zoomAboutCenter(svg, map, 1.35);
  });
  frame?.querySelector('[data-testid="map-reset"]')?.addEventListener("click", () => {
    showWholeNetwork(svg, map);
  });
}

function mountMap(svg: SVGSVGElement, map: Schematic): void {
  const camera = mapCamera ?? fittedCamera(svg, map);
  paintFrame(svg, map, camera);
  mapResize?.disconnect();
  if (typeof ResizeObserver !== "undefined") {
    mapResize = new ResizeObserver(() => {
      if (!svg.isConnected) return;
      paintFrame(svg, map, mapCamera ?? fittedCamera(svg, map));
    });
    mapResize.observe(svg);
  }
  requestAnimationFrame(() => {
    if (!svg.isConnected) return;
    paintFrame(svg, map, mapCamera ?? fittedCamera(svg, map));
  });
  bindMapGestures(svg, map);
}

function stationMark(station: StationLayout, selected: boolean): SVGGElement {
  const group = svgEl("g", {
    class: selected ? "station-mark is-selected" : "station-mark",
    "data-crs": station.crs,
    "data-x": String(station.x),
    "data-y": String(station.y),
    "data-lane": String(station.lane),
    "data-rank": String(station.rank),
  });
  const title = svgEl("title", {});
  title.textContent = stationName(station.crs);
  group.append(title);
  group.append(svgEl("circle", { class: "station-dot", r: "5" }));
  const offset = stationBadgeOffset(station.lane);
  const badge = svgEl("g", {
    class: "station-badge",
    transform: `translate(${offset.x.toFixed(1)} ${offset.y.toFixed(1)})`,
  });
  badge.append(svgEl("rect", {
    class: "station-plate",
    x: "-18",
    y: "-9",
    width: "36",
    height: "18",
    rx: "4",
  }));
  const label = svgEl("text", {
    class: "station-label",
    x: "0",
    y: "4",
    "text-anchor": "middle",
  });
  label.textContent = station.crs;
  badge.append(label);
  group.append(badge);
  return group;
}

function trainMarker(marker: MarkerLayout, selected: boolean): SVGGElement {
  const state = marker.problem ? "problem" : "on-time";
  const group = svgEl("g", {
    class: selected ? `train-marker ${state} is-selected` : `train-marker ${state}`,
    "data-testid": "train-marker",
    "data-service": marker.serviceGuid,
    "data-at-station": marker.atStation ? "true" : "false",
    "data-x": String(marker.x),
    "data-y": String(marker.y),
    "data-angle": String(marker.angle),
    "data-stack": String(marker.stack),
    "data-stack-size": String(marker.stackSize),
  });
  group.append(svgEl("polygon", { points: "-8,-5 12,0 -8,5" }));
  const title = svgEl("title", {});
  title.textContent = marker.serviceGuid;
  group.append(title);
  return group;
}

function markerSelected(marker: MarkerLayout, focus: ScheduleFocus | null): boolean {
  if (!focus || focus.kind !== "services") return false;
  const ids = marker.serviceGuid.split(",").filter((id) => id.length > 0);
  return ids.length === focus.serviceGuids.length && ids.every((id, index) => id === focus.serviceGuids[index]);
}

function schematicSvg(map: Schematic, focus: ScheduleFocus | null): SVGSVGElement {
  const svg = svgEl("svg", {
    class: "network-map",
    viewBox: formatViewBox(frameCamera(map.width, map.height, 0, 0)),
    role: "group",
    tabindex: "0",
    "aria-label": "Network overview",
    "aria-describedby": "map-hint",
    "data-testid": "network-map",
  });
  for (const corridor of map.corridors) {
    svg.append(svgEl("path", {
      class: "corridor",
      d: pathD(corridor.points),
      fill: "none",
      stroke: corridor.color,
    }));
  }
  for (const marker of map.markers) svg.append(trainMarker(marker, markerSelected(marker, focus)));
  for (const station of map.stations) {
    svg.append(stationMark(station, focus?.kind === "station" && focus.crs === station.crs));
  }
  return svg;
}

function mapTools(): HTMLElement {
  return el("div", { class: "map-tools" }, [
    el("button", { type: "button", "data-testid": "map-zoom-in", "aria-label": "Zoom in" }, ["+"]),
    el("button", { type: "button", "data-testid": "map-zoom-out", "aria-label": "Zoom out" }, ["−"]),
    el("button", { type: "button", "data-testid": "map-reset" }, ["Whole view"]),
  ]);
}

function networkOverview(vm: ScreenViewModel, now: Date): HTMLElement {
  if (!vm.networkHeld || !vm.diagramHeld) {
    const message = vm.emptyMessage || "Loading";
    return panel("Network overview", "network-overview", [el("p", { class: "nodata" }, [message])]);
  }
  const map = markersOn(vm.diagramHeld, vm.networkHeld.trains, londonHhmm(now));
  const svg = schematicSvg(map, vm.scheduleFocus);
  const frame = el("div", { class: "map-frame" }, [
    mapTools(),
    el("p", { class: "map-hint", id: "map-hint" }, [
      "Scroll to zoom. Hold Shift and drag to move. Click a station or train to list it.",
    ]),
    el("div", { class: "map-tooltip", "data-testid": "map-tooltip", hidden: "" }),
    svg,
  ]);
  mountMap(svg, map);
  const section = panel("Network overview", "network-overview", [mapLegend(), frame]);
  section.classList.add("network-panel");
  return section;
}

function renderNetwork(vm: ScreenViewModel, now: Date): HTMLElement {
  const message = networkListMessage(vm, now);
  const note = scheduleFocusNote(vm);
  const problems = problemTrains(vm);
  const disruption = disruptionMessage(vm);
  const schedules = panel("Live schedules", "live-schedules", [
    ...(note ? [note] : []),
    ...(message ? [el("p", { "data-testid": "network-message", class: "panel-message" }, [message])] : []),
    scheduleTable(vm, now),
  ]);
  return el("div", { class: "page" }, [
    el("div", { class: "toolbar" }, [
      stationFields(vm),
      el("label", { class: "field grow" }, [
        "Search",
        el("input", {
          "data-testid": "search-input",
          type: "search",
          value: vm.searchText,
        }),
      ]),
      el("p", { class: "route-filter", "data-testid": "route-filter" }, [
        "Filter by route ",
        el("span", { class: "nodata" }, ["no data"]),
      ]),
      intervalField(vm),
      el("button", { "data-testid": "open-station-pair", type: "button" }, ["Open station pair"]),
    ]),
    el("div", { class: "board" }, [
      networkOverview(vm, now),
      schedules,
      el("div", { class: "col-side" }, [
        panel(
          "Disruptions",
          "disruptions",
          messageOr(disruption, problemList(problems)),
        ),
        noDataPanel("Disruption trend", "disruption-trend"),
        panel("Incident logs", "incident-logs", messageOr(disruption, problemList(problems))),
        panel("System alerts", "system-alerts", [
          el("p", { class: "nodata" }, [systemAlertsMessage(vm)]),
        ]),
      ]),
    ]),
    networkFooterBar(networkFooter(vm)),
  ]);
}

function pairTile(label: string, value: string, testId: string): HTMLElement {
  return footerTile(label, value, testId);
}

function pairCountValue(counts: PairCounts, key: "running" | "problems" | "cancelled"): string {
  return counts.ready ? String(counts[key]) : "no data";
}

function pairRow(train: PairTrain, earlier: string, later: string): HTMLElement {
  const cancelled = isCancellationRow(train);
  const id = cancelled ? (train.trainUid ?? "") : (train.serviceGuid ?? "");
  const flag = cancelled
    ? el("span", { class: "status problem" }, ["cancelled"])
    : el("span", { class: train.issue ? "status problem" : "status ok" }, [
        train.issue ? "issue" : "on time",
      ]);
  return el("tr", {}, [
    textCell(id),
    textCell(earlier),
    textCell(later),
    textCell(cancelled ? "" : (train.reachedCrs ?? "")),
    textCell(cancelled ? "" : (train.nextCrs ?? "")),
    el("td", {}, [flag]),
  ]);
}

function pairTable(vm: ScreenViewModel): HTMLElement {
  const earlier = vm.pairHeld?.earlierCrs ?? vm.pairEarlier;
  const later = vm.pairHeld?.laterCrs ?? vm.pairLater;
  const table = el("table", { "data-testid": "pair-list" }, [
    el("caption", { class: "sr-only" }, ["Station pair"]),
    el("thead", {}, [
      el("tr", {}, [
        el("th", {}, ["Train"]),
        el("th", {}, ["Earlier"]),
        el("th", {}, ["Later"]),
        el("th", {}, ["Reached"]),
        el("th", {}, ["Next"]),
        el("th", {}, ["Status"]),
      ]),
    ]),
  ]);
  const body = el("tbody");
  for (const train of visiblePairTrains(vm)) {
    body.append(pairRow(train, earlier, later));
  }
  table.append(body);
  return table;
}

const PAIR_FILTERS = ["all", "on time", "delayed", "cancelled"] as const;

function pairFilters(vm: ScreenViewModel): HTMLElement {
  const group = el("div", { class: "filters", role: "group", "aria-label": "Filter" });
  for (const filter of PAIR_FILTERS) {
    const pressed = vm.pairFilter === filter;
    group.append(
      el(
        "button",
        {
          type: "button",
          class: pressed ? "filter-button is-selected" : "filter-button",
          "data-testid": `filter-${filter.replace(" ", "-")}`,
          "data-pair-filter": filter,
          "aria-pressed": pressed ? "true" : "false",
        },
        [filter],
      ),
    );
  }
  return group;
}

function renderPair(vm: ScreenViewModel): HTMLElement {
  const counts = pairCounts(vm);
  const message = pairListMessage(vm);
  return el("div", { class: "page" }, [
    el("div", { class: "pair-meta" }, [
      el("p", { "data-testid": "feed-status" }, ["Feed status ", el("span", { class: "nodata" }, ["no data"])]),
      el("p", { "data-testid": "berth-status" }, ["Berth status ", el("span", { class: "nodata" }, ["no data"])]),
      el("p", {}, [
        el("button", { "data-testid": "open-network", type: "button", class: "crumb" }, [
          "Network Overview",
        ]),
        el("span", { class: "crumb-current" }, ["Station pair"]),
      ]),
      el("p", { "data-testid": "line-code" }, ["Line code ", el("span", { class: "nodata" }, ["no data"])]),
      el("p", { "data-testid": "tactical-strategy" }, [
        "Tactical strategy ",
        el("span", { class: "nodata" }, ["no data"]),
      ]),
    ]),
    el("div", { class: "toolbar" }, [
      stationFields(vm),
      el("p", { "data-testid": "shortcut-chips" }, [
        "Shortcut chips ",
        el("span", { class: "nodata" }, ["no data"]),
      ]),
      intervalField(vm),
    ]),
    el("div", { class: "kpi-row" }, [
      pairTile("Running", pairCountValue(counts, "running"), "tile-running"),
      pairTile("Problems", pairCountValue(counts, "problems"), "tile-problems"),
      pairTile("Cancelled", pairCountValue(counts, "cancelled"), "tile-cancelled"),
      pairTile("PPM", "no data", "tile-ppm"),
      pairTile("Average delay", "no data", "tile-average-delay"),
      pairTile("Route miles", "no data", "tile-route-miles"),
      pairTile("Journey time", "no data", "tile-journey-time"),
    ]),
    noDataPanel("Berth schematic", "berth-schematic"),
    el("div", { class: "toolbar" }, [
      el("p", { "data-testid": "headcode-filter" }, [
        "Filter by headcode ",
        el("span", { class: "nodata" }, ["no data"]),
      ]),
      el("p", { "data-testid": "fleet" }, ["Fleet ", el("span", { class: "nodata" }, ["no data"])]),
      pairFilters(vm),
    ]),
    panel("Station pair", "pair-schedules", [
      ...(message ? [el("p", { "data-testid": "pair-message", class: "panel-message" }, [message])] : []),
      pairTable(vm),
    ]),
  ]);
}

export function renderApp(root: HTMLElement, vm: ScreenViewModel, now = new Date()): void {
  root.replaceChildren();
  const title = vm.page === "pair" && vm.phase === "loaded"
    ? "WEST COAST SOUTH CONTROL"
    : "JOURNEY CONTROL CENTRE";
  const shell = el("div", { class: "shell" }, [header(vm, title), statusBar(vm)]);
  if (vm.phase === "empty") shell.append(renderEmpty(vm));
  else if (vm.page === "pair") shell.append(renderPair(vm));
  else shell.append(renderNetwork(vm, now));
  root.append(shell);
}
