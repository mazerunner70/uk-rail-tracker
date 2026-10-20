"""Replay harvested Push Port JSON into a working copy of the schedule store."""

from __future__ import annotations

from collections.abc import Iterable
from typing import Any, cast

from schedule_poc.harvest import HarvestRecord
from schedule_poc.kafka_classify import (
    classify_update_types,
    extract_rid,
    extract_ssd,
    extract_uid,
    iter_update_items,
)
from schedule_poc.repository import ScheduleRepository


def _as_location_list(value: Any) -> list[dict[str, Any]]:
    if value is None:
        return []
    if isinstance(value, list):
        return [cast(dict[str, Any], item) for item in value if isinstance(item, dict)]
    if isinstance(value, dict):
        return [cast(dict[str, Any], value)]
    return []


def _hhmm_from_forecast(node: Any) -> str | None:
    if not isinstance(node, dict):
        return None
    for key in ("at", "et", "wet"):
        value = node.get(key)
        if isinstance(value, str) and value.strip():
            digits = "".join(ch for ch in value if ch.isdigit())
            if len(digits) >= 4:
                return digits[:4]
    return None


def resolve_service_guid(
    repo: ScheduleRepository,
    item: dict[str, Any],
) -> str | None:
    """
    Resolve a Push Port child to a stable serviceGUID.

    Prefer Darwin ``rid`` alias when previously mapped; else ``uid`` + ``ssd``.
    """
    rid = extract_rid(item)
    if rid:
        by_rid = repo.resolve_alias("rid", rid)
        if by_rid:
            return by_rid
    uid = extract_uid(item)
    ssd = extract_ssd(item)
    if uid and ssd:
        journey = repo.get_journey_by_uid_date(uid, ssd)
        if journey:
            if rid:
                repo.set_alias("rid", rid, journey.service_guid)
            return journey.service_guid
    if uid:
        # Fall back: any loaded journey with this UID (POC single-run assumption)
        for journey in repo.list_journeys():
            if journey.train_uid == uid:
                if rid:
                    repo.set_alias("rid", rid, journey.service_guid)
                return journey.service_guid
    return None


def _apply_ts(repo: ScheduleRepository, item: dict[str, Any], offset: int | None) -> bool:
    guid = resolve_service_guid(repo, item)
    if not guid:
        return False
    rid = extract_rid(item)
    if rid:
        repo.set_alias("rid", rid, guid)
    for location in _as_location_list(item.get("Location")):
        tiploc = location.get("tpl") or location.get("tiploc")
        if not isinstance(tiploc, str) or not tiploc.strip():
            continue
        arr = _hhmm_from_forecast(location.get("arr"))
        dep = _hhmm_from_forecast(location.get("dep"))
        if arr is None and dep is None:
            continue
        repo.overlay_calling_point_times(
            guid, tiploc.strip(), public_arr=arr, public_dep=dep
        )
    repo.record_applied_update(
        service_guid=guid,
        update_type="TS",
        payload=item,
        rid=rid,
        source_offset=offset,
    )
    return True


def _apply_schedule(
    repo: ScheduleRepository, item: dict[str, Any], offset: int | None
) -> bool:
    guid = resolve_service_guid(repo, item)
    rid = extract_rid(item)
    if guid and rid:
        repo.set_alias("rid", rid, guid)
    if not guid:
        return False
    repo.record_applied_update(
        service_guid=guid,
        update_type="schedule",
        payload=item,
        rid=rid,
        source_offset=offset,
    )
    return True


def _apply_deactivated(
    repo: ScheduleRepository, item: dict[str, Any], offset: int | None
) -> bool:
    guid = resolve_service_guid(repo, item)
    if not guid:
        # deactivated may carry only rid
        rid = extract_rid(item)
        if rid:
            guid = repo.resolve_alias("rid", rid)
    if not guid:
        return False
    repo.update_journey_status(guid, "deactivated")
    repo.record_applied_update(
        service_guid=guid,
        update_type="deactivated",
        payload=item,
        rid=extract_rid(item),
        source_offset=offset,
    )
    return True


def replay_payload(
    repo: ScheduleRepository,
    payload: dict[str, Any],
    *,
    source_offset: int | None = None,
) -> int:
    """Apply one Push Port payload; return number of update children applied."""
    applied = 0
    for update_type in classify_update_types(payload):
        for item in iter_update_items(payload, update_type):
            ok = False
            if update_type == "TS":
                ok = _apply_ts(repo, item, source_offset)
            elif update_type == "schedule":
                ok = _apply_schedule(repo, item, source_offset)
            elif update_type == "deactivated":
                ok = _apply_deactivated(repo, item, source_offset)
            else:
                # Record unknown-but-classified types without mutating journeys
                guid = resolve_service_guid(repo, item)
                repo.record_applied_update(
                    service_guid=guid,
                    update_type=update_type,
                    payload=item,
                    rid=extract_rid(item),
                    source_offset=source_offset,
                )
                ok = guid is not None
            if ok:
                applied += 1
    return applied


def replay_records(
    repo: ScheduleRepository, records: Iterable[HarvestRecord]
) -> int:
    total = 0
    for record in records:
        total += replay_payload(
            repo, record.payload, source_offset=record.offset
        )
    return total
