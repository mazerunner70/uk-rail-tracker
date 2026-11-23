import { WINDOW_RADIUS } from "./routeStrip.js";
import type { NetworkTrain } from "./types.js";

export type SideBranch = {
  junctionCrs: string;
  stations: string[];
};

export type ForkCandidate = {
  atCrs: string;
  nextCrs: string;
};

export type MergeSpineResult = {
  spine: string[];
  forks: ForkCandidate[];
};

export type MergedStationWindow = {
  window: string[];
  spine: string[];
  sideBranches: SideBranch[];
};

const START = "__S__";

/** Ordered public CRS codes from a train's stops. */
export function callingCrsList(
  train: { stops?: readonly { crs?: string }[] },
): string[] {
  const out: string[] = [];
  for (const stop of train.stops ?? []) {
    const crs = stop.crs?.trim().toUpperCase();
    if (crs) out.push(crs);
  }
  return out;
}

/** Calling-stop lists for trains that include S. */
export function journeysThroughStation(
  trains: readonly NetworkTrain[],
  selectedCrs: string,
): string[][] {
  const S = selectedCrs.trim().toUpperCase();
  if (!S) return [];
  const out: string[][] = [];
  for (const train of trains) {
    const list = callingCrsList(train);
    if (list.includes(S)) out.push(list);
  }
  return out;
}

/** Undirected adjacency from consecutive calling stops. */
export function adjacentGraph(
  journeys: readonly (readonly string[])[],
): Map<string, Set<string>> {
  const graph = new Map<string, Set<string>>();
  const link = (a: string, b: string) => {
    if (a === b) return;
    const left = graph.get(a) ?? new Set<string>();
    left.add(b);
    graph.set(a, left);
    const right = graph.get(b) ?? new Set<string>();
    right.add(a);
    graph.set(b, right);
  };
  for (const journey of journeys) {
    for (let i = 0; i < journey.length - 1; i++) {
      link(journey[i]!, journey[i + 1]!);
    }
  }
  return graph;
}

function bumpSucc(
  succ: Map<string, Map<string, number>>,
  from: string,
  to: string,
): void {
  const row = succ.get(from) ?? new Map<string, number>();
  row.set(to, (row.get(to) ?? 0) + 1);
  succ.set(from, row);
}

function reaches(
  succ: Map<string, Map<string, number>>,
  from: string,
  target: string,
  seen = new Set<string>(),
): boolean {
  if (from === target) return true;
  if (seen.has(from)) return false;
  seen.add(from);
  for (const next of succ.get(from)?.keys() ?? []) {
    if (reaches(succ, next, target, seen)) return true;
  }
  return false;
}

/** Successors of cur that are not reachable via another successor (closest forks). */
function immediateSuccessors(
  succ: Map<string, Map<string, number>>,
  cur: string,
): string[] {
  const raw = [...(succ.get(cur)?.keys() ?? [])];
  return raw.filter(
    (crs) => !raw.some((other) => other !== crs && reaches(succ, other, crs)),
  );
}

function pickPrimary(
  succ: Map<string, Map<string, number>>,
  cur: string,
  candidates: readonly string[],
): string {
  const counts = succ.get(cur) ?? new Map<string, number>();
  const sorted = [...candidates].sort((a, b) => {
    const diff = (counts.get(b) ?? 0) - (counts.get(a) ?? 0);
    if (diff !== 0) return diff;
    return a < b ? -1 : a > b ? 1 : 0;
  });
  return sorted[0]!;
}

/**
 * Merge sequences ordered away from S into one chain.
 * On incomparable forks, keep majority/lex primary; record others as forks at cur.
 */
export function mergeAwayFromS(
  sides: readonly (readonly string[])[],
): { chain: string[]; forks: ForkCandidate[] } {
  const succ = new Map<string, Map<string, number>>();
  for (const side of sides) {
    if (side.length === 0) continue;
    bumpSucc(succ, START, side[0]!);
    for (let i = 0; i < side.length - 1; i++) {
      bumpSucc(succ, side[i]!, side[i + 1]!);
    }
  }
  const chain: string[] = [];
  const forks: ForkCandidate[] = [];
  const seen = new Set<string>();
  let cur = START;
  for (;;) {
    const immediate = immediateSuccessors(succ, cur).filter((crs) => !seen.has(crs));
    if (immediate.length === 0) break;
    const primary = pickPrimary(succ, cur, immediate);
    for (const other of immediate) {
      if (other === primary) continue;
      forks.push({ atCrs: cur, nextCrs: other });
    }
    chain.push(primary);
    seen.add(primary);
    cur = primary;
  }
  return { chain, forks };
}

function splitJourney(
  journey: readonly string[],
  S: string,
): { left: string[]; right: string[] } | null {
  const at = journey.indexOf(S);
  if (at < 0) return null;
  return {
    left: journey.slice(0, at),
    right: journey.slice(at + 1),
  };
}

