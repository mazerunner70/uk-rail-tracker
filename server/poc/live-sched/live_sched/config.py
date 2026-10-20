"""Settings loaded from the workspace-root `.env` (Kafka keys match the Kafka POC)."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

PACKAGE_ROOT = Path(__file__).resolve().parents[1]
WORKSPACE_ROOT = PACKAGE_ROOT.parents[2]
DEFAULT_ENV_PATH = WORKSPACE_ROOT / ".env"

DEFAULT_DURATION_SECONDS = 3600
DEFAULT_DB_PATH = PACKAGE_ROOT / "data" / "live_sched.sqlite"
DEFAULT_CSV_PATH = PACKAGE_ROOT / "output" / "kafka-messages.csv"
DEFAULT_CHANGE_LOG_PATH = PACKAGE_ROOT / "output" / "db-changes.md"
DEFAULT_DOCS_DIR = PACKAGE_ROOT / "docs"
DEFAULT_SCHEDULE_DIR = WORKSPACE_ROOT / "server" / "poc" / "timetable"
DEFAULT_SCHEDULE_FILE = "RJTTF944MCA.txt"
DEFAULT_MCA_PATH = DEFAULT_SCHEDULE_DIR / DEFAULT_SCHEDULE_FILE
DEFAULT_CFA_WATCH_DIR = PACKAGE_ROOT / "data" / "cfa-inbox"
DEFAULT_FEED_SEQUENCE_SEED = "990"


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

    def topic_for_format(self, fmt: str = "json") -> str:
        topic = {
            "json": self.topic_json,
            "xml": self.topic_xml,
            "avro": self.topic_avro,
        }.get(fmt)
        if not topic:
            raise ValueError(
                f"Topic for format '{fmt}' is not set. "
                f"Set KAFKA_TOPIC_{fmt.upper()} in the workspace-root .env"
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


def _load_env(env_path: str | Path | None = None) -> None:
    """Load workspace-root `.env`, or an explicit path when that file exists."""
    path = Path(env_path) if env_path else DEFAULT_ENV_PATH
    if path.is_file():
        load_dotenv(path, override=False)
    elif env_path is None:
        load_dotenv(override=False)


def resolve_schedule_path(env_path: str | Path | None = None) -> Path:
    """MCA path for `live-sched load`: `SCHEDULE_DIR` / `SCHEDULE_FILE`.

    A relative `SCHEDULE_DIR` is resolved from the workspace root. When either
    variable is unset, the built-in timetable default is used.
    """
    _load_env(env_path)
    folder = os.getenv("SCHEDULE_DIR") or str(DEFAULT_SCHEDULE_DIR)
    name = os.getenv("SCHEDULE_FILE") or DEFAULT_SCHEDULE_FILE
    directory = Path(folder).expanduser()
    if not directory.is_absolute():
        directory = WORKSPACE_ROOT / directory
    return directory / name


def resolve_cfa_watch_dir(env_path: str | Path | None = None) -> Path:
    """Watch folder for daily CFA update zips (`CFA_WATCH_DIR`)."""
    _load_env(env_path)
    folder = os.getenv("CFA_WATCH_DIR") or str(DEFAULT_CFA_WATCH_DIR)
    directory = Path(folder).expanduser()
    if not directory.is_absolute():
        directory = WORKSPACE_ROOT / directory
    return directory


def resolve_feed_sequence_seed(env_path: str | Path | None = None) -> str:
    """Bootstrap previous-load code when the DB has no feed_meta row yet."""
    _load_env(env_path)
    return os.getenv("FEED_SEQUENCE_SEED") or DEFAULT_FEED_SEQUENCE_SEED


def load_kafka_settings(env_path: str | Path | None = None) -> KafkaSettings:
    """Load Kafka credentials from workspace-root `.env` (or an explicit path)."""
    _load_env(env_path)

    required = {
        "KAFKA_BOOTSTRAP_SERVERS": os.getenv("KAFKA_BOOTSTRAP_SERVERS"),
        "KAFKA_CONSUMER_KEY": os.getenv("KAFKA_CONSUMER_KEY"),
        "KAFKA_CONSUMER_SECRET": os.getenv("KAFKA_CONSUMER_SECRET"),
        "KAFKA_GROUP_ID": os.getenv("KAFKA_GROUP_ID"),
    }
    missing = [name for name, value in required.items() if not value]
    if missing:
        raise RuntimeError(
            "Missing required environment variables: "
            + ", ".join(missing)
            + f". Copy workspace-root .env.example to {DEFAULT_ENV_PATH} "
            "and fill from RDM Pub/Sub (same names as server/poc/kafka)."
        )

    return KafkaSettings(
        bootstrap_servers=required["KAFKA_BOOTSTRAP_SERVERS"] or "",
        consumer_key=required["KAFKA_CONSUMER_KEY"] or "",
        consumer_secret=required["KAFKA_CONSUMER_SECRET"] or "",
        group_id=required["KAFKA_GROUP_ID"] or "",
        ssl_ca_location=os.getenv("KAFKA_SSL_CA_LOCATION") or None,
        topic_json=os.getenv("KAFKA_TOPIC_JSON") or None,
        topic_xml=os.getenv("KAFKA_TOPIC_XML") or None,
        topic_avro=os.getenv("KAFKA_TOPIC_AVRO") or None,
    )
