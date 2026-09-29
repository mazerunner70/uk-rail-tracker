"""Shared Kafka configuration for the RDM Darwin PubSub POC."""

from __future__ import annotations

import os
import sys
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent / ".env")


@dataclass(frozen=True)
class KafkaSettings:
    bootstrap_servers: str
    consumer_key: str
    consumer_secret: str
    group_id: str
    ssl_ca_location: str | None
    topic_json: str | None
    topic_xml: str | None
    topic_avro: str | None

    def topic_for_format(self, fmt: str) -> str:
        topic = {
            "json": self.topic_json,
            "xml": self.topic_xml,
            "avro": self.topic_avro,
        }.get(fmt)
        if not topic:
            raise ValueError(
                f"Topic for format '{fmt}' is not set. "
                f"Set KAFKA_TOPIC_{fmt.upper()} in .env"
            )
        return topic

    def consumer_config(self, *, group_id: str | None = None) -> dict[str, str]:
        config: dict[str, str] = {
            "bootstrap.servers": self.bootstrap_servers,
            "security.protocol": "SASL_SSL",
            "sasl.mechanism": "PLAIN",
            "sasl.username": self.consumer_key,
            "sasl.password": self.consumer_secret,
            "group.id": group_id or self.group_id,
            "auto.offset.reset": "latest",
            "enable.auto.commit": "false",
        }
        if self.ssl_ca_location:
            config["ssl.ca.location"] = self.ssl_ca_location
        return config


def load_settings() -> KafkaSettings:
    required = {
        "KAFKA_BOOTSTRAP_SERVERS": os.getenv("KAFKA_BOOTSTRAP_SERVERS"),
        "KAFKA_CONSUMER_KEY": os.getenv("KAFKA_CONSUMER_KEY"),
        "KAFKA_CONSUMER_SECRET": os.getenv("KAFKA_CONSUMER_SECRET"),
        "KAFKA_GROUP_ID": os.getenv("KAFKA_GROUP_ID"),
    }
    missing = [name for name, value in required.items() if not value]
    if missing:
        print(
            "Missing required environment variables: "
            + ", ".join(missing)
            + "\nCopy .env.example to .env and fill from RDM Pub/Sub tab.",
            file=sys.stderr,
        )
        sys.exit(1)

    return KafkaSettings(
        bootstrap_servers=required["KAFKA_BOOTSTRAP_SERVERS"],
        consumer_key=required["KAFKA_CONSUMER_KEY"],
        consumer_secret=required["KAFKA_CONSUMER_SECRET"],
        group_id=required["KAFKA_GROUP_ID"],
        ssl_ca_location=os.getenv("KAFKA_SSL_CA_LOCATION") or None,
        topic_json=os.getenv("KAFKA_TOPIC_JSON") or None,
        topic_xml=os.getenv("KAFKA_TOPIC_XML") or None,
        topic_avro=os.getenv("KAFKA_TOPIC_AVRO") or None,
    )
