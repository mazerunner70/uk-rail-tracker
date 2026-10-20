"""Apply a CFA daily update with sequence gate and N/R/D counting."""

from __future__ import annotations

import logging
import sqlite3
from dataclasses import dataclass, field
from pathlib import Path

from live_sched.cfa_report import ApplyCounts, ZipOutcome
from live_sched.cfa_zip import find_cfa_member, read_cfa_bytes
from live_sched.feed_sequence import (
    DEFAULT_SEED,
    get_feed_sequence,
    next_sequence,
    resolve_feed_sequence,
    set_feed_sequence,
)
from live_sched.identity import build_service_guid, normalize_hhmm
from live_sched.repository import CallingPoint, Journey, ScheduleRepository

log = logging.getLogger(__name__)

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
    txn: str = "N"
    train_uid: str = ""
    date_from: str = ""
    date_to: str = ""
    days_run: str = ""
    stp_indicator: str = "P"
    toc: str = ""
    locations: list[_Location] = field(default_factory=list)


def _slice(line: str, start: int, end: int) -> str:
    return line.ljust(end)[start:end].strip()


def _public_time(line: str, working_start: int = 10, public_start: int = 15) -> str | None:
    public = _slice(line, public_start, public_start + 4)
    if public and public[0].isdigit():
        return normalize_hhmm(public)
    working = _slice(line, working_start, working_start + 4)
    if working and working[0].isdigit():
        return normalize_hhmm(working)
    return None


def _parse_ti_crs(line: str) -> tuple[str, str | None]:
    tiploc = _slice(line, 2, 9)
    crs = _slice(line, 53, 56) or None
    if crs and (len(crs) != 3 or not crs.isalpha()):
        crs = None
    return tiploc, crs


def _build_journey(
    draft: _ScheduleDraft,
    tip_crs: dict[str, str],
) -> tuple[Journey, list[CallingPoint]] | None:
    if draft.toc != LM_TOC or not draft.train_uid or not draft.locations:
        return None
    origin = draft.locations[0]
    dest = draft.locations[-1]
    origin_crs = origin.crs or tip_crs.get(origin.tiploc)
    dest_crs = dest.crs or tip_crs.get(dest.tiploc)
    if not origin_crs or not dest_crs or not origin.public_dep:
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
    return journey, points


def _load_stored_tip_crs(conn: sqlite3.Connection) -> dict[str, str]:
    """TIPLOC → CRS already stored on calling points.

    A daily CFA has schedule records and no TI (TIPLOC insert) extract, so
    origin and destination CRS have to come from the database loaded by the MCA.
    """
    rows = conn.execute(
        """
        SELECT tiploc, crs FROM calling_points
        WHERE crs IS NOT NULL AND crs != ''
        """
    )
    found: dict[str, str] = {}
    for row in rows:
        tiploc = str(row["tiploc"]).strip()
        crs = str(row["crs"]).strip()
        if tiploc and tiploc not in found:
            found[tiploc] = crs
    return found


def _flush_draft(
    draft: _ScheduleDraft,
    repo: ScheduleRepository,
    tip_crs: dict[str, str],
    toc_filter: str,
    counts: ApplyCounts,
    touched_uids: set[str],
) -> tuple[ApplyCounts, set[str]]:
    txn = (draft.txn or "N").upper()
    if txn == "D":
        if not draft.train_uid:
            return counts, touched_uids
        before = counts.deletes
        counts = _apply_draft(draft, repo, tip_crs, counts)
        if counts.deletes != before:
            touched_uids.add(draft.train_uid)
        return counts, touched_uids
    if draft.toc != toc_filter:
        return counts, touched_uids
    before_total = counts.total
    counts = _apply_draft(draft, repo, tip_crs, counts)
    if counts.total != before_total and draft.train_uid:
        touched_uids.add(draft.train_uid)
    return counts, touched_uids


def _apply_draft(
    draft: _ScheduleDraft,
    repo: ScheduleRepository,
    tip_crs: dict[str, str],
    counts: ApplyCounts,
) -> ApplyCounts:
    txn = (draft.txn or "N").upper()
    identity = repo.get_journey_by_cif_identity(
        draft.train_uid, draft.date_from, draft.stp_indicator or "P"
    )

    if txn == "D":
        # A delete BS has no TOC. Only a journey already in this store is ours.
        if identity is None:
            return counts
        repo.delete_journey(identity.service_guid)
        return ApplyCounts(
            total=counts.total + 1,
            new=counts.new,
            override=counts.override,
            duplicate=counts.duplicate,
            deletes=counts.deletes + 1,
        )

    built = _build_journey(draft, tip_crs)
    if built is None:
        log.warning(
            "skipped LM %s %s %s: missing CRS or origin departure",
            txn,
            draft.train_uid,
            draft.date_from,
        )
        return counts

    journey, points = built
    total = counts.total + 1
    new = counts.new
    override = counts.override
    duplicate = counts.duplicate

    if txn == "R":
        if identity is not None and identity.service_guid != journey.service_guid:
            repo.delete_journey(identity.service_guid)
        repo.upsert_journey(journey)
        repo.replace_calling_points(journey.service_guid, points)
        override += 1
    elif identity is not None:
        duplicate += 1
    else:
        repo.upsert_journey(journey)
        repo.replace_calling_points(journey.service_guid, points)
        new += 1

    return ApplyCounts(
        total=total,
        new=new,
        override=override,
        duplicate=duplicate,
        deletes=counts.deletes,
    )


