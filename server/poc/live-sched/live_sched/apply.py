"""Apply Schedule-POC-equivalent Push Port kinds to matching LM journeys."""

from __future__ import annotations

import logging
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Any, cast

from live_sched.change_log import append_db_change
from live_sched.kafka_classify import (
    APPLY_TYPES,
    classify_update_types,
    extract_rid,
    extract_ssd,
    extract_uid,
    iter_update_items,
)
from live_sched.repository import ScheduleRepository

log = logging.getLogger(__name__)

ChangeLogger = Callable[..., None]


@dataclass(frozen=True)
class ApplyResult:
    applied: int
    ignored: int
    logged_types: list[str]


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


_SCHEDULE_FIELDS = (
    "toc",
    "trainId",
    "status",
    "isPassengerSvc",
    "qtrain",
    "can",
    "fid",
    "category",
)


def _show(value: str | None) -> str:
    return value if value else "—"


def _context_line(
    repo: ScheduleRepository,
    guid: str,
    item: dict[str, Any],
) -> str:
    """Identity of the journey this update matched."""
    journey = repo.get_journey(guid)
    parts: list[str] = []
    uid = extract_uid(item) or (journey.train_uid if journey else None)
    raw_ssd = item.get("ssd")
    ssd = raw_ssd.strip() if isinstance(raw_ssd, str) and raw_ssd.strip() else None
    if ssd is None and journey is not None:
        ssd = journey.run_date
    rid = extract_rid(item)
    if uid:
        parts.append(f"uid={uid}")
    if ssd:
        parts.append(f"ssd={ssd}")
    if rid:
        parts.append(f"rid={rid}")
    if journey is not None:
        parts.append(
            f"{journey.origin_crs}→{journey.dest_crs} std={journey.origin_std} "
            f"toc={journey.toc} status={journey.status}"
        )
    return " ".join(parts)


def _report_change(
    path: Path | None,
    *,
    update_type: str,
    service_guid: str,
    lines: list[str],
) -> None:
    log.info("applied %s %s", update_type, service_guid)
    for line in lines:
        log.info("  %s", line)
    if path is not None:
        append_db_change(
            path,
            update_type=update_type,
            service_guid=service_guid,
            lines=lines,
        )


def resolve_service_guid(
    repo: ScheduleRepository,
    item: dict[str, Any],
) -> str | None:
    """Resolve a Push Port child to a stable serviceGUID in the LM store."""
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
        for journey in repo.list_journeys():
            if journey.train_uid == uid:
                if rid:
                    repo.set_alias("rid", rid, journey.service_guid)
                return journey.service_guid
    return None


def _apply_ts(
    repo: ScheduleRepository,
    item: dict[str, Any],
    offset: int | None,
    log_change: ChangeLogger,
) -> bool:
    guid = resolve_service_guid(repo, item)
    if not guid:
        return False
    rid = extract_rid(item)
    if rid:
        repo.set_alias("rid", rid, guid)
    before = {point.tiploc: point for point in repo.list_calling_points(guid)}
    lines = [_context_line(repo, guid, item)]
    updated = 0
    for location in _as_location_list(item.get("Location")):
        tiploc = location.get("tpl") or location.get("tiploc")
        if not isinstance(tiploc, str) or not tiploc.strip():
            lines.append("location skipped: missing tiploc")
            continue
        code = tiploc.strip()
        arr = _hhmm_from_forecast(location.get("arr"))
        dep = _hhmm_from_forecast(location.get("dep"))
        if arr is None and dep is None:
            lines.append(f"{code}: no forecast arr/dep in message")
            continue
        prior = before.get(code)
        if prior is None or not repo.overlay_calling_point_times(
            guid, code, public_arr=arr, public_dep=dep
        ):
            lines.append(f"{code}: not on this journey")
            continue
        bits: list[str] = []
        if arr is not None:
            bits.append(f"forecast arr {_show(prior.forecast_arr)} → {arr}")
        if dep is not None:
            bits.append(f"forecast dep {_show(prior.forecast_dep)} → {dep}")
        place = f"{code} ({prior.crs})" if prior.crs else code
        lines.append(f"{place}: " + ", ".join(bits))
        updated += 1
    if updated == 0:
        lines.append("no calling-point times updated")
    repo.record_applied_update(
        service_guid=guid,
        update_type="TS",
        payload=item,
        rid=rid,
        source_offset=offset,
    )
    log_change(update_type="TS", service_guid=guid, lines=lines)
    return True


