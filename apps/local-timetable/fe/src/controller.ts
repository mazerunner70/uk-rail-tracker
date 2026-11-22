import type { ApiClient } from "./apiClient.js";
import type {
  AskResult,
  EventTag,
  LoadProgress,
  NetworkAnswer,
  NetworkDiagram,
  NetworkTrain,
  PairTrain,
  RemovedTrain,
  ServiceDetail,
  StationPairAnswer,
} from "./types.js";
import {
  reachedAndNextCrs,
  scheduledHhmm,
  type StopRow,
} from "./stopTime.js";
import {
  corridorThroughStation,
  spinePath,
  trainEnds,
  windowAroundPlace,
  windowAroundStation,
  windowKeyForPlace,
} from "./routeStrip.js";
import { placeTrain } from "./networkMap.js";

export type ScreenPhase = "empty" | "loaded";
export type ScreenPage = "network" | "pair" | "route";
export type PairFilter = "all" | "on time" | "delayed" | "cancelled";

export type ScheduleFocus =
  | { kind: "station"; crs: string }
  | { kind: "services"; serviceGuids: readonly string[] };

export type RoutePreview = { serviceGuid: string };

export type RouteTrack =
  | { kind: "train"; serviceGuid: string; lastWindowKey: string | null }
  | { kind: "station"; crs: string; lastWindowKey: string | null };

/** @deprecated Use RouteTrack — kept alias for gradual rename in tests. */
export type RouteView = RouteTrack;

export type ScreenViewModel = {
  phase: ScreenPhase;
  page: ScreenPage;
  loadProgress: LoadProgress | null;
  loadSucceeded: boolean;
  intervalSeconds: number;
  commsLost: boolean;
  statusLine: string;
  networkHeld: NetworkAnswer | null;
  cancellationsHeld: Array<RemovedTrain & { stops?: NetworkTrain["stops"] }>;
  diagramHeld: NetworkDiagram | null;
  diagramStale: boolean;
  pairHeld: StationPairAnswer | null;
  pairEarlier: string;
  pairLater: string;
  searchText: string;
  scheduleFocus: ScheduleFocus | null;
  networkEventFilter: EventTag | null;
  routePreview: RoutePreview | null;
  routeTrack: RouteTrack | null;
  serviceDetailHeld: ServiceDetail | null;
  pairFilter: PairFilter;
  emptyMessage: string;
  updatesPerMin: number | null;
};

export type DisruptionSummaryRow = {
  tag: EventTag;
  count: number;
};

/** Display order for non-zero summary rows. */
export const EVENT_TAG_ORDER: readonly EventTag[] = [
  "cancelled",
  "deactivated",
  "delayed",
  "actual_removed",
  "rerouted",
  "associated",
  "alert",
  "arrived",
  "departed",
  "passed_location",
  "pass_forecast",
  "call_forecast",
  "platform_set",
  "platform_suppressed",
  "length_reported",
  "cancel_cleared",
  "formation_changed",
] as const;

const EVENT_TAG_SET = new Set<string>(EVENT_TAG_ORDER);

export type NetworkFooter =
  | { ready: false }
  | {
      ready: true;
      active: number;
      onTime: number;
      onTimePercent: number | null;
      delayed: number;
      updatesPerMin: number | null;
    };

export type PairCounts =
  | { ready: false }
  | { ready: true; running: number; problems: number; cancelled: number };

export function parseIntervalInput(raw: string, current: number): number | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;
  const n = Number(trimmed);
  if (!Number.isFinite(n) || n <= 0 || !Number.isInteger(n)) return null;
  return n;
}

export function createInitialViewModel(): ScreenViewModel {
  return {
    phase: "empty",
    page: "network",
    loadProgress: null,
    loadSucceeded: false,
    intervalSeconds: 30,
    commsLost: false,
    statusLine: "",
    networkHeld: null,
    cancellationsHeld: [],
    diagramHeld: null,
    diagramStale: true,
    pairHeld: null,
    pairEarlier: "",
    pairLater: "",
    searchText: "",
    scheduleFocus: null,
    networkEventFilter: null,
    routePreview: null,
    routeTrack: null,
    serviceDetailHeld: null,
    pairFilter: "all",
    emptyMessage: "",
    updatesPerMin: null,
  };
}

export function isEventTag(value: string): value is EventTag {
  return EVENT_TAG_SET.has(value);
}

export function nextNetworkEventFilter(
  current: EventTag | null,
  pick: EventTag,
): EventTag | null {
  return current === pick ? null : pick;
}

