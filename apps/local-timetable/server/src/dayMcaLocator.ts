import { readdirSync } from "node:fs";
import { join } from "node:path";

export type LocateResult =
  | { ok: true; path: string }
  | { ok: false; reason: "missing-dir" | "no-match" | "duplicate" };

export function londonYyyymmdd(now = new Date()): string {
  const fmt = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/London",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const parts = fmt.formatToParts(now);
  const year = parts.find((part) => part.type === "year")?.value;
  const month = parts.find((part) => part.type === "month")?.value;
  const day = parts.find((part) => part.type === "day")?.value;
  if (!year || !month || !day) {
    throw new Error("could not format Europe/London calendar date");
  }
  return `${year}${month}${day}`;
}

export function locateDayMca(mcaDir: string, calendarDate: string): LocateResult {
  let names: string[];
  try {
    names = readdirSync(mcaDir);
  } catch {
    return { ok: false, reason: "missing-dir" };
  }
  const matches = names.filter((name) => name.includes(calendarDate));
  if (matches.length === 0) {
    return { ok: false, reason: "no-match" };
  }
  if (matches.length > 1) {
    return { ok: false, reason: "duplicate" };
  }
  const filename = matches[0];
  if (!filename) {
    return { ok: false, reason: "no-match" };
  }
  return { ok: true, path: join(mcaDir, filename) };
}
