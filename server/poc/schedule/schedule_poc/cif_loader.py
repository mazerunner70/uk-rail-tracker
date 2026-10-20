"""Stream-parse offline CIF MCA and persist LM journeys."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from schedule_poc.identity import build_service_guid, normalize_hhmm
from schedule_poc.repository import CallingPoint, Journey, ScheduleRepository

LM_TOC = "LM"


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
        stp_indicator=draft.stp_indicator or "P",
    )
    repo.upsert_journey(journey)
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
    repo.replace_calling_points(guid, points)
    return guid


def load_lm_schedules(
    mca_path: str | Path,
    repo: ScheduleRepository,
    *,
    toc_filter: str = LM_TOC,
) -> list[str]:
    """
    Stream-parse an MCA (or tiny excerpt) and persist journeys for ``toc_filter``.

    Uses ``date_runs_from`` as the representative ``run_date`` for POC identity.
    """
    tip_crs: dict[str, str] = {}
    draft: _ScheduleDraft | None = None
    loaded: list[str] = []

    with Path(mca_path).open(encoding="utf-8", errors="replace") as handle:
        for raw in handle:
            line = raw.rstrip("\n")
            if len(line) < 2:
                continue
            rec = line[:2]
            if rec == "TI":
                tiploc, crs = _parse_ti_crs(line)
                if tiploc and crs:
                    tip_crs[tiploc] = crs
                continue
            if rec == "BS":
                if draft is not None:
                    guid = _flush_lm(draft, repo, tip_crs) if draft.toc == toc_filter else None
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
                # ATOC TOC code at columns 12-13 (1-based) → [11:13]
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
                # Intermediate public times: CIF LI public arr 26-29, public dep 30-33 (1-based)
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
                    # Passing point — keep for path fidelity but mark activity
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
    return loaded
