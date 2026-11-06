import type { ApiClient } from "./apiClient.js";
import type {
  AskResult,
  LoadProgress,
  NetworkAnswer,
  NetworkDiagram,
  NetworkTrain,
  PairTrain,
  StationPairAnswer,
} from "./types.js";

export type ScreenPhase = "empty" | "loaded";
export type ScreenPage = "network" | "pair";
export type PairFilter = "all" | "on time" | "delayed" | "cancelled";

export type ScheduleFocus =
  | { kind: "station"; crs: string }
  | { kind: "services"; serviceGuids: readonly string[] };

export type ScreenViewModel = {
  phase: ScreenPhase;
  page: ScreenPage;
  loadProgress: LoadProgress | null;
  loadSucceeded: boolean;
  intervalSeconds: number;
  commsLost: boolean;
  statusLine: string;
  networkHeld: NetworkAnswer | null;
  diagramHeld: NetworkDiagram | null;
  diagramStale: boolean;
  pairHeld: StationPairAnswer | null;
  pairEarlier: string;
  pairLater: string;
  searchText: string;
  scheduleFocus: ScheduleFocus | null;
  pairFilter: PairFilter;
  emptyMessage: string;
};

export type NetworkFooter =
  | { ready: false }
  | {
      ready: true;
      active: number;
      onTime: number;
      onTimePercent: number | null;
      delayed: number;
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
    diagramHeld: null,
    diagramStale: true,
    pairHeld: null,
    pairEarlier: "",
    pairLater: "",
    searchText: "",
    scheduleFocus: null,
    pairFilter: "all",
    emptyMessage: "",
  };
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
  setPairFilter: (filter: PairFilter) => void;
  startLoad: () => Promise<void>;
  tick: () => Promise<void>;
};

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
    },
    swapStations() {
      const earlier = vm.pairEarlier;
      vm.pairEarlier = vm.pairLater;
      vm.pairLater = earlier;
    },
    setPage(page: ScreenPage) {
      vm.page = page;
    },
    setSearchText(text: string) {
      vm.searchText = text;
    },
    setScheduleFocus(focus: ScheduleFocus | null) {
      vm.scheduleFocus = focus;
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
        vm.commsLost = false;
      } else if (result.kind === "comms") {
        vm.commsLost = true;
        vm.statusLine = "comms is lost";
      }
    },
    async tick() {
      if (!vm.loadSucceeded) {
        const progress = await api.getLoadProgress();
        if (progress.ok) {
          applyLoadProgress(vm, progress.data);
          vm.commsLost = false;
        } else if (progress.kind === "comms") {
          vm.commsLost = true;
          vm.statusLine = "comms is lost";
        }
        return;
      }

      if (vm.loadProgress?.state === "running") {
        const progress = await api.getLoadProgress();
        if (progress.ok) {
          applyLoadProgress(vm, progress.data);
          vm.commsLost = false;
        } else if (progress.kind === "comms") {
          vm.commsLost = true;
          vm.statusLine = "comms is lost";
        }
        if (vm.loadProgress?.state === "running" || !vm.loadSucceeded) return;
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

      const network = await api.getNetwork();
      applyTrainAsk(vm, network, (data) => {
        vm.networkHeld = data;
      });

      if (vm.pairEarlier.length === 3 && vm.pairLater.length === 3) {
        const pair = await api.postPair(vm.pairEarlier, vm.pairLater);
        applyTrainAsk(vm, pair, (data) => {
          vm.pairHeld = data;
        });
      }
    },
  };
}

const SCHEDULE_WINDOW_MINUTES = 120;

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

function inScheduleWindow(train: NetworkTrain, nowHhmm: string): boolean {
  if (!train.scheduledTime) return false;
  const offset = scheduledOffsetMinutes(train.scheduledTime, nowHhmm);
  return offset !== null && Math.abs(offset) <= SCHEDULE_WINDOW_MINUTES;
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

export function visibleNetworkTrains(vm: ScreenViewModel, now = new Date()): NetworkTrain[] {
  const nowHhmm = londonHhmm(now);
  const query = vm.searchText.trim().toLowerCase();
  const focus = vm.scheduleFocus;
  const held = vm.networkHeld?.trains ?? [];
  const rows = focus?.kind === "services"
    ? held.filter((train) => focus.serviceGuids.includes(train.serviceGuid))
    : held.filter((train) => inScheduleWindow(train, nowHhmm));
  return rows
    .filter((train) => matchesSearch(train, query))
    .filter((train) => focus?.kind !== "station" || callsAt(train, focus.crs))
    .sort(byScheduledTime(nowHhmm));
}

export function problemTrains(vm: ScreenViewModel): NetworkTrain[] {
  return (vm.networkHeld?.trains ?? []).filter((train) => train.problem);
}

export function networkListMessage(vm: ScreenViewModel, now = new Date()): string {
  if (vm.commsLost) return "";
  if (!vm.networkHeld) {
    if (vm.emptyMessage) return vm.emptyMessage;
    return "Loading";
  }
  if (vm.networkHeld.trains.length === 0) return "No trains";
  if (visibleNetworkTrains(vm, now).length === 0) {
    if (vm.scheduleFocus?.kind === "station") return "No trains through this station";
    if (vm.scheduleFocus?.kind === "services") return "No trains on this arrow";
    return "Nothing matches";
  }
  return "";
}

export function disruptionMessage(vm: ScreenViewModel): string {
  if (vm.commsLost && !vm.networkHeld) return "";
  if (!vm.networkHeld) {
    if (vm.emptyMessage) return vm.emptyMessage;
    return "Loading";
  }
  if (problemTrains(vm).length === 0) return "No problems";
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
