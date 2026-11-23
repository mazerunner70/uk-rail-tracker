import { placeTrain, type TrainPlace } from "./networkMap.js";
import type { NetworkDiagram, NetworkTrain, StatusLabel } from "./types.js";

export type SegmentSeverity = "on-time" | "minor" | "severe";

export type StripStation = {
  crs: string;
  x: number;
  y: number;
};

export type StripSegment = {
  fromCrs: string;
  toCrs: string;
  severity: SegmentSeverity;
  x1: number;
  x2: number;
  y: number;
};

export type StripTrainMarker = {
  serviceGuid: string;
  x: number;
  y: number;
  angle: number;
  severity: SegmentSeverity;
  progress: number;
  above: boolean;
};

export type StripLayout = {
  stations: StripStation[];
  segments: StripSegment[];
  markers: StripTrainMarker[];
  width: number;
  height: number;
  selectedProgress: number | null;
};

const STRIP_PAD_X = 48;
const STRIP_Y = 80;
const STRIP_HEIGHT = 160;
const STRIP_PAD_Y = 24;
const STATION_GAP = 120;
const TRAIN_OFFSET_Y = 28;
const TRAIN_STACK_GAP = 14;

export function adjacency(diagram: NetworkDiagram): Map<string, string[]> {
  const links = new Map<string, string[]>();
  const add = (a: string, b: string) => {
    const list = links.get(a) ?? [];
    if (!list.includes(b)) list.push(b);
    links.set(a, list);
  };
  for (const corridor of diagram.corridors) {
    const seq = [corridor.from, ...corridor.via, corridor.to];
    for (let i = 0; i < seq.length - 1; i++) {
      const from = seq[i]!;
      const to = seq[i + 1]!;
      add(from, to);
      add(to, from);
    }
  }
  return links;
}

export function spinePath(
  diagram: NetworkDiagram,
  origin: string,
  dest: string,
): string[] {
  if (origin === dest) return [origin];
  const links = adjacency(diagram);
  const prev = new Map<string, string | null>([[origin, null]]);
  const queue = [origin];
  while (queue.length > 0) {
    const crs = queue.shift()!;
    for (const neighbor of links.get(crs) ?? []) {
      if (prev.has(neighbor)) continue;
      prev.set(neighbor, crs);
      queue.push(neighbor);
    }
  }
  if (!prev.has(dest)) return [origin, dest];
  const path: string[] = [];
  let cur: string | null = dest;
  while (cur) {
    path.push(cur);
    cur = prev.get(cur) ?? null;
  }
  path.reverse();
  return path;
}

function spineIndex(spine: readonly string[]): Map<string, number> {
  const index = new Map<string, number>();
  for (let i = 0; i < spine.length; i++) index.set(spine[i]!, i);
  return index;
}

export function placeOnSpine(
  place: TrainPlace,
  spine: readonly string[],
): { progress: number; towardDest: boolean } | null {
  const index = spineIndex(spine);
  if (spine.length < 2) {
    if (place.atStation && index.has(place.crs)) return { progress: 0, towardDest: true };
    return null;
  }
  const last = spine.length - 1;
  if (place.atStation) {
    const at = index.get(place.crs);
    if (at === undefined) return null;
    const toward = place.towardCrs ? index.get(place.towardCrs) : undefined;
    const towardDest = toward === undefined ? true : toward >= at;
    return { progress: at / last, towardDest };
  }
  const from = index.get(place.fromCrs);
  const to = index.get(place.toCrs);
  // Hop fully on the strip.
  if (from !== undefined && to !== undefined) {
    const span = Math.abs(to - from);
    if (span === 0) return { progress: from / last, towardDest: true };
    const local = from < to ? place.fraction : 1 - place.fraction;
    const lo = Math.min(from, to);
    const progress = (lo + local * span) / last;
    return { progress, towardDest: from < to };
  }
  // Entering the window: map the external→on-spine hop onto the last strip
  // hop into `to` (e.g. MKC→APS while the window starts at CED).
  if (from === undefined && to !== undefined) {
    const prev = Math.max(0, to - 1);
    const progress = (prev + place.fraction * (to - prev)) / last;
    return { progress, towardDest: true };
  }
  // Leaving the window: keep the marker at/near the last on-spine station.
  if (from !== undefined && to === undefined) {
    const next = Math.min(last, from + 1);
    const progress = (from + place.fraction * (next - from)) / last;
    return { progress, towardDest: next >= from };
  }
  return null;
}

