import type {
  AskResult,
  FailureBody,
  LoadProgress,
  NetworkAnswer,
  NetworkDiagram,
  StationPairAnswer,
} from "./types.js";

export type NetworkCursor = {
  since?: number;
  generation?: string;
};

export type ApiClient = {
  startLoad: () => Promise<AskResult<LoadProgress>>;
  getLoadProgress: () => Promise<AskResult<LoadProgress>>;
  getNetwork: (cursor?: NetworkCursor) => Promise<AskResult<NetworkAnswer>>;
  getNetworkMap: () => Promise<AskResult<NetworkDiagram>>;
  postPair: (earlierCrs: string, laterCrs: string) => Promise<AskResult<StationPairAnswer>>;
};

function isFailureBody(value: unknown): value is FailureBody {
  if (typeof value !== "object" || value === null) return false;
  const reason = (value as FailureBody).reason;
  return (
    reason === "missing" ||
    reason === "deleted" ||
    reason === "locked" ||
    reason === "older-schema"
  );
}

async function readJson(res: Response): Promise<unknown> {
  const text = await res.text();
  if (!text.trim()) return {};
  return JSON.parse(text) as unknown;
}

export function createApiClient(baseUrl: string, fetchFn: typeof fetch = fetch): ApiClient {
  const base = baseUrl.replace(/\/$/, "");

  async function ask<T>(
    path: string,
    init: RequestInit,
    trainAsk: boolean,
  ): Promise<AskResult<T>> {
    try {
      const res = await fetchFn(`${base}${path}`, {
        ...init,
        headers: {
          Accept: "application/json",
          "Accept-Encoding": "gzip",
          ...(init.headers ?? {}),
        },
      });
      let body: unknown;
      try {
        body = await readJson(res);
      } catch {
        return { ok: false, kind: "parse" };
      }
      if (trainAsk && res.status === 503 && isFailureBody(body)) {
        return { ok: false, kind: "train-failure", reason: body.reason };
      }
      if (!res.ok) {
        return { ok: false, kind: trainAsk ? "train-failure" : "parse" };
      }
      return { ok: true, data: body as T };
    } catch {
      return { ok: false, kind: "comms" };
    }
  }

  return {
    startLoad: () =>
      ask<LoadProgress>("/load", { method: "POST" }, false),
    getLoadProgress: () =>
      ask<LoadProgress>("/load", { method: "GET" }, false),
    getNetwork: (cursor = {}) => {
      const params = new URLSearchParams();
      if (cursor.since !== undefined) {
        params.set("since", String(cursor.since));
      }
      if (cursor.generation !== undefined) {
        params.set("generation", cursor.generation);
      }
      const query = params.toString();
      return ask<NetworkAnswer>(
        `/network${query ? `?${query}` : ""}`,
        { method: "GET" },
        true,
      );
    },
    getNetworkMap: () =>
      ask<NetworkDiagram>("/network-map", { method: "GET" }, true),
    postPair: (earlierCrs, laterCrs) =>
      ask<StationPairAnswer>(
        "/pair",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ earlierCrs, laterCrs }),
        },
        true,
      ),
  };
}
