import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

export type StationGeo = { lat: number; lon: number };

export type GeoIndex = ReadonlyMap<string, StationGeo>;

type GeoRow = { crs?: unknown; lat?: unknown; lon?: unknown };

const GEO_FILENAME = "station-geo.json";

export function stationGeoPath(): string {
  return join(dirname(fileURLToPath(import.meta.url)), "..", "data", GEO_FILENAME);
}

function isGeoRow(row: unknown): row is { crs: string; lat: number; lon: number } {
  if (typeof row !== "object" || row === null) return false;
  const candidate = row as GeoRow;
  return typeof candidate.crs === "string"
    && candidate.crs.length > 0
    && typeof candidate.lat === "number"
    && Number.isFinite(candidate.lat)
    && typeof candidate.lon === "number"
    && Number.isFinite(candidate.lon);
}

export function indexStationGeo(rows: readonly unknown[]): GeoIndex {
  const out = new Map<string, StationGeo>();
  for (const row of rows) {
    if (!isGeoRow(row)) continue;
    out.set(row.crs, { lat: row.lat, lon: row.lon });
  }
  return out;
}

/** Load CRS → lat/lon. Returns empty map if the file is missing or invalid. */
export function loadStationGeo(path = stationGeoPath()): GeoIndex {
  try {
    const parsed: unknown = JSON.parse(readFileSync(path, "utf8"));
    if (!Array.isArray(parsed)) return new Map();
    return indexStationGeo(parsed);
  } catch {
    return new Map();
  }
}
