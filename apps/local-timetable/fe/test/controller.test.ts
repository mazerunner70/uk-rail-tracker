import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  createInitialViewModel,
  createScreenController,
  mergeNetworkAnswer,
  disruptionMessage,
  disruptionSummary,
  networkFooter,
  networkListMessage,
  nextNetworkEventFilter,
  pairListMessage,
  parseIntervalInput,
  problemTrains,
  recordPollUpdates,
  resolveRouteWindow,
  syncRouteWindowKey,
  updatesPerMinute,
  routeVisibleTrains,
  markerVisibleTrains,
  dueToDepartWithin,
  visibleNetworkTrains,
  visiblePairTrains,
  nextScheduleFocus,
} from "../src/controller.js";
import type { ApiClient } from "../src/apiClient.js";
import type { AskResult, LoadProgress, NetworkAnswer, NetworkDiagram, StationPairAnswer } from "../src/types.js";

function stubApi(handlers: Partial<ApiClient>): ApiClient {
  const fail = (): Promise<AskResult<never>> =>
    Promise.resolve({ ok: false, kind: "comms" });
  return {
    startLoad: handlers.startLoad ?? fail,
    getLoadProgress: handlers.getLoadProgress ?? fail,
    getNetwork: handlers.getNetwork ?? fail,
    getNetworkMap: handlers.getNetworkMap ?? fail,
    getService: handlers.getService ?? fail,
    postPair: handlers.postPair ?? fail,
  };
}

describe("parseIntervalInput", () => {
  it("refuses blank and non-positive values", () => {
    assert.equal(parseIntervalInput("", 30), null);
    assert.equal(parseIntervalInput("0", 30), null);
    assert.equal(parseIntervalInput("1.5", 30), null);
  });

  it("accepts positive integers", () => {
    assert.equal(parseIntervalInput("45", 30), 45);
  });
});

