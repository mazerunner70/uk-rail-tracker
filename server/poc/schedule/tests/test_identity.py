"""Stable journey identity tests (FR3)."""

from __future__ import annotations

from schedule_poc.cif_loader import load_lm_schedules
from schedule_poc.harvest import read_harvest_corpus
from schedule_poc.identity import build_service_guid, parse_service_guid
from schedule_poc.replay import replay_records, resolve_service_guid


def test_build_service_guid_format():
    guid = build_service_guid("CRE", "BHM", "260517", "0925")
    assert guid == "SVC-CREBHM2605170925"
    parts = parse_service_guid(guid)
    assert parts["origin"] == "CRE"
    assert parts["dest"] == "BHM"
    assert parts["run_date"] == "260517"
    assert parts["origin_std"] == "0925"


def test_stable_id_survives_updates(repo, lm_mca, harvest_path):
    load_lm_schedules(lm_mca, repo)
    guid = "SVC-CREBHM2605170925"
    assert repo.get_journey(guid) is not None

    records = read_harvest_corpus(harvest_path)
    replay_records(repo, records)

    # Same journey remains addressable by stable id after overlay
    journey = repo.get_journey(guid)
    assert journey is not None
    assert journey.train_uid == "C16922"
    assert journey.status == "published"


def test_rid_maps_to_service_guid_when_present(repo, lm_mca, harvest_path):
    """
    Kafka FINDINGS document rid/uid/ssd on TS/schedule.

    Optional Step 10: map Darwin rid → stable serviceGUID (no LDB serviceId
    traces in the kafka POC, so serviceId mapping is not invented).
    """
    load_lm_schedules(lm_mca, repo)
    records = read_harvest_corpus(harvest_path)
    replay_records(repo, records)

    guid = repo.resolve_alias("rid", "202605178765432")
    assert guid == "SVC-CREBHM2605170925"

    resolved = resolve_service_guid(
        repo,
        {"rid": "202605178765432", "uid": "C16922", "ssd": "2026-05-17"},
    )
    assert resolved == guid
