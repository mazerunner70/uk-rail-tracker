from __future__ import annotations

from pathlib import Path

import pytest

from live_sched.db import connect, init_schema
from live_sched.repository import ScheduleRepository

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
def kafka_payloads_path() -> Path:
    return FIXTURES / "kafka_payloads.jsonl"
