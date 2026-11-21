import type { GeoIndex } from "./stationGeo.js";

const RANK_STEP = 72;
const LANE_STEP = 48;
const PAD = 36;
const FLOW_X = Math.SQRT1_2;
const FLOW_Y = -Math.SQRT1_2;
const PERP_X = Math.SQRT1_2;
const PERP_Y = Math.SQRT1_2;
const SPINE_COLOR = "#7dffa8";
const BRANCH_COLORS = ["#f0b45a", "#e07a3d", "#3dbebf", "#6aa0ff"] as const;
const GEO_SCALE_PX_PER_DEG = 900;
const DOT_RADIUS = 5;
const DOT_MIN_DIST = DOT_RADIUS * 2 + 4;
const BADGE_HALF_W = 18;
const BADGE_HALF_H = 9;
const BADGE_OFFSET_DIST = 28;
const NUDGE_ITERATIONS = 60;
const NUDGE_STEP = 4;

export type SchematicStop = { crs?: string | null };

export type SchematicJourney = { stops: readonly SchematicStop[] };

export type LayoutOptions = {
  geo?: GeoIndex;
};

export type StationLayout = {
  crs: string;
  x: number;
  y: number;
  rank: number;
  lane: number;
};

export type CorridorLayout = {
  from: string;
  to: string;
  via: string[];
  points: { x: number; y: number }[];
  color: string;
};

/** Ordered CRS sequence for tracking / topology (junction→…→junction walks). */
export type SchematicLine = {
  id: string;
  stations: string[];
};

export type DiagramBody = {
  stations: StationLayout[];
  corridors: CorridorLayout[];
  lines: SchematicLine[];
  width: number;
  height: number;
};

export type LayoutInputNode = {
  crs: string;
  x: number;
  y: number;
  rank: number;
  lane: number;
};

export type LayoutInputEdge = {
  from: string;
  to: string;
  weight: number;
};

export type LayoutInputLineHop = {
  linename: string;
  from: string;
  to: string;
};

export type LayoutInput = {
  nodes: LayoutInputNode[];
  edges: LayoutInputEdge[];
  lines: LayoutInputLineHop[];
};

/** Pixel scale applied to MILP integer grid coordinates. */
export const OCT_CELL_PX = 48;

export class LayoutDegreeError extends Error {
  readonly maxDegree: number;
  readonly crs: string;

  constructor(crs: string, maxDegree: number) {
    super(
      `Station ${crs} has degree ${maxDegree}; octilinear layout requires degree ≤ 8.`,
    );
    this.name = "LayoutDegreeError";
    this.crs = crs;
    this.maxDegree = maxDegree;
  }
}

type Proj = {
  toXY: (lat: number, lon: number) => { x: number; y: number };
};

type Edge = { from: string; to: string; weight: number };

type Walk = { from: string; to: string; via: string[] };

type Graph = {
  adj: Map<string, Map<string, number>>;
  originCount: Map<string, number>;
  destCount: Map<string, number>;
  stopLists: string[][];
};

function bump(counts: Map<string, number>, crs: string): void {
  counts.set(crs, (counts.get(crs) ?? 0) + 1);
}

function ensureNode(adj: Map<string, Map<string, number>>, crs: string): void {
  if (!adj.has(crs)) adj.set(crs, new Map());
}

function edgeKey(left: string, right: string): string {
  return left < right ? `${left}|${right}` : `${right}|${left}`;
}

function addHop(adj: Map<string, Map<string, number>>, left: string, right: string): void {
  if (left === right) return;
  ensureNode(adj, left);
  ensureNode(adj, right);
  const fromLeft = adj.get(left)!;
  const fromRight = adj.get(right)!;
  fromLeft.set(right, (fromLeft.get(right) ?? 0) + 1);
  fromRight.set(left, (fromRight.get(left) ?? 0) + 1);
}

function publicCrs(stops: readonly SchematicStop[]): string[] {
  const out: string[] = [];
  for (const stop of stops) {
    if (stop.crs) out.push(stop.crs);
  }
  return out;
}

function buildGraph(journeys: readonly SchematicJourney[]): Graph {
  const adj = new Map<string, Map<string, number>>();
  const originCount = new Map<string, number>();
  const destCount = new Map<string, number>();
  const stopLists: string[][] = [];
  for (const journey of journeys) {
    const stops = publicCrs(journey.stops);
    if (stops.length === 0) continue;
    stopLists.push(stops);
    bump(originCount, stops[0]!);
    bump(destCount, stops[stops.length - 1]!);
    for (const crs of stops) ensureNode(adj, crs);
    for (let i = 0; i < stops.length - 1; i++) addHop(adj, stops[i]!, stops[i + 1]!);
  }
  return { adj, originCount, destCount, stopLists };
}

function routeName(route: { from: string; to: string; via: readonly string[] }): string {
  return `${route.from}|${route.to}|${route.via.join(",")}`;
}

function openNeighbors(graph: Graph, crs: string, drop: ReadonlySet<string>): string[] {
  return [...(graph.adj.get(crs)?.keys() ?? [])]
    .filter((neighbor) => !drop.has(edgeKey(crs, neighbor)))
    .sort();
}

