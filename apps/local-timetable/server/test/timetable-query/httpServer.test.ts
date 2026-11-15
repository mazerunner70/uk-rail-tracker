import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { request as httpRequest } from "node:http";
import { describe, it } from "node:test";
import { createTimetableHttpServer } from "../../src/httpServer.js";
import { networkSchematicPath } from "../../src/networkSchematicFile.js";
import {
  CALENDAR_YYMMDD,
  createFixtureDb,
  insertLmJourney,
} from "./helpers.js";

async function request(
  port: number,
  method: string,
  path: string,
  body?: unknown,
): Promise<{ status: number; json: unknown }> {
  const payload = body === undefined ? undefined : JSON.stringify(body);
  return new Promise((resolve, reject) => {
    const req = httpRequest(
      {
        hostname: "127.0.0.1",
        port,
        path,
        method,
        headers: payload
          ? {
              "Content-Type": "application/json",
              "Content-Length": Buffer.byteLength(payload),
            }
          : {},
      },
      (res) => {
        const chunks: Buffer[] = [];
        res.on("data", (chunk) => chunks.push(chunk as Buffer));
        res.on("end", () => {
          const text = Buffer.concat(chunks).toString("utf8");
          resolve({
            status: res.statusCode ?? 0,
            json: text ? JSON.parse(text) : null,
          });
        });
      },
    );
    req.on("error", reject);
    if (payload) {
      req.write(payload);
    }
    req.end();
  });
}

describe("httpServer", () => {
  it("returns 503 when the timetable file is missing", async () => {
    const server = createTimetableHttpServer({
      sqlitePath: "/tmp/no-such-timetable-for-http.sqlite",
      load: {
        startLoad: () => ({ state: "idle", sentence: "idle" }),
        getLoadProgress: () => ({ state: "idle", sentence: "idle" }),
      },
    });
    await new Promise<void>((resolve) => server.listen(0, resolve));
    const address = server.address();
    if (!address || typeof address === "string") {
      throw new Error("no port");
    }
    const response = await request(address.port, "GET", "/network");
    const map = await request(address.port, "GET", "/network-map");
    server.close();
    assert.equal(response.status, 503);
    assert.deepEqual(response.json, { reason: "missing" });
    assert.equal(map.status, 503);
    assert.deepEqual(map.json, { reason: "missing" });
  });

  it("returns network JSON for a fixture database", async () => {
    const { sqlitePath, db } = await createFixtureDb();
    insertLmJourney(db);
    db.close();
    const server = createTimetableHttpServer({
      sqlitePath,
      clock: {
        calendarYyymmdd: () => CALENDAR_YYMMDD,
        nowHhmm: () => "1000",
      },
      load: {
        startLoad: () => ({ state: "idle", sentence: "idle" }),
        getLoadProgress: () => ({ state: "idle", sentence: "idle" }),
      },
    });
    await new Promise<void>((resolve) => server.listen(0, resolve));
    const address = server.address();
    if (!address || typeof address === "string") {
      throw new Error("no port");
    }
    const response = await request(address.port, "GET", "/network");
    server.close();
    assert.equal(response.status, 200);
    const json = response.json as { trains: unknown[] };
    assert.equal(json.trains.length, 1);
  });

  it("returns 503 for network-map when the schematic file is missing", async () => {
    const { sqlitePath, db } = await createFixtureDb();
    insertLmJourney(db);
    db.prepare("INSERT INTO feed_meta (key, value) VALUES ('load_day', '20261027')").run();
    db.prepare("INSERT INTO feed_meta (key, value) VALUES ('hd_sequence', '004')").run();
    db.close();
    assert.equal(existsSync(networkSchematicPath(sqlitePath)), false);
    const server = createTimetableHttpServer({ sqlitePath });
    await new Promise<void>((resolve) => server.listen(0, resolve));
    const address = server.address();
    if (!address || typeof address === "string") {
      throw new Error("no port");
    }
    const response = await request(address.port, "GET", "/network-map");
    server.close();
    assert.equal(response.status, 503);
    assert.deepEqual(response.json, { reason: "missing" });
  });
});
