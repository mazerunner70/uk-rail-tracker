import { detectEventTags, type EventTag } from "./eventTags.js";
import {
  extractRid,
  extractSsd,
  extractUid,
  locationForecasts,
  scheduleCancelFlag,
  type PushPortItem,
  type UpdateType,
} from "./pushPort.js";

export type ForecastWrite = {
  tiploc: string;
  forecastArr: string | null;
  forecastDep: string | null;
  platform: string | null;
  platformSuppressed: boolean | null;
  trainLength: number | null;
};

export type OverlayWrite =
  | {
      kind: "TS";
      serviceGuid: string;
      rid: string | null;
      uid: string | null;
      ssd: string | null;
      item: PushPortItem;
      forecasts: ForecastWrite[];
      tags: EventTag[];
    }
  | {
      kind: "schedule";
      serviceGuid: string;
      rid: string | null;
      uid: string | null;
      ssd: string | null;
      item: PushPortItem;
      cancel: "cancel" | "clear" | "unchanged";
      tags: EventTag[];
    }
  | {
      kind: "deactivated";
      serviceGuid: string;
      rid: string | null;
      uid: string | null;
      ssd: string | null;
      item: PushPortItem;
      tags: EventTag[];
    }
  | {
      kind: "miss";
      updateType: UpdateType;
      rid: string | null;
      uid: string | null;
      ssd: string | null;
      item: PushPortItem;
    };

export function planOverlayWrite(
  type: UpdateType,
  item: PushPortItem,
  serviceGuid: string | null,
): OverlayWrite {
  const rid = extractRid(item);
  const uid = extractUid(item);
  const ssd = extractSsd(item);
  if (!serviceGuid) {
    return { kind: "miss", updateType: type, rid, uid, ssd, item };
  }
  const tags = detectEventTags(type, item);
  if (type === "TS") {
    return {
      kind: "TS",
      serviceGuid,
      rid,
      uid,
      ssd,
      item,
      forecasts: locationForecasts(item),
      tags,
    };
  }
  if (type === "schedule") {
    return {
      kind: "schedule",
      serviceGuid,
      rid,
      uid,
      ssd,
      item,
      cancel: scheduleCancelFlag(item),
      tags,
    };
  }
  return {
    kind: "deactivated",
    serviceGuid,
    rid,
    uid,
    ssd,
    item,
    tags,
  };
}