function isJunction(graph: Graph, crs: string, drop: ReadonlySet<string>): boolean {
  return openNeighbors(graph, crs, drop).length !== 2;
}

function otherNeighbor(graph: Graph, crs: string, previous: string, drop: ReadonlySet<string>): string | null {
  for (const neighbor of openNeighbors(graph, crs, drop)) {
    if (neighbor !== previous) return neighbor;
  }
  return null;
}

function orderWalk(start: string, end: string, via: readonly string[]): Walk {
  if (start < end) return { from: start, to: end, via: [...via] };
  return { from: end, to: start, via: [...via].reverse() };
}

function walksBetweenJunctions(graph: Graph, drop: ReadonlySet<string>): Walk[] {
  const walks: Walk[] = [];
  const seen = new Set<string>();
  const junctions = [...graph.adj.keys()].filter((crs) => isJunction(graph, crs, drop)).sort();
  for (const start of junctions) {
    for (const next of openNeighbors(graph, start, drop)) {
      if (seen.has(edgeKey(start, next))) continue;
      const via: string[] = [];
      let prev = start;
      let current = next;
      seen.add(edgeKey(start, next));
      while (!isJunction(graph, current, drop)) {
        via.push(current);
        const onward = otherNeighbor(graph, current, prev, drop);
        if (!onward || seen.has(edgeKey(current, onward))) break;
        seen.add(edgeKey(current, onward));
        prev = current;
        current = onward;
      }
      if (isJunction(graph, current, drop)) walks.push(orderWalk(start, current, via));
    }
  }
  return walks;
}

function walkHops(walk: Walk): string[] {
  const seq = [walk.from, ...walk.via, walk.to];
  const hops: string[] = [];
  for (let i = 0; i < seq.length - 1; i++) hops.push(edgeKey(seq[i]!, seq[i + 1]!));
  return hops;
}

function isProperSubset(left: ReadonlySet<string>, right: ReadonlySet<string>): boolean {
  if (left.size >= right.size) return false;
  for (const crs of left) if (!right.has(crs)) return false;
  return true;
}

function dropSubsetWalks(walks: readonly Walk[], drop: Set<string>): void {
  const sets = walks.map((walk) => new Set(walk.via));
  for (let i = 0; i < walks.length; i++) {
    const smaller = sets[i]!;
    const larger = walks.filter((_, index) => index !== i && isProperSubset(smaller, sets[index]!));
    if (larger.length === 0) continue;
    const kept = new Set(larger.flatMap((walk) => walkHops(walk)));
    for (const hop of walkHops(walks[i]!)) {
      if (!kept.has(hop)) drop.add(hop);
    }
  }
}

function markSkippedPairs(graph: Graph, stops: readonly string[], drop: Set<string>): void {
  // Drop every non-adjacent pair on this journey (A-C, A-D, A-E, B-E, …)
  // when a direct hop for that pair also exists somewhere in the graph.
  for (let i = 0; i < stops.length; i++) {
    for (let j = i + 2; j < stops.length; j++) {
      const left = stops[i]!;
      const right = stops[j]!;
      if (left === right || !graph.adj.get(left)?.has(right)) continue;
      drop.add(edgeKey(left, right));
    }
  }
}

/** Equirectangular km-ish units for comparing path length vs chord length. */
function geoDist(
  left: string,
  right: string,
  geo: GeoIndex,
): number | null {
  const a = geo.get(left);
  const b = geo.get(right);
  if (!a || !b) return null;
  const midLat = ((a.lat + b.lat) / 2) * (Math.PI / 180);
  const dx = (b.lon - a.lon) * Math.cos(midLat);
  const dy = b.lat - a.lat;
  return Math.hypot(dx, dy);
}

/**
 * Drop chord u-v when a longer hop-path u-…-v exists that is nearly as short
 * geographically (composed local services covering an express skip), for any
 * span (A-C, A-D, A-E, …).
 */
function markGeoShortcuts(graph: Graph, geo: GeoIndex, drop: Set<string>): void {
  const maxHops = 12;
  const slack = 1.35;
  const edges: { left: string; right: string }[] = [];
  const seen = new Set<string>();
  for (const [left, neighbors] of graph.adj) {
    for (const right of neighbors.keys()) {
      const key = edgeKey(left, right);
      if (seen.has(key) || drop.has(key)) continue;
      seen.add(key);
      edges.push({ left, right });
    }
  }
  for (const { left, right } of edges) {
    const direct = geoDist(left, right, geo);
    if (direct === null || direct < 1e-9) continue;
    const blocked = edgeKey(left, right);
    const queue: { crs: string; hops: number; pathKm: number }[] = [{ crs: left, hops: 0, pathKm: 0 }];
    const best = new Map<string, { hops: number; pathKm: number }>([[left, { hops: 0, pathKm: 0 }]]);
    let found = false;
    while (queue.length > 0) {
      const cur = queue.shift()!;
      if (cur.crs === right && cur.hops >= 2) {
        if (cur.pathKm <= direct * slack) {
          found = true;
          break;
        }
        continue;
      }
      if (cur.hops >= maxHops) continue;
      for (const next of graph.adj.get(cur.crs)?.keys() ?? []) {
        const hop = edgeKey(cur.crs, next);
        if (hop === blocked) continue;
        const hopKm = geoDist(cur.crs, next, geo);
        if (hopKm === null) continue;
        const pathKm = cur.pathKm + hopKm;
        if (pathKm > direct * slack) continue;
        const hops = cur.hops + 1;
        const prev = best.get(next);
        if (prev && prev.pathKm <= pathKm && prev.hops <= hops) continue;
        best.set(next, { hops, pathKm });
        queue.push({ crs: next, hops, pathKm });
      }
    }
    if (found) drop.add(blocked);
  }
}