describe("screen controller", () => {
  it("stays empty until load succeeds", async () => {
    const progress: LoadProgress = { state: "running", sentence: "Going", howFar: "1" };
    const api = stubApi({
      startLoad: async () => ({ ok: true, data: progress }),
      getLoadProgress: async () => ({ ok: true, data: progress }),
    });
    const c = createScreenController(api);
    await c.startLoad();
    assert.equal(c.getView().phase, "empty");
    assert.equal(c.getView().loadSucceeded, false);
  });

  it("holds network answer after success", async () => {
    const succeeded: LoadProgress = { state: "succeeded", sentence: "Done" };
    const network: NetworkAnswer = {
      calendarDate: "251102",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [{ serviceGuid: "g1", scheduledTime: "1300", problem: false }],
    };
    const api = stubApi({
      getLoadProgress: async () => ({ ok: true, data: succeeded }),
      getNetwork: async () => ({ ok: true, data: network }),
    });
    const c = createScreenController(api);
    c.getView().loadSucceeded = true;
    c.getView().phase = "loaded";
    await c.tick();
    assert.deepEqual(c.getView().networkHeld, network);
    assert.equal(networkListMessage(c.getView(), new Date("2026-11-06T13:00:00Z")), "");
  });

  it("fetches map and trains on the same tick that discovers load succeeded", async () => {
    const succeeded: LoadProgress = { state: "succeeded", sentence: "Done" };
    const network: NetworkAnswer = {
      calendarDate: "251102",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [{ serviceGuid: "g1", scheduledTime: "1300", problem: false }],
    };
    const diagram = {
      loadDay: "20261106",
      hdSequence: "1",
      stations: [],
      corridors: [],
      width: 100,
      height: 80,
    };
    let progressCalls = 0;
    let mapCalls = 0;
    let networkCalls = 0;
    const api = stubApi({
      getLoadProgress: async () => {
        progressCalls += 1;
        return { ok: true, data: succeeded };
      },
      getNetworkMap: async () => {
        mapCalls += 1;
        return { ok: true, data: diagram };
      },
      getNetwork: async () => {
        networkCalls += 1;
        return { ok: true, data: network };
      },
    });
    const c = createScreenController(api);
    assert.equal(c.getView().loadSucceeded, false);
    await c.tick();
    assert.equal(c.getView().loadSucceeded, true);
    assert.equal(c.getView().phase, "loaded");
    assert.deepEqual(c.getView().diagramHeld, diagram);
    assert.deepEqual(c.getView().networkHeld, network);
    assert.equal(progressCalls, 1);
    assert.equal(mapCalls, 1);
    assert.equal(networkCalls, 1);
  });

  it("sets comms lost without clearing held trains", async () => {
    const held: NetworkAnswer = { calendarDate: "251102",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [], trains: [] };
    const api = stubApi({
      getNetwork: async () => ({ ok: false, kind: "comms" }),
    });
    const c = createScreenController(api);
    c.getView().loadSucceeded = true;
    c.getView().networkHeld = held;
    await c.tick();
    assert.equal(c.getView().commsLost, true);
    assert.deepEqual(c.getView().networkHeld, held);
  });

  it("shows No trains for empty pair success", async () => {
    const pair: StationPairAnswer = {
      calendarDate: "251102",
      earlierCrs: "AAA",
      laterCrs: "BBB",
      trains: [],
    };
    const c = createScreenController(
      stubApi({ postPair: async () => ({ ok: true, data: pair }) }),
    );
    c.getView().loadSucceeded = true;
    c.getView().pairEarlier = "AAA";
    c.getView().pairLater = "BBB";
    c.getView().pairHeld = pair;
    assert.equal(pairListMessage(c.getView()), "No trains");
  });

  it("keeps held answers when the page changes", () => {
    const c = createScreenController(stubApi({}));
    const network: NetworkAnswer = { calendarDate: "251102",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [], trains: [] };
    const pair: StationPairAnswer = {
      calendarDate: "251102",
      earlierCrs: "CRE",
      laterCrs: "BHM",
      trains: [],
    };
    c.getView().networkHeld = network;
    c.getView().pairHeld = pair;
    c.setPage("pair");
    assert.equal(c.getView().page, "pair");
    assert.equal(c.getView().networkHeld, network);
    assert.equal(c.getView().pairHeld, pair);
    c.setPage("network");
    assert.equal(c.getView().networkHeld, network);
  });

  it("loads the stored diagram once and drops it when a load starts", async () => {
    let maps = 0;
    const diagram: NetworkDiagram = {
      loadDay: "20261106",
      hdSequence: "1",
      stations: [],
      corridors: [],
      width: 10,
      height: 10,
    };
    const c = createScreenController(
      stubApi({
        getNetworkMap: async () => {
          maps += 1;
          return { ok: true, data: diagram };
        },
        getNetwork: async () => ({ ok: true, data: { calendarDate: "251102",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [], trains: [] } }),
        startLoad: async () => ({ ok: true, data: { state: "running", sentence: "Going" } }),
      }),
    );
    c.getView().loadSucceeded = true;
    await c.tick();
    await c.tick();
    assert.equal(maps, 1);
    assert.equal(c.getView().diagramHeld, diagram);
    await c.startLoad();
    assert.equal(c.getView().diagramHeld, null);
    assert.equal(c.getView().diagramStale, true);
  });
});

