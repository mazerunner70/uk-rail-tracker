"""Feed sequence store and HD parse (FR2)."""

from __future__ import annotations

import pytest

from live_sched.feed_sequence import (
    get_feed_sequence,
    next_sequence,
    parse_hd_sequence,
    resolve_feed_sequence,
    sequence_from_cfa_member,
    set_feed_sequence,
)
from tests.cfa_fixtures import make_hd


def test_seed_990_when_empty(db_conn):
    assert get_feed_sequence(db_conn) == "990"
    assert get_feed_sequence(db_conn) == "990"


def test_set_and_next_wrap(db_conn):
    set_feed_sequence(db_conn, "999")
    assert get_feed_sequence(db_conn) == "999"
    assert next_sequence("999") == "000"
    assert next_sequence("990") == "991"


def test_parse_hd_sequence_from_current_file_ref():
    assert parse_hd_sequence(make_hd("991")) == "991"


def test_parse_hd_refuses_without_digits():
    # Current File Reference without digits (mirrors full MCA DFTTISD shape).
    bad = _hd_without_sequence()
    with pytest.raises(ValueError, match="no 3-digit sequence"):
        parse_hd_sequence(bad)


def test_sequence_from_cfa_member_name():
    assert sequence_from_cfa_member("RJTTC991CFA.txt") == "991"
    assert sequence_from_cfa_member("RJTTC000.CFA") == "000"


def test_resolve_uses_member_name_when_hd_has_no_digits():
    assert resolve_feed_sequence(_hd_without_sequence(), "RJTTC991CFA.txt") == "991"


def _hd_without_sequence() -> str:
    return (
        "HDTPS.UCFCATE.PD1107191907112108DFTTISD       FA190711300912"
        + " " * 20
    )[:80]