export function trainsOnSpine(
  trains: readonly NetworkTrain[],
  spine: readonly string[],
  nowHhmm: string,
): NetworkTrain[] {
  const out: NetworkTrain[] = [];
  for (const train of trains) {
    const place = placeTrain(train.stops ?? [], nowHhmm);
    if (!place) continue;
    if (placeOnSpine(place, spine)) out.push(train);
  }
  return out;
}

function statusSeverity(label: StatusLabel | undefined, problem: boolean): SegmentSeverity {
  if (label === "cancelled" || label === "deactivated") return "severe";
  if (label === "delayed" || problem) return "minor";
  return "on-time";
}

function worse(a: SegmentSeverity, b: SegmentSeverity): SegmentSeverity {
  const rank = { "on-time": 0, minor: 1, severe: 2 } as const;
  return rank[a] >= rank[b] ? a : b;
}

export function segmentSeverity(
  spine: readonly string[],
  trains: readonly NetworkTrain[],
  nowHhmm: string,
): SegmentSeverity[] {
  const sevs: SegmentSeverity[] = [];
  for (let i = 0; i < spine.length - 1; i++) sevs.push("on-time");
  const index = spineIndex(spine);
  for (const train of trains) {
    const place = placeTrain(train.stops ?? [], nowHhmm);
    if (!place) continue;
    const severity = statusSeverity(train.statusLabel, train.problem);
    if (place.atStation) {
      const at = index.get(place.crs);
      if (at === undefined) continue;
      if (at > 0) sevs[at - 1] = worse(sevs[at - 1]!, severity);
      if (at < spine.length - 1) sevs[at] = worse(sevs[at]!, severity);
      continue;
    }
    const from = index.get(place.fromCrs);
    const to = index.get(place.toCrs);
    if (from === undefined || to === undefined) continue;
    const lo = Math.min(from, to);
    const hi = Math.max(from, to);
    for (let hop = lo; hop < hi; hop++) {
      sevs[hop] = worse(sevs[hop]!, severity);
    }
  }
  return sevs;
}

export function trainEnds(train: NetworkTrain): { originCrs: string; destCrs: string } | null {
  const origin =
    train.originCrs
    ?? train.stops?.find((stop) => stop.crs)?.crs
    ?? null;
  const dest =
    train.destCrs
    ?? [...(train.stops ?? [])].reverse().find((stop) => stop.crs)?.crs
    ?? null;
  if (!origin || !dest) return null;
  return { originCrs: origin, destCrs: dest };
}

export function selectedProgress(
  train: NetworkTrain | undefined,
  spine: readonly string[],
  nowHhmm: string,
): number | null {
  if (!train) return null;
  const place = placeTrain(train.stops ?? [], nowHhmm);
  if (!place) return null;
  return placeOnSpine(place, spine)?.progress ?? null;
}

/** Progress 0–1 along the train's own calling-point CRS spine (full journey). */
export function journeyProgress(
  train: { stops?: readonly { crs?: string; arr: string | null; dep: string | null }[] },
  nowHhmm: string,
): number | null {
  const spine = (train.stops ?? [])
    .map((stop) => stop.crs)
    .filter((crs): crs is string => Boolean(crs));
  if (spine.length < 2) return null;
  const place = placeTrain(train.stops ?? [], nowHhmm);
  if (!place) return null;
  return placeOnSpine(place, spine)?.progress ?? null;
}

export const WINDOW_RADIUS = 5;

export function windowKeyForPlace(place: TrainPlace): string {
  if (place.atStation) return `at:${place.crs}`;
  return `between:${place.fromCrs}|${place.toCrs}`;
}

function sliceWindow(
  spine: readonly string[],
  startIdx: number,
  endIdx: number,
): string[] {
  const lo = Math.max(0, startIdx);
  const hi = Math.min(spine.length - 1, endIdx);
  if (hi < lo) return [];
  return spine.slice(lo, hi + 1);
}