export function eventTagLabel(tag: EventTag): string {
  return tag.replaceAll("_", " ");
}

export function updatesPerMinute(
  applied: number,
  intervalSeconds: number,
): number {
  if (intervalSeconds <= 0) return 0;
  return Math.round((Math.max(0, applied) * 60) / intervalSeconds);
}

export function recordPollUpdates(
  vm: ScreenViewModel,
  previous: { generation: string; revision: number } | null,
  next: NetworkAnswer,
): void {
  if (
    previous &&
    previous.generation === next.generation &&
    !next.full
  ) {
    vm.updatesPerMin = updatesPerMinute(
      next.revision - previous.revision,
      vm.intervalSeconds,
    );
    return;
  }
  if (!previous || previous.generation !== next.generation) {
    vm.updatesPerMin = null;
  }
}

function applyLoadProgress(vm: ScreenViewModel, progress: LoadProgress): void {
  vm.loadProgress = progress;
  if (progress.state === "succeeded") {
    vm.loadSucceeded = true;
    vm.phase = "loaded";
  } else if (progress.state === "failed") {
    vm.loadSucceeded = false;
    vm.phase = "empty";
    vm.emptyMessage = progress.sentence;
  }
}

function applyTrainAsk<T>(
  vm: ScreenViewModel,
  result: AskResult<T>,
  assign: (data: T) => void,
): void {
  if (result.ok) {
    vm.commsLost = false;
    assign(result.data);
    return;
  }
  if (result.kind === "comms") {
    vm.commsLost = true;
    vm.statusLine = "comms is lost";
    return;
  }
  if (result.kind === "train-failure") {
    vm.commsLost = false;
    if (!vm.networkHeld && !vm.pairHeld) {
      vm.emptyMessage = "No data can be retrieved";
    }
    return;
  }
  vm.commsLost = true;
  vm.statusLine = "comms is lost";
}

function stopsAsRows(train: NetworkTrain): StopRow[] {
  return (train.stops ?? []).map((stop, seq) => ({
    seq,
    crs: stop.crs ?? null,
    publicArr: stop.arr,
    publicDep: stop.dep,
    forecastArr: null,
    forecastDep: null,
  }));
}

export function withLiveFields(trains: NetworkTrain[], nowHhmm: string): NetworkTrain[] {
  return trains.map((train) => {
    const rows = stopsAsRows(train);
    if (rows.length === 0) {
      return train;
    }
    const { reachedCrs, nextCrs } = reachedAndNextCrs(rows, nowHhmm);
    return {
      ...train,
      reachedCrs,
      nextCrs,
      scheduledTime: scheduledHhmm(rows, nowHhmm),
    };
  });
}

export function pairMatchesCallingPoints(
  points: Array<{ crs?: string | null }>,
  earlierCrs: string,
  laterCrs: string,
): boolean {
  let earlierIndex = -1;
  for (let i = 0; i < points.length; i++) {
    if (points[i]?.crs === earlierCrs) {
      earlierIndex = i;
      break;
    }
  }
  if (earlierIndex === -1) {
    return false;
  }
  for (let j = earlierIndex + 1; j < points.length; j++) {
    if (points[j]?.crs === laterCrs) {
      return true;
    }
  }
  return false;
}

function derivePairHeld(vm: ScreenViewModel, nowHhmm: string): void {
  if (vm.pairEarlier.length !== 3 || vm.pairLater.length !== 3 || !vm.networkHeld) {
    vm.pairHeld = null;
    return;
  }
  const live = withLiveFields(vm.networkHeld.trains, nowHhmm);
  const trains: PairTrain[] = [];
  for (const train of live) {
    if (!pairMatchesCallingPoints(train.stops ?? [], vm.pairEarlier, vm.pairLater)) {
      continue;
    }
    trains.push({
      serviceGuid: train.serviceGuid,
      status: train.statusLabel ?? (train.problem ? "delayed" : "on time"),
      reachedCrs: train.reachedCrs ?? null,
      nextCrs: train.nextCrs ?? null,
      issue: train.problem,
    });
  }
  for (const cancelled of vm.cancellationsHeld) {
    const stops = cancelled.stops ?? [];
    if (stops.length > 0 && !pairMatchesCallingPoints(stops, vm.pairEarlier, vm.pairLater)) {
      continue;
    }
    trains.push({
      serviceGuid: null,
      trainUid: cancelled.trainUid,
      cancellationLabel: "cancelled",
      status: "cancelled",
      reachedCrs: null,
      nextCrs: null,
      issue: true,
    });
  }
  vm.pairHeld = {
    calendarDate: vm.networkHeld.calendarDate,
    earlierCrs: vm.pairEarlier,
    laterCrs: vm.pairLater,
    trains,
  };
}

