"""CLI cfa-update intake (FR1, FR2.4)."""

from __future__ import annotations

from pathlib import Path

from live_sched.cli import main
from live_sched.db import connect, init_schema
from live_sched.feed_sequence import get_feed_sequence
from tests.cfa_fixtures import BS_NEW_LM, write_cfa_zip


def test_cli_empty_folder(tmp_path: Path, capsys):
    db = tmp_path / "t.sqlite"
    watch = tmp_path / "inbox"
    watch.mkdir()
    code = main(["cfa-update", "--db", str(db), "--watch", str(watch)])
    assert code == 0
    out = capsys.readouterr().out
    assert "status: nothing" in out


def test_cli_happy_path_leaves_zip(tmp_path: Path, capsys):
    db = tmp_path / "t.sqlite"
    watch = tmp_path / "inbox"
    zpath = write_cfa_zip(watch / "upd.zip", "991", BS_NEW_LM)
    code = main(["cfa-update", "--db", str(db), "--watch", str(watch), "--seed", "990"])
    assert code == 0
    out = capsys.readouterr().out
    assert "status: accepted" in out
    assert "new: 1" in out
    assert zpath.is_file()
    conn = connect(db)
    init_schema(conn)
    assert get_feed_sequence(conn) == "991"
    conn.close()


def test_cli_refuse_leaves_sequence(tmp_path: Path, capsys):
    db = tmp_path / "t.sqlite"
    watch = tmp_path / "inbox"
    write_cfa_zip(watch / "bad.zip", "500", BS_NEW_LM)
    code = main(["cfa-update", "--db", str(db), "--watch", str(watch), "--seed", "990"])
    assert code == 1
    out = capsys.readouterr().out
    assert "status: refused" in out
    conn = connect(db)
    init_schema(conn)
    assert get_feed_sequence(conn) == "990"
    conn.close()