/**
 * Put back dropped hops that would split the hop graph into more components
 * than the full adjacency (e.g. WOS–DTW / WOF–DTW both marked as skip chords).
 * Prefers heavier edges when several candidates reconnect the same cut.
 */
function restoreConnectivityDrops(graph: Graph, drop: Set<string>): void {
  if (drop.size === 0) return;

  const parent = new Map<string, string>();
  const find = (crs: string): string => {
    const current = parent.get(crs) ?? crs;
    if (current === crs) return crs;
    const root = find(current);
    parent.set(crs, root);
    return root;
  };
  const unite = (left: string, right: string): boolean => {
    const a = find(left);
    const b = find(right);
    if (a === b) return false;
    parent.set(a, b);
    return true;
  };

  for (const crs of graph.adj.keys()) parent.set(crs, crs);

  const kept: { left: string; right: string; weight: number }[] = [];
  const candidates: { left: string; right: string; weight: number }[] = [];
  const seen = new Set<string>();
  for (const [left, neighbors] of graph.adj) {
    for (const [right, weight] of neighbors) {
      const key = edgeKey(left, right);
      if (seen.has(key)) continue;
      seen.add(key);
      const edge = {
        left: left < right ? left : right,
        right: left < right ? right : left,
        weight,
      };
      if (drop.has(key)) candidates.push(edge);
      else kept.push(edge);
    }
  }

  for (const edge of kept) unite(edge.left, edge.right);

  candidates.sort((a, b) => {
    if (b.weight !== a.weight) return b.weight - a.weight;
    return edgeKey(a.left, a.right) < edgeKey(b.left, b.right) ? -1 : 1;
  });
  for (const edge of candidates) {
    if (unite(edge.left, edge.right)) drop.delete(edgeKey(edge.left, edge.right));
  }
}

function bridgedHops(graph: Graph, geo?: GeoIndex): Set<string> {
  const drop = new Set<string>();
  for (const stops of graph.stopLists) markSkippedPairs(graph, stops, drop);
  if (geo && geo.size > 0) markGeoShortcuts(graph, geo, drop);
  const groups = new Map<string, Walk[]>();
  for (const walk of walksBetweenJunctions(graph, drop)) {
    const key = `${walk.from}|${walk.to}`;
    const list = groups.get(key) ?? [];
    list.push(walk);
    groups.set(key, list);
  }
  for (const walks of groups.values()) dropSubsetWalks(walks, drop);
  restoreConnectivityDrops(graph, drop);
  return drop;
}

function reducedEdges(graph: Graph, geo?: GeoIndex): Edge[] {
  const drop = bridgedHops(graph, geo);
  const edges: Edge[] = [];
  const seen = new Set<string>();
  for (const [left, neighbors] of graph.adj) {
    for (const [right, weight] of neighbors) {
      const key = edgeKey(left, right);
      if (seen.has(key) || drop.has(key)) continue;
      seen.add(key);
      edges.push({
        from: left < right ? left : right,
        to: left < right ? right : left,
        weight,
      });
    }
  }
  return edges;
}

function maximumSpanningTree(edges: readonly Edge[]): Edge[] {
  const parent = new Map<string, string>();
  const find = (crs: string): string => {
    const current = parent.get(crs) ?? crs;
    if (current === crs) return crs;
    const root = find(current);
    parent.set(crs, root);
    return root;
  };
  for (const edge of edges) {
    parent.set(edge.from, edge.from);
    parent.set(edge.to, edge.to);
  }
  const sorted = [...edges].sort((a, b) => {
    if (b.weight !== a.weight) return b.weight - a.weight;
    return edgeKey(a.from, a.to) < edgeKey(b.from, b.to) ? -1 : 1;
  });
  const tree: Edge[] = [];
  for (const edge of sorted) {
    const left = find(edge.from);
    const right = find(edge.to);
    if (left === right) continue;
    parent.set(left, right);
    tree.push(edge);
  }
  return tree;
}

function treeLinks(tree: readonly Edge[]): Map<string, string[]> {
  const links = new Map<string, string[]>();
  const push = (from: string, to: string) => {
    const list = links.get(from) ?? [];
    list.push(to);
    links.set(from, list);
  };
  for (const edge of tree) {
    push(edge.from, edge.to);
    push(edge.to, edge.from);
  }
  for (const neighbors of links.values()) neighbors.sort();
  return links;
}