export function mergeNetworkAnswer(
  vm: ScreenViewModel,
  data: NetworkAnswer,
): void {
  if (data.full || vm.networkHeld?.generation !== data.generation) {
    vm.networkHeld = data;
    vm.cancellationsHeld = [];
    return;
  }
  const byGuid = new Map(
    (vm.networkHeld?.trains ?? []).map((train) => [train.serviceGuid, train]),
  );
  for (const train of data.trains) {
    byGuid.set(train.serviceGuid, train);
    vm.cancellationsHeld = vm.cancellationsHeld.filter(
      (row) => row.serviceGuid !== train.serviceGuid,
    );
  }
  for (const removed of data.removed) {
    const prior = byGuid.get(removed.serviceGuid);
    byGuid.delete(removed.serviceGuid);
    if (!vm.cancellationsHeld.some((row) => row.serviceGuid === removed.serviceGuid)) {
      vm.cancellationsHeld.push({
        ...removed,
        stops: prior?.stops,
      });
    }
  }
  vm.networkHeld = {
    ...data,
    full: false,
    trains: [...byGuid.values()],
    removed: data.removed,
  };
}

const PAIR_FILTERS: readonly PairFilter[] = ["all", "on time", "delayed", "cancelled"];

export function isPairFilter(value: string): value is PairFilter {
  return (PAIR_FILTERS as readonly string[]).includes(value);
}

export type ScreenController = {
  getView: () => ScreenViewModel;
  setIntervalSeconds: (seconds: number) => void;
  setPairCrs: (earlier: string, later: string) => void;
  swapStations: () => void;
  setPage: (page: ScreenPage) => void;
  setSearchText: (text: string) => void;
  setScheduleFocus: (focus: ScheduleFocus | null) => void;
  setNetworkEventFilter: (tag: EventTag | null) => void;
  setRoutePreview: (preview: RoutePreview | null) => boolean;
  openRouteFromTrain: (train: NetworkTrain) => boolean;
  openRouteFromStation: (crs: string) => boolean;
  selectRouteTrain: (serviceGuid: string) => void;
  clearRoute: () => void;
  setPairFilter: (filter: PairFilter) => void;
  startLoad: () => Promise<void>;
  tick: () => Promise<void>;
  refreshServiceDetail: () => Promise<void>;
};

export function findHeldTrain(
  vm: ScreenViewModel,
  serviceGuid: string,
): NetworkTrain | null {
  return vm.networkHeld?.trains.find((train) => train.serviceGuid === serviceGuid) ?? null;
}

export function openRouteFromTrainOn(
  vm: ScreenViewModel,
  train: NetworkTrain,
): boolean {
  const ends = trainEnds(train);
  if (!ends) return false;
  const same =
    vm.routeTrack?.kind === "train"
    && vm.routeTrack.serviceGuid === train.serviceGuid;
  vm.routeTrack = {
    kind: "train",
    serviceGuid: train.serviceGuid,
    lastWindowKey: null,
  };
  vm.routePreview = null;
  vm.page = "route";
  if (!same) {
    vm.serviceDetailHeld = null;
  }
  syncRouteWindowKey(vm);
  return true;
}

export function openRouteFromStationOn(
  vm: ScreenViewModel,
  crs: string,
): boolean {
  const code = crs.trim().toUpperCase();
  if (!code) return false;
  vm.routeTrack = {
    kind: "station",
    crs: code,
    lastWindowKey: `at:${code}`,
  };
  vm.routePreview = null;
  vm.serviceDetailHeld = null;
  vm.page = "route";
  return true;
}

export type ResolvedRouteWindow = {
  window: string[];
  windowKey: string;
  selectedServiceGuid: string | null;
};