function bumpVote(votes: Map<string, number>, crs: string | undefined): void {
  if (!crs) return;
  votes.set(crs, (votes.get(crs) ?? 0) + 1);
}

function topVote(votes: Map<string, number>, exclude?: string | null): string | null {
  let best: string | null = null;
  let bestN = -1;
  for (const [crs, n] of votes) {
    if (exclude && crs === exclude) continue;
    if (n > bestN || (n === bestN && best !== null && crs < best) || (n === bestN && best === null)) {
      best = crs;
      bestN = n;
    }
  }
  return best;
}

/**
 * Choose two distinct neighbours of S as spine poles so bidirectional
 * journeys cannot place the same geographic path on both Left and Right.
 *
 * Only when journeys approach S from both temporal sides (before and after).
 * Inventing a second pole from the same side (e.g. skipped-stop insert) would
 * split one geographic approach across Left* and Right* and duplicate CRS.
 */
export function polesAtS(
  journeys: readonly (readonly string[])[],
  S: string,
): { leftPole: string | null; rightPole: string | null } {
  const beforeVotes = new Map<string, number>();
  const afterVotes = new Map<string, number>();
  for (const journey of journeys) {
    const split = splitJourney(journey, S);
    if (!split) continue;
    bumpVote(beforeVotes, split.left[split.left.length - 1]);
    bumpVote(afterVotes, split.right[0]);
  }
  if (beforeVotes.size === 0 || afterVotes.size === 0) {
    return { leftPole: null, rightPole: null };
  }
  // Prefer "after S" as the right pole, "before S" as left — but force distinct.
  let rightPole = topVote(afterVotes);
  let leftPole = topVote(beforeVotes, rightPole);
  if (leftPole && rightPole && leftPole === rightPole) {
    const altRight = topVote(afterVotes, leftPole);
    const altLeft = topVote(beforeVotes, rightPole);
    if (altRight) rightPole = altRight;
    else if (altLeft) leftPole = altLeft;
  }
  if (!leftPole || !rightPole || leftPole === rightPole) {
    return { leftPole: null, rightPole: null };
  }
  return { leftPole, rightPole };
}

/**
 * Spine = reverse(Left*) + [S] + Right* from merging all journeys through S.
 * Orient sides with distinct poles so opposing OD directions do not mirror.
 */
export function mergeSpine(
  journeys: readonly (readonly string[])[],
  selectedCrs: string,
): MergeSpineResult {
  const S = selectedCrs.trim().toUpperCase();
  const { leftPole, rightPole } = polesAtS(journeys, S);
  const usePoles = Boolean(leftPole && rightPole && leftPole !== rightPole);
  const lefts: string[][] = [];
  const rights: string[][] = [];
  for (const journey of journeys) {
    const split = splitJourney(journey, S);
    if (!split) continue;
    const before = split.left[split.left.length - 1] ?? null;
    const after = split.right[0] ?? null;
    if (usePoles) {
      // Paths from S toward leftPole / rightPole (away-from-S sequences).
      if (before === leftPole && split.left.length > 0) {
        lefts.push([...split.left].reverse());
      } else if (after === leftPole && split.right.length > 0) {
        lefts.push([...split.right]);
      }
      if (after === rightPole && split.right.length > 0) {
        rights.push([...split.right]);
      } else if (before === rightPole && split.left.length > 0) {
        rights.push([...split.left].reverse());
      }
    } else {
      // One-sided / single-neighbour: classic merge (preserves insert-skipped stops).
      if (split.left.length > 0) lefts.push([...split.left].reverse());
      if (split.right.length > 0) rights.push([...split.right]);
    }
  }
  let leftMerge = mergeAwayFromS(lefts);
  let rightMerge = mergeAwayFromS(rights);
  // Safety: never allow the same first hop on both sides (mirrored spine).
  if (
    leftMerge.chain[0]
    && rightMerge.chain[0]
    && leftMerge.chain[0] === rightMerge.chain[0]
  ) {
    const banned = leftMerge.chain[0];
    const filteredRights = rights
      .map((side) => (side[0] === banned ? side.slice(1) : side))
      .filter((side) => side.length > 0);
    // Prefer reassigning journeys that headed the banned way onto the other pole.
    rightMerge = mergeAwayFromS(
      filteredRights.length > 0
        ? filteredRights
        : rights.filter((side) => side[0] !== banned),
    );
  }
  const spine = [...leftMerge.chain].reverse().concat(S, rightMerge.chain);

  const forks: ForkCandidate[] = [];
  for (const fork of leftMerge.forks) {
    forks.push({
      atCrs: fork.atCrs === START ? S : fork.atCrs,
      nextCrs: fork.nextCrs,
    });
  }
  for (const fork of rightMerge.forks) {
    forks.push({
      atCrs: fork.atCrs === START ? S : fork.atCrs,
      nextCrs: fork.nextCrs,
    });
  }
  return { spine, forks };
}