describe("network derivations", () => {
  it("filters held rows by service or crs and leaves an empty success as No trains", () => {
    const vm = createInitialViewModel();
    vm.networkHeld = {
      calendarDate: "251102",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        { serviceGuid: "g-ok", originCrs: "EUS", destCrs: "BHM", scheduledTime: "1300", problem: false },
        { serviceGuid: "g-late", originCrs: "CRE", destCrs: "STA", scheduledTime: "1250", problem: true },
      ],
    };
    const now = new Date("2026-11-06T13:00:00Z");
    vm.searchText = "cre";
    assert.deepEqual(
      visibleNetworkTrains(vm, now).map((train) => train.serviceGuid),
      ["g-late"],
    );
    vm.searchText = "missing";
    assert.equal(networkListMessage(vm, now), "Nothing matches");
    vm.networkHeld = { calendarDate: "251102",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [], trains: [] };
    vm.searchText = "cre";
    assert.equal(networkListMessage(vm), "No trains");
  });

  it("lists live trains through a station, and every train on an arrow", () => {
    const vm = createInitialViewModel();
    const now = new Date("2026-11-06T13:00:00Z");
    vm.networkHeld = {
      calendarDate: "261106",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        {
          serviceGuid: "through",
          scheduledTime: "1300",
          problem: false,
          stops: [
            { crs: "EUS", arr: null, dep: "1200" },
            { crs: "MKC", arr: "1400", dep: null },
          ],
        },
        {
          serviceGuid: "elsewhere",
          scheduledTime: "1310",
          problem: false,
          stops: [
            { crs: "BHM", arr: null, dep: "1200" },
            { crs: "STA", arr: "1400", dep: null },
          ],
        },
        {
          serviceGuid: "later-on-arrow",
          scheduledTime: "1800",
          problem: false,
          stops: [
            { crs: "EUS", arr: null, dep: "1700" },
            { crs: "MKC", arr: "1800", dep: null },
          ],
        },
      ],
    };
    vm.scheduleFocus = { kind: "station", crs: "MKC" };
    assert.deepEqual(
      visibleNetworkTrains(vm, now).map((train) => train.serviceGuid),
      ["through"],
    );
    assert.equal(networkListMessage(vm, now), "");
    vm.scheduleFocus = { kind: "station", crs: "NUN" };
    assert.equal(networkListMessage(vm, now), "No trains through this station");
    vm.scheduleFocus = { kind: "services", serviceGuids: ["elsewhere", "later-on-arrow"] };
    assert.deepEqual(
      visibleNetworkTrains(vm, now).map((train) => train.serviceGuid),
      ["elsewhere", "later-on-arrow"],
    );
    assert.equal(
      nextScheduleFocus(vm.scheduleFocus, { kind: "services", serviceGuids: ["elsewhere", "later-on-arrow"] }),
      null,
    );
  });

  it("keeps scheduled times within two hours of London now, earliest first", () => {
    const vm = createInitialViewModel();
    const now = new Date("2026-11-06T13:00:00Z");
    vm.networkHeld = {
      calendarDate: "261106",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        { serviceGuid: "later", scheduledTime: "1500", problem: false },
        { serviceGuid: "too-early", scheduledTime: "1000", problem: false },
        { serviceGuid: "earlier", scheduledTime: "1200", problem: false },
        { serviceGuid: "missing", problem: false },
        { serviceGuid: "on-the-hour", scheduledTime: "1400", problem: false },
      ],
    };
    assert.deepEqual(
      visibleNetworkTrains(vm, now).map((train) => train.serviceGuid),
      ["earlier", "on-the-hour", "later"],
    );
  });

  it("shows triangle markers only for departures in the next 15 minutes", () => {
    const soon = {
      serviceGuid: "soon",
      originCrs: "EUS",
      destCrs: "MKC",
      problem: false,
      stops: [
        { crs: "EUS", arr: null, dep: "1310" },
        { crs: "MKC", arr: "1400", dep: null },
      ],
    };
    const later = {
      serviceGuid: "later",
      originCrs: "EUS",
      destCrs: "MKC",
      problem: false,
      stops: [
        { crs: "EUS", arr: null, dep: "1340" },
        { crs: "MKC", arr: "1430", dep: null },
      ],
    };
    const moving = {
      serviceGuid: "moving",
      originCrs: "EUS",
      destCrs: "MKC",
      problem: false,
      stops: [
        { crs: "EUS", arr: null, dep: "1200" },
        { crs: "MKC", arr: "1400", dep: null },
      ],
    };
    assert.equal(dueToDepartWithin(soon, "1300"), true);
    assert.equal(dueToDepartWithin(later, "1300"), false);
    assert.equal(dueToDepartWithin(moving, "1300"), false);
    assert.deepEqual(
      markerVisibleTrains([soon, later, moving], "1300").map((train) => train.serviceGuid),
      ["soon"],
    );
    assert.deepEqual(
      markerVisibleTrains([soon, later, moving], "1300", "moving").map(
        (train) => train.serviceGuid,
      ),
      ["soon", "moving"],
    );
  });

  it("applies the same ±2h window on the route strip and keeps the tracked train", () => {
    const nowHhmm = "1700";
    const tracked = {
      serviceGuid: "evening",
      originCrs: "EUS",
      destCrs: "BHM",
      problem: false,
      stops: [
        { crs: "EUS", arr: null, dep: "1823" },
        { crs: "WFJ", arr: "1900", dep: "1905" },
        { crs: "BHM", arr: "2030", dep: null },
      ],
    };
    const morning = {
      serviceGuid: "morning",
      originCrs: "EUS",
      destCrs: "BHM",
      problem: true,
      statusLabel: "delayed" as const,
      stops: [
        { crs: "EUS", arr: null, dep: "1100" },
        { crs: "WFJ", arr: "1130", dep: "1135" },
        { crs: "BHM", arr: "1300", dep: null },
      ],
    };
    const nearNow = {
      serviceGuid: "near",
      originCrs: "EUS",
      destCrs: "MKC",
      problem: false,
      stops: [
        { crs: "EUS", arr: null, dep: "1600" },
        { crs: "MKC", arr: "1730", dep: null },
      ],
    };
    assert.deepEqual(
      routeVisibleTrains([morning, nearNow, tracked], nowHhmm, "evening").map(
        (train) => train.serviceGuid,
      ),
      ["near", "evening"],
    );
    // Tracked train stays even when its next event is outside the live window.
    const finishedEarly = {
      serviceGuid: "dawn",
      originCrs: "EUS",
      destCrs: "BHM",
      problem: false,
      stops: [
        { crs: "EUS", arr: null, dep: "0700" },
        { crs: "BHM", arr: "0900", dep: null },
      ],
    };
    assert.deepEqual(
      routeVisibleTrains([finishedEarly, tracked], "1200", "evening").map(
        (train) => train.serviceGuid,
      ),
      ["evening"],
    );
  });

  it("applies the live-schedule filters to disruption rows", () => {
    const vm = createInitialViewModel();
    const now = new Date("2026-11-06T13:00:00Z");
    vm.networkHeld = {
      calendarDate: "261106",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        {
          serviceGuid: "in-window",
          originCrs: "CRE",
          destCrs: "STA",
          scheduledTime: "1300",
          problem: true,
          eventTags: ["delayed"],
          stops: [
            { crs: "CRE", arr: null, dep: "1200" },
            { crs: "STA", arr: "1400", dep: null },
          ],
        },
        {
          serviceGuid: "outside-window",
          originCrs: "CRE",
          destCrs: "STA",
          scheduledTime: "1800",
          problem: true,
          eventTags: ["delayed"],
        },
        {
          serviceGuid: "ok-in-window",
          originCrs: "EUS",
          destCrs: "MKC",
          scheduledTime: "1310",
          problem: false,
        },
        {
          serviceGuid: "other-station",
          originCrs: "BHM",
          destCrs: "NUN",
          scheduledTime: "1320",
          problem: true,
          eventTags: ["deactivated"],
          stops: [
            { crs: "BHM", arr: null, dep: "1200" },
            { crs: "NUN", arr: "1400", dep: null },
          ],
        },
      ],
    };
    assert.deepEqual(
      problemTrains(vm, now).map((train) => train.serviceGuid),
      ["in-window", "other-station"],
    );
    assert.deepEqual(disruptionSummary(vm, now), [
      { tag: "deactivated", count: 1 },
      { tag: "delayed", count: 1 },
    ]);
    vm.searchText = "cre";
    assert.deepEqual(
      problemTrains(vm, now).map((train) => train.serviceGuid),
      ["in-window"],
    );
    assert.deepEqual(disruptionSummary(vm, now), [{ tag: "delayed", count: 1 }]);
    vm.searchText = "";
    vm.scheduleFocus = { kind: "station", crs: "NUN" };
    assert.deepEqual(
      problemTrains(vm, now).map((train) => train.serviceGuid),
      ["other-station"],
    );
    vm.scheduleFocus = { kind: "station", crs: "MKC" };
    assert.equal(disruptionMessage(vm, now), "No interesting events");
  });

  it("filters live schedules by selected disruption summary tag", () => {
    const vm = createInitialViewModel();
    const now = new Date("2026-11-06T13:00:00Z");
    vm.networkHeld = {
      calendarDate: "261106",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        {
          serviceGuid: "late",
          scheduledTime: "1300",
          problem: true,
          eventTags: ["delayed", "call_forecast"],
        },
        {
          serviceGuid: "done",
          scheduledTime: "1310",
          problem: true,
          eventTags: ["deactivated"],
        },
        {
          serviceGuid: "ok",
          scheduledTime: "1320",
          problem: false,
          eventTags: [],
        },
      ],
    };
    vm.cancellationsHeld = [
      { serviceGuid: "cx", trainUid: "C1", eventTags: ["cancelled"] },
    ];
    assert.deepEqual(
      disruptionSummary(vm, now).map((row) => row.tag),
      ["cancelled", "deactivated", "delayed", "call_forecast"],
    );
    vm.networkEventFilter = "delayed";
    assert.deepEqual(
      visibleNetworkTrains(vm, now).map((train) => train.serviceGuid),
      ["late"],
    );
    vm.networkEventFilter = nextNetworkEventFilter("delayed", "delayed");
    assert.equal(vm.networkEventFilter, null);
    vm.networkEventFilter = "cancelled";
    assert.deepEqual(
      visibleNetworkTrains(vm, now).map((train) => train.serviceGuid),
      ["cx"],
    );
  });

  it("orders a window that crosses midnight by scheduled time", () => {
    const vm = createInitialViewModel();
    const now = new Date("2026-11-06T00:30:00Z");
    vm.networkHeld = {
      calendarDate: "261106",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        { serviceGuid: "after", scheduledTime: "0130", problem: false },
        { serviceGuid: "too-late", scheduledTime: "0300", problem: false },
        { serviceGuid: "before", scheduledTime: "2300", problem: false },
      ],
    };
    assert.deepEqual(
      visibleNetworkTrains(vm, now).map((train) => train.serviceGuid),
      ["before", "after"],
    );
  });

  it("counts on time as a percent only when a train is active", () => {
    const vm = createInitialViewModel();
    assert.equal(networkFooter(vm).ready, false);
    vm.networkHeld = { calendarDate: "251102",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [], trains: [] };
    const empty = networkFooter(vm);
    assert.equal(empty.ready, true);
    if (!empty.ready) return;
    assert.equal(empty.active, 0);
    assert.equal(empty.onTimePercent, null);
    assert.equal(empty.updatesPerMin, null);
    vm.networkHeld = {
      calendarDate: "251102",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        { serviceGuid: "a", problem: false },
        { serviceGuid: "b", problem: true },
      ],
    };
    vm.updatesPerMin = 12;
    const counted = networkFooter(vm);
    assert.equal(counted.ready, true);
    if (!counted.ready) return;
    assert.equal(counted.active, 2);
    assert.equal(counted.onTime, 1);
    assert.equal(counted.delayed, 1);
    assert.equal(counted.onTimePercent, 50);
    assert.equal(counted.updatesPerMin, 12);
  });
});