function components(members: readonly string[], links: Map<string, string[]>): string[][] {
  const allowed = new Set(members);
  const seen = new Set<string>();
  const out: string[][] = [];
  for (const start of [...members].sort()) {
    if (seen.has(start)) continue;
    const comp: string[] = [];
    const queue = [start];
    seen.add(start);
    while (queue.length > 0) {
      const crs = queue.shift()!;
      comp.push(crs);
      for (const neighbor of links.get(crs) ?? []) {
        if (seen.has(neighbor) || !allowed.has(neighbor)) continue;
        seen.add(neighbor);
        queue.push(neighbor);
      }
    }
    out.push(comp);
  }
  return out;
}

function farthest(links: Map<string, string[]>, start: string, members: readonly string[]): string {
  const dist = new Map<string, number>([[start, 0]]);
  const queue = [start];
  while (queue.length > 0) {
    const crs = queue.shift()!;
    for (const neighbor of links.get(crs) ?? []) {
      if (dist.has(neighbor)) continue;
      dist.set(neighbor, (dist.get(crs) ?? 0) + 1);
      queue.push(neighbor);
    }
  }
  let best = start;
  let bestDist = 0;
  for (const crs of members) {
    const distance = dist.get(crs) ?? -1;
    if (distance > bestDist || (distance === bestDist && crs < best)) {
      best = crs;
      bestDist = distance;
    }
  }
  return best;
}

function pathBetween(links: Map<string, string[]>, start: string, end: string): string[] {
  if (start === end) return [start];
  const prev = new Map<string, string | null>([[start, null]]);
  const queue = [start];
  while (queue.length > 0) {
    const crs = queue.shift()!;
    for (const neighbor of links.get(crs) ?? []) {
      if (prev.has(neighbor)) continue;
      prev.set(neighbor, crs);
      queue.push(neighbor);
    }
  }
  if (!prev.has(end)) return [start];
  const path: string[] = [];
  let cur: string | null = end;
  while (cur) {
    path.push(cur);
    cur = prev.get(cur) ?? null;
  }
  path.reverse();
  return path;
}

function scoreEnd(crs: string, origin: Map<string, number>, dest: Map<string, number>): number {
  return (dest.get(crs) ?? 0) - (origin.get(crs) ?? 0);
}

function lowerLeftEnd(
  spine: readonly string[],
  origin: Map<string, number>,
  dest: Map<string, number>,
): string {
  const left = spine[0]!;
  const right = spine[spine.length - 1]!;
  if (left === "EUS" || right === "EUS") return "EUS";
  const leftScore = scoreEnd(left, origin, dest);
  const rightScore = scoreEnd(right, origin, dest);
  if (leftScore === rightScore) return left < right ? left : right;
  return leftScore > rightScore ? left : right;
}

function ranksFrom(links: Map<string, string[]>, root: string): Map<string, number> {
  const rank = new Map<string, number>([[root, 0]]);
  const queue = [root];
  while (queue.length > 0) {
    const crs = queue.shift()!;
    for (const neighbor of links.get(crs) ?? []) {
      if (rank.has(neighbor)) continue;
      rank.set(neighbor, (rank.get(crs) ?? 0) + 1);
      queue.push(neighbor);
    }
  }
  return rank;
}

function branchRoots(links: Map<string, string[]>, spine: readonly string[]): string[] {
  const onSpine = new Set(spine);
  const roots: string[] = [];
  for (let i = 0; i < spine.length; i++) {
    const crs = spine[i]!;
    const parent = i === 0 ? null : spine[i - 1];
    const children = (links.get(crs) ?? []).filter((next) => next !== parent && !onSpine.has(next));
    roots.push(...children);
  }
  return roots;
}

function laneForBranch(index: number): number {
  const step = Math.floor(index / 2) + 1;
  return index % 2 === 0 ? step : -step;
}

function lanesFor(links: Map<string, string[]>, spine: readonly string[]): Map<string, number> {
  const lane = new Map<string, number>();
  for (const crs of spine) lane.set(crs, 0);
  const roots = branchRoots(links, spine);
  for (let index = 0; index < roots.length; index++) {
    const branchLane = laneForBranch(index);
    const stack = [roots[index]!];
    while (stack.length > 0) {
      const crs = stack.pop()!;
      if (lane.has(crs)) continue;
      lane.set(crs, branchLane);
      for (const neighbor of links.get(crs) ?? []) {
        if (!lane.has(neighbor)) stack.push(neighbor);
      }
    }
  }
  return lane;
}

function rawPoint(rank: number, lane: number): { x: number; y: number } {
  return {
    x: (rank * RANK_STEP + lane * LANE_STEP) * FLOW_X,
    y: rank * RANK_STEP * FLOW_Y + lane * LANE_STEP * PERP_Y,
  };
}

function placeComponent(
  members: readonly string[],
  links: Map<string, string[]>,
  origin: Map<string, number>,
  dest: Map<string, number>,
  laneShift: number,
): StationLayout[] {
  const start = [...members].sort()[0]!;
  const far = farthest(links, start, members);
  const other = farthest(links, far, members);
  let spine = pathBetween(links, far, other);
  const pole = lowerLeftEnd(spine, origin, dest);
  if (spine[0] !== pole) spine = [...spine].reverse();
  const rank = ranksFrom(links, spine[0]!);
  const lane = lanesFor(links, spine);
  return members.map((crs) => {
    const stationRank = rank.get(crs) ?? 0;
    const stationLane = lane.get(crs) ?? 0;
    const point = rawPoint(stationRank, stationLane + laneShift);
    return { crs, rank: stationRank, lane: stationLane, x: point.x, y: point.y };
  });
}