export function windowAroundMergedSpine(
  spine: readonly string[],
  selectedCrs: string,
  radius = WINDOW_RADIUS,
): string[] {
  const S = selectedCrs.trim().toUpperCase();
  const at = spine.indexOf(S);
  if (at < 0) return S ? [S] : [];
  const lo = Math.max(0, at - radius);
  const hi = Math.min(spine.length - 1, at + radius);
  return spine.slice(lo, hi + 1);
}

function hopDistanceOnSpine(
  spine: readonly string[],
  fromCrs: string,
  toCrs: string,
): number | null {
  const a = spine.indexOf(fromCrs);
  const b = spine.indexOf(toCrs);
  if (a < 0 || b < 0) return null;
  return Math.abs(a - b);
}

/**
 * Grow a branch from junction via firstStep, away from the spine.
 * Stops when hop budget from S (via junction) is exhausted or next hop is a fork/end.
 */
function growBranch(
  junctionCrs: string,
  firstStep: string,
  graph: Map<string, Set<string>>,
  spineSet: ReadonlySet<string>,
  hopsFromSToJunction: number,
  radius: number,
): string[] {
  const budget = radius - hopsFromSToJunction;
  if (budget <= 0) return [];
  const stations: string[] = [];
  let prev = junctionCrs;
  let cur = firstStep;
  for (let hops = 1; hops <= budget; hops++) {
    if (spineSet.has(cur) && cur !== junctionCrs) break;
    stations.push(cur);
    const neighbours = [...(graph.get(cur) ?? [])].filter((n) => n !== prev);
    // Stop at further multi-way choice or dead end (KDD-003 v1).
    if (neighbours.length !== 1) break;
    prev = cur;
    cur = neighbours[0]!;
  }
  return stations;
}

export function sideBranchesAtWindow(args: {
  spineWindow: readonly string[];
  fullSpine: readonly string[];
  graph: Map<string, Set<string>>;
  forks: readonly ForkCandidate[];
  selectedCrs: string;
  radius?: number;
}): SideBranch[] {
  const radius = args.radius ?? WINDOW_RADIUS;
  const S = args.selectedCrs.trim().toUpperCase();
  const spineSet = new Set(args.fullSpine);
  const windowSet = new Set(args.spineWindow);
  const branches: SideBranch[] = [];
  const seen = new Set<string>();

  const consider = (junctionCrs: string, nextCrs: string) => {
    if (!windowSet.has(junctionCrs)) return;
    if (spineSet.has(nextCrs) && args.fullSpine.includes(nextCrs)) {
      // Skip if next is the spine neighbour of junction
      const ji = args.fullSpine.indexOf(junctionCrs);
      if (ji >= 0) {
        const pred = ji > 0 ? args.fullSpine[ji - 1] : null;
        const succ = ji < args.fullSpine.length - 1 ? args.fullSpine[ji + 1] : null;
        if (nextCrs === pred || nextCrs === succ) return;
      }
    }
    const key = `${junctionCrs}|${nextCrs}`;
    if (seen.has(key)) return;
    seen.add(key);
    const dist = hopDistanceOnSpine(args.fullSpine, S, junctionCrs) ?? 0;
    const stations = growBranch(
      junctionCrs,
      nextCrs,
      args.graph,
      spineSet,
      dist,
      radius,
    );
    if (stations.length === 0) return;
    branches.push({ junctionCrs, stations });
  };

  for (const fork of args.forks) {
    consider(fork.atCrs === START ? S : fork.atCrs, fork.nextCrs);
  }

  // Also catch alternatives present in G but not recorded during merge.
  for (const crs of args.spineWindow) {
    const ji = args.fullSpine.indexOf(crs);
    const pred = ji > 0 ? args.fullSpine[ji - 1]! : null;
    const succ =
      ji >= 0 && ji < args.fullSpine.length - 1 ? args.fullSpine[ji + 1]! : null;
    for (const n of args.graph.get(crs) ?? []) {
      if (n === pred || n === succ) continue;
      consider(crs, n);
    }
  }

  return branches;
}

/** Full KDD-003 station-tap resolution from network trains. */
export function mergedStationWindow(
  trains: readonly NetworkTrain[],
  selectedCrs: string,
  radius = WINDOW_RADIUS,
): MergedStationWindow | null {
  const S = selectedCrs.trim().toUpperCase();
  if (!S) return null;
  const journeys = journeysThroughStation(trains, S);
  if (journeys.length === 0) return null;
  const graph = adjacentGraph(journeys);
  const { spine, forks } = mergeSpine(journeys, S);
  if (spine.length === 0) return null;
  const window = windowAroundMergedSpine(spine, S, radius);
  const sideBranches = sideBranchesAtWindow({
    spineWindow: window,
    fullSpine: spine,
    graph,
    forks,
    selectedCrs: S,
    radius,
  });
  return { window, spine, sideBranches };
}
