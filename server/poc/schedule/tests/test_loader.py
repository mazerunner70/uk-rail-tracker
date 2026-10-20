"""Offline LM schedule load tests (FR1)."""

from __future__ import annotations

from schedule_poc.cif_loader import load_lm_schedules


def test_load_lm_fixture_filters_non_lm(repo, lm_mca):
    guids = load_lm_schedules(lm_mca, repo)
    assert len(guids) == 1

    journeys = repo.list_journeys()
    assert len(journeys) == 1
    journey = journeys[0]
    assert journey.toc == "LM"
    assert journey.train_uid == "C16922"
    assert journey.origin_crs == "CRE"
    assert journey.dest_crs == "BHM"
    assert journey.origin_std == "0925"
    assert journey.service_guid == "SVC-CREBHM2605170925"

    # Non-LM VT schedule must not be persisted
    assert repo.get_journey_by_uid_date("Y99999", "260517") is None

    points = repo.list_calling_points(journey.service_guid)
    assert points[0].tiploc == "CREWE"
    assert points[-1].tiploc == "BHAMNWS"
