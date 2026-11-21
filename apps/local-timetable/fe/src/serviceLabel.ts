import type { NetworkStop, NetworkTrain } from "./types.js";

function crs3(value: string | null | undefined): string {
  const text = (value ?? "").trim().toUpperCase();
  if (!text) return "???";
  return text.slice(0, 3).padEnd(3, "?");
}

/** Digits-only HHMM from a Darwin/public time string. */
export function hhmm4(value: string | null | undefined): string | null {
  if (!value) return null;
  const digits = [...value].filter((ch) => ch >= "0" && ch <= "9").join("");
  return digits.length >= 4 ? digits.slice(0, 4) : null;
}

function originDeparture(stops: readonly NetworkStop[]): string | null {
  const first = stops.find((stop) => stop.crs);
  if (!first) return null;
  return hhmm4(first.dep) ?? hhmm4(first.arr);
}

/** Short service label: ORGDST-HHMM (origin CRS + dest CRS + origin STD). */
export function serviceShortLabel(train: {
  originCrs?: string | null;
  destCrs?: string | null;
  scheduledTime?: string | null;
  stops?: readonly NetworkStop[] | null;
}): string {
  const stops = train.stops ?? [];
  const origin =
    train.originCrs
    ?? stops.find((stop) => stop.crs)?.crs
    ?? null;
  const dest =
    train.destCrs
    ?? [...stops].reverse().find((stop) => stop.crs)?.crs
    ?? null;
  const time =
    originDeparture(stops)
    ?? hhmm4(train.scheduledTime)
    ?? "----";
  return `${crs3(origin)}${crs3(dest)}-${time}`;
}

export function serviceShortLabelsForGuids(
  guids: readonly string[],
  byGuid: ReadonlyMap<string, NetworkTrain>,
): string {
  return guids
    .map((guid) => {
      const train = byGuid.get(guid);
      return train ? serviceShortLabel(train) : guid;
    })
    .join("\n");
}

export function trainLabelMap(
  trains: readonly NetworkTrain[],
): Map<string, NetworkTrain> {
  const map = new Map<string, NetworkTrain>();
  for (const train of trains) map.set(train.serviceGuid, train);
  return map;
}