/** ±radius around an at-station place, or radius either side of a between hop. */
export function windowAroundPlace(
  spine: readonly string[],
  place: TrainPlace,
  radius = WINDOW_RADIUS,
): string[] {
  const index = spineIndex(spine);
  if (place.atStation) {
    const at = index.get(place.crs);
    if (at === undefined) return [...spine];
    return sliceWindow(spine, at - radius, at + radius);
  }
  const from = index.get(place.fromCrs);
  const to = index.get(place.toCrs);
  if (from === undefined || to === undefined) return [...spine];
  const lo = Math.min(from, to);
  const hi = Math.max(from, to);
  return sliceWindow(spine, lo - radius, hi + radius);
}

/** Walk along degree-2 hops away from `prev`, up to `limit` stations. */
function walkHopChain(
  links: Map<string, string[]>,
  start: string,
  prev: string,
  limit: number,
): string[] {
  const out: string[] = [];
  let cur = start;
  let back = prev;
  for (let i = 0; i < limit; i++) {
    out.push(cur);
    const nexts = (links.get(cur) ?? []).filter((n) => n !== back);
    if (nexts.length !== 1) break;
    back = cur;
    cur = nexts[0]!;
  }
  return out;
}

/**
 * Prefer the longest schematic line containing crs.
 * Fallback: local two-direction hop chain (never global rank sort).
 */
export function corridorThroughStation(
  diagram: NetworkDiagram,
  crs: string,
): string[] {
  let best: string[] = [crs];
  for (const line of diagram.lines ?? []) {
    if (!line.stations.includes(crs)) continue;
    if (line.stations.length > best.length) best = line.stations;
  }
  if (best.length > 1) return best;

  for (const corridor of diagram.corridors) {
    const seq = [corridor.from, ...corridor.via, corridor.to];
    if (!seq.includes(crs)) continue;
    if (seq.length > best.length) best = seq;
  }
  if (best.length > 2) return best;

  const links = adjacency(diagram);
  const neighbors = [...(links.get(crs) ?? [])].sort();
  if (neighbors.length === 0) return best;
  const before = walkHopChain(links, neighbors[0]!, crs, WINDOW_RADIUS).reverse();
  const after =
    neighbors.length >= 2
      ? walkHopChain(links, neighbors[1]!, crs, WINDOW_RADIUS)
      : [];
  return [...before, crs, ...after];
}

export function windowAroundStation(
  diagram: NetworkDiagram,
  crs: string,
  radius = WINDOW_RADIUS,
): string[] {
  const spine = corridorThroughStation(diagram, crs);
  const index = spineIndex(spine);
  const at = index.get(crs);
  if (at === undefined) return [crs];
  return sliceWindow(spine, at - radius, at + radius);
}

export type StationAggregate = {
  crs: string;
  atCount: number;
  approachingCount: number;
  worstStatus: StatusLabel | null;
};

function worseStatus(
  left: StatusLabel | null,
  right: StatusLabel | undefined,
): StatusLabel | null {
  const rank: Record<StatusLabel, number> = {
    "on time": 0,
    delayed: 1,
    cancelled: 2,
    deactivated: 3,
  };
  if (!right) return left;
  if (!left) return right;
  return rank[right] >= rank[left] ? right : left;
}

/** Aggregate live trains at / approaching each window station. */
export function stationAggregates(
  window: readonly string[],
  trains: readonly NetworkTrain[],
  nowHhmm: string,
): StationAggregate[] {
  const index = spineIndex(window);
  const rows = window.map((crs) => ({
    crs,
    atCount: 0,
    approachingCount: 0,
    worstStatus: null as StatusLabel | null,
  }));
  for (const train of trains) {
    const place = placeTrain(train.stops ?? [], nowHhmm);
    if (!place) continue;
    if (place.atStation) {
      const at = index.get(place.crs);
      if (at === undefined) continue;
      const row = rows[at]!;
      row.atCount += 1;
      row.worstStatus = worseStatus(row.worstStatus, train.statusLabel);
      continue;
    }
    const to = index.get(place.toCrs);
    if (to === undefined) continue;
    const row = rows[to]!;
    row.approachingCount += 1;
    row.worstStatus = worseStatus(row.worstStatus, train.statusLabel);
  }
  return rows;
}

