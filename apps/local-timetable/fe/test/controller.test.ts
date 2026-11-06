import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  createInitialViewModel,
  createScreenController,
  networkFooter,
  networkListMessage,
  pairListMessage,
  parseIntervalInput,
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

  it("sets comms lost without clearing held trains", async () => {
    const held: NetworkAnswer = { calendarDate: "251102", trains: [] };
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
    const network: NetworkAnswer = { calendarDate: "251102", trains: [] };
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
        getNetwork: async () => ({ ok: true, data: { calendarDate: "251102", trains: [] } }),
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
    vm.networkHeld = { calendarDate: "251102", trains: [] };
    vm.searchText = "cre";
    assert.equal(networkListMessage(vm), "No trains");
  });

  it("lists live trains through a station, and every train on an arrow", () => {
    const vm = createInitialViewModel();
    const now = new Date("2026-11-06T13:00:00Z");
    vm.networkHeld = {
      calendarDate: "261106",
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

  it("orders a window that crosses midnight by scheduled time", () => {
    const vm = createInitialViewModel();
    const now = new Date("2026-11-06T00:30:00Z");
    vm.networkHeld = {
      calendarDate: "261106",
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
    vm.networkHeld = { calendarDate: "251102", trains: [] };
    const empty = networkFooter(vm);
    assert.equal(empty.ready, true);
    if (!empty.ready) return;
    assert.equal(empty.active, 0);
    assert.equal(empty.onTimePercent, null);
    vm.networkHeld = {
      calendarDate: "251102",
      trains: [
        { serviceGuid: "a", problem: false },
        { serviceGuid: "b", problem: true },
      ],
    };
    const counted = networkFooter(vm);
    assert.equal(counted.ready, true);
    if (!counted.ready) return;
    assert.equal(counted.active, 2);
    assert.equal(counted.onTime, 1);
    assert.equal(counted.delayed, 1);
    assert.equal(counted.onTimePercent, 50);
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
        { serviceGuid: "run", status: "published", issue: false },
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
