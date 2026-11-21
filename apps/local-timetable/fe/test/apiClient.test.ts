import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { createApiClient } from "../src/apiClient.js";

describe("apiClient", () => {
  it("maps 503 train failure on network", async () => {
    const fetchFn = async () =>
      new Response(JSON.stringify({ reason: "missing" }), {
        status: 503,
        headers: { "Content-Type": "application/json" },
      });
    const api = createApiClient("http://example", fetchFn as typeof fetch);
    const result = await api.getNetwork();
    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.equal(result.kind, "train-failure");
      assert.equal(result.reason, "missing");
    }
  });

  it("returns comms on fetch throw", async () => {
    const fetchFn = async () => {
      throw new Error("offline");
    };
    const api = createApiClient("http://example", fetchFn as typeof fetch);
    const result = await api.getLoadProgress();
    assert.equal(result.ok, false);
    if (!result.ok) assert.equal(result.kind, "comms");
  });

  it("parses successful load progress", async () => {
    const fetchFn = async () =>
      new Response(JSON.stringify({ state: "running", sentence: "Working", howFar: "10" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    const api = createApiClient("http://example", fetchFn as typeof fetch);
    const result = await api.getLoadProgress();
    assert.equal(result.ok, true);
    if (result.ok) assert.equal(result.data.state, "running");
  });

  it("prefixes paths with baseUrl and sends Bearer token", async () => {
    let seenUrl = "";
    let seenAuth = "";
    const fetchFn = async (input: RequestInfo | URL, init?: RequestInit) => {
      seenUrl = String(input);
      const headers = new Headers(init?.headers);
      seenAuth = headers.get("Authorization") ?? "";
      return new Response(JSON.stringify({ state: "idle", sentence: "", howFar: "" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };
    const api = createApiClient("/api", {
      fetchFn: fetchFn as typeof fetch,
      getAccessToken: async () => "id.token.value",
    });
    await api.getLoadProgress();
    assert.equal(seenUrl, "/api/load");
    assert.equal(seenAuth, "Bearer id.token.value");
  });
});
