"""Helpers to build synthetic HD / CFA / zip fixtures for unit tests."""

from __future__ import annotations

import zipfile
from pathlib import Path


def make_hd(sequence: str, *, update_indicator: str = "U") -> str:
    """80-char HD with 3-digit sequence in Current File Reference (cols 33–39)."""
    seq = f"{int(sequence) % 1000:03d}"
    file_id = "TPS.UCFCATE.PDTEST1"  # 20
    date = "011026"
    time = "2200"
    current = f"TTIS{seq}"  # 7
    last = "TTIS000"  # 7
    body = (
        "HD"
        + file_id
        + date
        + time
        + current
        + last
        + update_indicator
        + "A"
        + "011026"
        + "021026"
    )
    return body.ljust(80)[:80]


def make_cfa(
    sequence: str,
    schedule_lines: list[str] | None = None,
    *,
    tip_lines: list[str] | None = None,
) -> str:
    """Minimal CFA text: HD + TI (default CRE/BHM) + schedules + ZZ."""
    parts = [make_hd(sequence)]
    tips = tip_lines if tip_lines is not None else [TIP_CREWE, TIP_BHAM]
    parts.extend(tips)
    if schedule_lines:
        parts.extend(schedule_lines)
    parts.append("ZZ")
    return "\n".join(parts) + "\n"


TIP_CREWE = (
    "TICREWE  00124300JCREWE                     421402835CRECREWE"
)
TIP_BHAM = (
    "TIBHAMNWS40112700EBIRMINGHAM NEW STREET     656302810BHMBIRMINGHAM N ST"
)

# New LM schedule (N) CRE→BHM
BS_NEW_LM = [
    "BSNC169222605172612060000001 PXX1G453012122300000 EMU506 110      S S          P",
    "BX         LMYLM301200",
    "LOCREWE   0925 09257  FL     TB",
    "LTBHAMNWS 1025 10255     TF",
]

# Revise same identity (R)
BS_REVISE_LM = [
    "BSRC169222605172612060000001 PXX1G453012122300000 EMU506 110      S S          P",
    "BX         LMYLM301200",
    "LOCREWE   0930 09307  FL     TB",
    "LTBHAMNWS 1030 10305     TF",
]

# Delete (D) — BS alone
BS_DELETE_LM = [
    "BSDC169222605172612060000001 PXX1G453012122300000 EMU506 110      S S          P",
]


def write_cfa_zip(zip_path: Path, sequence: str, schedule_lines: list[str] | None = None) -> Path:
    """Write a zip containing ``RJTTC{seq}.CFA`` with synthetic content."""
    seq = f"{int(sequence) % 1000:03d}"
    cfa_name = f"RJTTC{seq}.CFA"
    text = make_cfa(
        seq,
        schedule_lines,
        tip_lines=[TIP_CREWE, TIP_BHAM],
    )
    zip_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path, "w") as zf:
        zf.writestr(cfa_name, text)
    return zip_path
