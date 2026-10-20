"""Schedule path resolution from SCHEDULE_DIR and SCHEDULE_FILE."""

from __future__ import annotations

import argparse
import os
from pathlib import Path

import pytest

from live_sched.cif_loader import LoadResult
from live_sched.cli import cmd_load
from live_sched.config import (
    DEFAULT_SCHEDULE_DIR,
    DEFAULT_SCHEDULE_FILE,
    WORKSPACE_ROOT,
    resolve_schedule_path,
)


@pytest.fixture
def isolated_schedule_env(monkeypatch):
    monkeypatch.delenv("SCHEDULE_DIR", raising=False)
    monkeypatch.delenv("SCHEDULE_FILE", raising=False)
    yield
    os.environ.pop("SCHEDULE_DIR", None)
    os.environ.pop("SCHEDULE_FILE", None)


def test_schedule_path_defaults_when_unset(isolated_schedule_env, tmp_path):
    path = resolve_schedule_path(tmp_path / "missing.env")
    assert path == DEFAULT_SCHEDULE_DIR / DEFAULT_SCHEDULE_FILE


def test_relative_schedule_dir_resolves_from_workspace(
    isolated_schedule_env, monkeypatch, tmp_path
):
    monkeypatch.setenv("SCHEDULE_DIR", "server/poc/timetable")
    monkeypatch.setenv("SCHEDULE_FILE", "RJTTF944MCA.txt")
    path = resolve_schedule_path(tmp_path / "missing.env")
    assert path == WORKSPACE_ROOT / "server" / "poc" / "timetable" / "RJTTF944MCA.txt"


def test_schedule_path_from_env_file(isolated_schedule_env, tmp_path):
    folder = tmp_path / "drops"
    env_file = tmp_path / ".env"
    env_file.write_text(
        f'SCHEDULE_DIR="{folder}"\nSCHEDULE_FILE="RJTTF950MCA.txt"\n',
        encoding="utf-8",
    )
    path = resolve_schedule_path(env_file)
    assert path == folder / "RJTTF950MCA.txt"


def test_cmd_load_uses_configured_folder_and_name(
    isolated_schedule_env, tmp_path, monkeypatch
):
    folder = tmp_path / "schedules"
    folder.mkdir()
    target = folder / "RJTTF1MCA.txt"
    target.write_text("HD\nZZ\n", encoding="utf-8")
    env_file = tmp_path / ".env"
    env_file.write_text(
        f'SCHEDULE_DIR="{folder}"\nSCHEDULE_FILE="RJTTF1MCA.txt"\n',
        encoding="utf-8",
    )
    seen: dict[str, Path] = {}

    def fake_load(path, repo):
        seen["path"] = Path(path)
        return LoadResult(service_guids=[], code_counts={})

    monkeypatch.setattr("live_sched.cli.load_lm_schedules", fake_load)
    args = argparse.Namespace(
        mca=None,
        env=str(env_file),
        db=":memory:",
        docs=str(tmp_path / "docs"),
    )
    assert cmd_load(args) == 0
    assert seen["path"] == target


def test_cmd_load_mca_flag_overrides_config(
    isolated_schedule_env, tmp_path, monkeypatch, lm_mca
):
    monkeypatch.setenv("SCHEDULE_DIR", str(tmp_path))
    monkeypatch.setenv("SCHEDULE_FILE", "not-used.mca")
    seen: dict[str, Path] = {}

    def fake_load(path, repo):
        seen["path"] = Path(path)
        return LoadResult(service_guids=[], code_counts={})

    monkeypatch.setattr("live_sched.cli.load_lm_schedules", fake_load)
    args = argparse.Namespace(
        mca=str(lm_mca),
        env=None,
        db=":memory:",
        docs=str(tmp_path / "docs"),
    )
    assert cmd_load(args) == 0
    assert seen["path"] == lm_mca
