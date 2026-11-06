import type { CorridorLayout, NetworkDiagram, NetworkStop, NetworkTrain, StationLayout } from "./types.js";

const PERP_X = Math.SQRT1_2;
const PERP_Y = Math.SQRT1_2;

export type { CorridorLayout, StationLayout } from "./types.js";

export type TrainPlace =
  | { atStation: false; fromCrs: string; toCrs: string; fraction: number }
  | { atStation: true; crs: string; towardCrs: string | null; fromCrs: string | null };

export type MarkerLayout = {
  serviceGuid: string;
  x: number;
  y: number;
  angle: number;
  problem: boolean;
  atStation: boolean;
  stack: number;
  stackSize: number;
};

export type Schematic = {
  stations: StationLayout[];
  corridors: CorridorLayout[];
  markers: MarkerLayout[];
  width: number;
  height: number;
};

type PublicStop = { crs: string; arr: string | null; dep: string | null };

type TimedEvent = { index: number; kind: "arr" | "dep"; at: number };

type Spot = { x: number; y: number; angle: number };

type DraftMarker = MarkerLayout & { group: string };

function minutes(hhmm: string): number {
  return Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(2, 4));
}

function comparableMinutes(stopHhmm: string, originStopHhmm: string): number {
  const extra = minutes(stopHhmm) < minutes(originStopHhmm) ? 24 * 60 : 0;
  return minutes(stopHhmm) + extra;
}

function nowComparableMinutes(originStopHhmm: string, nowHhmm: string): number {
  const nowMin = minutes(nowHhmm);
  const originMin = minutes(originStopHhmm);
  if (nowMin < originMin && nowMin < 6 * 60 && originMin >= 18 * 60) {
    return nowMin + 24 * 60;
  }
  return nowMin;
}

function publicStops(stops: readonly NetworkStop[]): PublicStop[] {
  const out: PublicStop[] = [];
  for (const stop of stops) {
    if (stop.crs) out.push({ crs: stop.crs, arr: stop.arr, dep: stop.dep });
  }
  return out;
}

function originHhmm(stops: readonly PublicStop[]): string | null {
  const first = stops[0];
  if (!first) return null;
  return first.dep ?? first.arr;
}

function eventsFor(stops: readonly PublicStop[], origin: string): TimedEvent[] {
  const events: TimedEvent[] = [];
  for (let index = 0; index < stops.length; index++) {
    const stop = stops[index]!;
    const last = index === stops.length - 1;
    if (stop.arr) {
      events.push({ index, kind: "arr", at: comparableMinutes(stop.arr, origin) });
    }
    const dep = stop.dep ?? (last ? null : stop.arr);
    if (dep) events.push({ index, kind: "dep", at: comparableMinutes(dep, origin) });
  }
  return events;
}

function latestEvent(events: readonly TimedEvent[], now: number): TimedEvent | null {
  let latest: TimedEvent | null = null;
  for (const event of events) {
    if (event.at <= now && (latest === null || event.at >= latest.at)) latest = event;
  }
  return latest;
}

function arrivalMinute(stop: PublicStop, origin: string): number | null {
  const hhmm = stop.arr ?? stop.dep;
  return hhmm ? comparableMinutes(hhmm, origin) : null;
}

function atStation(stops: readonly PublicStop[], index: number): TrainPlace {
  return {
    atStation: true,
    crs: stops[index]!.crs,
    towardCrs: stops[index + 1]?.crs ?? null,
    fromCrs: stops[index - 1]?.crs ?? null,
  };
}

function between(stops: readonly PublicStop[], index: number, origin: string, now: number): TrainPlace {
  const next = stops[index + 1]!;
  const departAt = latestDeparture(stops, index, origin);
  const arriveAt = arrivalMinute(next, origin);
  const span = arriveAt === null ? 0 : arriveAt - departAt;
  const fraction = span <= 0 ? 1 : Math.min(1, Math.max(0, (now - departAt) / span));
  return { atStation: false, fromCrs: stops[index]!.crs, toCrs: next.crs, fraction };
}

function latestDeparture(stops: readonly PublicStop[], index: number, origin: string): number {
  const stop = stops[index]!;
  const dep = stop.dep ?? stop.arr ?? "0000";
  return comparableMinutes(dep, origin);
}

