"""Kafka read-position stored in SQLite and used to resume."""

from __future__ import annotations

import json

from live_sched.consumer import ConsumedMessage, process_message_stream, start_offsets_for_partitions


def test_start_offsets_resume_saved_partitions_and_live_for_the_rest():
    starts = start_offsets_for_partitions(
        [2, 0, 1],
        {0: 40, 2: 7},
        live_offset=-1,
    )
    assert starts == [(0, 40), (1, -1), (2, 7)]


def test_process_message_stream_saves_next_offset(repo, tmp_path):
    csv_path = tmp_path / "kafka-messages.csv"
    change_log = tmp_path / "db-changes.md"
    payload = json.dumps({"uR": {"OW": {"id": "x"}}})
    messages = [
        ConsumedMessage(raw=payload, offset=10, partition=0, topic="darwin.json"),
        ConsumedMessage(raw="not-json", offset=11, partition=0, topic="darwin.json"),
        ConsumedMessage(raw=payload, offset=3, partition=1, topic="darwin.json"),
    ]

    result = process_message_stream(
        messages,
        repo,
        csv_path=csv_path,
        change_log_path=change_log,
        duration_seconds=60.0,
    )

    assert result.messages_seen == 3
    assert result.stopped_reason == "source_exhausted"
    assert repo.kafka_offsets_for_topic("darwin.json") == {0: 12, 1: 4}


def test_process_message_stream_ctrl_c_returns_partial_counts(repo, tmp_path):
    csv_path = tmp_path / "kafka-messages.csv"
    change_log = tmp_path / "db-changes.md"
    payload = json.dumps({"uR": {"OW": {"id": "x"}}})
    closed = {"value": False}

    def messages():
        try:
            yield ConsumedMessage(
                raw=payload, offset=1, partition=0, topic="darwin.json"
            )
            raise KeyboardInterrupt
        finally:
            closed["value"] = True

    result = process_message_stream(
        messages(),
        repo,
        csv_path=csv_path,
        change_log_path=change_log,
        duration_seconds=60.0,
    )

    assert result.stopped_reason == "keyboard_interrupt"
    assert result.messages_seen == 1
    assert result.applied == 0
    assert result.ignored == 1
    assert repo.kafka_offsets_for_topic("darwin.json") == {0: 2}
    assert closed["value"] is True


def test_save_kafka_next_offset_overwrites_same_partition(repo):
    repo.save_kafka_next_offset("darwin.json", 0, 5)
    repo.save_kafka_next_offset("darwin.json", 0, 9)
    repo.save_kafka_next_offset("darwin.json", 1, 2)
    assert repo.kafka_offsets_for_topic("darwin.json") == {0: 9, 1: 2}
    assert repo.kafka_offsets_for_topic("other") == {}
