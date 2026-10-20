"""Zip intake helpers (FR1)."""

from __future__ import annotations

from pathlib import Path

import pytest

from live_sched.cfa_zip import find_cfa_member, list_eligible_zips, read_cfa_bytes
from tests.cfa_fixtures import write_cfa_zip


def test_list_eligible_zips_lexicographic(tmp_path: Path):
    write_cfa_zip(tmp_path / "b.zip", "991")
    write_cfa_zip(tmp_path / "a.zip", "992")
    (tmp_path / "ignore.txt").write_text("nope")
    names = [p.name for p in list_eligible_zips(tmp_path)]
    assert names == ["a.zip", "b.zip"]


def test_list_eligible_zips_empty(tmp_path: Path):
    assert list_eligible_zips(tmp_path) == []


def test_find_and_read_cfa(tmp_path: Path):
    zpath = write_cfa_zip(tmp_path / "u.zip", "991")
    member = find_cfa_member(zpath)
    assert member.upper().endswith(".CFA")
    data = read_cfa_bytes(zpath)
    assert data.startswith(b"HD")
    assert b"TTIS991" in data


def test_find_cfa_txt_member_among_companions(tmp_path: Path):
    """Real daily-update zips name the CIF ``RJTTC991CFA.txt``."""
    import zipfile

    zpath = tmp_path / "RJTTC991.zip"
    with zipfile.ZipFile(zpath, "w") as zf:
        zf.writestr("RJTTC991CFA.txt", "HD timetable\n")
        zf.writestr("RJTTC991DAT.txt", "associations\n")
        for suffix in ("FLF", "MSN", "REJ", "SET", "TSI", "ZTR"):
            zf.writestr(f"RJTTF991{suffix}.txt", "ref\n")
    assert find_cfa_member(zpath) == "RJTTC991CFA.txt"
    assert read_cfa_bytes(zpath).startswith(b"HD")


def test_find_cfa_missing(tmp_path: Path):
    import zipfile

    zpath = tmp_path / "empty.zip"
    with zipfile.ZipFile(zpath, "w") as zf:
        zf.writestr("README.txt", "no cfa")
        zf.writestr("RJTTC991DAT.txt", "not the cif")
    with pytest.raises(FileNotFoundError, match="no .CFA"):
        find_cfa_member(zpath)