function placeStations(
  members: readonly string[],
  links: Map<string, string[]>,
  origin: Map<string, number>,
  dest: Map<string, number>,
): StationLayout[] {
  const placed: StationLayout[] = [];
  let laneShift = 0;
  for (const comp of components(members, links)) {
    const stations = placeComponent(comp, links, origin, dest, laneShift);
    placed.push(...stations);
    const lanes = stations.map((station) => station.lane);
    laneShift += Math.max(...lanes) - Math.min(...lanes) + 3;
  }
  return placed;
}

function colorForLane(lane: number): string {
  if (lane === 0) return SPINE_COLOR;
  const index = lane > 0 ? (lane - 1) * 2 : -lane * 2 - 1;
  return BRANCH_COLORS[index % BRANCH_COLORS.length] ?? SPINE_COLOR;
}

function strokeColor(fromLane: number, toLane: number): string {
  if (fromLane === 0 && toLane === 0) return SPINE_COLOR;
  return colorForLane(fromLane !== 0 ? fromLane : toLane);
}

const CURVE_STEPS = 14;

function mix(from: number, to: number, t: number): number {
  return from + (to - from) * t;
}

function quadPoint(
  start: { x: number; y: number },
  control: { x: number; y: number },
  end: { x: number; y: number },
  t: number,
): { x: number; y: number } {
  const u = 1 - t;
  return {
    x: u * u * start.x + 2 * u * t * control.x + t * t * end.x,
    y: u * u * start.y + 2 * u * t * control.y + t * t * end.y,
  };
}

function flowPoints(from: StationLayout, to: StationLayout): { x: number; y: number }[] {
  if (from.rank === to.rank || from.lane === to.lane) {
    return [
      { x: from.x, y: from.y },
      { x: to.x, y: to.y },
    ];
  }
  const corner = {
    x: from.x + (to.rank - from.rank) * RANK_STEP * FLOW_X,
    y: from.y + (to.rank - from.rank) * RANK_STEP * FLOW_Y,
  };
  const legIn = Math.hypot(corner.x - from.x, corner.y - from.y);
  const legOut = Math.hypot(to.x - corner.x, to.y - corner.y);
  const radius = Math.min(legIn, legOut) * 0.92;
  if (legIn < 1 || legOut < 1 || radius < 1) {
    return [
      { x: from.x, y: from.y },
      { x: to.x, y: to.y },
    ];
  }
  const start = {
    x: mix(corner.x, from.x, radius / legIn),
    y: mix(corner.y, from.y, radius / legIn),
  };
  const end = {
    x: mix(corner.x, to.x, radius / legOut),
    y: mix(corner.y, to.y, radius / legOut),
  };
  const points = [{ x: from.x, y: from.y }, start];
  for (let step = 1; step < CURVE_STEPS; step++) {
    points.push(quadPoint(start, corner, end, step / CURVE_STEPS));
  }
  points.push(end, { x: to.x, y: to.y });
  return points;
}

function paintCorridor(from: StationLayout, to: StationLayout): CorridorLayout {
  const points = flowPoints(from, to);
  const color = strokeColor(from.lane, to.lane);
  if (from.crs < to.crs) return { from: from.crs, to: to.crs, via: [], points, color };
  return { from: to.crs, to: from.crs, via: [], points: [...points].reverse(), color };
}

function paintCorridorGeo(from: StationLayout, to: StationLayout): CorridorLayout {
  const points = [
    { x: from.x, y: from.y },
    { x: to.x, y: to.y },
  ];
  const color = strokeColor(from.lane, to.lane);
  if (from.crs < to.crs) return { from: from.crs, to: to.crs, via: [], points, color };
  return { from: to.crs, to: from.crs, via: [], points: [...points].reverse(), color };
}

type Box = { left: number; top: number; right: number; bottom: number };

function makeProjection(
  points: readonly { lat: number; lon: number }[],
  scalePxPerDeg: number,
): Proj {
  const lat0 = points.reduce((sum, point) => sum + point.lat, 0) / points.length;
  const lon0 = points.reduce((sum, point) => sum + point.lon, 0) / points.length;
  const cos = Math.cos((lat0 * Math.PI) / 180);
  return {
    toXY(lat, lon) {
      return {
        x: (lon - lon0) * cos * scalePxPerDeg,
        y: (lat0 - lat) * scalePxPerDeg,
      };
    },
  };
}

function badgeOffset(lane: number): { x: number; y: number } {
  const sign = lane === 0 ? -1 : Math.sign(lane) || -1;
  return { x: PERP_X * sign * BADGE_OFFSET_DIST, y: PERP_Y * sign * BADGE_OFFSET_DIST };
}