/** Window of stations around the tracked train place or station. */
export function resolveRouteWindow(
  vm: ScreenViewModel,
  nowHhmm = londonHhmm(),
): ResolvedRouteWindow | null {
  const track = vm.routeTrack;
  const diagram = vm.diagramHeld;
  if (!track || !diagram) return null;

  if (track.kind === "station") {
    const window = windowAroundStation(diagram, track.crs);
    return {
      window,
      windowKey: `at:${track.crs}`,
      selectedServiceGuid: null,
    };
  }

  const train = findHeldTrain(vm, track.serviceGuid);
  if (!train) return null;
  const ends = trainEnds(train);
  if (!ends) return null;
  const spine = spinePath(diagram, ends.originCrs, ends.destCrs);
  const place = placeTrain(train.stops ?? [], nowHhmm);
  if (!place) {
    return {
      window: windowAroundStation(diagram, ends.originCrs),
      windowKey: `at:${ends.originCrs}`,
      selectedServiceGuid: track.serviceGuid,
    };
  }
  // Prefer the train OD spine; if place is off that path, fall back to corridor through place.
  let path = spine;
  if (!placeOnPath(spine, place)) {
    const anchor = place.atStation ? place.crs : place.fromCrs;
    path = corridorThroughStation(diagram, anchor);
    if (path.length < 2) path = spine;
  }
  const window = windowAroundPlace(path, place);
  return {
    window: window.length > 0 ? window : path,
    windowKey: windowKeyForPlace(place),
    selectedServiceGuid: track.serviceGuid,
  };
}

function placeOnPath(
  spine: readonly string[],
  place: ReturnType<typeof placeTrain>,
): boolean {
  if (!place) return false;
  if (place.atStation) return spine.includes(place.crs);
  return spine.includes(place.fromCrs) && spine.includes(place.toCrs);
}

/** Update lastWindowKey; returns true when the window centre changed. */
export function syncRouteWindowKey(
  vm: ScreenViewModel,
  nowHhmm = londonHhmm(),
): boolean {
  const track = vm.routeTrack;
  if (!track) return false;
  const resolved = resolveRouteWindow(vm, nowHhmm);
  if (!resolved) return false;
  if (track.lastWindowKey === resolved.windowKey) return false;
  vm.routeTrack = { ...track, lastWindowKey: resolved.windowKey };
  return true;
}

export function routeTrackTitle(track: RouteTrack): string {
  if (track.kind === "train") return `Tracking train ${track.serviceGuid}`;
  return `Tracking station ${track.crs}`;
}

export function createScreenController(api: ApiClient): ScreenController {
  const vm = createInitialViewModel();

  return {
    getView: () => vm,
    setIntervalSeconds(seconds: number) {
      vm.intervalSeconds = seconds;
    },
    setPairCrs(earlier: string, later: string) {
      vm.pairEarlier = earlier.trim().toUpperCase();
      vm.pairLater = later.trim().toUpperCase();
      derivePairHeld(vm, londonHhmm());
    },
    swapStations() {
      const earlier = vm.pairEarlier;
      vm.pairEarlier = vm.pairLater;
      vm.pairLater = earlier;
      derivePairHeld(vm, londonHhmm());
    },
    setPage(page: ScreenPage) {
      vm.page = page;
      if (page !== "route") {
        vm.routeTrack = null;
        vm.serviceDetailHeld = null;
      }
      if (page !== "network") {
        vm.routePreview = null;
      }
    },
    setSearchText(text: string) {
      vm.searchText = text;
    },
    setScheduleFocus(focus: ScheduleFocus | null) {
      vm.scheduleFocus = focus;
    },
    setNetworkEventFilter(tag: EventTag | null) {
      vm.networkEventFilter = tag;
    },
    setRoutePreview(preview: RoutePreview | null) {
      const nextGuid = preview?.serviceGuid ?? null;
      const curGuid = vm.routePreview?.serviceGuid ?? null;
      if (nextGuid === curGuid) return false;
      vm.routePreview = preview;
      return true;
    },
    openRouteFromTrain(train: NetworkTrain) {
      return openRouteFromTrainOn(vm, train);
    },
    openRouteFromStation(crs: string) {
      return openRouteFromStationOn(vm, crs);
    },
    selectRouteTrain(serviceGuid: string) {
      const train = findHeldTrain(vm, serviceGuid);
      if (!train) return;
      openRouteFromTrainOn(vm, train);
    },
    clearRoute() {
      vm.routeTrack = null;
      vm.routePreview = null;
      vm.serviceDetailHeld = null;
      vm.page = "network";
    },
    setPairFilter(filter: PairFilter) {
      vm.pairFilter = filter;
    },
    async startLoad() {
      const result = await api.startLoad();
      if (result.ok) {
        applyLoadProgress(vm, result.data);
        vm.diagramStale = true;
        vm.diagramHeld = null;
        vm.updatesPerMin = null;
        vm.commsLost = false;
      } else if (result.kind === "comms") {
        vm.commsLost = true;
        vm.statusLine = "comms is lost";
      }
    },
    async tick() {
      // Poll load status until the DB is ready, then continue into map + trains
      // on the same pass so a fresh page load does not wait a full interval.
      if (!vm.loadSucceeded || vm.loadProgress?.state === "running") {
        const progress = await api.getLoadProgress();
        if (progress.ok) {
          applyLoadProgress(vm, progress.data);
          vm.commsLost = false;
        } else if (progress.kind === "comms") {
          vm.commsLost = true;
          vm.statusLine = "comms is lost";
        }
        if (!vm.loadSucceeded || vm.loadProgress?.state === "running") return;
      }

      if (vm.diagramStale || !vm.diagramHeld) {
        const diagram = await api.getNetworkMap();
        if (diagram.ok) {
          vm.diagramHeld = diagram.data;
          vm.diagramStale = false;
          vm.commsLost = false;
        } else if (diagram.kind === "comms") {
          vm.commsLost = true;
          vm.statusLine = "comms is lost";
        }
      }

      const cursor =
        vm.networkHeld?.generation !== undefined &&
        vm.networkHeld.revision !== undefined
          ? {
              since: vm.networkHeld.revision,
              generation: vm.networkHeld.generation,
            }
          : undefined;
      const network = await api.getNetwork(cursor);
      applyTrainAsk(vm, network, (data) => {
        const previous = vm.networkHeld
          ? {
              generation: vm.networkHeld.generation,
              revision: vm.networkHeld.revision,
            }
          : null;
        mergeNetworkAnswer(vm, data);
        recordPollUpdates(vm, previous, data);
      });
      derivePairHeld(vm, londonHhmm());
      if (vm.page === "route") {
        syncRouteWindowKey(vm);
        await loadServiceDetail(vm, api);
      }
    },
    async refreshServiceDetail() {
      await loadServiceDetail(vm, api);
    },
  };
}

