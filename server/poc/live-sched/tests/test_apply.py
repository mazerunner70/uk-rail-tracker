"""Kafka classify/apply tests with fixture payloads (FR3)."""

from __future__ import annotations

import json

from live_sched.apply import apply_payload
from live_sched.cif_loader import load_lm_schedules
from live_sched.consumer import process_message_stream
from live_sched.kafka_classify import classify_update_types, decode_kafka_value


def _load_payloads(path):
    records = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        records.append(json.loads(line))
    return records


def test_apply_ts_schedule_deactivated_and_ignore_non_match(
    repo, lm_mca, kafka_payloads_path, tmp_path
):
    load_lm_schedules(lm_mca, repo)
    guid = "SVC-CREBHM2605170925"
    change_log = tmp_path / "db-changes.md"
    records = _load_payloads(kafka_payloads_path)

    before = {p.tiploc: p for p in repo.list_calling_points(guid)}
    assert before["STAFFRD"].public_arr == "0947"
    assert before["STAFFRD"].forecast_arr is None

    applied = 0
    for record in records:
        result = apply_payload(
            repo,
            record["payload"],
            source_offset=record["offset"],
            change_log_path=change_log,
        )
        applied += result.applied

    assert applied >= 3
    after = {p.tiploc: p for p in repo.list_calling_points(guid)}
    # Booked public times stay; TS writes forecast columns only.
    assert after["STAFFRD"].public_arr == "0947"
    assert after["STAFFRD"].public_dep == "0947"
    assert after["STAFFRD"].forecast_arr == "0950"
    assert after["STAFFRD"].forecast_dep == "0951"
    assert after["CREWE"].public_dep == "0925"
    assert after["CREWE"].forecast_dep == "0926"
    assert repo.get_journey(guid).status == "deactivated"
    assert repo.count_applied_updates(guid) >= 3

    log_text = change_log.read_text(encoding="utf-8")
    assert "`TS`" in log_text
    assert "`schedule`" in log_text
    assert "`deactivated`" in log_text
    assert "uid=C16922" in log_text
    assert "STAFFRD" in log_text
    assert "forecast arr — → 0950" in log_text
    assert "forecast dep — → 0951" in log_text
    assert "CREWE" in log_text
    assert "forecast dep — → 0926" in log_text
    assert "toc=LM" in log_text
    assert "timetable rows left unchanged" in log_text
    assert "status published → deactivated" in log_text


def test_ignore_non_apply_types_without_failing(repo, lm_mca):
    load_lm_schedules(lm_mca, repo)
    payload = {
        "uR": {"OW": {"id": "x"}, "TS": {"uid": "Y99999", "ssd": "2026-05-17"}}
    }
    assert "OW" in classify_update_types(payload)
    result = apply_payload(repo, payload)
    assert result.applied == 0
    assert "OW" in result.logged_types


def test_process_message_stream_archives_csv_and_stops_on_duration(
    repo, lm_mca, kafka_payloads_path, tmp_path
):
    load_lm_schedules(lm_mca, repo)
    csv_path = tmp_path / "kafka-messages.csv"
    change_log = tmp_path / "db-changes.md"
    records = _load_payloads(kafka_payloads_path)
    messages = [json.dumps(r["payload"]) for r in records]

    # Advance past duration after early messages are processed.
    sequence = [0.0, 0.1, 0.2, 10.0, 10.0]
    idx = {"i": 0}

    def clock() -> float:
        value = sequence[min(idx["i"], len(sequence) - 1)]
        idx["i"] += 1
        return value

    result = process_message_stream(
        messages,
        repo,
        csv_path=csv_path,
        change_log_path=change_log,
        duration_seconds=5.0,
        clock=clock,
    )
    assert result.stopped_reason == "max_duration"
    assert result.messages_seen >= 1
    csv_text = csv_path.read_text(encoding="utf-8")
    assert "received_at" in csv_text
    assert "uR" in csv_text
    assert decode_kafka_value(messages[0]) is not None