describe("updates per minute", () => {
  it("scales applied kafka rows to a per-minute rate", () => {
    assert.equal(updatesPerMinute(5, 30), 10);
    assert.equal(updatesPerMinute(0, 30), 0);
  });

  it("records the rate from the last delta poll", () => {
    const vm = createInitialViewModel();
    vm.intervalSeconds = 30;
    recordPollUpdates(
      vm,
      { generation: "g1", revision: 10 },
      {
        calendarDate: "251102",
        generation: "g1",
        revision: 25,
        full: false,
        removed: [],
        trains: [],
      },
    );
    assert.equal(vm.updatesPerMin, 30);
  });

  it("clears the rate on a full generation change", () => {
    const vm = createInitialViewModel();
    vm.updatesPerMin = 40;
    recordPollUpdates(
      vm,
      { generation: "g1", revision: 10 },
      {
        calendarDate: "251102",
        generation: "g2",
        revision: 0,
        full: true,
        removed: [],
        trains: [],
      },
    );
    assert.equal(vm.updatesPerMin, null);
  });
});

describe("pair filter", () => {
  it("shows Nothing in this filter when the chosen subset is empty", () => {
    const vm = createInitialViewModel();
    vm.pairEarlier = "CRE";
    vm.pairLater = "BHM";
    vm.pairHeld = {
      calendarDate: "251102",
      earlierCrs: "CRE",
      laterCrs: "BHM",
      trains: [
        { serviceGuid: "run", status: "on time", issue: false },
        {
          serviceGuid: null,
          trainUid: "C1",
          status: "cancelled",
          cancellationLabel: "cancelled",
          issue: true,
        },
      ],
    };
    vm.pairFilter = "delayed";
    assert.equal(visiblePairTrains(vm).length, 0);
    assert.equal(pairListMessage(vm), "Nothing in this filter");
    vm.pairFilter = "cancelled";
    assert.equal(visiblePairTrains(vm)[0]?.trainUid, "C1");
  });
});

