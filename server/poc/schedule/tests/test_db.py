"""Data model / database behavior tests (FR1.2, FR3.2, FR4.1)."""

from __future__ import annotations

from schedule_poc.db import connect, init_schema
from schedule_poc.repository import CallingPoint, Journey, ScheduleRepository


def test_schema_create_insert_query_by_stable_id(tmp_path):
    db_path = tmp_path / "schedule.db"
    conn = connect(db_path)
    init_schema(conn)
    repo = ScheduleRepository(conn)

    journey = Journey(
        service_guid="SVC-CREBHM2605170925",
        train_uid="C16922",
        run_date="260517",
        origin_crs="CRE",
        dest_crs="BHM",
        origin_std="0925",
        toc="LM",
    )
    repo.upsert_journey(journey)
    repo.replace_calling_points(
        journey.service_guid,
        [
            CallingPoint(
                service_guid=journey.service_guid,
                seq=0,
                tiploc="CREWE",
                crs="CRE",
                public_arr=None,
                public_dep="0925",
                activity="origin",
            ),
            CallingPoint(
                service_guid=journey.service_guid,
                seq=1,
                tiploc="BHAMNWS",
                crs="BHM",
                public_arr="1025",
                public_dep=None,
                activity="destination",
            ),
        ],
    )

    found = repo.get_journey("SVC-CREBHM2605170925")
    assert found is not None
    assert found.train_uid == "C16922"
    assert found.toc == "LM"
    points = repo.list_calling_points(journey.service_guid)
    assert len(points) == 2
    assert points[0].tiploc == "CREWE"
    conn.close()
