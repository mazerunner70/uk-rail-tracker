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
const STATION_GAP = 120;
const TRAIN_OFFSET_Y = 28;

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
  if (from === undefined || to === undefined) return null;
  const span = Math.abs(to - from);
  if (span === 0) return { progress: from / last, towardDest: true };
  const local = from < to ? place.fraction : 1 - place.fraction;
  const lo = Math.min(from, to);
  const progress = (lo + local * span) / last;
  return { progress, towardDest: from < to };
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
  const severities = segmentSeverity(spine, trains, nowHhmm);
  const segments: StripSegment[] = [];
  for (let i = 0; i < spine.length - 1; i++) {
    const from = stations[i]!;
    const to = stations[i + 1]!;
    segments.push({
      fromCrs: spine[i]!,
      toCrs: spine[i + 1]!,
      severity: severities[i] ?? "on-time",
      x1: from.x,
      x2: to.x,
      y: STRIP_Y,
    });
  }
  const markers: StripTrainMarker[] = [];
  let stackAbove = 0;
  let stackBelow = 0;
  for (const train of trains) {
    const place = placeTrain(train.stops ?? [], nowHhmm);
    if (!place) continue;
    const on = placeOnSpine(place, spine);
    if (!on) continue;
    const above = on.towardDest;
    const stack = above ? stackAbove++ : stackBelow++;
    const x =
      STRIP_PAD_X + on.progress * Math.max(1, spine.length - 1) * STATION_GAP;
    const y =
      STRIP_Y + (above ? -1 : 1) * (TRAIN_OFFSET_Y + stack * 14);
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
  return {
    stations,
    segments,
    markers,
    width: Math.max(width, STRIP_PAD_X * 2),
    height: STRIP_HEIGHT,
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