describe("mergeNetworkAnswer", () => {
  it("replaces one held train and leaves the others", () => {
    const vm = createInitialViewModel();
    vm.networkHeld = {
      calendarDate: "251102",
      generation: "g1",
      revision: 1,
      full: true,
      removed: [],
      trains: [
        { serviceGuid: "a", scheduledTime: "1200", problem: false },
        { serviceGuid: "b", scheduledTime: "1300", problem: false },
      ],
    };
    mergeNetworkAnswer(vm, {
      calendarDate: "251102",
      generation: "g1",
      revision: 2,
      full: false,
      removed: [],
      trains: [{ serviceGuid: "b", scheduledTime: "1310", problem: true }],
    });
    assert.equal(vm.networkHeld?.trains.length, 2);
    assert.equal(
      vm.networkHeld?.trains.find((t) => t.serviceGuid === "b")?.scheduledTime,
      "1310",
    );
    assert.equal(vm.networkHeld?.revision, 2);
  });

  it("moves a removed guid onto the pair panel as a cancellation", () => {
    const vm = createInitialViewModel();
    vm.pairEarlier = "CRE";
    vm.pairLater = "BHM";
    vm.networkHeld = {
      calendarDate: "251102",
      generation: "g1",
      revision: 1,
      full: true,
      removed: [],
      trains: [
        {
          serviceGuid: "gone",
          problem: false,
          stops: [
            { crs: "CRE", arr: null, dep: "0900" },
            { crs: "BHM", arr: "1000", dep: "1000" },
          ],
        },
      ],
    };
    mergeNetworkAnswer(vm, {
      calendarDate: "251102",
      generation: "g1",
      revision: 2,
      full: false,
      removed: [{ serviceGuid: "gone", trainUid: "C1" }],
      trains: [],
    });
    assert.equal(vm.networkHeld?.trains.length, 0);
    assert.equal(vm.cancellationsHeld[0]?.trainUid, "C1");
    const c = createScreenController(stubApi({}));
    Object.assign(c.getView(), vm);
    c.setPairCrs("CRE", "BHM");
    assert.ok(
      c.getView().pairHeld?.trains.some((t) => t.status === "cancelled"),
    );
  });

  it("replaces the snapshot on a new generation", () => {
    const vm = createInitialViewModel();
    vm.cancellationsHeld = [{ serviceGuid: "old", trainUid: "X" }];
    vm.networkHeld = {
      calendarDate: "251102",
      generation: "g1",
      revision: 9,
      full: true,
      removed: [],
      trains: [{ serviceGuid: "old", problem: false }],
    };
    mergeNetworkAnswer(vm, {
      calendarDate: "251103",
      generation: "g2",
      revision: 0,
      full: true,
      removed: [],
      trains: [{ serviceGuid: "new", problem: false }],
    });
    assert.equal(vm.networkHeld?.generation, "g2");
    assert.equal(vm.networkHeld?.trains[0]?.serviceGuid, "new");
    assert.equal(vm.cancellationsHeld.length, 0);
  });

  it("keeps held trains and revision when comms fails", async () => {
    const held: NetworkAnswer = {
      calendarDate: "251102",
      generation: "g1",
      revision: 4,
      full: true,
      removed: [],
      trains: [{ serviceGuid: "a", problem: false }],
    };
    const c = createScreenController(
      stubApi({ getNetwork: async () => ({ ok: false, kind: "comms" }) }),
    );
    c.getView().loadSucceeded = true;
    c.getView().networkHeld = held;
    await c.tick();
    assert.equal(c.getView().commsLost, true);
    assert.equal(c.getView().networkHeld?.revision, 4);
    assert.equal(c.getView().networkHeld?.trains[0]?.serviceGuid, "a");
  });
});

