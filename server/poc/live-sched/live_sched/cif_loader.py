"""Stream-parse offline CIF MCA and persist LM journeys."""

from __future__ import annotations

import logging
import sqlite3
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

from live_sched.identity import build_service_guid, normalize_hhmm
from live_sched.repository import CallingPoint, Journey, ScheduleRepository

log = logging.getLogger(__name__)

LM_TOC = "LM"
# Heartbeat while streaming a multi-hundred-MB MCA.
_PROGRESS_STEP_BYTES = 5 * 1024 * 1024

# Codes this load handles end-to-end (plus HD header seen on full MCA files).
HANDLED_CODES = ("HD", "TI", "BS", "BX", "LO", "LI", "LT", "ZZ")


@dataclass
class LoadResult:
    """Outcome of an MCA stream parse."""

    service_guids: list[str]
    code_counts: dict[str, int]


@dataclass
class _Location:
    tiploc: str
    crs: str | None
    public_arr: str | None
    public_dep: str | None
    activity: str


@dataclass
class _ScheduleDraft:
    train_uid: str = ""
    date_from: str = ""
    date_to: str = ""
    days_run: str = ""
    stp_indicator: str = "P"
    toc: str = ""
    locations: list[_Location] = field(default_factory=list)


def _slice(line: str, start: int, end: int) -> str:
    padded = line.ljust(end)
    return padded[start:end].strip()


def _public_time(line: str, working_start: int = 10, public_start: int = 15) -> str | None:
    public = _slice(line, public_start, public_start + 4)
    if public and public[0].isdigit():
        return normalize_hhmm(public)
    working = _slice(line, working_start, working_start + 4)
    if working and working[0].isdigit():
        return normalize_hhmm(working)
    return None


def _parse_ti_crs(line: str) -> tuple[str, str | None]:
    """TIPLOC at cols 3-9; CRS at cols 54-56 (1-based), per CIF TI layout."""
    tiploc = _slice(line, 2, 9)
    crs = _slice(line, 53, 56) or None
    if crs and (len(crs) != 3 or not crs.isalpha()):
        crs = None
    return tiploc, crs


def _flush_lm(
    draft: _ScheduleDraft,
    repo: ScheduleRepository,
    tip_crs: dict[str, str],
) -> str | None:
    if draft.toc != LM_TOC or not draft.train_uid or not draft.locations:
        return None
    origin = draft.locations[0]
    dest = draft.locations[-1]
    origin_crs = origin.crs or tip_crs.get(origin.tiploc)
    dest_crs = dest.crs or tip_crs.get(dest.tiploc)
    if not origin_crs or not dest_crs:
        return None
    if not origin.public_dep:
        return None
    run_date = draft.date_from
    guid = build_service_guid(origin_crs, dest_crs, run_date, origin.public_dep)
    journey = Journey(
        service_guid=guid,
        train_uid=draft.train_uid,
        run_date=run_date,
        origin_crs=origin_crs,
        dest_crs=dest_crs,
        origin_std=origin.public_dep,
        toc=draft.toc,
        date_runs_to=draft.date_to or run_date,
        days_run=draft.days_run or "0000000",
        stp_indicator=draft.stp_indicator or "P",
    )
    points = [
        CallingPoint(
            service_guid=guid,
            seq=idx,
            tiploc=loc.tiploc,
            crs=loc.crs or tip_crs.get(loc.tiploc),
            public_arr=loc.public_arr,
            public_dep=loc.public_dep,
            activity=loc.activity,
        )
        for idx, loc in enumerate(draft.locations)
    ]
    try:
        repo.upsert_journey(journey)
    except sqlite3.IntegrityError:
        _report_insert_failure(journey, draft, points, repo)
        raise
    repo.replace_calling_points(guid, points)
    return guid


def _report_insert_failure(
    journey: Journey,
    draft: _ScheduleDraft,
    points: list[CallingPoint],
    repo: ScheduleRepository,
) -> None:
    """Log the rejected insert and the row already stored for that UID and date."""
    insert_row = {
        "service_guid": journey.service_guid,
        "train_uid": journey.train_uid,
        "run_date": journey.run_date,
        "date_runs_to": journey.date_runs_to,
        "days_run": journey.days_run,
        "origin_crs": journey.origin_crs,
        "dest_crs": journey.dest_crs,
        "origin_std": journey.origin_std,
        "toc": journey.toc,
        "stp_indicator": journey.stp_indicator,
        "status": journey.status,
    }
    stops = [
        {
            "seq": p.seq,
            "tiploc": p.tiploc,
            "crs": p.crs,
            "public_arr": p.public_arr,
            "public_dep": p.public_dep,
            "activity": p.activity,
        }
        for p in points
    ]
    log.error(
        "insert failed; record not written:\n%s\nstops:\n%s",
        insert_row,
        stops,
    )
    existing = repo.query_journeys_by_uid_date(journey.train_uid, journey.run_date)
    log.error(
        "SELECT * FROM journeys WHERE train_uid = %r AND run_date = %r\n%s",
        journey.train_uid,
        journey.run_date,
        existing,
    )
    for row in existing:
        held = repo.list_calling_points(str(row["service_guid"]))
        log.error(
            "calling points for stored service_guid %s:\n%s",
            row["service_guid"],
            [
                {
                    "seq": p.seq,
                    "tiploc": p.tiploc,
                    "crs": p.crs,
                    "public_arr": p.public_arr,
                    "public_dep": p.public_dep,
                    "activity": p.activity,
                }
                for p in held
            ],
        )


