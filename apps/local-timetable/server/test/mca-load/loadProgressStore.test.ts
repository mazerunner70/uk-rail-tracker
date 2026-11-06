import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { IDLE_PROGRESS } from "../../src/loadProgress.js";
import { createLoadProgressStore } from "../../src/loadProgressStore.js";

describe("LoadProgressStore", () => {
  it("starts idle with a sentence and no howFar", () => {
    const store = createLoadProgressStore();
    const value = store.get();
    assert.equal(value.state, "idle");
    assert.equal(value.sentence, IDLE_PROGRESS.sentence);
    assert.equal(value.howFar, undefined);
  });

  it("records running progress with howFar as text", () => {
    const store = createLoadProgressStore();
    store.set({ state: "running", howFar: "40", sentence: "Reading today's MCA." });
    const value = store.get();
    assert.equal(value.state, "running");
    assert.equal(value.howFar, "40");
    assert.ok(value.sentence.length > 0);
  });

  it("returns the latest value from get", () => {
    const store = createLoadProgressStore();
    store.set({ state: "running", howFar: "10", sentence: "Looking for today's MCA." });
    store.set({ state: "succeeded", howFar: "100", sentence: "Today's timetable is loaded." });
    assert.equal(store.get().state, "succeeded");
    assert.equal(store.get().howFar, "100");
  });

  it("records failed with a sentence", () => {
    const store = createLoadProgressStore();
    store.set({ state: "failed", sentence: "The MCA cannot be read." });
    assert.equal(store.get().state, "failed");
    assert.ok(store.get().sentence.includes("cannot be read"));
  });

  it("a new store is idle after process-like death", () => {
    const first = createLoadProgressStore();
    first.set({ state: "running", howFar: "12", sentence: "Reading today's MCA." });
    const second = createLoadProgressStore();
    assert.equal(second.get().state, "idle");
    assert.equal(first.get().state, "running");
  });

  it("keeps sentence present on every state", () => {
    const store = createLoadProgressStore();
    for (const next of [
      { state: "idle" as const, sentence: "No load has been started." },
      { state: "running" as const, howFar: "0", sentence: "Looking for today's MCA." },
      { state: "succeeded" as const, howFar: "100", sentence: "Today's timetable is loaded." },
      { state: "failed" as const, sentence: "The MCA header was refused." },
    ]) {
      store.set(next);
      assert.equal(typeof store.get().sentence, "string");
      assert.ok(store.get().sentence.length > 0);
    }
  });

  it("does not start a load by itself", () => {
    const store = createLoadProgressStore();
    store.get();
    store.get();
    assert.equal(store.get().state, "idle");
  });
});
