"""Verbose startup diagnostics for the RDM Darwin Kafka POC."""

from __future__ import annotations

import textwrap
from typing import Any

from config import KafkaSettings

# RSPS5053 §4.2 — values that must match the RDM Pub/Sub tab exactly.
KAFKA_CLIENT_DOCS: dict[str, str] = {
    "bootstrap.servers": (
        "Kafka broker address from RDM Pub/Sub tab. "
        "Entry point for the cluster; the client discovers brokers from here."
    ),
    "security.protocol": (
        "Must be SASL_SSL (RSPS5053 §4.2.6). "
        "Encrypts traffic with TLS and authenticates via SASL."
    ),
    "sasl.mechanism": (
        "Must be PLAIN (RSPS5053 §4.2.6). "
        "Username/password sent over the encrypted channel."
    ),
    "sasl.username": (
        "RDM consumer key — same value as KAFKA_CONSUMER_KEY in .env. "
        "Acts as the Kafka SASL username."
    ),
    "sasl.password": (
        "RDM consumer secret — same value as KAFKA_CONSUMER_SECRET. "
        "Never logged in full."
    ),
    "group.id": (
        "Consumer group id from RDM Pub/Sub tab (KAFKA_GROUP_ID). "
        "Required by librdkafka even for manual assign(). The library probes "
        "the group coordinator once at startup; RDM may log "
        "GROUP_AUTHORIZATION_FAILED — that is non-fatal and consumption "
        "continues."
    ),
    "auto.offset.reset": (
        "Fallback when no committed offset exists. "
        "'latest' = only new messages; 'earliest' = oldest retained message."
    ),
    "enable.auto.commit": (
        "Disabled — this POC does not commit offsets to the consumer group."
    ),
    "ssl.ca.location": (
        "Optional path to a CA certificate bundle (RSPS5053 §4.2.7–4.2.8). "
        "Omit when the OS trust store is sufficient."
    ),
}

ENV_DOCS: dict[str, str] = {
    "KAFKA_BOOTSTRAP_SERVERS": KAFKA_CLIENT_DOCS["bootstrap.servers"],
    "KAFKA_CONSUMER_KEY": KAFKA_CLIENT_DOCS["sasl.username"],
    "KAFKA_CONSUMER_SECRET": KAFKA_CLIENT_DOCS["sasl.password"],
    "KAFKA_GROUP_ID": KAFKA_CLIENT_DOCS["group.id"],
    "KAFKA_SSL_CA_LOCATION": KAFKA_CLIENT_DOCS["ssl.ca.location"],
    "KAFKA_TOPIC_JSON": (
        "Full Kafka topic name for JSON-encoded Darwin Push Port messages "
        "(RSPS5053 §4.2.2). Shown on the RDM Pub/Sub tab."
    ),
    "KAFKA_TOPIC_XML": (
        "Full Kafka topic name for XML-encoded messages (RSPS5053 §4.2.2). "
        "XSD schemas: RSPS5051."
    ),
    "KAFKA_TOPIC_AVRO": (
        "Full Kafka topic name for Avro-encoded messages (RSPS5053 §4.2.2)."
    ),
}

FORMAT_DOCS: dict[str, str] = {
    "json": "Structured JSON Darwin updates — easiest to inspect in a POC.",
    "xml": "gzip-style Push Port XML as published on the XML topic.",
    "avro": "Avro binary encoding; schema from RDM reference material.",
}


def _mask(value: str, *, show: int = 4) -> str:
    if len(value) <= show * 2:
        return "*" * len(value)
    return f"{value[:show]}…{value[-show:]}"


def _print_block(title: str, lines: list[str]) -> None:
    print(f"\n{'=' * 72}")
    print(title)
    print("=" * 72)
    for line in lines:
        print(line)


def _wrap(label: str, text: str, indent: str = "    ") -> None:
    wrapped = textwrap.fill(
        text,
        width=72 - len(indent),
        initial_indent=indent,
        subsequent_indent=indent,
    )
    print(f"  {label}")
    print(wrapped)


