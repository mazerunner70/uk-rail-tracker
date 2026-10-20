"""Stable journey identity aligned with draft serviceGUID semantics."""

from __future__ import annotations

import re

_GUID_RE = re.compile(
    r"^SVC-"
    r"(?P<origin>[A-Z]{3})"
    r"(?P<dest>[A-Z]{3})"
    r"(?P<run_date>\d{6})"
    r"(?P<origin_std>\d{4})$"
)


def normalize_hhmm(raw: str) -> str:
    """Normalise a CIF/public time fragment to HHMM digits."""
    digits = "".join(ch for ch in raw if ch.isdigit())
    if len(digits) < 4:
        raise ValueError(f"time must have at least HHMM digits, got {raw!r}")
    return digits[:4]


def build_service_guid(
    origin_crs: str,
    dest_crs: str,
    run_date_yymmdd: str,
    origin_std_hhmm: str,
) -> str:
    """Build ``SVC-{originCrs}{destCrs}{runDate}{originStd}``."""
    origin = origin_crs.strip().upper()
    dest = dest_crs.strip().upper()
    if len(origin) != 3 or len(dest) != 3:
        raise ValueError(f"CRS codes must be 3 letters, got {origin!r}/{dest!r}")
    run_date = "".join(ch for ch in run_date_yymmdd if ch.isdigit())
    if len(run_date) != 6:
        raise ValueError(f"run_date must be YYMMDD, got {run_date_yymmdd!r}")
    std = normalize_hhmm(origin_std_hhmm)
    return f"SVC-{origin}{dest}{run_date}{std}"


def parse_service_guid(service_guid: str) -> dict[str, str]:
    """Parse a serviceGUID into its component fields."""
    match = _GUID_RE.match(service_guid)
    if not match:
        raise ValueError(f"invalid serviceGUID: {service_guid!r}")
    return match.groupdict()
