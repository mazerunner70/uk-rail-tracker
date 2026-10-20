"""Locate and open CFA daily-update zip files."""

from __future__ import annotations

import zipfile
from pathlib import Path


def list_eligible_zips(watch_folder: str | Path) -> list[Path]:
    """Return `*.zip` paths in lexicographic order (empty list if none)."""
    folder = Path(watch_folder)
    if not folder.is_dir():
        raise FileNotFoundError(f"watch folder not found: {folder}")
    return sorted(p for p in folder.iterdir() if p.is_file() and p.suffix.lower() == ".zip")


def is_cfa_member(name: str) -> bool:
    """True for a CIF update member: ``*.CFA`` or Network Rail ``*CFA.txt``."""
    path = Path(name)
    if name.endswith("/") or path.name.startswith("."):
        return False
    suffix = path.suffix.lower()
    if suffix == ".cfa":
        return True
    # Daily-update zips ship the CIF as RJTTC991CFA.txt (not RJTTC991.CFA).
    return suffix == ".txt" and path.stem.upper().endswith("CFA")


def find_cfa_member(zip_path: str | Path) -> str:
    """
    Return the archive member name of the CFA file inside a zip.

    Accepts ``*.CFA`` / ``*.cfa`` and ``*CFA.txt`` (for example
    ``RJTTC991CFA.txt``). Companion extracts such as ``*DAT.txt`` and
    ``RJTTF991FLF.txt`` are ignored. Refuses when none, or more than one
    CFA member, is present.
    """
    path = Path(zip_path)
    with zipfile.ZipFile(path, "r") as zf:
        members = [name for name in zf.namelist() if is_cfa_member(name)]
    if not members:
        raise FileNotFoundError(f"no .CFA member in zip: {path}")
    if len(members) > 1:
        raise ValueError(f"multiple .CFA members in zip {path}: {members}")
    return members[0]


def read_cfa_bytes(zip_path: str | Path, member: str | None = None) -> bytes:
    """Read CFA file bytes from a zip (optionally naming the member)."""
    path = Path(zip_path)
    name = member or find_cfa_member(path)
    with zipfile.ZipFile(path, "r") as zf:
        return zf.read(name)