function badgeBox(station: StationLayout): Box {
  const offset = badgeOffset(station.lane);
  const cx = station.x + offset.x;
  const cy = station.y + offset.y;
  return {
    left: cx - BADGE_HALF_W,
    top: cy - BADGE_HALF_H,
    right: cx + BADGE_HALF_W,
    bottom: cy + BADGE_HALF_H,
  };
}

function boxesOverlap(left: Box, right: Box): boolean {
  return !(left.right <= right.left || right.right <= left.left || left.bottom <= right.top || right.bottom <= left.top);
}

function dotsCollide(left: StationLayout, right: StationLayout): boolean {
  return Math.hypot(right.x - left.x, right.y - left.y) < DOT_MIN_DIST;
}

function stationsCollide(left: StationLayout, right: StationLayout): boolean {
  return dotsCollide(left, right) || boxesOverlap(badgeBox(left), badgeBox(right));
}

function collisionScore(stations: readonly StationLayout[]): number {
  let score = 0;
  for (let i = 0; i < stations.length; i++) {
    for (let j = i + 1; j < stations.length; j++) {
      const left = stations[i]!;
      const right = stations[j]!;
      if (dotsCollide(left, right)) {
        score += DOT_MIN_DIST - Math.hypot(right.x - left.x, right.y - left.y);
      }
      if (boxesOverlap(badgeBox(left), badgeBox(right))) score += BADGE_HALF_W;
    }
  }
  return score;
}

function neighbourAverageDir(
  station: StationLayout,
  byCrs: ReadonlyMap<string, StationLayout>,
  adj: Map<string, Map<string, number>>,
): { x: number; y: number } {
  let sx = 0;
  let sy = 0;
  for (const neighbor of adj.get(station.crs)?.keys() ?? []) {
    const other = byCrs.get(neighbor);
    if (!other) continue;
    sx += other.x - station.x;
    sy += other.y - station.y;
  }
  const len = Math.hypot(sx, sy);
  if (len < 1e-6) return { x: 1, y: 0 };
  return { x: sx / len, y: sy / len };
}

function degreeOf(crs: string, adj: Map<string, Map<string, number>>): number {
  return adj.get(crs)?.size ?? 0;
}

function preferMove(
  left: StationLayout,
  right: StationLayout,
  adj: Map<string, Map<string, number>>,
): StationLayout {
  const leftDeg = degreeOf(left.crs, adj);
  const rightDeg = degreeOf(right.crs, adj);
  if (leftDeg !== rightDeg) return leftDeg < rightDeg ? left : right;
  return left.crs > right.crs ? left : right;
}

function northUpPlace(
  meta: readonly StationLayout[],
  adj: Map<string, Map<string, number>>,
  geo: GeoIndex,
): { stations: StationLayout[]; applied: boolean } {
  const withGeo = meta.flatMap((station) => {
    const point = geo.get(station.crs);
    return point ? [{ crs: station.crs, lat: point.lat, lon: point.lon }] : [];
  });
  if (withGeo.length === 0) return { stations: [...meta], applied: false };

  const proj = makeProjection(withGeo, GEO_SCALE_PX_PER_DEG);
  const byCrs = new Map(meta.map((station) => [station.crs, { ...station }]));
  const placed = new Set<string>();

  for (const station of meta) {
    const point = geo.get(station.crs);
    if (!point) continue;
    const xy = proj.toXY(point.lat, point.lon);
    const current = byCrs.get(station.crs)!;
    byCrs.set(station.crs, { ...current, x: xy.x, y: xy.y });
    placed.add(station.crs);
  }

  let pending = meta.filter((station) => !placed.has(station.crs)).map((station) => station.crs);
  let guard = pending.length + 3;
  while (pending.length > 0 && guard-- > 0) {
    const still: string[] = [];
    let progressed = false;
    for (const crs of pending) {
      const neighbors = [...(adj.get(crs)?.keys() ?? [])].filter((neighbor) => placed.has(neighbor));
      if (neighbors.length === 0) {
        still.push(crs);
        continue;
      }
      const x = neighbors.reduce((sum, neighbor) => sum + byCrs.get(neighbor)!.x, 0) / neighbors.length;
      const y = neighbors.reduce((sum, neighbor) => sum + byCrs.get(neighbor)!.y, 0) / neighbors.length;
      const current = byCrs.get(crs)!;
      byCrs.set(crs, { ...current, x, y });
      placed.add(crs);
      progressed = true;
    }
    if (!progressed) {
      const anchored = [...placed].map((crs) => byCrs.get(crs)!);
      const cx = anchored.reduce((sum, station) => sum + station.x, 0) / Math.max(1, anchored.length);
      const cy = anchored.reduce((sum, station) => sum + station.y, 0) / Math.max(1, anchored.length);
      for (const crs of still) {
        const current = byCrs.get(crs)!;
        byCrs.set(crs, { ...current, x: cx, y: cy });
        placed.add(crs);
      }
      break;
    }
    pending = still;
  }

  return { stations: [...byCrs.values()], applied: true };
}

