"""CIF/MCA load and docs generation tests (FR1, FR2)."""

from __future__ import annotations

import logging

from live_sched.cif_loader import load_lm_schedules
from live_sched.docs_gen import write_schedule_code_docs


def test_load_lm_fixture_filters_non_lm(repo, lm_mca):
    result = load_lm_schedules(lm_mca, repo)
    assert len(result.service_guids) == 1
    assert result.code_counts.get("LI", 0) == 1
    assert result.code_counts.get("BS", 0) == 2

    journeys = repo.list_journeys()
    assert len(journeys) == 1
    journey = journeys[0]
    assert journey.toc == "LM"
    assert journey.train_uid == "C16922"
    assert journey.origin_crs == "CRE"
    assert journey.dest_crs == "BHM"
    assert journey.origin_std == "0925"
    assert journey.service_guid == "SVC-CREBHM2605170925"

    assert repo.get_journey_by_uid_date("Y99999", "260517") is None

    points = repo.list_calling_points(journey.service_guid)
    assert points[0].tiploc == "CREWE"
    assert points[-1].tiploc == "BHAMNWS"


def test_load_logs_stream_progress(repo, lm_mca, caplog):
    with caplog.at_level(logging.INFO, logger="live_sched.cif_loader"):
        load_lm_schedules(lm_mca, repo)
    assert "streaming" in caplog.text
    assert "line by line" in caplog.text
    assert "done" in caplog.text


def test_docs_generation_writes_summary_and_algorithms(repo, lm_mca, tmp_path):
    result = load_lm_schedules(lm_mca, repo)
    docs_dir = tmp_path / "docs"
    summary, algorithms = write_schedule_code_docs(
        docs_dir,
        result.code_counts,
        journeys_loaded=len(result.service_guids),
    )
    summary_text = summary.read_text(encoding="utf-8")
    algorithms_text = algorithms.read_text(encoding="utf-8")
    assert "Journeys persisted (LM): **1**" in summary_text
    assert "| LI |" in summary_text
    assert "## LI — Intermediate location" in algorithms_text
    assert "## BS — Basic schedule" in algorithms_text
