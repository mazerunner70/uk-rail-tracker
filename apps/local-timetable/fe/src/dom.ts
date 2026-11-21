import type {
  DisruptionSummaryRow,
  NetworkFooter,
  PairCounts,
  ScheduleFocus,
  ScreenViewModel,
} from "./controller.js";
import type { EventTag } from "./types.js";
import { stationName } from "./stationNames.js";
import {
  calendarLabelFor,
  disruptionMessage,
  disruptionSummary,
  eventTagLabel,
  findHeldTrain,
  formatLondonClock,
  networkFooter,
  networkListMessage,
  isCancellationRow,
  pairCounts,
  pairListMessage,
  previewTrain,
  problemTrains,
  resolveRouteWindow,
  routeTrackTitle,
  selectedRouteTrain,
  systemAlertsMessage,
  londonHhmm,
  routeVisibleTrains,
  visibleNetworkTrains,
  visiblePairTrains,
  withLiveFields,
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
import {
  corridorHopsOnSpine,
  corridorTouchesSpine,
  spinePath,
  stationAggregates,
  stripLayout,
  trainsOnSpine,
  type SegmentSeverity,
  type StationAggregate,
  type StripLayout,
} from "./routeStrip.js";
import { placeTrain } from "./networkMap.js";
import {
  serviceShortLabelsForGuids,
  trainLabelMap,
} from "./serviceLabel.js";
import type { NetworkDiagram, NetworkTrain, PairTrain, StatusLabel } from "./types.js";

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

/** Narrow / touch-first viewports: map is the primary surface. */
export function isMobileViewport(media: Pick<Window, "matchMedia"> | null = typeof window !== "undefined" ? window : null): boolean {
  if (!media || typeof media.matchMedia !== "function") return false;
  return media.matchMedia("(max-width: 720px)").matches
    || media.matchMedia("(pointer: coarse) and (max-width: 900px)").matches;
}

function touchPanEnabled(media: Pick<Window, "matchMedia"> | null = typeof window !== "undefined" ? window : null): boolean {
  if (!media || typeof media.matchMedia !== "function") return false;
  return media.matchMedia("(pointer: coarse)").matches
    || media.matchMedia("(max-width: 720px)").matches;
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

function statusWord(opts: {
  label: string;
  changeFromScheduled: boolean;
}): HTMLElement {
  return el("span", {
    class: opts.changeFromScheduled ? "status problem" : "status ok",
  }, [opts.label]);
}

function networkStatusWord(train: NetworkTrain): HTMLElement {
  return statusWord({
    label: train.statusLabel ?? (train.problem ? "delayed" : "on time"),
    changeFromScheduled: train.problem,
  });
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
        el("th", {}, ["From"]),
        el("th", {}, ["To"]),
        el("th", {}, ["At"]),
        el("th", {}, ["Next"]),
        el("th", {}, ["Time"]),
        el("th", {}, ["Status"]),
        el("th", {}, ["Plat"]),
      ]),
    ]),
  ]);
  const body = el("tbody");
  const previewGuid = vm.routePreview?.serviceGuid ?? null;
  for (const train of visibleNetworkTrains(vm, now)) {
    body.append(scheduleRow(train, train.serviceGuid === previewGuid));
  }
  table.append(body);
  return table;
}

function delayStatusText(train: NetworkTrain): string {
  const where = train.reachedCrs ? stationName(train.reachedCrs) : "route";
  if (train.delayMinutes != null && train.delayMinutes > 0) {
    return `Delayed +${train.delayMinutes}m at ${where}`;
  }
  const label = train.statusLabel ?? (train.problem ? "delayed" : "on time");
  if (label === "on time") return `On time at ${where}`;
  return `${label} at ${where}`;
}

function journeyCard(train: NetworkTrain, progress: number | null, testId: string): HTMLElement {
  const origin = stationName(train.originCrs ?? "");
  const dest = stationName(train.destCrs ?? "");
  const pct = progress === null ? 0 : Math.round(Math.min(1, Math.max(0, progress)) * 100);
  return el("aside", { class: "journey-card", "data-testid": testId }, [
    el("p", { class: "journey-card-title" }, [
      "SELECTED TRAIN: ",
      el("span", { class: "mono" }, [train.serviceGuid]),
    ]),
    el("p", { class: "journey-card-sub" }, [
      `(${train.scheduledTime ?? "----"} ${origin} to ${dest})`,
    ]),
    el("div", { class: "journey-progress", "aria-hidden": "true" }, [
      el("div", {
        class: "journey-progress-bar",
        style: `width:${pct}%`,
      }),
    ]),
    el("p", { class: "journey-meta" }, [
      el("span", { class: "dot location" }, []),
      " Current Location: ",
      stationName(train.reachedCrs ?? train.originCrs ?? ""),
    ]),
    el("p", { class: "journey-meta" }, [
      el("span", {
        class: train.problem ? "dot severe" : "dot ok",
      }, []),
      " ",
      delayStatusText(train),
    ]),
  ]);
}