async function loadServiceDetail(
  vm: ScreenViewModel,
  api: ApiClient,
): Promise<void> {
  const track = vm.routeTrack;
  if (!track || track.kind !== "train") {
    vm.serviceDetailHeld = null;
    return;
  }
  const result = await api.getService(track.serviceGuid);
  if (result.ok) {
    vm.serviceDetailHeld = result.data;
    vm.commsLost = false;
  } else if (result.kind === "comms") {
    vm.commsLost = true;
    vm.statusLine = "comms is lost";
  }
}

/** Live schedule window: ±2 hours of London now (same rule on network list and route strip). */
export const SCHEDULE_WINDOW_MINUTES = 120;

/** Triangle markers on network map + route strip: only imminent departures. */
export const MARKER_DEPARTURE_WINDOW_MINUTES = 15;

function includesQuery(value: string | null | undefined, query: string): boolean {
  return (value ?? "").toLowerCase().includes(query);
}

function hhmmToMinutes(hhmm: string): number | null {
  if (!/^\d{4}$/.test(hhmm)) return null;
  const hours = Number(hhmm.slice(0, 2));
  const minutes = Number(hhmm.slice(2, 4));
  if (hours > 23 || minutes > 59) return null;
  return hours * 60 + minutes;
}

export function londonHhmm(now = new Date()): string {
  const fmt = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/London",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  const parts = fmt.formatToParts(now);
  const hour = parts.find((part) => part.type === "hour")?.value;
  const minute = parts.find((part) => part.type === "minute")?.value;
  if (hour === undefined || minute === undefined) {
    throw new Error("could not format Europe/London time");
  }
  return `${hour.padStart(2, "0")}${minute.padStart(2, "0")}`;
}

export function scheduledOffsetMinutes(scheduledHhmm: string, nowHhmm: string): number | null {
  const scheduled = hhmmToMinutes(scheduledHhmm);
  const nowMinutes = hhmmToMinutes(nowHhmm);
  if (scheduled === null || nowMinutes === null) return null;
  let delta = scheduled - nowMinutes;
  if (delta > 12 * 60) delta -= 24 * 60;
  if (delta < -12 * 60) delta += 24 * 60;
  return delta;
}

