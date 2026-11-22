import type BetterSqlite3 from "better-sqlite3";
import { planOverlayWrite, type OverlayWrite } from "./overlayPlan.js";
import {
  decodePushPort,
  extractRid,
  extractSsd,
  extractUid,
  updateItems,
  type PushPortItem,
  type UpdateType,
} from "./pushPort.js";
import {
  ensureAppliedUpdatesEventTags,
  ensureCallingPointLiveFields,
} from "./sqliteSchema.js";

export type KafkaMessageMeta = {
  topic: string;
  partition: number;
  offset: number;
};

export type ApplyMessageResult = {
  applied: number;
  ignored: number;
  advancedOffset: boolean;
};

function resolveServiceGuid(
  db: BetterSqlite3.Database,
  item: PushPortItem,
): string | null {
  const rid = extractRid(item);
  if (rid) {
    const byRid = db
      .prepare(
        `SELECT service_guid FROM service_aliases
         WHERE alias_type = 'rid' AND alias_value = ?`,
      )
      .get(rid) as { service_guid: string } | undefined;
    if (byRid) {
      return byRid.service_guid;
    }
  }
  const uid = extractUid(item);
  const ssd = extractSsd(item);
  if (uid && ssd) {
    const byDay = db
      .prepare(
        `SELECT service_guid FROM service_day
         WHERE train_uid = ? AND calendar_date = ?`,
      )
      .get(uid, ssd) as { service_guid: string | null } | undefined;
    if (byDay?.service_guid) {
      return byDay.service_guid;
    }
  }
  return null;
}

function upsertRidAlias(
  db: BetterSqlite3.Database,
  rid: string | null,
  serviceGuid: string,
): void {
  if (!rid) {
    return;
  }
  db.prepare(
    `INSERT INTO service_aliases (alias_type, alias_value, service_guid)
     VALUES ('rid', ?, ?)
     ON CONFLICT(alias_type, alias_value) DO UPDATE SET
       service_guid = excluded.service_guid`,
  ).run(rid, serviceGuid);
}

