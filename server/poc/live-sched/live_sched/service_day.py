"""Resolve CIF schedules into one service_day per UID and calendar date."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta


@dataclass(frozen=True)
class ScheduleWindow:
    """One CIF schedule identity and the days it can cover."""

    service_guid: str
    train_uid: str
    date_runs_from: str
    date_runs_to: str
    days_run: str
    stp_indicator: str


def parse_yymmdd(value: str) -> date:
    digits = "".join(ch for ch in value if ch.isdigit())
    if len(digits) != 6:
        raise ValueError(f"expected YYMMDD, got {value!r}")
    return datetime.strptime(digits, "%y%m%d").date()


def format_yymmdd(value: date) -> str:
    return value.strftime("%y%m%d")


def iter_matching_dates(
    date_runs_from: str,
    date_runs_to: str,
    days_run: str,
) -> list[str]:
    """Calendar dates (YYMMDD) inside the CIF window whose weekday bit is set."""
    start = parse_yymmdd(date_runs_from)
    end = parse_yymmdd(date_runs_to)
    if end < start:
        return []
    bits = (days_run or "").ljust(7, "0")[:7]
    out: list[str] = []
    cursor = start
    while cursor <= end:
        if bits[cursor.weekday()] == "1":
            out.append(format_yymmdd(cursor))
        cursor += timedelta(days=1)
    return out


def covers_date(schedule: ScheduleWindow, calendar_date: str) -> bool:
    try:
        day = parse_yymmdd(calendar_date)
        start = parse_yymmdd(schedule.date_runs_from)
        end = parse_yymmdd(schedule.date_runs_to)
    except ValueError:
        return False
    if day < start or day > end:
        return False
    bits = (schedule.days_run or "").ljust(7, "0")[:7]
    return bits[day.weekday()] == "1"


def pick_winner(
    candidates: list[ScheduleWindow],
) -> tuple[str | None, bool]:
    """
    STP priority for one calendar day (RSPS5046 §5.5.2).

    Returns ``(service_guid, cancelled)``. Cancellation wins; otherwise overlay,
    then permanent, then new.
    """
    if not candidates:
        return None, False
    by_stp: dict[str, list[ScheduleWindow]] = {}
    for schedule in candidates:
        by_stp.setdefault(schedule.stp_indicator or "P", []).append(schedule)

    def _first(stp: str) -> ScheduleWindow | None:
        rows = by_stp.get(stp) or []
        if not rows:
            return None
        return sorted(rows, key=lambda s: s.service_guid)[0]

    if _first("C") is not None:
        return None, True
    overlay = _first("O")
    if overlay is not None:
        return overlay.service_guid, False
    permanent = _first("P")
    if permanent is not None:
        return permanent.service_guid, False
    new = _first("N")
    if new is not None:
        return new.service_guid, False
    # Unknown STP letter: deterministic first by guid.
    return sorted(candidates, key=lambda s: s.service_guid)[0].service_guid, False


def resolve_service_days(
    schedules: list[ScheduleWindow],
) -> dict[tuple[str, str], tuple[str | None, bool]]:
    """
    Materialise ``(train_uid, calendar_date) → (service_guid|None, cancelled)``.

    Only dates covered by at least one schedule for that UID are emitted.
    """
    by_uid: dict[str, list[ScheduleWindow]] = {}
    for schedule in schedules:
        by_uid.setdefault(schedule.train_uid, []).append(schedule)

    resolved: dict[tuple[str, str], tuple[str | None, bool]] = {}
    for train_uid, rows in by_uid.items():
        dates: set[str] = set()
        for schedule in rows:
            dates.update(
                iter_matching_dates(
                    schedule.date_runs_from,
                    schedule.date_runs_to,
                    schedule.days_run,
                )
            )
        for calendar_date in sorted(dates):
            candidates = [s for s in rows if covers_date(s, calendar_date)]
            resolved[(train_uid, calendar_date)] = pick_winner(candidates)
    return resolved