function resolveCollisions(
  stations: readonly StationLayout[],
  adj: Map<string, Map<string, number>>,
): StationLayout[] {
  const out = stations.map((station) => ({ ...station }));
  const byCrs = () => new Map(out.map((station) => [station.crs, station]));

  // Phase 1: expand short graph hops along the edge so corridor order stays geographic.
  for (let step = 0; step < NUDGE_ITERATIONS; step++) {
    let moved = false;
    const seen = new Set<string>();
    for (const [leftCrs, neighbors] of adj) {
      for (const rightCrs of neighbors.keys()) {
        const key = edgeKey(leftCrs, rightCrs);
        if (seen.has(key)) continue;
        seen.add(key);
        const left = byCrs().get(leftCrs);
        const right = byCrs().get(rightCrs);
        if (!left || !right) continue;
        const dx = right.x - left.x;
        const dy = right.y - left.y;
        const dist = Math.hypot(dx, dy) || 0.01;
        if (dist >= DOT_MIN_DIST) continue;
        const push = (DOT_MIN_DIST - dist) / 2;
        const ux = dx / dist;
        const uy = dy / dist;
        left.x -= ux * push;
        left.y -= uy * push;
        right.x += ux * push;
        right.y += uy * push;
        moved = true;
      }
    }
    if (!moved) break;
  }

  // Phase 2: clear badge overlaps with a perpendicular nudge (preserves along-track place).
  for (let step = 0; step < NUDGE_ITERATIONS; step++) {
    let moved = false;
    for (let i = 0; i < out.length; i++) {
      for (let j = i + 1; j < out.length; j++) {
        const left = out[i]!;
        const right = out[j]!;
        if (!boxesOverlap(badgeBox(left), badgeBox(right))) continue;
        const primary = preferMove(left, right, adj);
        const along = neighbourAverageDir(primary, byCrs(), adj);
        // Perpendicular to the local corridor / neighbour average.
        const dir = { x: -along.y, y: along.x };
        const before = collisionScore([left, right]);
        const ox = primary.x;
        const oy = primary.y;
        primary.x += dir.x * NUDGE_STEP;
        primary.y += dir.y * NUDGE_STEP;
        if (collisionScore([left, right]) < before) {
          moved = true;
          continue;
        }
        primary.x = ox - dir.x * NUDGE_STEP;
        primary.y = oy - dir.y * NUDGE_STEP;
        if (collisionScore([left, right]) < before) {
          moved = true;
          continue;
        }
        primary.x = ox;
        primary.y = oy;
      }
    }
    if (!moved) break;
  }
  return out;
}

function emptyDiagram(): DiagramBody {
  return { stations: [], corridors: [], lines: [], width: PAD * 2, height: PAD * 2 };
}

/**
 * Ordered line paths from the same reduced journey graph the layout uses.
 * Each entry is a junction-to-junction walk (terminals count as junctions).
 */
export function linePathsFromJourneys(
  journeys: readonly SchematicJourney[],
  options: LayoutOptions = {},
): SchematicLine[] {
  const graph = buildGraph(journeys);
  if (graph.adj.size === 0) return [];
  const geo = options.geo && options.geo.size > 0 ? options.geo : undefined;
  const drop = bridgedHops(graph, geo);
  const walks = walksBetweenJunctions(graph, drop);
  const lines: SchematicLine[] = [];
  const seen = new Set<string>();
  for (const walk of walks) {
    const stations = [walk.from, ...walk.via, walk.to];
    if (stations.length < 2) continue;
    const id = stations.join("|");
    if (seen.has(id)) continue;
    seen.add(id);
    lines.push({ id, stations });
  }
  return lines.sort((a, b) => (a.id < b.id ? -1 : 1));
}

export function withSchematicLines(
  body: Omit<DiagramBody, "lines"> & { lines?: SchematicLine[] },
  lines: SchematicLine[],
): DiagramBody {
  return {
    stations: body.stations,
    corridors: body.corridors,
    width: body.width,
    height: body.height,
    lines,
  };
}

function edgeDegrees(edges: readonly Edge[]): Map<string, number> {
  const degree = new Map<string, number>();
  for (const edge of edges) {
    degree.set(edge.from, (degree.get(edge.from) ?? 0) + 1);
    degree.set(edge.to, (degree.get(edge.to) ?? 0) + 1);
  }
  return degree;
}

function assertOctilinearDegree(edges: readonly Edge[]): void {
  for (const [crs, degree] of edgeDegrees(edges)) {
    if (degree > 8) throw new LayoutDegreeError(crs, degree);
  }
}

function lineHopsFromStopLists(
  stopLists: readonly string[][],
  edgeKeys: ReadonlySet<string>,
): LayoutInputLineHop[] {
  const hops: LayoutInputLineHop[] = [];
  const seenPaths = new Set<string>();
  let lineIndex = 0;
  for (const stops of stopLists) {
    const pathKey = stops.join("|");
    if (seenPaths.has(pathKey)) continue;
    seenPaths.add(pathKey);
    const linename = `L${lineIndex++}`;
    for (let i = 0; i < stops.length - 1; i++) {
      const from = stops[i]!;
      const to = stops[i + 1]!;
      if (!edgeKeys.has(edgeKey(from, to))) continue;
      hops.push({ linename, from, to });
    }
  }
  return hops;
}

/**
 * Topology + geographic seed for the PuLP+HiGHS octilinear MILP.
 * Positions come from north-up geo projection without collision nudging.
 */