/** Next public departure from the station the train is currently standing at. */
export function nextStationDepartureHhmm(
  train: NetworkTrain,
  nowHhmm: string,
): string | null {
  const stops = train.stops ?? [];
  const place = placeTrain(stops, nowHhmm);
  if (!place || !place.atStation || !place.towardCrs) return null;
  for (let i = 0; i < stops.length - 1; i++) {
    const stop = stops[i]!;
    const next = stops[i + 1]!;
    if (stop.crs === place.crs && next.crs === place.towardCrs) {
      return stop.dep;
    }
  }
  return null;
}

/** True when the train is at a station and due to leave within the marker window. */
export function dueToDepartWithin(
  train: NetworkTrain,
  nowHhmm: string,
  windowMinutes = MARKER_DEPARTURE_WINDOW_MINUTES,
): boolean {
  const dep = nextStationDepartureHhmm(train, nowHhmm);
  if (!dep) return false;
  const offset = scheduledOffsetMinutes(dep, nowHhmm);
  return offset !== null && offset >= 0 && offset <= windowMinutes;
}

/** Trains drawn as triangles: due to depart in the next 15 minutes (plus optional tracked). */
export function markerVisibleTrains(
  trains: readonly NetworkTrain[],
  nowHhmm: string,
  alwaysIncludeGuid: string | null = null,
): NetworkTrain[] {
  return trains.filter(
    (train) =>
      (alwaysIncludeGuid !== null && train.serviceGuid === alwaysIncludeGuid)
      || dueToDepartWithin(train, nowHhmm),
  );
}

function matchesSearch(train: NetworkTrain, query: string): boolean {
  if (!query) return true;
  return [train.serviceGuid, train.originCrs, train.destCrs, train.reachedCrs, train.nextCrs].some(
    (value) => includesQuery(value, query),
  );
}

function callsAt(train: NetworkTrain, crs: string): boolean {
  const stops = train.stops ?? [];
  if (stops.length > 0) return stops.some((stop) => stop.crs === crs);
  return [train.originCrs, train.destCrs, train.reachedCrs, train.nextCrs].some((value) => value === crs);
}

export function inScheduleWindow(train: NetworkTrain, nowHhmm: string): boolean {
  if (!train.scheduledTime) return false;
  const offset = scheduledOffsetMinutes(train.scheduledTime, nowHhmm);
  return offset !== null && Math.abs(offset) <= SCHEDULE_WINDOW_MINUTES;
}

/** Route strip/list: live ±2h window, always keeping the tracked train when set. */
export function routeVisibleTrains(
  trains: readonly NetworkTrain[],
  nowHhmm: string,
  trackedServiceGuid: string | null,
): NetworkTrain[] {
  const live = withLiveFields([...trains], nowHhmm);
  return live.filter(
    (train) =>
      (trackedServiceGuid !== null && train.serviceGuid === trackedServiceGuid)
      || inScheduleWindow(train, nowHhmm),
  );
}

function byScheduledTime(nowHhmm: string): (left: NetworkTrain, right: NetworkTrain) => number {
  return (left, right) => {
    const leftOffset = scheduledOffsetMinutes(left.scheduledTime ?? "", nowHhmm) ?? 0;
    const rightOffset = scheduledOffsetMinutes(right.scheduledTime ?? "", nowHhmm) ?? 0;
    return leftOffset - rightOffset;
  };
}

export function sameScheduleFocus(left: ScheduleFocus | null, right: ScheduleFocus | null): boolean {
  if (left === right) return true;
  if (!left || !right || left.kind !== right.kind) return false;
  if (left.kind === "station" && right.kind === "station") return left.crs === right.crs;
  if (left.kind === "services" && right.kind === "services") {
    return (
      left.serviceGuids.length === right.serviceGuids.length &&
      left.serviceGuids.every((id, index) => id === right.serviceGuids[index])
    );
  }
  return false;
}

export function nextScheduleFocus(
  current: ScheduleFocus | null,
  pick: ScheduleFocus,
): ScheduleFocus | null {
  return sameScheduleFocus(current, pick) ? null : pick;
}

function trainHasTag(train: NetworkTrain, tag: EventTag): boolean {
  return (train.eventTags ?? []).includes(tag);
}

function cancelledAsNetworkTrain(
  row: RemovedTrain & { stops?: NetworkTrain["stops"] },
): NetworkTrain {
  const tags = row.eventTags ?? [];
  return {
    serviceGuid: row.serviceGuid,
    problem: true,
    statusLabel: "cancelled",
    eventTags: tags.includes("cancelled") ? tags : [...tags, "cancelled"],
    stops: row.stops,
  };
}