def print_env_config(settings: KafkaSettings, *, fmt: str | None = None) -> None:
    """Explain .env values loaded for this run."""
    lines = [
        "Source: .env in this directory (see .env.example for template).",
        "Credentials and connection details come from RDM → Darwin PubSub → Pub/Sub tab.",
        "",
        "Environment variables:",
    ]
    env_values: list[tuple[str, str, str]] = [
        ("KAFKA_BOOTSTRAP_SERVERS", settings.bootstrap_servers, "plain"),
        ("KAFKA_CONSUMER_KEY", settings.consumer_key, "partial"),
        ("KAFKA_CONSUMER_SECRET", settings.consumer_secret, "secret"),
        ("KAFKA_GROUP_ID", settings.group_id, "plain"),
        (
            "KAFKA_SSL_CA_LOCATION",
            settings.ssl_ca_location or "(not set — using OS trust store)",
            "plain",
        ),
        ("KAFKA_TOPIC_JSON", settings.topic_json or "(not set)", "plain"),
        ("KAFKA_TOPIC_XML", settings.topic_xml or "(not set)", "plain"),
        ("KAFKA_TOPIC_AVRO", settings.topic_avro or "(not set)", "plain"),
    ]
    for name, value, kind in env_values:
        if kind == "secret":
            display = _mask(value)
        elif kind == "partial":
            display = _mask(value)
        else:
            display = value
        lines.append(f"  {name}={display}")

    _print_block("RDM / .env configuration", lines)

    for name, _, _ in env_values:
        if name in ENV_DOCS:
            _wrap(f"{name}:", ENV_DOCS[name])

    if fmt:
        topic = {
            "json": settings.topic_json,
            "xml": settings.topic_xml,
            "avro": settings.topic_avro,
        }.get(fmt)
        _print_block(
            f"Selected format: {fmt}",
            [
                f"Topic: {topic or '(not configured)'}",
                "",
                FORMAT_DOCS.get(fmt, ""),
            ],
        )


def print_kafka_client_config(config: dict[str, str]) -> None:
    """Explain the confluent-kafka Consumer config dict."""
    lines = ["Kafka client properties passed to confluent_kafka.Consumer:"]
    for key, value in config.items():
        if key == "sasl.password":
            lines.append(f"  {key}={_mask(value)}")
        elif key == "sasl.username":
            lines.append(f"  {key}={_mask(value)}")
        else:
            lines.append(f"  {key}={value}")

    _print_block("Kafka consumer client config", lines)

    for key in config:
        if key in KAFKA_CLIENT_DOCS:
            _wrap(f"{key}:", KAFKA_CLIENT_DOCS[key])


def print_consumption_mode(*, from_beginning: bool, use_group: bool) -> None:
    mode = (
        "catch-up (earliest retained offsets)"
        if from_beginning
        else "live stream (end offsets — only messages arriving now)"
    )
    assignment = (
        "subscribe() + consumer group — joins group coordinator, commits offsets"
        if use_group
        else "assign() — manual partition assignment, no group join"
    )
    _print_block(
        "Consumption mode",
        [
            f"Read mode: {mode}",
            f"Assignment: {assignment}",
            "",
            "Why assign() instead of subscribe():",
            textwrap.fill(
                "RDM subscriptions can reject consumer-group joins "
                "(GROUP_AUTHORIZATION_FAILED) when the group id does not match "
                "the ACL bound to your credentials. Manual assign() reads topic "
                "partitions directly using only topic READ permission.",
                width=70,
                initial_indent="  ",
                subsequent_indent="  ",
            ),
        ],
    )


def print_topic_discovery(
    topic: str,
    partition_ids: list[int],
    *,
    from_beginning: bool,
    watermarks: list[dict[str, Any]] | None = None,
) -> None:
    lines = [
        f"Topic: {topic}",
        f"Partitions discovered: {partition_ids}",
        f"Starting offset per partition: {'BEGINNING (0 or low watermark)' if from_beginning else 'END (high watermark — live only)'}",
    ]
    if watermarks:
        lines.append("")
        lines.append("Partition watermarks (messages currently retained in the log):")
        total = 0
        for row in watermarks:
            retained = row["retained_messages"]
            total += retained
            lines.append(
                f"  partition {row['partition']:>2}: "
                f"low={row['low_offset']:,} high={row['high_offset']:,} "
                f"retained={retained:,}"
            )
        lines.append(f"  total retained (all partitions): {total:,}")
        lines.append("")
        lines.append(
            "Retained count = messages still in Kafka (until retention expires). "
            "It is not the same as 'unread queue depth' for your consumer."
        )

    _print_block("Topic discovery", lines)