function recordAppliedUpdate(
  db: BetterSqlite3.Database,
  write: Extract<OverlayWrite, { serviceGuid: string }>,
  sourceOffset: number,
  appliedAt: string,
): void {
  db.prepare(
    `INSERT INTO applied_updates (
      service_guid, update_type, rid, payload_json, applied_at, source_offset, event_tags
    ) VALUES (?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    write.serviceGuid,
    write.kind,
    write.rid,
    JSON.stringify(write.item),
    appliedAt,
    sourceOffset,
    JSON.stringify(write.tags),
  );
}

function applyTs(
  db: BetterSqlite3.Database,
  write: Extract<OverlayWrite, { kind: "TS" }>,
  sourceOffset: number,
  appliedAt: string,
): void {
  upsertRidAlias(db, write.rid, write.serviceGuid);
  for (const forecast of write.forecasts) {
    const row = db
      .prepare(
        `SELECT id, forecast_arr, forecast_dep, platform, platform_suppressed, train_length
         FROM calling_points
         WHERE service_guid = ? AND tiploc = ?`,
      )
      .get(write.serviceGuid, forecast.tiploc) as
      | {
          id: number;
          forecast_arr: string | null;
          forecast_dep: string | null;
          platform: string | null;
          platform_suppressed: number;
          train_length: number | null;
        }
      | undefined;
    if (!row) {
      continue;
    }
    db.prepare(
      `UPDATE calling_points
       SET forecast_arr = ?, forecast_dep = ?,
           platform = ?, platform_suppressed = ?, train_length = ?
       WHERE id = ?`,
    ).run(
      forecast.forecastArr ?? row.forecast_arr,
      forecast.forecastDep ?? row.forecast_dep,
      forecast.platform ?? row.platform,
      forecast.platformSuppressed === null
        ? row.platform_suppressed
        : forecast.platformSuppressed
          ? 1
          : 0,
      forecast.trainLength ?? row.train_length,
      row.id,
    );
  }
  recordAppliedUpdate(db, write, sourceOffset, appliedAt);
}

function applySchedule(
  db: BetterSqlite3.Database,
  write: Extract<OverlayWrite, { kind: "schedule" }>,
  sourceOffset: number,
  appliedAt: string,
): void {
  upsertRidAlias(db, write.rid, write.serviceGuid);
  if (write.cancel !== "unchanged" && write.uid && write.ssd) {
    db.prepare(
      `UPDATE service_day
       SET cancelled = ?
       WHERE train_uid = ? AND calendar_date = ? AND service_guid = ?`,
    ).run(
      write.cancel === "cancel" ? 1 : 0,
      write.uid,
      write.ssd,
      write.serviceGuid,
    );
  }
  recordAppliedUpdate(db, write, sourceOffset, appliedAt);
}

function applyDeactivated(
  db: BetterSqlite3.Database,
  write: Extract<OverlayWrite, { kind: "deactivated" }>,
  sourceOffset: number,
  appliedAt: string,
): void {
  upsertRidAlias(db, write.rid, write.serviceGuid);
  db.prepare(
    `UPDATE journeys SET status = 'deactivated' WHERE service_guid = ?`,
  ).run(write.serviceGuid);
  recordAppliedUpdate(db, write, sourceOffset, appliedAt);
}

function applyWrite(
  db: BetterSqlite3.Database,
  write: OverlayWrite,
  sourceOffset: number,
  appliedAt: string,
): boolean {
  if (write.kind === "miss") {
    return false;
  }
  if (write.kind === "TS") {
    applyTs(db, write, sourceOffset, appliedAt);
    return true;
  }
  if (write.kind === "schedule") {
    applySchedule(db, write, sourceOffset, appliedAt);
    return true;
  }
  applyDeactivated(db, write, sourceOffset, appliedAt);
  return true;
}

function saveNextOffset(
  db: BetterSqlite3.Database,
  meta: KafkaMessageMeta,
  updatedAt: string,
): void {
  db.prepare(
    `INSERT INTO kafka_offsets (topic, partition, next_offset, updated_at)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(topic, partition) DO UPDATE SET
       next_offset = excluded.next_offset,
       updated_at = excluded.updated_at`,
  ).run(meta.topic, meta.partition, meta.offset + 1, updatedAt);
}

export function openOverlayWriter(db: BetterSqlite3.Database): void {
  db.pragma("journal_mode = WAL");
  db.pragma("synchronous = NORMAL");
  db.pragma("foreign_keys = ON");
  db.pragma("busy_timeout = 5000");
  ensureAppliedUpdatesEventTags(db);
  ensureCallingPointLiveFields(db);
}

export function kafkaOffsetsForTopic(
  db: BetterSqlite3.Database,
  topic: string,
): Map<number, number> {
  const rows = db
    .prepare(
      `SELECT partition, next_offset FROM kafka_offsets WHERE topic = ?`,
    )
    .all(topic) as Array<{ partition: number; next_offset: number }>;
  return new Map(rows.map((row) => [row.partition, row.next_offset]));
}

/** Unsaved partitions use high watermark (live end). */
export function startOffsetForPartition(
  saved: number | undefined,
  highWatermark: number,
): number {
  return saved === undefined ? highWatermark : saved;
}

export function applyKafkaMessage(
  db: BetterSqlite3.Database,
  raw: Buffer | string | object,
  meta: KafkaMessageMeta,
  deps: { nowIso?: () => string } = {},
): ApplyMessageResult {
  const nowIso = deps.nowIso ?? (() => new Date().toISOString());
  const appliedAt = nowIso();
  const payload = decodePushPort(raw);
  const items = payload ? updateItems(payload) : [];

  const apply = db.transaction(() => {
    let applied = 0;
    let ignored = 0;
    if (!payload || items.length === 0) {
      ignored = 1;
    } else {
      for (const { type, item } of items) {
        const guid = resolveServiceGuid(db, item);
        const write = planOverlayWrite(type as UpdateType, item, guid);
        if (applyWrite(db, write, meta.offset, appliedAt)) {
          applied += 1;
        } else {
          ignored += 1;
        }
      }
    }
    saveNextOffset(db, meta, appliedAt);
    return { applied, ignored, advancedOffset: true };
  });

  return apply();
}
