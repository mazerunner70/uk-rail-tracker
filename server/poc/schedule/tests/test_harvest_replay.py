"""Harvest stop-rule and replay overlay tests (FR2)."""

from __future__ import annotations

import json

from schedule_poc.cif_loader import load_lm_schedules
from schedule_poc.harvest import (
    harvest_lm_events,
    read_harvest_corpus,
    should_stop_harvest,
)
from schedule_poc.replay import replay_records


def _ts_payload(uid: str, rid: str, toc: str | None = None) -> dict:
    body: dict = {
        "TS": {
            "rid": rid,
            "uid": uid,
            "ssd": "2026-05-17",
            "Location": {
                "tpl": "CREWE",
                "dep": {"et": "09:30"},
            },
        }
    }
    if toc:
        body["TS"]["toc"] = toc
    return {"ts": "2026-05-17T08:00:00Z", "uR": body}


def test_should_stop_at_20_lm_events():
    assert should_stop_harvest(lm_event_count=19, elapsed_seconds=10.0) is None
    assert (
        should_stop_harvest(lm_event_count=20, elapsed_seconds=10.0) == "max_lm_events"
    )


def test_should_stop_at_two_hours():
    two_hours = 2 * 60 * 60
    assert should_stop_harvest(lm_event_count=1, elapsed_seconds=two_hours - 1) is None
    assert (
        should_stop_harvest(lm_event_count=1, elapsed_seconds=two_hours)
        == "max_duration"
    )


def test_harvest_stops_at_20_with_injected_clock():
    known = {"C16922"}
    messages = [
        json.dumps(_ts_payload("C16922", f"rid-{i}")) for i in range(25)
    ]
    # Clock never advances — only the event counter can stop harvest
    result = harvest_lm_events(
        messages,
        known_lm_uids=known,
        max_lm_events=20,
        max_duration_seconds=7200,
        clock=lambda: 0.0,
    )
    assert result.lm_event_count == 20
    assert result.stopped_reason == "max_lm_events"


def test_harvest_stops_on_duration_with_injected_clock():
    known = {"C16922"}
    messages = [json.dumps(_ts_payload("C16922", f"rid-{i}")) for i in range(5)]
    ticks = [0.0, 100.0, 200.0, 300.0, 400.0, 500.0, 8000.0, 8000.0]
    idx = {"i": 0}

    def clock() -> float:
        value = ticks[min(idx["i"], len(ticks) - 1)]
        idx["i"] += 1
        return value

    result = harvest_lm_events(
        messages,
        known_lm_uids=known,
        max_lm_events=20,
        max_duration_seconds=7200,
        clock=clock,
    )
    assert result.stopped_reason == "max_duration"
    assert result.lm_event_count < 20


def test_replay_overlay_from_harvest_fixture(repo, lm_mca, harvest_path):
    load_lm_schedules(lm_mca, repo)
    guid = "SVC-CREBHM2605170925"
    before = {p.tiploc: p for p in repo.list_calling_points(guid)}
    assert before["STAFFRD"].public_arr == "0947"

    records = read_harvest_corpus(harvest_path)
    # Drop the non-LM noise record for deterministic overlay count
    lm_records = [r for r in records if "C16922" in json.dumps(r.payload)]
    applied = replay_records(repo, lm_records)
    assert applied >= 1

    after = {p.tiploc: p for p in repo.list_calling_points(guid)}
    assert after["STAFFRD"].public_arr == "0950"
    assert after["STAFFRD"].public_dep == "0951"
    assert after["CREWE"].public_dep == "0926"
    assert repo.count_applied_updates(guid) >= 1
    assert repo.get_journey(guid) is not None
