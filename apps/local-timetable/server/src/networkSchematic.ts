const RANK_STEP = 72;
const LANE_STEP = 48;
const PAD = 36;
const FLOW_X = Math.SQRT1_2;
const FLOW_Y = -Math.SQRT1_2;
const PERP_X = Math.SQRT1_2;
const PERP_Y = Math.SQRT1_2;
const SPINE_COLOR = "#7dffa8";
const BRANCH_COLORS = ["#f0b45a", "#e07a3d", "#3dbebf", "#6aa0ff"] as const;

export type SchematicStop = { crs?: string | null };

export type SchematicJourney = { stops: readonly SchematicStop[] };

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

export type DiagramBody = {
  stations: StationLayout[];
  corridors: CorridorLayout[];
  width: number;
  height: number;
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
  for (let i = 0; i < stops.length; i++) {
    for (let j = i + 2; j < stops.length; j++) {
      const left = stops[i]!;
      const right = stops[j]!;
      if (left === right || !graph.adj.get(left)?.has(right)) continue;
      drop.add(edgeKey(left, right));
    }
  }
}

function bridgedHops(graph: Graph): Set<string> {
  const drop = new Set<string>();
  for (const stops of graph.stopLists) markSkippedPairs(graph, stops, drop);
  const groups = new Map<string, Walk[]>();
  for (const walk of walksBetweenJunctions(graph, drop)) {
    const key = `${walk.from}|${walk.to}`;
    const list = groups.get(key) ?? [];
    list.push(walk);
    groups.set(key, list);
  }
  for (const walks of groups.values()) dropSubsetWalks(walks, drop);
  return drop;
}

function reducedEdges(graph: Graph): Edge[] {
  const drop = bridgedHops(graph);
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

function emptyDiagram(): DiagramBody {
  return { stations: [], corridors: [], width: PAD * 2, height: PAD * 2 };
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
    width: maxX - minX + PAD * 2 + 28,
    height: maxY - minY + PAD * 2 + 8,
  };
}

export function layoutDiagram(journeys: readonly SchematicJourney[]): DiagramBody {
  const graph = buildGraph(journeys);
  if (graph.adj.size === 0) return emptyDiagram();
  const edges = reducedEdges(graph);
  const tree = maximumSpanningTree(edges);
  const placed = placeStations([...graph.adj.keys()], treeLinks(tree), graph.originCount, graph.destCount);
  const byCrs = new Map(placed.map((station) => [station.crs, station]));
  const corridors: CorridorLayout[] = [];
  for (const edge of edges) {
    const from = byCrs.get(edge.from);
    const to = byCrs.get(edge.to);
    if (!from || !to) continue;
    corridors.push(paintCorridor(from, to));
  }
  return fit(placed, corridors);
}