def _apply_schedule(
    repo: ScheduleRepository,
    item: dict[str, Any],
    offset: int | None,
    log_change: ChangeLogger,
) -> bool:
    guid = resolve_service_guid(repo, item)
    rid = extract_rid(item)
    if guid and rid:
        repo.set_alias("rid", rid, guid)
    if not guid:
        return False
    lines = [_context_line(repo, guid, item)]
    fields: list[str] = []
    for key in _SCHEDULE_FIELDS:
        value = item.get(key)
        if isinstance(value, (str, int, bool)) and value != "":
            fields.append(f"{key}={value}")
    if fields:
        lines.append(", ".join(fields))
    locations = _as_location_list(item.get("Location"))
    if locations:
        names: list[str] = []
        for location in locations:
            tpl = location.get("tpl") or location.get("tiploc") or "?"
            names.append(str(tpl))
        shown = ", ".join(names[:8])
        extra = f" (+{len(names) - 8} more)" if len(names) > 8 else ""
        lines.append(f"locations ({len(names)}): {shown}{extra}")
    lines.append("timetable rows left unchanged; update stored on the journey")
    repo.record_applied_update(
        service_guid=guid,
        update_type="schedule",
        payload=item,
        rid=rid,
        source_offset=offset,
    )
    log_change(update_type="schedule", service_guid=guid, lines=lines)
    return True


def _apply_deactivated(
    repo: ScheduleRepository,
    item: dict[str, Any],
    offset: int | None,
    log_change: ChangeLogger,
) -> bool:
    guid = resolve_service_guid(repo, item)
    if not guid:
        rid = extract_rid(item)
        if rid:
            guid = repo.resolve_alias("rid", rid)
    if not guid:
        return False
    journey = repo.get_journey(guid)
    previous = journey.status if journey is not None else "unknown"
    repo.update_journey_status(guid, "deactivated")
    rid = extract_rid(item)
    lines = [
        _context_line(repo, guid, item),
        f"status {_show(previous)} → deactivated",
    ]
    repo.record_applied_update(
        service_guid=guid,
        update_type="deactivated",
        payload=item,
        rid=rid,
        source_offset=offset,
    )
    log_change(update_type="deactivated", service_guid=guid, lines=lines)
    return True


def apply_payload(
    repo: ScheduleRepository,
    payload: dict[str, Any],
    *,
    source_offset: int | None = None,
    change_log_path: str | Path | None = None,
) -> ApplyResult:
    """
    Apply TS / schedule / deactivated children that resolve to LM journeys.

    Other classified types are logged at INFO and skipped without failing.
    """

    path = Path(change_log_path) if change_log_path is not None else None

    def log_change(
        *,
        update_type: str,
        service_guid: str,
        lines: list[str],
    ) -> None:
        _report_change(
            path,
            update_type=update_type,
            service_guid=service_guid,
            lines=lines,
        )

    applied = 0
    ignored = 0
    logged_types: list[str] = []
    for update_type in classify_update_types(payload):
        if update_type not in APPLY_TYPES:
            log.info("ignoring update type %s (not in apply set)", update_type)
            logged_types.append(update_type)
            ignored += 1
            continue
        for item in iter_update_items(payload, update_type):
            ok = False
            if update_type == "TS":
                ok = _apply_ts(repo, item, source_offset, log_change)
            elif update_type == "schedule":
                ok = _apply_schedule(repo, item, source_offset, log_change)
            elif update_type == "deactivated":
                ok = _apply_deactivated(repo, item, source_offset, log_change)
            if ok:
                applied += 1
            else:
                ignored += 1
                log.info(
                    "no matching LM journey for %s uid=%s rid=%s",
                    update_type,
                    extract_uid(item),
                    extract_rid(item),
                )
    return ApplyResult(applied=applied, ignored=ignored, logged_types=logged_types)