function applyBaseNetworkFilters(
  trains: NetworkTrain[],
  vm: ScreenViewModel,
  nowHhmm: string,
): NetworkTrain[] {
  const query = vm.searchText.trim().toLowerCase();
  const focus = vm.scheduleFocus;
  const held = withLiveFields(trains, nowHhmm);
  const rows = focus?.kind === "services"
    ? held.filter((train) => focus.serviceGuids.includes(train.serviceGuid))
    : held.filter((train) => inScheduleWindow(train, nowHhmm));
  return rows
    .filter((train) => matchesSearch(train, query))
    .filter((train) => focus?.kind !== "station" || callsAt(train, focus.crs));
}

/** Live-schedule window + search + map focus (before event-tag filter). */
export function baseVisibleNetworkTrains(
  vm: ScreenViewModel,
  now = new Date(),
): NetworkTrain[] {
  const nowHhmm = londonHhmm(now);
  return applyBaseNetworkFilters(vm.networkHeld?.trains ?? [], vm, nowHhmm).sort(
    byScheduledTime(nowHhmm),
  );
}

function matchingCancelledTrains(
  vm: ScreenViewModel,
  nowHhmm: string,
): NetworkTrain[] {
  const cancelled = vm.cancellationsHeld.map(cancelledAsNetworkTrain);
  const query = vm.searchText.trim().toLowerCase();
  const focus = vm.scheduleFocus;
  return cancelled
    .filter((train) => matchesSearch(train, query))
    .filter((train) => {
      if (focus?.kind === "services") {
        return focus.serviceGuids.includes(train.serviceGuid);
      }
      if (focus?.kind === "station") {
        return callsAt(train, focus.crs);
      }
      return true;
    })
    .map((train) => {
      const live = withLiveFields([train], nowHhmm)[0] ?? train;
      return live;
    });
}

export function disruptionSummary(
  vm: ScreenViewModel,
  now = new Date(),
): DisruptionSummaryRow[] {
  const nowHhmm = londonHhmm(now);
  const trains = [
    ...baseVisibleNetworkTrains(vm, now),
    ...matchingCancelledTrains(vm, nowHhmm),
  ];
  const counts = new Map<EventTag, number>();
  for (const train of trains) {
    const seen = new Set<EventTag>();
    for (const tag of train.eventTags ?? []) {
      if (seen.has(tag)) continue;
      seen.add(tag);
      counts.set(tag, (counts.get(tag) ?? 0) + 1);
    }
  }
  const rows: DisruptionSummaryRow[] = [];
  for (const tag of EVENT_TAG_ORDER) {
    const count = counts.get(tag) ?? 0;
    if (count > 0) {
      rows.push({ tag, count });
    }
  }
  for (const [tag, count] of counts) {
    if (!EVENT_TAG_ORDER.includes(tag) && count > 0) {
      rows.push({ tag, count });
    }
  }
  return rows;
}

export function visibleNetworkTrains(vm: ScreenViewModel, now = new Date()): NetworkTrain[] {
  const nowHhmm = londonHhmm(now);
  const filter = vm.networkEventFilter;
  if (filter === "cancelled") {
    return matchingCancelledTrains(vm, nowHhmm)
      .filter((train) => trainHasTag(train, "cancelled"))
      .sort(byScheduledTime(nowHhmm));
  }
  const base = baseVisibleNetworkTrains(vm, now);
  if (!filter) {
    return base;
  }
  return base.filter((train) => trainHasTag(train, filter));
}

export function problemTrains(vm: ScreenViewModel, now = new Date()): NetworkTrain[] {
  return baseVisibleNetworkTrains(vm, now).filter((train) => train.problem);
}

export function networkListMessage(vm: ScreenViewModel, now = new Date()): string {
  if (vm.commsLost) return "";
  if (!vm.networkHeld) {
    if (vm.emptyMessage) return vm.emptyMessage;
    return "Loading";
  }
  if (vm.networkHeld.trains.length === 0 && vm.cancellationsHeld.length === 0) {
    return "No trains";
  }
  if (visibleNetworkTrains(vm, now).length === 0) {
    if (vm.networkEventFilter) return "Nothing in this filter";
    if (vm.scheduleFocus?.kind === "station") return "No trains through this station";
    if (vm.scheduleFocus?.kind === "services") return "No trains on this arrow";
    return "Nothing matches";
  }
  return "";
}

