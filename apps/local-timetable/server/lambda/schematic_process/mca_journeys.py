"""Stream an MCA file into HD meta + LM journey CRS stop lists for layout.

Journey acceptance mirrors server/src/cif.ts prepareLmJourney:
LM TOC, origin CRS, dest CRS, and origin publicDep must all be present.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

LM_TOC = "LM"
TIPLOC_CODES = frozenset({"TI", "TA", "AA"})
NAME_SEQUENCE_RE = re.compile(r"RJTT[FC](\d{3})", re.IGNORECASE)
DIGITS_RE = re.compile(r"(\d{3})")


def _slice(line: str, start: int, end: int) -> str:
    padded = line.ljust(end, " ")
    return padded[start:end].strip()


def _normalize_hhmm(raw: str) -> str:
    digits = "".join(ch for ch in raw if "0" <= ch <= "9")
    if len(digits) < 4:
        raise ValueError(f"time must have at least HHMM digits, got {raw}")
    return digits[:4]


def _public_time(line: str, working_start: int = 10, public_start: int = 15) -> str | None:
    """Match cif.ts publicTime (public HHMM, else working HHMM)."""
    pub = _slice(line, public_start, public_start + 4)
    if pub and "0" <= pub[0] <= "9":
        return _normalize_hhmm(pub)
    working = _slice(line, working_start, working_start + 4)
    if working and "0" <= working[0] <= "9":
        return _normalize_hhmm(working)
    return None


def accept_header(hd_line: str, filename: str | None = None) -> str | None:
    line = hd_line.rstrip("\r\n")
    if not line.startswith("HD") or len(line) < 47:
        return None
    padded = line.ljust(80, " ")
    update = padded[46:47]
    if update not in ("F", "U"):
        return None
    if filename:
        base = Path(filename).name
        m = NAME_SEQUENCE_RE.search(base)
        if m:
            return m.group(1)
    current_ref = padded[32:39].strip()
    digits = DIGITS_RE.search(current_ref)
    if digits:
        return digits.group(1)
    return current_ref or None


def parse_tiploc_crs(line: str) -> tuple[str, str | None]:
    tiploc = _slice(line, 2, 9)
    crs = _slice(line, 53, 56) or None
    if crs and (len(crs) != 3 or not crs.isalpha()):
        crs = None
    return tiploc, crs.upper() if crs else None


@dataclass
class _Loc:
    tiploc: str
    crs: str | None
    public_dep: str | None


@dataclass
class _Draft:
    toc: str = ""
    locations: list[_Loc] = field(default_factory=list)


@dataclass
class McaJourneyParse:
    hd_sequence: str
    stop_lists: list[list[str]]


def iter_mca_journeys(mca_path: Path) -> McaJourneyParse:
    tip_crs: dict[str, str] = {}
    stop_lists: list[list[str]] = []
    hd_sequence: str | None = None
    draft: _Draft | None = None

    def flush() -> None:
        nonlocal draft
        if draft is None or draft.toc != LM_TOC or not draft.locations:
            draft = None
            return
        origin = draft.locations[0]
        dest = draft.locations[-1]
        origin_crs = origin.crs or tip_crs.get(origin.tiploc)
        dest_crs = dest.crs or tip_crs.get(dest.tiploc)
        # prepareLmJourney gate: origin/dest CRS + origin public departure
        if not origin_crs or not dest_crs or not origin.public_dep:
            draft = None
            return
        stops: list[str] = []
        for loc in draft.locations:
            resolved = loc.crs or tip_crs.get(loc.tiploc)
            if resolved:
                stops.append(resolved)
        if len(stops) >= 2:
            stop_lists.append(stops)
        draft = None

    with mca_path.open("r", encoding="utf-8", errors="replace") as handle:
        for raw in handle:
            line = raw.rstrip("\r\n")
            if not line:
                continue
            rec = line[:2] if len(line) >= 2 else ""
            if hd_sequence is None:
                seq = accept_header(line, mca_path.name)
                if seq is None:
                    if line.startswith("HD"):
                        raise ValueError("MCA header was refused")
                    continue
                hd_sequence = seq
                continue
            if rec in TIPLOC_CODES:
                tiploc, crs = parse_tiploc_crs(line)
                if tiploc and crs:
                    tip_crs[tiploc] = crs
                continue
            if rec == "BS":
                flush()
                draft = _Draft()
                continue
            if draft is None:
                if rec == "ZZ":
                    break
                continue
            if rec == "BX":
                draft.toc = _slice(line, 11, 13)
                continue
            if rec == "LO":
                tiploc = _slice(line, 2, 9)
                draft.locations.append(
                    _Loc(
                        tiploc=tiploc,
                        crs=tip_crs.get(tiploc),
                        public_dep=_public_time(line),
                    )
                )
                continue
            if rec == "LI":
                tiploc = _slice(line, 2, 9)
                # Intermediate public times not required for layout filter
                draft.locations.append(
                    _Loc(tiploc=tiploc, crs=tip_crs.get(tiploc), public_dep=None)
                )
                continue
            if rec == "LT":
                tiploc = _slice(line, 2, 9)
                draft.locations.append(
                    _Loc(tiploc=tiploc, crs=tip_crs.get(tiploc), public_dep=None)
                )
                continue
            if rec == "ZZ":
                flush()
                break
        flush()

    if not hd_sequence:
        raise ValueError("MCA header was refused")
    return McaJourneyParse(hd_sequence=hd_sequence, stop_lists=stop_lists)
