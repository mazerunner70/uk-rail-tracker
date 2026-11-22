import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { createGzip } from "node:zlib";
import type { LoadProgress } from "./loadProgress.js";
import { readNetworkDiagram } from "./networkSchematicFile.js";
import { CONTAINER_SQLITE_PATH } from "./paths.js";
import {
  buildNetworkAnswer,
  buildServiceDetailAnswer,
  buildStationPairAnswer,
  isFailureBody,
} from "./trainViews.js";
import type { LondonClock } from "./londonClock.js";

export type LoadHandlers = {
  startLoad: () => LoadProgress;
  getLoadProgress: () => LoadProgress;
};

export type HttpServerOptions = {
  port?: number;
  sqlitePath?: string;
  clock?: LondonClock;
  load?: LoadHandlers;
};

const GZIP_MIN_BYTES = 1024;

function wireLoadProgress(progress: LoadProgress): LoadProgress {
  return {
    state: progress.state,
    howFar: progress.howFar,
    sentence: progress.sentence,
  };
}

async function readJsonBody(req: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) {
    chunks.push(typeof chunk === "string" ? Buffer.from(chunk) : chunk);
  }
  const text = Buffer.concat(chunks).toString("utf8").trim();
  if (!text) {
    return {};
  }
  return JSON.parse(text) as unknown;
}

function sendJson(
  res: ServerResponse,
  status: number,
  body: unknown,
  req: IncomingMessage,
): void {
  const payload = JSON.stringify(body);
  const accept = req.headers["accept-encoding"] ?? "";
  if (
    payload.length >= GZIP_MIN_BYTES &&
    typeof accept === "string" &&
    accept.includes("gzip")
  ) {
    res.writeHead(status, {
      "Content-Type": "application/json; charset=utf-8",
      "Content-Encoding": "gzip",
    });
    const gzip = createGzip();
    gzip.pipe(res);
    gzip.end(payload);
    return;
  }
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(payload);
}

function methodNotAllowed(res: ServerResponse): void {
  res.writeHead(405, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify({ error: "method not allowed" }));
}

export function createTimetableHttpServer(
  options: HttpServerOptions = {},
): ReturnType<typeof createServer> {
  const port = options.port ?? 8080;
  const sqlitePath = options.sqlitePath ?? CONTAINER_SQLITE_PATH;
  const clock = options.clock;
  const load = options.load;

  return createServer(async (req, res) => {
    try {
      const requestUrl = new URL(req.url ?? "/", "http://localhost");
      const url = requestUrl.pathname;
      if (url === "/load") {
        if (!load) {
          sendJson(res, 503, { error: "load not configured" }, req);
          return;
        }
        if (req.method === "POST") {
          sendJson(res, 202, wireLoadProgress(load.startLoad()), req);
          return;
        }
        if (req.method === "GET") {
          sendJson(res, 200, wireLoadProgress(load.getLoadProgress()), req);
          return;
        }
        methodNotAllowed(res);
        return;
      }

      if (url === "/network-map" && req.method === "GET") {
        const diagram = readNetworkDiagram(sqlitePath);
        if ("reason" in diagram) {
          sendJson(res, 503, diagram, req);
          return;
        }
        sendJson(res, 200, diagram, req);
        return;
      }

      if (url === "/network" && req.method === "GET") {
        const sinceRaw = requestUrl.searchParams.get("since");
        const generation = requestUrl.searchParams.get("generation") ?? undefined;
        const since =
          sinceRaw !== null && sinceRaw !== "" && /^\d+$/.test(sinceRaw)
            ? Number(sinceRaw)
            : undefined;
        const answer = buildNetworkAnswer({
          sqlitePath,
          clock,
          since,
          generation,
        });
        if (isFailureBody(answer)) {
          sendJson(res, 503, answer, req);
          return;
        }
        sendJson(res, 200, answer, req);
        return;
      }

      if (url === "/pair" && req.method === "POST") {
        const body = (await readJsonBody(req)) as {
          earlierCrs?: string;
          laterCrs?: string;
        };
        if (!body.earlierCrs || !body.laterCrs) {
          sendJson(res, 400, { error: "earlierCrs and laterCrs required" }, req);
          return;
        }
        const answer = buildStationPairAnswer({
          sqlitePath,
          clock,
          earlierCrs: body.earlierCrs,
          laterCrs: body.laterCrs,
        });
        if (isFailureBody(answer)) {
          sendJson(res, 503, answer, req);
          return;
        }
        sendJson(res, 200, answer, req);
        return;
      }

      if (url === "/service" && req.method === "GET") {
        const serviceGuid = requestUrl.searchParams.get("guid")?.trim() ?? "";
        if (!serviceGuid) {
          sendJson(res, 400, { error: "guid required" }, req);
          return;
        }
        const answer = buildServiceDetailAnswer({
          sqlitePath,
          clock,
          serviceGuid,
        });
        if (isFailureBody(answer)) {
          sendJson(res, 503, answer, req);
          return;
        }
        if ("missing" in answer) {
          sendJson(res, 404, { error: "service not found" }, req);
          return;
        }
        sendJson(res, 200, answer, req);
        return;
      }

      res.writeHead(404, { "Content-Type": "application/json; charset=utf-8" });
      res.end(JSON.stringify({ error: "not found" }));
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      sendJson(res, 500, { error: message }, req);
    }
  });
}