export function disruptionMessage(vm: ScreenViewModel, now = new Date()): string {
  if (vm.commsLost && !vm.networkHeld) return "";
  if (!vm.networkHeld) {
    if (vm.emptyMessage) return vm.emptyMessage;
    return "Loading";
  }
  if (disruptionSummary(vm, now).length === 0) return "No interesting events";
  return "";
}

export function systemAlertsMessage(vm: ScreenViewModel): string {
  if (vm.networkHeld) return "This panel has no data";
  if (vm.emptyMessage || vm.commsLost) return "No data can be retrieved";
  return "Loading";
}

export function networkFooter(vm: ScreenViewModel): NetworkFooter {
  if (!vm.networkHeld) return { ready: false };
  const active = vm.networkHeld.trains.length;
  const delayed = vm.networkHeld.trains.filter((train) => train.problem).length;
  const onTime = active - delayed;
  return {
    ready: true,
    active,
    onTime,
    onTimePercent: active > 0 ? Math.round((onTime / active) * 100) : null,
    delayed,
    updatesPerMin: vm.updatesPerMin,
  };
}

export function isCancellationRow(train: PairTrain): boolean {
  return train.status === "cancelled" || train.cancellationLabel === "cancelled";
}

export function visiblePairTrains(vm: ScreenViewModel): PairTrain[] {
  const trains = vm.pairHeld?.trains ?? [];
  if (vm.pairFilter === "cancelled") return trains.filter(isCancellationRow);
  if (vm.pairFilter === "on time") {
    return trains.filter((train) => !isCancellationRow(train) && !train.issue);
  }
  if (vm.pairFilter === "delayed") {
    return trains.filter((train) => !isCancellationRow(train) && train.issue);
  }
  return [...trains];
}

export function pairListMessage(vm: ScreenViewModel): string {
  if (vm.commsLost) return "";
  if (!vm.pairHeld) {
    if (vm.emptyMessage && !vm.networkHeld) return vm.emptyMessage;
    if (vm.pairEarlier.length < 3 || vm.pairLater.length < 3) return "Nothing matches";
    return "Loading";
  }
  if (visiblePairTrains(vm).length === 0) {
    return vm.pairFilter === "all" ? "No trains" : "Nothing in this filter";
  }
  return "";
}

export function pairCounts(vm: ScreenViewModel): PairCounts {
  if (!vm.pairHeld) return { ready: false };
  const runningRows = vm.pairHeld.trains.filter((train) => !isCancellationRow(train));
  return {
    ready: true,
    running: runningRows.length,
    problems: runningRows.filter((train) => train.issue).length,
    cancelled: vm.pairHeld.trains.filter(isCancellationRow).length,
  };
}

export function formatLondonClock(now = new Date()): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/London",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(now);
}

export function formatCalendarLabel(yyymmdd: string | null, now = new Date()): string {
  if (yyymmdd && /^\d{6}$/.test(yyymmdd)) {
    const year = 2000 + Number(yyymmdd.slice(0, 2));
    const month = Number(yyymmdd.slice(2, 4));
    const day = Number(yyymmdd.slice(4, 6));
    if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
      return new Intl.DateTimeFormat("en-GB", {
        timeZone: "UTC",
        day: "2-digit",
        month: "short",
        year: "numeric",
      }).format(new Date(Date.UTC(year, month - 1, day)));
    }
  }
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/London",
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(now);
}

export function calendarLabelFor(vm: ScreenViewModel, now = new Date()): string {
  const token =
    vm.page === "pair"
      ? (vm.pairHeld?.calendarDate ?? vm.networkHeld?.calendarDate ?? null)
      : (vm.networkHeld?.calendarDate ?? vm.pairHeld?.calendarDate ?? null);
  return formatCalendarLabel(token, now);
}

export function previewTrain(vm: ScreenViewModel, now = new Date()): NetworkTrain | null {
  const guid = vm.routePreview?.serviceGuid;
  if (!guid || !vm.networkHeld) return null;
  const held = findHeldTrain(vm, guid);
  if (!held) return null;
  return withLiveFields([held], londonHhmm(now))[0] ?? null;
}

export function selectedRouteTrain(vm: ScreenViewModel, now = new Date()): NetworkTrain | null {
  const track = vm.routeTrack;
  if (!track || track.kind !== "train" || !vm.networkHeld) return null;
  const held = findHeldTrain(vm, track.serviceGuid);
  if (!held) return null;
  return withLiveFields([held], londonHhmm(now))[0] ?? null;
}
