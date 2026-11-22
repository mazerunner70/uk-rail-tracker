export type PushPortPayload = Record<string, unknown>;
export type PushPortItem = Record<string, unknown>;

export type UpdateType = "TS" | "schedule" | "deactivated";

export type UpdateItem = {
  type: UpdateType;
  item: PushPortItem;
};

const APPLY_TYPES: readonly UpdateType[] = ["TS", "schedule", "deactivated"];
const WRAPPER_KEYS = ["uR", "sR"] as const;

function asObject(value: unknown): PushPortPayload | null {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return null;
  }
  return value as PushPortPayload;
}

export function decodePushPort(raw: Buffer | string | object): PushPortPayload | null {
  if (typeof raw === "object" && !Buffer.isBuffer(raw)) {
    return asObject(raw);
  }
  const text = Buffer.isBuffer(raw) ? raw.toString("utf8") : raw;
  let envelope: unknown;
  try {
    envelope = JSON.parse(text);
  } catch {
    return null;
  }
  const typed = asObject(envelope);
  if (!typed) {
    return null;
  }
  const inner = typed.bytes;
  if (typeof inner === "string") {
    try {
      const nested = JSON.parse(inner);
      const nestedObject = asObject(nested);
      if (nestedObject) {
        return nestedObject;
      }
    } catch {
      return typed;
    }
  }
  return typed;
}

function wrapperBody(payload: PushPortPayload): PushPortPayload | null {
  for (const key of WRAPPER_KEYS) {
    const body = asObject(payload[key]);
    if (body) {
      return body;
    }
  }
  return null;
}

function asItemList(value: unknown): PushPortItem[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is PushPortItem => asObject(item) !== null);
  }
  const single = asObject(value);
  return single ? [single] : [];
}

export function updateItems(payload: PushPortPayload): UpdateItem[] {
  const body = wrapperBody(payload);
  if (!body) {
    return [];
  }
  const items: UpdateItem[] = [];
  for (const type of APPLY_TYPES) {
    for (const item of asItemList(body[type])) {
      items.push({ type, item });
    }
  }
  return items;
}

function nonEmptyString(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }
  const text = value.trim();
  return text ? text : null;
}

export function extractRid(item: PushPortItem): string | null {
  return nonEmptyString(item.rid);
}

export function extractUid(item: PushPortItem): string | null {
  for (const key of ["uid", "trainUid", "train_uid"] as const) {
    const value = nonEmptyString(item[key]);
    if (value) {
      return value;
    }
  }
  return null;
}

export function extractSsd(item: PushPortItem): string | null {
  const value = nonEmptyString(item.ssd);
  if (!value) {
    return null;
  }
  const digits = [...value].filter((ch) => ch >= "0" && ch <= "9").join("");
  if (digits.length === 8) {
    return digits.slice(2);
  }
  if (digits.length === 6) {
    return digits;
  }
  return value;
}

function forecastFromNode(node: unknown): string | null {
  const object = asObject(node);
  if (!object) {
    return null;
  }
  for (const key of ["at", "et", "wet"] as const) {
    const value = nonEmptyString(object[key]);
    if (!value) {
      continue;
    }
    const digits = [...value].filter((ch) => ch >= "0" && ch <= "9").join("");
    if (digits.length >= 4) {
      return digits.slice(0, 4);
    }
  }
  return null;
}

export type LocationForecast = {
  tiploc: string;
  forecastArr: string | null;
  forecastDep: string | null;
  platform: string | null;
  platformSuppressed: boolean | null;
  trainLength: number | null;
};

function truthyFlag(value: unknown): boolean {
  return value === true || value === "true" || value === "1" || value === 1;
}

function platformText(location: PushPortItem): string | null {
  const plat = location.plat;
  if (plat === undefined || plat === null || plat === "") {
    return null;
  }
  if (typeof plat === "string") {
    const text = plat.trim();
    return text || null;
  }
  if (typeof plat === "object" && !Array.isArray(plat)) {
    const obj = plat as Record<string, unknown>;
    return nonEmptyString(obj[""]) ?? nonEmptyString(obj["#text"]);
  }
  return String(plat);
}

function trainLengthValue(location: PushPortItem): number | null {
  const length = location.length;
  if (typeof length === "number" && Number.isFinite(length) && length > 0) {
    return Math.trunc(length);
  }
  if (typeof length === "string") {
    const n = Number(length.trim());
    if (Number.isFinite(n) && n > 0) {
      return Math.trunc(n);
    }
  }
  return null;
}

function platformSuppressedFlag(location: PushPortItem): boolean | null {
  if (
    "platsup" in location
    || "cisPlatsup" in location
    || "suppr" in location
  ) {
    return (
      truthyFlag(location.platsup)
      || truthyFlag(location.cisPlatsup)
      || truthyFlag(location.suppr)
    );
  }
  return null;
}

export function locationForecasts(item: PushPortItem): LocationForecast[] {
  const locations = asItemList(item.Location);
  const out: LocationForecast[] = [];
  for (const location of locations) {
    const tiploc =
      nonEmptyString(location.tpl) ?? nonEmptyString(location.tiploc);
    if (!tiploc) {
      continue;
    }
    const forecastArr = forecastFromNode(location.arr);
    const forecastDep = forecastFromNode(location.dep);
    const platform = platformText(location);
    const platformSuppressed = platformSuppressedFlag(location);
    const trainLength = trainLengthValue(location);
    if (
      forecastArr === null
      && forecastDep === null
      && platform === null
      && platformSuppressed === null
      && trainLength === null
    ) {
      continue;
    }
    out.push({
      tiploc,
      forecastArr,
      forecastDep,
      platform,
      platformSuppressed,
      trainLength,
    });
  }
  return out;
}

export type CancelFlag = "cancel" | "clear" | "unchanged";

export function scheduleCancelFlag(item: PushPortItem): CancelFlag {
  if (!("can" in item)) {
    return "unchanged";
  }
  const value = item.can;
  if (value === true || value === "true" || value === "1" || value === 1) {
    return "cancel";
  }
  if (value === false || value === "false" || value === "0" || value === 0) {
    return "clear";
  }
  return "unchanged";
}