export function placeTrain(stops: readonly NetworkStop[], nowHhmm: string): TrainPlace | null {
  const pubs = publicStops(stops);
  const origin = originHhmm(pubs);
  if (!origin) return null;
  const events = eventsFor(pubs, origin);
  if (events.length === 0) return null;
  const latest = latestEvent(events, nowComparableMinutes(origin, nowHhmm));
  if (!latest) return atStation(pubs, 0);
  if (latest.kind === "dep" && pubs[latest.index + 1]) {
    return between(pubs, latest.index, origin, nowComparableMinutes(origin, nowHhmm));
  }
  return atStation(pubs, latest.index);
}
export function stationBadgeOffset(lane: number): { x: number; y: number } {
  const sign = lane === 0 ? -1 : Math.sign(lane);
  const dist = 28;
  return { x: PERP_X * sign * dist, y: PERP_Y * sign * dist };
}
function sequenceOf(corridor: CorridorLayout, reversed: boolean): string[] {
  const forward = [corridor.from, ...corridor.via, corridor.to];
  return reversed ? [...forward].reverse() : forward;
}

function hopT(sequence: readonly string[], from: string, to: string, fraction: number): number | null {
  for (let i = 0; i < sequence.length - 1; i++) {
    if (sequence[i] === from && sequence[i + 1] === to) {
      return (i + fraction) / (sequence.length - 1);
    }
  }
  return null;
}

function pointOnPolyline(
  points: readonly { x: number; y: number }[],
  t: number,
): Spot | null {
  if (points.length === 0) return null;
  const lengths = [0];
  for (let i = 1; i < points.length; i++) {
    const dx = points[i]!.x - points[i - 1]!.x;
    const dy = points[i]!.y - points[i - 1]!.y;
    lengths.push(lengths[i - 1]! + Math.hypot(dx, dy));
  }
  const total = lengths[lengths.length - 1] ?? 0;
  if (total === 0) return { x: points[0]!.x, y: points[0]!.y, angle: 0 };
  const target = Math.min(1, Math.max(0, t)) * total;
  for (let i = 1; i < points.length; i++) {
    const end = lengths[i]!;
    const start = lengths[i - 1]!;
    if (target > end && i < points.length - 1) continue;
    const span = end - start;
    const local = span === 0 ? 0 : (target - start) / span;
    const from = points[i - 1]!;
    const next = points[i]!;
    return {
      x: from.x + (next.x - from.x) * local,
      y: from.y + (next.y - from.y) * local,
      angle: Math.atan2(next.y - from.y, next.x - from.x),
    };
  }
  return null;
}

function spotOnHop(
  corridors: readonly CorridorLayout[],
  fromCrs: string,
  toCrs: string,
  fraction: number,
): Spot | null {
  for (const corridor of corridors) {
    const forward = hopT(sequenceOf(corridor, false), fromCrs, toCrs, fraction);
    if (forward !== null) return pointOnPolyline(corridor.points, forward);
    const backward = hopT(sequenceOf(corridor, true), fromCrs, toCrs, fraction);
    if (backward !== null) return pointOnPolyline([...corridor.points].reverse(), backward);
  }
  return null;
}

function stationSpot(stations: ReadonlyMap<string, StationLayout>, crs: string): Spot | null {
  const station = stations.get(crs);
  if (!station) return null;
  return { x: station.x, y: station.y, angle: 0 };
}

function pointingAngle(fromCrs: string, toCrs: string, stations: ReadonlyMap<string, StationLayout>, corridors: readonly CorridorLayout[]): number {
  const from = stations.get(fromCrs);
  const to = stations.get(toCrs);
  if (from && to) return Math.atan2(to.y - from.y, to.x - from.x);
  return spotOnHop(corridors, fromCrs, toCrs, 0)?.angle ?? 0;
}

function spotForPlace(
  place: TrainPlace,
  stations: ReadonlyMap<string, StationLayout>,
  corridors: readonly CorridorLayout[],
): Spot | null {
  if (!place.atStation) return spotOnHop(corridors, place.fromCrs, place.toCrs, place.fraction);
  const dot = stationSpot(stations, place.crs) ?? spotOnHop(corridors, place.fromCrs ?? place.crs, place.crs, 1);
  if (!dot) return null;
  const angle = place.towardCrs
    ? pointingAngle(place.crs, place.towardCrs, stations, corridors)
    : place.fromCrs
      ? pointingAngle(place.fromCrs, place.crs, stations, corridors)
      : 0;
  return { x: dot.x, y: dot.y, angle };
}

function groupKey(place: TrainPlace): string {
  if (place.atStation) return `at:${place.crs}`;
  return place.fromCrs < place.toCrs ? `${place.fromCrs}|${place.toCrs}` : `${place.toCrs}|${place.fromCrs}`;
}