function scheduledCell(time: string | null | undefined): HTMLElement {
  if (!time) return textCell("no data", "nodata");
  return textCell(time);
}

function scheduleRow(train: NetworkTrain, previewed: boolean): HTMLElement {
  const attrs: Record<string, string> = {
    "data-service": train.serviceGuid,
    "data-testid": "schedule-row",
    class: previewed ? "schedule-row is-preview" : "schedule-row",
    tabindex: "0",
  };
  return el("tr", attrs, [
    textCell(train.serviceGuid),
    textCell(train.originCrs ?? ""),
    textCell(train.destCrs ?? ""),
    textCell(train.reachedCrs ?? ""),
    textCell(train.nextCrs ?? ""),
    scheduledCell(train.scheduledTime),
    el("td", {}, [networkStatusWord(train)]),
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
        networkStatusWord(train),
      ]),
    );
  }
  return list;
}

function disruptionSummaryList(
  rows: DisruptionSummaryRow[],
  selected: EventTag | null,
): HTMLElement {
  const list = el("ul", { class: "problem-list summary-list", "data-testid": "disruption-summary" });
  for (const row of rows) {
    const pressed = selected === row.tag;
    list.append(
      el(
        "li",
        {
          class: pressed ? "summary-row is-selected" : "summary-row",
          "data-event-filter": row.tag,
          "data-testid": `event-filter-${row.tag}`,
          tabindex: "0",
          role: "button",
          "aria-pressed": pressed ? "true" : "false",
        },
        [
          el("span", { class: "summary-label" }, [eventTagLabel(row.tag)]),
          el("span", { class: "summary-count mono" }, [String(row.count)]),
        ],
      ),
    );
  }
  return list;
}