def _log_progress(
    pos: int,
    total: int,
    records: int,
    journeys: int,
    *,
    done: bool = False,
) -> None:
    pct = (100.0 * pos / total) if total else 100.0
    log.info(
        "progress %.1f/%.1f MiB (%.0f%%) records=%d lm_journeys=%d%s",
        pos / (1024 * 1024),
        total / (1024 * 1024),
        min(pct, 100.0),
        records,
        journeys,
        " done" if done else "",
    )


def load_lm_schedules(
    mca_path: str | Path,
    repo: ScheduleRepository,
    *,
    toc_filter: str = LM_TOC,
) -> LoadResult:
    """
    Stream-parse an MCA (or tiny excerpt) and persist journeys for ``toc_filter``.

    CIF identity is ``(train_uid, date_runs_from, stp_indicator)``; ``run_date``
    stores ``date_runs_from``. After the stream, ``service_day`` is materialised
    so each UID/calendar date points at the winning STP schedule.
    Counts every two-letter record code seen (including codes not applied).
    """
    path = Path(mca_path)
    total_bytes = path.stat().st_size
    tip_crs: dict[str, str] = {}
    draft: _ScheduleDraft | None = None
    loaded: list[str] = []
    counts: Counter[str] = Counter()
    records = 0
    pos = 0
    next_mark = _PROGRESS_STEP_BYTES

    log.info(
        "streaming %s (%.1f MiB) line by line; progress every %.0f MiB",
        path,
        total_bytes / (1024 * 1024),
        _PROGRESS_STEP_BYTES / (1024 * 1024),
    )

    with path.open("rb") as handle:
        for raw in handle:
            records += 1
            pos += len(raw)
            if pos >= next_mark:
                _log_progress(pos, total_bytes, records, len(loaded))
                while next_mark <= pos:
                    next_mark += _PROGRESS_STEP_BYTES
            line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
            if len(line) < 2:
                continue
            rec = line[:2]
            counts[rec] += 1
            if rec == "TI":
                tiploc, crs = _parse_ti_crs(line)
                if tiploc and crs:
                    tip_crs[tiploc] = crs
                continue
            if rec == "BS":
                if draft is not None:
                    guid = (
                        _flush_lm(draft, repo, tip_crs)
                        if draft.toc == toc_filter
                        else None
                    )
                    if guid:
                        loaded.append(guid)
                draft = _ScheduleDraft(
                    train_uid=_slice(line, 3, 9),
                    date_from=_slice(line, 9, 15),
                    date_to=_slice(line, 15, 21),
                    days_run=_slice(line, 21, 28),
                    stp_indicator=_slice(line, 79, 80) or "P",
                )
                continue
            if draft is None:
                continue
            if rec == "BX":
                draft.toc = _slice(line, 11, 13)
                continue
            if rec == "LO":
                tiploc = _slice(line, 2, 9)
                draft.locations.append(
                    _Location(
                        tiploc=tiploc,
                        crs=tip_crs.get(tiploc),
                        public_arr=None,
                        public_dep=_public_time(line),
                        activity="origin",
                    )
                )
                continue
            if rec == "LI":
                tiploc = _slice(line, 2, 9)
                arr = _public_time(line, working_start=10, public_start=25)
                dep = _public_time(line, working_start=15, public_start=29)
                pub_arr = _slice(line, 25, 29)
                pub_dep = _slice(line, 29, 33)
                public_arr = (
                    normalize_hhmm(pub_arr)
                    if pub_arr and pub_arr[0].isdigit()
                    else arr
                )
                public_dep = (
                    normalize_hhmm(pub_dep)
                    if pub_dep and pub_dep[0].isdigit()
                    else dep
                )
                if public_arr is None and public_dep is None:
                    draft.locations.append(
                        _Location(
                            tiploc=tiploc,
                            crs=tip_crs.get(tiploc),
                            public_arr=None,
                            public_dep=None,
                            activity="pass",
                        )
                    )
                else:
                    draft.locations.append(
                        _Location(
                            tiploc=tiploc,
                            crs=tip_crs.get(tiploc),
                            public_arr=public_arr,
                            public_dep=public_dep,
                            activity="call",
                        )
                    )
                continue
            if rec == "LT":
                tiploc = _slice(line, 2, 9)
                draft.locations.append(
                    _Location(
                        tiploc=tiploc,
                        crs=tip_crs.get(tiploc),
                        public_arr=_public_time(line),
                        public_dep=None,
                        activity="destination",
                    )
                )
                continue
            if rec == "ZZ":
                break

    if draft is not None and draft.toc == toc_filter:
        guid = _flush_lm(draft, repo, tip_crs)
        if guid:
            loaded.append(guid)
    if loaded:
        repo.rematerialize_service_days(
            {j.train_uid for j in repo.list_journeys(toc=toc_filter)}
        )
    _log_progress(pos, total_bytes, records, len(loaded), done=True)
    return LoadResult(service_guids=loaded, code_counts=dict(sorted(counts.items())))
