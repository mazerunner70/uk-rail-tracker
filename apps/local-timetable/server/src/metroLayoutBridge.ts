import { spawn } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  diagramFromOctCoords,
  type DiagramBody,
  type LayoutInput,
  type OctStationCoord,
} from "./networkSchematic.js";

const HERE = dirname(fileURLToPath(import.meta.url));
/** Dist layout: dist/metroLayoutBridge.js → ../layout/metro_layout.py; src via tsx → ../layout. */
export const DEFAULT_METRO_LAYOUT_SCRIPT = join(HERE, "..", "layout", "metro_layout.py");

export type MetroLayoutEngine = "milp" | "geo";

export function metroLayoutEngine(): MetroLayoutEngine {
  const raw = (process.env.METRO_LAYOUT_ENGINE ?? "milp").trim().toLowerCase();
  if (raw === "geo") return "geo";
  // Legacy alias from the Gurobi OptiMods experiment.
  if (raw === "optimods" || raw === "milp" || raw === "highs" || raw === "pulp") return "milp";
  return "milp";
}

export function metroLayoutTimeLimitSec(): number {
  const raw = Number(process.env.METRO_LAYOUT_TIME_LIMIT_SEC ?? "1200");
  if (!Number.isFinite(raw) || raw <= 0) return 1200;
  return raw;
}

export type OctLayoutResult = {
  stations: OctStationCoord[];
  edges: { from: string; to: string }[];
};

function isOctLayoutResult(value: unknown): value is OctLayoutResult {
  if (typeof value !== "object" || value === null) return false;
  const row = value as OctLayoutResult;
  return Array.isArray(row.stations) && Array.isArray(row.edges);
}

export function diagramFromOctLayoutResult(result: OctLayoutResult): DiagramBody {
  return diagramFromOctCoords(result.stations, result.edges);
}

/**
 * Run PuLP+HiGHS metro_layout.py; returns octilinear station coords.
 * Throws on non-zero exit or invalid JSON.
 */
export function runMilpLayout(
  input: LayoutInput,
  options: {
    scriptPath?: string;
    timeLimitSec?: number;
    pythonPath?: string;
  } = {},
): Promise<OctLayoutResult> {
  const scriptPath = options.scriptPath ?? process.env.METRO_LAYOUT_SCRIPT ?? DEFAULT_METRO_LAYOUT_SCRIPT;
  const timeLimitSec = options.timeLimitSec ?? metroLayoutTimeLimitSec();
  const pythonPath = options.pythonPath ?? process.env.METRO_LAYOUT_PYTHON ?? "python3";

  return new Promise((resolve, reject) => {
    const child = spawn(
      pythonPath,
      [scriptPath, "--time-limit-sec", String(timeLimitSec)],
      { stdio: ["pipe", "pipe", "pipe"] },
    );
    let stdout = "";
    let stderr = "";
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (chunk: string) => {
      stdout += chunk;
    });
    child.stderr.on("data", (chunk: string) => {
      stderr += chunk;
      process.stderr.write(chunk);
    });
    child.on("error", (err) => {
      reject(err);
    });
    child.on("close", (code) => {
      if (code !== 0) {
        reject(new Error(`metro_layout.py exited ${code}: ${stderr.trim() || stdout.trim()}`));
        return;
      }
      try {
        // HiGHS may leak a banner onto stdout; take the last JSON object line.
        const jsonLine = stdout
          .split("\n")
          .map((line) => line.trim())
          .filter((line) => line.startsWith("{") && line.endsWith("}"))
          .at(-1);
        if (!jsonLine) {
          reject(new Error("metro_layout.py produced no JSON on stdout"));
          return;
        }
        const parsed: unknown = JSON.parse(jsonLine);
        if (!isOctLayoutResult(parsed)) {
          reject(new Error("metro_layout.py returned unexpected JSON shape"));
          return;
        }
        resolve(parsed);
      } catch (err) {
        reject(err instanceof Error ? err : new Error(String(err)));
      }
    });
    child.stdin.write(JSON.stringify(input));
    child.stdin.end();
  });
}

/** @deprecated Use runMilpLayout */
export const runOptiModsLayout = runMilpLayout;

/** Apply a pre-parsed octilinear JSON fixture (for tests without spawning Python). */
export function applyOctLayoutJson(raw: unknown): DiagramBody {
  if (!isOctLayoutResult(raw)) {
    throw new Error("oct layout JSON has unexpected shape");
  }
  return diagramFromOctLayoutResult(raw);
}