function scheduleFocusNote(vm: ScreenViewModel): HTMLElement | null {
  const focus = vm.scheduleFocus;
  const eventFilter = vm.networkEventFilter;
  if (!focus && !eventFilter) return null;
  const parts: (Node | string)[] = [];
  if (focus) {
    parts.push(
      focus.kind === "station"
        ? `Trains through ${stationName(focus.crs)}`
        : "Trains on this arrow",
    );
  }
  if (eventFilter) {
    if (parts.length > 0) parts.push(" · ");
    parts.push(`Event: ${eventTagLabel(eventFilter)}`);
  }
  parts.push(" ");
  if (focus?.kind === "station") {
    parts.push(
      el("button", { type: "button", "data-testid": "focus-route" }, ["Focus"]),
    );
    parts.push(" ");
  }
  parts.push(
    el("button", { type: "button", "data-testid": "clear-schedule-focus" }, ["Show all"]),
  );
  return el("p", { class: "schedule-focus", "data-testid": "schedule-focus" }, parts);
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
  const updates =
    footer.ready && footer.updatesPerMin !== null
      ? String(footer.updatesPerMin)
      : "no data";
  return el("footer", { class: "board-footer", "data-testid": "network-footer" }, [
    footerTile("Active", active, "footer-active"),
    footerTile("On time", onTimeText(footer), "footer-on-time"),
    footerTile("Delayed", delayed, "footer-delayed"),
    footerTile("Cancelled", "no data", "footer-cancelled"),
    footerTile("Updates / min", updates, "footer-updates-per-min"),
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
  const train = target.closest(".train-marker") ?? target.closest(".route-train");
  if (!train) return null;
  const tip = train.getAttribute("data-tip");
  if (tip) return tip;
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
    if (ev.button !== 0) return;
    const touchPan = touchPanEnabled();
    if (!touchPan && !ev.shiftKey) return;
    if (touchPan && ev.target instanceof Element) {
      if (ev.target.closest(".station-mark, .train-marker, button")) return;
    }
    drag.active = true;
    drag.id = ev.pointerId;
    drag.x = ev.clientX;
    drag.y = ev.clientY;
    if (ev.shiftKey || touchPan) svg.classList.add("is-shift");
    svg.classList.add("is-panning");
    if (tip) tip.hidden = true;
    if (typeof svg.setPointerCapture === "function") svg.setPointerCapture(ev.pointerId);
  });
  svg.addEventListener("pointermove", (ev) => {
    const touchPan = touchPanEnabled();
    if (ev.shiftKey || (touchPan && drag.active)) svg.classList.add("is-shift");
    else if (!drag.active) svg.classList.remove("is-shift");
    const canPan = drag.active && drag.id === ev.pointerId && (ev.shiftKey || touchPan);
    if (canPan) {
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

function trainMarker(
  marker: MarkerLayout,
  selected: boolean,
  byGuid: ReadonlyMap<string, NetworkTrain>,
): SVGGElement {
  const state = marker.problem ? "problem" : "on-time";
  const ids = marker.serviceGuid.split(",").filter((id) => id.length > 0);
  const tip = serviceShortLabelsForGuids(ids, byGuid);
  const group = svgEl("g", {
    class: selected ? `train-marker ${state} is-selected` : `train-marker ${state}`,
    "data-testid": "train-marker",
    "data-service": marker.serviceGuid,
    "data-tip": tip,
    "data-at-station": marker.atStation ? "true" : "false",
    "data-x": String(marker.x),
    "data-y": String(marker.y),
    "data-angle": String(marker.angle),
    "data-stack": String(marker.stack),
    "data-stack-size": String(marker.stackSize),
  });
  group.append(svgEl("polygon", { points: "-8,-5 12,0 -8,5" }));
  const title = svgEl("title", {});
  title.textContent = tip;
  group.append(title);
  return group;
}

function markerSelected(marker: MarkerLayout, focus: ScheduleFocus | null): boolean {
  if (!focus || focus.kind !== "services") return false;
  const ids = marker.serviceGuid.split(",").filter((id) => id.length > 0);
  return ids.length === focus.serviceGuids.length && ids.every((id, index) => id === focus.serviceGuids[index]);
}

function previewSpine(vm: ScreenViewModel): string[] | null {
  const guid = vm.routePreview?.serviceGuid;
  if (!guid || !vm.diagramHeld) return null;
  const train = findHeldTrain(vm, guid);
  if (!train) return null;
  const origin = train.originCrs
    ?? train.stops?.find((stop) => stop.crs)?.crs
    ?? null;
  const dest = train.destCrs
    ?? [...(train.stops ?? [])].reverse().find((stop) => stop.crs)?.crs
    ?? null;
  if (!origin || !dest) return null;
  return spinePath(vm.diagramHeld, origin, dest);
}

function schematicSvg(
  map: Schematic,
  focus: ScheduleFocus | null,
  previewGuid: string | null,
  spine: readonly string[] | null,
  diagram: NetworkDiagram | null,
  byGuid: ReadonlyMap<string, NetworkTrain>,
): SVGSVGElement {
  const svg = svgEl("svg", {
    class: spine ? "network-map has-route-preview" : "network-map",
    viewBox: formatViewBox(frameCamera(map.width, map.height, 0, 0)),
    role: "group",
    tabindex: "0",
    "aria-label": "Network overview",
    "aria-describedby": "map-hint map-hint-mobile",
    "data-testid": "network-map",
  });
  const spineSet = spine ? new Set(spine) : null;
  const spineHops = spine && diagram ? corridorHopsOnSpine(diagram, spine) : null;
  for (const corridor of map.corridors) {
    const onSpine = spineHops
      ? corridorTouchesSpine(corridor.from, corridor.to, corridor.via, spineHops)
      : false;
    const classes = onSpine ? "corridor is-spine" : spine ? "corridor is-dim" : "corridor";
    svg.append(svgEl("path", {
      class: classes,
      d: pathD(corridor.points),
      fill: "none",
      stroke: corridor.color,
    }));
  }
  for (const marker of map.markers) {
    const ids = marker.serviceGuid.split(",").filter((id) => id.length > 0);
    const previewed = previewGuid !== null && ids.includes(previewGuid);
    svg.append(trainMarker(marker, markerSelected(marker, focus) || previewed, byGuid));
  }
  for (const station of map.stations) {
    const selected = (focus?.kind === "station" && focus.crs === station.crs)
      || (spineSet?.has(station.crs) ?? false);
    svg.append(stationMark(station, selected));
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
  const nowHhmm = londonHhmm(now);
  const live = withLiveFields(vm.networkHeld.trains, nowHhmm);
  const map = markersOn(vm.diagramHeld, live, nowHhmm);
  const spine = previewSpine(vm);
  const byGuid = trainLabelMap(live);
  const svg = schematicSvg(
    map,
    vm.scheduleFocus,
    vm.routePreview?.serviceGuid ?? null,
    spine,
    vm.diagramHeld,
    byGuid,
  );
  const preview = previewTrain(vm, now);
  const frameChildren: (Node | string)[] = [
    mapTools(),
    el("p", { class: "map-hint map-hint-desktop", id: "map-hint" }, [
      "Scroll to zoom. Hold Shift and drag to move. Click a station to track it and list trains. Hover or click a train for its route.",
    ]),
    el("p", { class: "map-hint map-hint-mobile", id: "map-hint-mobile" }, [
      "Use +/− to zoom. Drag to move. Tap a station to track it, or a train for its route.",
    ]),
    el("div", { class: "map-tooltip", "data-testid": "map-tooltip", hidden: "" }),
    svg,
  ];
  if (preview && spine) {
    const layout = stripLayout(spine, [preview], nowHhmm, preview.serviceGuid);
    frameChildren.push(journeyCard(preview, layout.selectedProgress, "route-preview-card"));
  }
  const frame = el("div", { class: "map-frame" }, frameChildren);
  mountMap(svg, map);
  const section = panel("Network overview", "network-overview", [mapLegend(), frame]);
  section.classList.add("network-panel");
  return section;
}

function renderNetwork(vm: ScreenViewModel, now: Date): HTMLElement {
  const message = networkListMessage(vm, now);
  const note = scheduleFocusNote(vm);
  const problems = problemTrains(vm, now);
  const summary = disruptionSummary(vm, now);
  const disruption = disruptionMessage(vm, now);
  const incidentEmpty =
    vm.commsLost && !vm.networkHeld
      ? ""
      : !vm.networkHeld
        ? vm.emptyMessage || "Loading"
        : problems.length === 0
          ? "No problems"
          : "";
  const schedules = panel("Live schedules", "live-schedules", [
    ...(note ? [note] : []),
    ...(message ? [el("p", { "data-testid": "network-message", class: "panel-message" }, [message])] : []),
    scheduleTable(vm, now),
  ]);
  const side = el("div", { class: "col-side" }, [
    panel(
      "Disruption summary",
      "disruptions",
      messageOr(disruption, disruptionSummaryList(summary, vm.networkEventFilter)),
    ),
    noDataPanel("Disruption trend", "disruption-trend"),
    panel(
      "Incident logs",
      "incident-logs",
      messageOr(incidentEmpty, problemList(problems)),
    ),
    panel("System alerts", "system-alerts", [
      el("p", { class: "nodata" }, [systemAlertsMessage(vm)]),
    ]),
  ]);
  const secondary = el("details", {
    class: "board-secondary",
    "data-testid": "board-secondary",
  }, [
    el("summary", { "data-testid": "board-secondary-toggle" }, ["Schedules & alerts"]),
    el("div", { class: "board-secondary-body" }, [schedules, side]),
  ]);
  const page = el("div", { class: "page" }, [
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
      secondary,
    ]),
    networkFooterBar(networkFooter(vm)),
  ]);
  if (isMobileViewport()) page.classList.add("is-mobile");
  return page;
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
  const label = cancelled ? "cancelled" : train.status;
  const changeFromScheduled = cancelled || train.issue;
  return el("tr", {}, [
    textCell(id),
    textCell(earlier),
    textCell(later),
    textCell(cancelled ? "" : (train.reachedCrs ?? "")),
    textCell(cancelled ? "" : (train.nextCrs ?? "")),
    el("td", {}, [statusWord({ label, changeFromScheduled })]),
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

function severityClass(severity: SegmentSeverity): string {
  if (severity === "minor") return "sev-minor";
  if (severity === "severe") return "sev-severe";
  return "sev-ok";
}

function routeStripSvg(
  layout: StripLayout,
  selectedGuid: string | null,
  byGuid: ReadonlyMap<string, NetworkTrain>,
): SVGSVGElement {
  const svg = svgEl("svg", {
    class: "route-strip",
    viewBox: `0 0 ${layout.width} ${layout.height}`,
    preserveAspectRatio: "xMidYMid meet",
    role: "group",
    "aria-label": "Route live track",
    "data-testid": "route-strip",
  });
  for (const segment of layout.segments) {
    svg.append(svgEl("line", {
      class: "route-segment",
      x1: String(segment.x1),
      y1: String(segment.y),
      x2: String(segment.x2),
      y2: String(segment.y),
      stroke: "#5a7a9a",
      "stroke-width": "6",
      "stroke-linecap": "round",
    }));
  }
  for (const station of layout.stations) {
    const group = svgEl("g", {
      class: "route-station",
      transform: `translate(${station.x} ${station.y})`,
      "data-crs": station.crs,
    });
    group.append(svgEl("circle", { r: "7", class: "route-station-dot" }));
    const label = svgEl("text", {
      class: "route-station-label",
      x: "0",
      y: "28",
      "text-anchor": "middle",
    });
    label.textContent = stationName(station.crs);
    group.append(label);
    svg.append(group);
  }
  for (const marker of layout.markers) {
    const selected = marker.serviceGuid === selectedGuid;
    const tip = serviceShortLabelsForGuids([marker.serviceGuid], byGuid);
    const group = svgEl("g", {
      class: selected
        ? `route-train ${severityClass(marker.severity)} is-selected`
        : `route-train ${severityClass(marker.severity)}`,
      transform: `translate(${marker.x.toFixed(1)} ${marker.y.toFixed(1)}) rotate(${((marker.angle * 180) / Math.PI).toFixed(1)})`,
      "data-testid": "route-train",
      "data-service": marker.serviceGuid,
      "data-tip": tip,
    });
    group.append(svgEl("polygon", { points: "-8,-5 12,0 -8,5" }));
    const title = svgEl("title", {});
    title.textContent = tip;
    group.append(title);
    const tag = svgEl("text", {
      class: "route-train-label",
      x: "0",
      y: marker.above ? "-12" : "18",
      "text-anchor": "middle",
      transform: `rotate(${((-marker.angle * 180) / Math.PI).toFixed(1)})`,
    });
    tag.textContent = tip;
    group.append(tag);
    svg.append(group);
  }
  return svg;
}

function placeLabel(train: NetworkTrain, nowHhmm: string): string {
  const place = placeTrain(train.stops ?? [], nowHhmm);
  if (!place) return "unknown";
  if (place.atStation) return `at ${stationName(place.crs)}`;
  return `${stationName(place.fromCrs)} → ${stationName(place.toCrs)}`;
}

function statusWordText(label: StatusLabel | undefined, problem: boolean): string {
  if (label) return label;
  return problem ? "delayed" : "on time";
}

function stationEntityCard(
  crs: string,
  aggregates: StationAggregate[],
): HTMLElement {
  const row = aggregates.find((item) => item.crs === crs);
  return el("aside", { class: "journey-card", "data-testid": "selected-entity-card" }, [
    el("p", { class: "journey-card-title" }, [
      "SELECTED STATION: ",
      el("span", { class: "mono" }, [crs]),
    ]),
    el("p", { class: "journey-card-sub" }, [stationName(crs)]),
    el("p", { class: "journey-meta" }, [
      `At station: ${row?.atCount ?? 0}`,
    ]),
    el("p", { class: "journey-meta" }, [
      `Approaching: ${row?.approachingCount ?? 0}`,
    ]),
    el("p", { class: "journey-meta" }, [
      el("span", {
        class: row?.worstStatus && row.worstStatus !== "on time" ? "dot severe" : "dot ok",
      }, []),
      " Worst status: ",
      row?.worstStatus ?? "none",
    ]),
  ]);
}

function otherTrainsPane(
  trains: readonly NetworkTrain[],
  trackedGuid: string | null,
  nowHhmm: string,
): HTMLElement {
  const others = trains.filter((train) => train.serviceGuid !== trackedGuid);
  if (others.length === 0) {
    return panel("Other visible trains", "route-other-trains", [
      el("p", { class: "nodata" }, ["No other trains in window"]),
    ]);
  }
  const list = el("ul", { class: "route-side-list" }, []);
  for (const train of others) {
    list.append(
      el("li", { class: "route-side-row", "data-service": train.serviceGuid }, [
        el("span", { class: "mono" }, [train.serviceGuid]),
        el("span", {}, [statusWordText(train.statusLabel, train.problem)]),
        el("span", { class: "muted" }, [placeLabel(train, nowHhmm)]),
      ]),
    );
  }
  return panel("Other visible trains", "route-other-trains", [list]);
}

function stationsPane(aggregates: StationAggregate[]): HTMLElement {
  if (aggregates.length === 0) {
    return panel("Stations", "route-stations", [
      el("p", { class: "nodata" }, ["No stations in window"]),
    ]);
  }
  const list = el("ul", { class: "route-side-list" }, []);
  for (const row of aggregates) {
    list.append(
      el("li", { class: "route-side-row", "data-crs": row.crs }, [
        el("span", { class: "mono" }, [row.crs]),
        el("span", {}, [stationName(row.crs)]),
        el("span", {}, [
          `at ${row.atCount} · app ${row.approachingCount}`,
        ]),
        el("span", { class: "muted" }, [row.worstStatus ?? "—"]),
      ]),
    );
  }
  return panel("Stations", "route-stations", [list]);
}

function renderRoute(vm: ScreenViewModel, now: Date): HTMLElement {
  const track = vm.routeTrack;
  if (!track || !vm.diagramHeld || !vm.networkHeld) {
    return el("div", { class: "page" }, [
      el("p", { class: "panel-message" }, ["Route unavailable"]),
      el("button", { type: "button", "data-testid": "open-network" }, ["Back to network"]),
    ]);
  }
  const nowHhmm = londonHhmm(now);
  const resolved = resolveRouteWindow(vm, nowHhmm);
  if (!resolved || resolved.window.length === 0) {
    return el("div", { class: "page" }, [
      el("p", { class: "panel-message" }, ["Route window unavailable"]),
      el("button", { type: "button", "data-testid": "open-network" }, ["Back to network"]),
    ]);
  }
  const live = routeVisibleTrains(
    vm.networkHeld.trains,
    nowHhmm,
    resolved.selectedServiceGuid,
  );
  const onWindow = trainsOnSpine(live, resolved.window, nowHhmm);
  const byGuid = trainLabelMap(live);
  const layout = stripLayout(
    resolved.window,
    onWindow,
    nowHhmm,
    resolved.selectedServiceGuid,
  );
  const selected = selectedRouteTrain(vm, now);
  const aggregates = stationAggregates(resolved.window, onWindow, nowHhmm);
  const title =
    track.kind === "station"
      ? `Tracking station ${stationName(track.crs)}`
      : routeTrackTitle(track);

  const entityPane =
    track.kind === "station"
      ? stationEntityCard(track.crs, aggregates)
      : selected
        ? journeyCard(selected, layout.selectedProgress, "selected-entity-card")
        : el("aside", { class: "journey-card", "data-testid": "selected-entity-card" }, [
            el("p", { class: "nodata" }, ["Tracked train not in feed"]),
          ]);

  return el("div", { class: "page route-page" }, [
    el("div", { class: "toolbar" }, [
      el("p", {}, [
        el("button", { type: "button", "data-testid": "open-network", class: "crumb" }, [
          "Network Overview",
        ]),
        el("span", { class: "crumb-current" }, ["Route detail"]),
      ]),
      intervalField(vm),
    ]),
    el("div", { class: "route-header" }, [
      el("p", { class: "route-selected", "data-testid": "selected-route" }, [
        el("strong", {}, [title]),
      ]),
      el("div", { class: "route-stats", "data-testid": "route-stats" }, [
        el("p", {}, [`Visible trains: ${onWindow.length}`]),
        el("p", {}, [`Stations: ${resolved.window.length}`]),
      ]),
    ]),
    panel("Route & Live Track", "route-track", [
      routeStripSvg(layout, resolved.selectedServiceGuid, byGuid),
    ]),
    el("div", { class: "route-footer" }, [
      entityPane,
      otherTrainsPane(onWindow, resolved.selectedServiceGuid, nowHhmm),
      stationsPane(aggregates),
    ]),
  ]);
}

export function renderApp(root: HTMLElement, vm: ScreenViewModel, now = new Date()): void {
  root.replaceChildren();
  const title = vm.page === "pair" && vm.phase === "loaded"
    ? "WEST COAST SOUTH CONTROL"
    : vm.page === "route" && vm.phase === "loaded"
      ? "Route & Live Track"
      : "JOURNEY CONTROL CENTRE";
  const shell = el("div", { class: isMobileViewport() ? "shell is-mobile" : "shell" }, [
    header(vm, title),
    statusBar(vm),
  ]);
  if (vm.phase === "empty") shell.append(renderEmpty(vm));
  else if (vm.page === "pair") shell.append(renderPair(vm));
  else if (vm.page === "route") shell.append(renderRoute(vm, now));
  else shell.append(renderNetwork(vm, now));
  root.append(shell);
}