export function stripLayout(
  spine: readonly string[],
  trains: readonly NetworkTrain[],
  nowHhmm: string,
  selectedServiceGuid: string | null,
): StripLayout {
  const stations: StripStation[] = spine.map((crs, i) => ({
    crs,
    x: STRIP_PAD_X + i * STATION_GAP,
    y: STRIP_Y,
  }));
  // Neutral segments for this stage — no severity colouring on the line.
  const segments: StripSegment[] = [];
  for (let i = 0; i < spine.length - 1; i++) {
    const from = stations[i]!;
    const to = stations[i + 1]!;
    segments.push({
      fromCrs: spine[i]!,
      toCrs: spine[i + 1]!,
      severity: "on-time",
      x1: from.x,
      x2: to.x,
      y: STRIP_Y,
    });
  }
  const markers: StripTrainMarker[] = [];
  // Stack only trains that share the same place + side, so a cluster at one
  // end of the strip cannot push a mid-hop marker out of the viewBox.
  const stackByLocation = new Map<string, number>();
  for (const train of trains) {
    const place = placeTrain(train.stops ?? [], nowHhmm);
    if (!place) continue;
    const on = placeOnSpine(place, spine);
    if (!on) continue;
    const above = on.towardDest;
    const locationKey = `${windowKeyForPlace(place)}:${above ? "above" : "below"}`;
    const stack = stackByLocation.get(locationKey) ?? 0;
    stackByLocation.set(locationKey, stack + 1);
    const x =
      STRIP_PAD_X + on.progress * Math.max(1, spine.length - 1) * STATION_GAP;
    const y =
      STRIP_Y + (above ? -1 : 1) * (TRAIN_OFFSET_Y + stack * TRAIN_STACK_GAP);
    markers.push({
      serviceGuid: train.serviceGuid,
      x,
      y,
      angle: above ? 0 : Math.PI,
      severity: statusSeverity(train.statusLabel, train.problem),
      progress: on.progress,
      above,
    });
  }
  const selected = selectedServiceGuid
    ? trains.find((train) => train.serviceGuid === selectedServiceGuid)
    : undefined;
  const width =
    STRIP_PAD_X * 2 + Math.max(0, spine.length - 1) * STATION_GAP;
  let minY = STRIP_Y;
  let maxY = STRIP_Y;
  for (const marker of markers) {
    minY = Math.min(minY, marker.y);
    maxY = Math.max(maxY, marker.y);
  }
  // Keep station labels (~+28) and train tags (~±18) inside the viewBox.
  const top = Math.min(0, minY - STRIP_PAD_Y);
  const bottom = Math.max(STRIP_HEIGHT, maxY + STRIP_PAD_Y + 28);
  const shift = -top;
  if (shift > 0) {
    for (const station of stations) station.y += shift;
    for (const segment of segments) segment.y += shift;
    for (const marker of markers) marker.y += shift;
  }
  return {
    stations,
    segments,
    markers,
    width: Math.max(width, STRIP_PAD_X * 2),
    height: bottom + shift,
    selectedProgress: selectedProgress(selected, spine, nowHhmm),
  };
}

export function corridorHopsOnSpine(
  diagram: NetworkDiagram,
  spine: readonly string[],
): Set<string> {
  const spineSet = new Set(spine);
  const hops = new Set<string>();
  for (let i = 0; i < spine.length - 1; i++) {
    hops.add(`${spine[i]}|${spine[i + 1]}`);
    hops.add(`${spine[i + 1]}|${spine[i]}`);
  }
  for (const corridor of diagram.corridors) {
    const seq = [corridor.from, ...corridor.via, corridor.to];
    let on = true;
    for (const crs of seq) {
      if (!spineSet.has(crs)) {
        on = false;
        break;
      }
    }
    if (!on) continue;
    for (let i = 0; i < seq.length - 1; i++) {
      hops.add(`${seq[i]}|${seq[i + 1]}`);
      hops.add(`${seq[i + 1]}|${seq[i]}`);
    }
  }
  return hops;
}

export function corridorTouchesSpine(
  from: string,
  to: string,
  via: readonly string[],
  spineHops: ReadonlySet<string>,
): boolean {
  const seq = [from, ...via, to];
  for (let i = 0; i < seq.length - 1; i++) {
    if (spineHops.has(`${seq[i]}|${seq[i + 1]}`)) return true;
  }
  return false;
}