function assignStacks(markers: DraftMarker[]): void {
  const groups = new Map<string, DraftMarker[]>();
  for (const marker of markers) {
    const list = groups.get(marker.group) ?? [];
    list.push(marker);
    groups.set(marker.group, list);
  }
  for (const list of groups.values()) {
    list.sort((a, b) => (a.serviceGuid < b.serviceGuid ? -1 : 1));
    for (let index = 0; index < list.length; index++) {
      const marker = list[index]!;
      marker.stack = index;
      marker.stackSize = list.length;
    }
  }
}

function wrapAngle(angle: number): number {
  return Math.atan2(Math.sin(angle), Math.cos(angle));
}

function sameDeparture(left: number, right: number): boolean {
  return Math.abs(wrapAngle(left - right)) <= Math.PI / 2;
}

function headingOf(list: readonly DraftMarker[]): number {
  let x = 0;
  let y = 0;
  for (const marker of list) {
    x += Math.cos(marker.angle);
    y += Math.sin(marker.angle);
  }
  return Math.atan2(y, x);
}

function dominantHeading(list: readonly DraftMarker[]): number {
  const ordered = [...list].sort((a, b) => (a.serviceGuid < b.serviceGuid ? -1 : 1));
  let best = ordered[0]!.angle;
  let bestCount = -1;
  for (const candidate of ordered) {
    const count = ordered.filter((marker) => sameDeparture(marker.angle, candidate.angle)).length;
    if (count > bestCount) {
      best = candidate.angle;
      bestCount = count;
    }
  }
  return best;
}

function mergeDeparture(list: readonly DraftMarker[], angle: number, group: string): DraftMarker {
  const ordered = [...list].sort((a, b) => (a.serviceGuid < b.serviceGuid ? -1 : 1));
  const first = ordered[0]!;
  return {
    ...first,
    serviceGuid: ordered.map((marker) => marker.serviceGuid).join(","),
    angle: wrapAngle(angle),
    problem: ordered.some((marker) => marker.problem),
    stack: 0,
    stackSize: 1,
    group,
  };
}

function departuresAtStation(list: readonly DraftMarker[], place: string): DraftMarker[] {
  if (list.length === 1) return [{ ...list[0]!, stack: 0, stackSize: 1, group: `${place}:onward` }];
  const primary = dominantHeading(list);
  const onward = list.filter((marker) => sameDeparture(marker.angle, primary));
  const back = list.filter((marker) => !sameDeparture(marker.angle, primary));
  const arrows = [mergeDeparture(onward, headingOf(onward), `${place}:onward`)];
  if (back.length > 0) {
    arrows.push(mergeDeparture(back, headingOf(onward) + Math.PI, `${place}:back`));
  }
  return arrows;
}

function placeKey(marker: DraftMarker): string {
  return `${marker.x.toFixed(2)}|${marker.y.toFixed(2)}`;
}

function directStationMarkers(markers: readonly DraftMarker[]): DraftMarker[] {
  const standing = new Map<string, DraftMarker[]>();
  const moving: DraftMarker[] = [];
  for (const marker of markers) {
    if (!marker.atStation) {
      moving.push(marker);
      continue;
    }
    const key = placeKey(marker);
    const list = standing.get(key) ?? [];
    list.push(marker);
    standing.set(key, list);
  }
  const arrows: DraftMarker[] = [];
  for (const [place, list] of standing) arrows.push(...departuresAtStation(list, place));
  return [...moving, ...arrows];
}

function placeMarkers(
  trains: readonly NetworkTrain[],
  nowHhmm: string,
  stations: readonly StationLayout[],
  corridors: readonly CorridorLayout[],
): MarkerLayout[] {
  const byCrs = new Map(stations.map((station) => [station.crs, station]));
  const drafts: DraftMarker[] = [];
  for (const train of trains) {
    const place = placeTrain(train.stops ?? [], nowHhmm);
    if (!place) continue;
    const spot = spotForPlace(place, byCrs, corridors);
    if (!spot) continue;
    drafts.push({
      serviceGuid: train.serviceGuid,
      x: spot.x,
      y: spot.y,
      angle: spot.angle,
      problem: train.problem,
      atStation: place.atStation,
      stack: 0,
      stackSize: 1,
      group: groupKey(place),
    });
  }
  const directed = directStationMarkers(drafts);
  assignStacks(directed);
  return directed.map(({ group: _group, ...marker }) => marker);
}

export function markersOn(
  diagram: NetworkDiagram,
  trains: readonly NetworkTrain[],
  nowHhmm: string,
): Schematic {
  return {
    stations: diagram.stations,
    corridors: diagram.corridors,
    markers: placeMarkers(trains, nowHhmm, diagram.stations, diagram.corridors),
    width: diagram.width,
    height: diagram.height,
  };
}
