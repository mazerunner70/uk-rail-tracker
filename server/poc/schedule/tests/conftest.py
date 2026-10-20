from __future__ import annotations

from pathlib import Path

import pytest

from schedule_poc.db import connect, init_schema
from schedule_poc.repository import ScheduleRepository

FIXTURES = Path(__file__).resolve().parents[1] / "fixtures"


@pytest.fixture
def db_conn():
    conn = connect(":memory:")
    init_schema(conn)
    yield conn
    conn.close()


@pytest.fixture
def repo(db_conn):
    return ScheduleRepository(db_conn)


@pytest.fixture
def lm_mca() -> Path:
    return FIXTURES / "lm_tiny.mca"


@pytest.fixture
def harvest_path() -> Path:
    return FIXTURES / "harvest_corpus.jsonl"