export function buildLayoutInput(
  journeys: readonly SchematicJourney[],
  options: LayoutOptions = {},
): LayoutInput {
  const graph = buildGraph(journeys);
  if (graph.adj.size === 0) return { nodes: [], edges: [], lines: [] };
  const geo = options.geo && options.geo.size > 0 ? options.geo : undefined;
  const edges = reducedEdges(graph, geo);
  assertOctilinearDegree(edges);
  const links = treeLinks(maximumSpanningTree(edges));
  const meta = placeStations([...graph.adj.keys()], links, graph.originCount, graph.destCount);
  let placed = meta;
  if (geo) {
    const projected = northUpPlace(meta, graph.adj, geo);
    if (projected.applied) placed = projected.stations;
  }
  const byCrs = new Map(placed.map((station) => [station.crs, station]));
  const edgeKeys = new Set(edges.map((edge) => edgeKey(edge.from, edge.to)));
  return {
    nodes: [...byCrs.values()]
      .map((station) => ({
        crs: station.crs,
        x: station.x,
        y: station.y,
        rank: station.rank,
        lane: station.lane,
      }))
      .sort((a, b) => (a.crs < b.crs ? -1 : 1)),
    edges: edges
      .map((edge) => ({ from: edge.from, to: edge.to, weight: edge.weight }))
      .sort((a, b) => (edgeKey(a.from, a.to) < edgeKey(b.from, b.to) ? -1 : 1)),
    lines: lineHopsFromStopLists(graph.stopLists, edgeKeys),
  };
}

export type OctStationCoord = {
  crs: string;
  x: number;
  y: number;
  rank: number;
  lane: number;
};

/** Build a DiagramBody from MILP (or fixture) octilinear station coordinates. */
export function diagramFromOctCoords(
  stations: readonly OctStationCoord[],
  edges: readonly { from: string; to: string }[],
  scalePx = OCT_CELL_PX,
): DiagramBody {
  const scaled: StationLayout[] = stations.map((station) => ({
    crs: station.crs,
    x: station.x * scalePx,
    y: station.y * scalePx,
    rank: station.rank,
    lane: station.lane,
  }));
  const byCrs = new Map(scaled.map((station) => [station.crs, station]));
  const corridors: CorridorLayout[] = [];
  for (const edge of edges) {
    const from = byCrs.get(edge.from);
    const to = byCrs.get(edge.to);
    if (!from || !to) continue;
    corridors.push(paintCorridorGeo(from, to));
  }
  return fit(scaled, corridors);
}

function fit(stations: StationLayout[], corridors: CorridorLayout[]): DiagramBody {
  const xs = [
    ...stations.map((station) => station.x),
    ...corridors.flatMap((corridor) => corridor.points.map((point) => point.x)),
  ];
  const ys = [
    ...stations.map((station) => station.y),
    ...corridors.flatMap((corridor) => corridor.points.map((point) => point.y)),
  ];
  if (xs.length === 0 || ys.length === 0) return emptyDiagram();
  const minX = Math.min(...xs);
  const minY = Math.min(...ys);
  const maxX = Math.max(...xs);
  const maxY = Math.max(...ys);
  const shiftX = PAD - minX;
  const shiftY = PAD - minY;
  return {
    stations: stations
      .map((station) => ({ ...station, x: station.x + shiftX, y: station.y + shiftY }))
      .sort((a, b) => (a.crs < b.crs ? -1 : 1)),
    corridors: corridors
      .map((corridor) => ({
        ...corridor,
        points: corridor.points.map((point) => ({ x: point.x + shiftX, y: point.y + shiftY })),
      }))
      .sort((a, b) => (routeName(a) < routeName(b) ? -1 : 1)),
    lines: [],
    width: maxX - minX + PAD * 2 + 28,
    height: maxY - minY + PAD * 2 + 8,
  };
}

export function layoutDiagram(
  journeys: readonly SchematicJourney[],
  options: LayoutOptions = {},
): DiagramBody {
  const graph = buildGraph(journeys);
  if (graph.adj.size === 0) return emptyDiagram();
  const geo = options.geo && options.geo.size > 0 ? options.geo : undefined;
  const edges = reducedEdges(graph, geo);
  const links = treeLinks(maximumSpanningTree(edges));
  const meta = placeStations([...graph.adj.keys()], links, graph.originCount, graph.destCount);
  let placed = meta;
  let useGeo = false;
  if (geo) {
    const projected = northUpPlace(meta, graph.adj, geo);
    placed = projected.stations;
    useGeo = projected.applied;
    if (useGeo) placed = resolveCollisions(placed, graph.adj);
  }
  const byCrs = new Map(placed.map((station) => [station.crs, station]));
  const corridors: CorridorLayout[] = [];
  const paint = useGeo ? paintCorridorGeo : paintCorridor;
  for (const edge of edges) {
    const from = byCrs.get(edge.from);
    const to = byCrs.get(edge.to);
    if (!from || !to) continue;
    corridors.push(paint(from, to));
  }
  return withSchematicLines(fit(placed, corridors), linePathsFromJourneys(journeys, options));
}
