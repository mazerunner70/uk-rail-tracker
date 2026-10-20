"""Data-model / schema tests (FR1 happy-path floor)."""

from __future__ import annotations

from live_sched.db import connect, init_schema


def test_init_schema_creates_tables(tmp_path):
    db_path = tmp_path / "live_sched.sqlite"
    conn = connect(db_path)
    init_schema(conn)
    names = {
        row[0]
        for row in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'"
        ).fetchall()
    }
    assert {
        "journeys",
        "calling_points",
        "service_day",
        "applied_updates",
        "service_aliases",
        "feed_meta",
        "kafka_offsets",
    } <= names
    conn.execute(
        """
        INSERT INTO journeys (
            service_guid, train_uid, run_date, date_runs_to, days_run,
            origin_crs, dest_crs, origin_std, toc
        ) VALUES (
            'SVC-CREBHM2605170925', 'C16922', '260517', '260517', '0000001',
            'CRE', 'BHM', '0925', 'LM'
        )
        """
    )
    conn.commit()
    row = conn.execute(
        "SELECT toc FROM journeys WHERE service_guid = ?",
        ("SVC-CREBHM2605170925",),
    ).fetchone()
    assert row["toc"] == "LM"
    conn.close()
