import {
  scheduleCancelFlag,
  type PushPortItem,
  type UpdateType,
} from "./pushPort.js";

/**
 * Stable §6 tags. formation_changed / rerouted / associated / alert are deferred
 * until those update types are applied by updateItems.
 */
export type EventTag =
  | "arrived"
  | "departed"
  | "pass_forecast"
  | "passed_location"
  | "call_forecast"
  | "delayed"
  | "actual_removed"
  | "cancelled"
  | "cancel_cleared"
  | "deactivated"
  | "platform_set"
  | "platform_suppressed"
  | "length_reported"
  | "formation_changed"
  | "rerouted"
  | "associated"
  | "alert";

function asObject(value: unknown): Record<string, unknown> | null {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return null;
  }
  return value as Record<string, unknown>;
}

function asItemList(value: unknown): Record<string, unknown>[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is Record<string, unknown> => asObject(item) !== null);
  }
  const single = asObject(value);
  return single ? [single] : [];
}

function nonEmptyString(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }
  const text = value.trim();
  return text ? text : null;
}

function truthyFlag(value: unknown): boolean {
  return value === true || value === "true" || value === "1" || value === 1;
}

/** Digits-only HHMM from Darwin time strings (09:50 / 0950 / 09:50:00). */
export function hhmmDigits(value: unknown): string | null {
  const text = nonEmptyString(value);
  if (!text) {
    return null;
  }
  const digits = [...text].filter((ch) => ch >= "0" && ch <= "9").join("");
  return digits.length >= 4 ? digits.slice(0, 4) : null;
}

function minutes(hhmm: string): number {
  return Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(2, 4));
}

/** True when movement is later than booked, allowing overnight wrap. */
export function isLaterThanBooked(movement: string, booked: string): boolean {
  let late = minutes(movement) - minutes(booked);
  if (late < -12 * 60) late += 24 * 60;
  if (late > 12 * 60) late -= 24 * 60;
  return late > 0;
}

function movementHasAt(node: Record<string, unknown> | null): boolean {
  return node !== null && hhmmDigits(node.at) !== null;
}

function movementHasEstimate(node: Record<string, unknown> | null): boolean {
  if (!node) {
    return false;
  }
  return hhmmDigits(node.et) !== null || hhmmDigits(node.wet) !== null;
}

function movementTime(node: Record<string, unknown> | null): string | null {
  if (!node) {
    return null;
  }
  return hhmmDigits(node.at) ?? hhmmDigits(node.et) ?? hhmmDigits(node.wet);
}

function movementAtRemoved(node: Record<string, unknown> | null): boolean {
  return node !== null && truthyFlag(node.atRemoved);
}

function bookedForArr(location: Record<string, unknown>): string | null {
  return hhmmDigits(location.pta) ?? hhmmDigits(location.wta);
}

function bookedForDep(location: Record<string, unknown>): string | null {
  return hhmmDigits(location.ptd) ?? hhmmDigits(location.wtd);
}

function bookedForPass(location: Record<string, unknown>): string | null {
  return hhmmDigits(location.wtp);
}

function platformPresent(location: Record<string, unknown>): boolean {
  const plat = location.plat;
  if (plat === undefined || plat === null || plat === "") {
    return false;
  }
  if (typeof plat === "string") {
    return plat.trim().length > 0;
  }
  if (typeof plat === "object" && !Array.isArray(plat)) {
    const obj = plat as Record<string, unknown>;
    const text = nonEmptyString(obj[""]) ?? nonEmptyString(obj["#text"]);
    if (text) {
      return true;
    }
    return Object.keys(obj).length > 0;
  }
  return true;
}

function lengthReported(location: Record<string, unknown>): boolean {
  const length = location.length;
  if (typeof length === "number") {
    return length > 0;
  }
  if (typeof length === "string") {
    const n = Number(length.trim());
    return Number.isFinite(n) && n > 0;
  }
  return false;
}

function add(tags: Set<EventTag>, tag: EventTag): void {
  tags.add(tag);
}

function detectTsTags(item: PushPortItem, tags: Set<EventTag>): void {
  if (truthyFlag(item.delayed) || truthyFlag(item.etUnknown) || item.LateReason != null) {
    add(tags, "delayed");
  }

  for (const location of asItemList(item.Location)) {
    const arr = asObject(location.arr);
    const dep = asObject(location.dep);
    const pass = asObject(location.pass);

    if (movementHasAt(arr)) {
      add(tags, "arrived");
    }
    if (movementHasAt(dep)) {
      add(tags, "departed");
    }
    if (
      (!movementHasAt(arr) && movementHasEstimate(arr)) ||
      (!movementHasAt(dep) && movementHasEstimate(dep))
    ) {
      add(tags, "call_forecast");
    }

    if (movementHasAt(pass)) {
      add(tags, "passed_location");
    } else if (movementHasEstimate(pass)) {
      add(tags, "pass_forecast");
    }

    if (movementAtRemoved(arr) || movementAtRemoved(dep) || movementAtRemoved(pass)) {
      add(tags, "actual_removed");
    }

    const arrMove = movementTime(arr);
    const arrBooked = bookedForArr(location);
    if (arrMove && arrBooked && isLaterThanBooked(arrMove, arrBooked)) {
      add(tags, "delayed");
    }
    const depMove = movementTime(dep);
    const depBooked = bookedForDep(location);
    if (depMove && depBooked && isLaterThanBooked(depMove, depBooked)) {
      add(tags, "delayed");
    }
    const passMove = movementTime(pass);
    const passBooked = bookedForPass(location);
    if (passMove && passBooked && isLaterThanBooked(passMove, passBooked)) {
      add(tags, "delayed");
    }

    if (platformPresent(location)) {
      add(tags, "platform_set");
    }
    if (
      truthyFlag(location.platsup) ||
      truthyFlag(location.cisPlatsup) ||
      truthyFlag(location.suppr)
    ) {
      add(tags, "platform_suppressed");
    }
    if (lengthReported(location)) {
      add(tags, "length_reported");
    }
  }
}

export function detectEventTags(type: UpdateType, item: PushPortItem): EventTag[] {
  const tags = new Set<EventTag>();
  if (type === "deactivated") {
    add(tags, "deactivated");
    return [...tags];
  }
  if (type === "schedule") {
    const cancel = scheduleCancelFlag(item);
    if (cancel === "cancel") {
      add(tags, "cancelled");
    } else if (cancel === "clear") {
      add(tags, "cancel_cleared");
    }
    return [...tags];
  }
  detectTsTags(item, tags);
  return [...tags];
}
