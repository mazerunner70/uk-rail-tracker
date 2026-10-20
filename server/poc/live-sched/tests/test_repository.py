"""Repository / data-access tests (FR1)."""

from __future__ import annotations

from live_sched.repository import CallingPoint, Journey


def test_upsert_journey_and_calling_points(repo):
    journey = Journey(
        service_guid="SVC-CREBHM2605170925",
        train_uid="C16922",
        run_date="260517",
        origin_crs="CRE",
        dest_crs="BHM",
        origin_std="0925",
        toc="LM",
        date_runs_to="260517",
        days_run="0000001",
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
    loaded = repo.get_journey(journey.service_guid)
    assert loaded is not None
    assert loaded.toc == "LM"
    points = repo.list_calling_points(journey.service_guid)
    assert len(points) == 2
    assert points[0].tiploc == "CREWE"
    assert repo.known_lm_uids() == {"C16922"}