def apply_cfa_bytes(
    cfa_bytes: bytes,
    repo: ScheduleRepository,
    *,
    toc_filter: str = LM_TOC,
    member_name: str | None = None,
) -> tuple[str, ApplyCounts]:
    """
    Parse CFA bytes, gate on feed sequence, apply LM N/R/D, update sequence.

    The sequence comes from the HD Current File Reference when that field
    contains three digits. Otherwise it comes from ``member_name`` (for
    example ``RJTTC991CFA.txt``).

    Returns ``(accepted_sequence, counts)``. Raises ``ValueError`` on refuse
    (sequence / HD) without mutating schedule rows. Other errors roll back.
    """
    conn = repo._conn  # noqa: SLF001 — shared SQLite connection for meta + schedules
    text = cfa_bytes.decode("utf-8", errors="replace")
    lines = text.splitlines()
    hd_line = next((ln for ln in lines if ln.startswith("HD")), None)
    if hd_line is None:
        raise ValueError("CFA has no HD header record")
    actual = resolve_feed_sequence(hd_line, member_name)
    previous = get_feed_sequence(conn)
    expected = next_sequence(previous)
    if actual != expected:
        raise ValueError(
            f"sequence refuse: expected {expected}, got {actual} "
            f"(previous load {previous})"
        )

    tip_crs: dict[str, str] = _load_stored_tip_crs(conn)
    draft: _ScheduleDraft | None = None
    counts = ApplyCounts()
    touched_uids: set[str] = set()

    repo.begin_batch()
    try:
        for raw in lines:
            line = raw.rstrip("\r\n")
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
                    counts, touched_uids = _flush_draft(
                        draft, repo, tip_crs, toc_filter, counts, touched_uids
                    )
                draft = _ScheduleDraft(
                    txn=_slice(line, 2, 3) or "N",
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
                activity = (
                    "pass"
                    if public_arr is None and public_dep is None
                    else "call"
                )
                draft.locations.append(
                    _Location(
                        tiploc=tiploc,
                        crs=tip_crs.get(tiploc),
                        public_arr=public_arr,
                        public_dep=public_dep,
                        activity=activity,
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

        if draft is not None:
            counts, touched_uids = _flush_draft(
                draft, repo, tip_crs, toc_filter, counts, touched_uids
            )

        if touched_uids:
            repo.rematerialize_service_days(touched_uids)

        # Persist sequence inside the same deferred batch.
        conn.execute(
            """
            INSERT INTO feed_meta (key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """,
            ("feed_sequence", actual),
        )
        repo.commit_batch()
    except Exception:
        repo.rollback_batch()
        raise

    return actual, counts


@dataclass(frozen=True)
class FolderRunResult:
    outcomes: list[ZipOutcome]
    exit_code: int


def process_watch_folder(
    watch_folder: str | Path,
    repo: ScheduleRepository,
    *,
    seed: str = DEFAULT_SEED,
) -> FolderRunResult:
    """
    One-shot: process every ``*.zip`` in lexicographic order; leave zips in place.

    Empty folder → success with a single ``nothing`` outcome.
    Any refuse/fail → non-zero exit; remaining zips are still attempted.
    """
    from live_sched.cfa_zip import list_eligible_zips

    # Ensure seed exists before gating any zip.
    get_feed_sequence(repo._conn, seed=seed)  # noqa: SLF001

    folder = Path(watch_folder)
    try:
        zips = list_eligible_zips(folder)
    except FileNotFoundError as exc:
        return FolderRunResult(
            outcomes=[
                ZipOutcome(
                    zip_path=str(folder),
                    status="failed",
                    reason=str(exc),
                )
            ],
            exit_code=1,
        )

    if not zips:
        return FolderRunResult(
            outcomes=[
                ZipOutcome(
                    zip_path=str(folder),
                    status="nothing",
                    reason="nothing to process",
                )
            ],
            exit_code=0,
        )

    outcomes: list[ZipOutcome] = []
    failed = False
    for zip_path in zips:
        previous = get_feed_sequence(repo._conn, seed=seed)  # noqa: SLF001
        expected = next_sequence(previous)
        try:
            member = find_cfa_member(zip_path)
            cfa_bytes = read_cfa_bytes(zip_path, member)
            sequence, counts = apply_cfa_bytes(
                cfa_bytes, repo, member_name=member
            )
            outcomes.append(
                ZipOutcome(
                    zip_path=str(zip_path),
                    status="accepted",
                    sequence=sequence,
                    expected=expected,
                    counts=counts,
                )
            )
        except ValueError as exc:
            failed = True
            msg = str(exc)
            status = "refused" if "sequence refuse" in msg or "HD" in msg else "failed"
            outcomes.append(
                ZipOutcome(
                    zip_path=str(zip_path),
                    status=status,
                    expected=expected,
                    reason=msg,
                )
            )
            log.warning("%s: %s", zip_path, msg)
        except (OSError, sqlite3.Error, KeyError) as exc:
            failed = True
            outcomes.append(
                ZipOutcome(
                    zip_path=str(zip_path),
                    status="failed",
                    expected=expected,
                    reason=str(exc),
                )
            )
            log.exception("failed applying %s", zip_path)

    return FolderRunResult(outcomes=outcomes, exit_code=1 if failed else 0)


# Re-export for callers that set sequence outside apply_cfa_bytes
__all__ = [
    "FolderRunResult",
    "apply_cfa_bytes",
    "process_watch_folder",
    "set_feed_sequence",
]