describe("route page controller", () => {
  const train = {
    serviceGuid: "1M45",
    originCrs: "EUS",
    destCrs: "BHM",
    problem: false,
    stops: [
      { crs: "EUS", arr: null, dep: "1530" },
      { crs: "BHM", arr: "1700", dep: null },
    ],
  };

  it("opens a route track from a train and clears preview", () => {
    const c = createScreenController(stubApi({}));
    assert.equal(c.setRoutePreview({ serviceGuid: "1M45" }), true);
    assert.equal(c.setRoutePreview({ serviceGuid: "1M45" }), false);
    assert.equal(c.openRouteFromTrain(train), true);
    assert.equal(c.getView().page, "route");
    assert.deepEqual(c.getView().routeTrack, {
      kind: "train",
      serviceGuid: "1M45",
      lastWindowKey: null,
    });
    assert.equal(c.getView().routePreview, null);
  });

  it("opens a route track from a station focus", () => {
    const c = createScreenController(stubApi({}));
    assert.equal(c.openRouteFromStation("mkc"), true);
    assert.equal(c.getView().page, "route");
    assert.deepEqual(c.getView().routeTrack, {
      kind: "station",
      crs: "MKC",
      lastWindowKey: "at:MKC",
    });
  });

  it("selects another train on the open route and clears back to network", () => {
    const c = createScreenController(stubApi({}));
    c.getView().networkHeld = {
      calendarDate: "261106",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        train,
        {
          serviceGuid: "2A08",
          originCrs: "EUS",
          destCrs: "BHM",
          problem: false,
          stops: [
            { crs: "EUS", arr: null, dep: "1600" },
            { crs: "BHM", arr: "1730", dep: null },
          ],
        },
      ],
    };
    c.openRouteFromTrain(train);
    c.selectRouteTrain("2A08");
    const track = c.getView().routeTrack;
    assert.ok(track);
    assert.equal(track.kind, "train");
    assert.equal(track.kind === "train" ? track.serviceGuid : null, "2A08");
    c.clearRoute();
    assert.equal(c.getView().page, "network");
    assert.equal(c.getView().routeTrack, null);
  });

  it("refuses a train with no usable ends", () => {
    const c = createScreenController(stubApi({}));
    assert.equal(c.openRouteFromTrain({ serviceGuid: "x", problem: false, stops: [] }), false);
    assert.equal(c.getView().page, "network");
  });

  it("rewindows when the tracked train advances past the next station", () => {
    const vm = createInitialViewModel();
    vm.diagramHeld = {
      loadDay: "20261106",
      hdSequence: "1",
      stations: ["EUS", "WFJ", "MKC", "NMP", "BHM"].map((crs, rank) => ({
        crs,
        x: rank * 100,
        y: 80,
        rank,
        lane: 0,
      })),
      corridors: [
        {
          from: "EUS",
          to: "BHM",
          via: ["WFJ", "MKC", "NMP"],
          points: [],
          color: "#7dffa8",
        },
      ],
      width: 500,
      height: 160,
    };
    vm.networkHeld = {
      calendarDate: "261106",
      generation: "g1",
      revision: 0,
      full: true,
      removed: [],
      trains: [
        {
          serviceGuid: "1M45",
          originCrs: "EUS",
          destCrs: "BHM",
          problem: false,
          stops: [
            { crs: "EUS", arr: null, dep: "1000" },
            { crs: "WFJ", arr: "1030", dep: "1035" },
            { crs: "MKC", arr: "1100", dep: "1105" },
            { crs: "BHM", arr: "1200", dep: null },
          ],
        },
      ],
    };
    vm.routeTrack = {
      kind: "train",
      serviceGuid: "1M45",
      lastWindowKey: null,
    };
    assert.equal(syncRouteWindowKey(vm, "1032"), true);
    assert.equal(vm.routeTrack?.lastWindowKey, "at:WFJ");
    const first = resolveRouteWindow(vm, "1032");
    assert.ok(first?.window.includes("WFJ"));

    assert.equal(syncRouteWindowKey(vm, "1032"), false);
    assert.equal(syncRouteWindowKey(vm, "1102"), true);
    assert.equal(vm.routeTrack?.lastWindowKey, "at:MKC");
  });
});
