import type {
  AskResult,
  FailureBody,
  LoadProgress,
  NetworkAnswer,
  NetworkDiagram,
  ServiceDetail,
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
  getService: (serviceGuid: string) => Promise<AskResult<ServiceDetail>>;
  postPair: (earlierCrs: string, laterCrs: string) => Promise<AskResult<StationPairAnswer>>;
};

export type CreateApiClientOptions = {
  fetchFn?: typeof fetch;
  getAccessToken?: () => string | null | Promise<string | null>;
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

export function createApiClient(
  baseUrl: string,
  fetchFnOrOptions: typeof fetch | CreateApiClientOptions = fetch,
): ApiClient {
  const options: CreateApiClientOptions =
    typeof fetchFnOrOptions === "function"
      ? { fetchFn: fetchFnOrOptions }
      : fetchFnOrOptions;
  const fetchFn = options.fetchFn ?? fetch;
  const getAccessToken = options.getAccessToken;
  const base = baseUrl.replace(/\/$/, "");

  async function ask<T>(
    path: string,
    init: RequestInit,
    trainAsk: boolean,
  ): Promise<AskResult<T>> {
    try {
      const headers: Record<string, string> = {
        Accept: "application/json",
        "Accept-Encoding": "gzip",
      };
      if (init.headers) {
        const h = new Headers(init.headers);
        h.forEach((value, key) => {
          headers[key] = value;
        });
      }
      if (getAccessToken) {
        const token = await getAccessToken();
        if (token) headers.Authorization = `Bearer ${token}`;
      }
      const res = await fetchFn(`${base}${path}`, {
        ...init,
        headers,
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
    startLoad: () => ask<LoadProgress>("/load", { method: "POST" }, false),
    getLoadProgress: () => ask<LoadProgress>("/load", { method: "GET" }, false),
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
    getService: (serviceGuid: string) => {
      const params = new URLSearchParams({ guid: serviceGuid });
      return ask<ServiceDetail>(`/service?${params}`, { method: "GET" }, true);
    },
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
