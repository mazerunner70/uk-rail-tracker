"""Push Port JSON classification grounded in server/poc/kafka FINDINGS."""

from __future__ import annotations

import json
from typing import Any, cast

UPDATE_TYPES = frozenset(
    {
        "schedule",
        "deactivated",
        "association",
        "scheduleFormations",
        "TS",
        "formationLoading",
        "serviceLoading",
        "trainOrder",
        "OW",
        "trainAlert",
        "trackingID",
        "alarm",
        "stationInformation",
    }
)
_WRAPPER_KEYS = ("uR", "sR")


def decode_kafka_value(raw: bytes | str | dict[str, Any]) -> dict[str, Any] | None:
    """Decode a Kafka JSON topic value into a Push Port payload dict."""
    if isinstance(raw, dict):
        payload = raw
    else:
        text = raw.decode("utf-8") if isinstance(raw, bytes) else raw
        try:
            envelope = json.loads(text)
        except json.JSONDecodeError:
            return None
        if not isinstance(envelope, dict):
            return None
        typed = cast(dict[str, Any], envelope)
        inner = typed.get("bytes")
        if isinstance(inner, str):
            try:
                nested = json.loads(inner)
            except json.JSONDecodeError:
                return typed
            if isinstance(nested, dict):
                return cast(dict[str, Any], nested)
        payload = typed
    return payload


def wrapper_body(payload: dict[str, Any]) -> dict[str, Any] | None:
    for key in _WRAPPER_KEYS:
        body = payload.get(key)
        if isinstance(body, dict):
            return cast(dict[str, Any], body)
    return None


def classify_update_types(payload: dict[str, Any]) -> list[str]:
    body = wrapper_body(payload)
    if body is None:
        return []
    return sorted(key for key in body if key in UPDATE_TYPES)


def iter_update_items(
    payload: dict[str, Any], update_type: str
) -> list[dict[str, Any]]:
    """Return normalised list of child objects for an update type under uR/sR."""
    body = wrapper_body(payload)
    if body is None:
        return []
    value = body.get(update_type)
    if value is None:
        return []
    if isinstance(value, list):
        return [cast(dict[str, Any], item) for item in value if isinstance(item, dict)]
    if isinstance(value, dict):
        return [cast(dict[str, Any], value)]
    return []


def extract_uid(item: dict[str, Any]) -> str | None:
    for key in ("uid", "trainUid", "train_uid"):
        value = item.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


def extract_rid(item: dict[str, Any]) -> str | None:
    value = item.get("rid")
    if isinstance(value, str) and value.strip():
        return value.strip()
    return None


def extract_ssd(item: dict[str, Any]) -> str | None:
    """Schedule start date — Darwin uses YYYY-MM-DD; normalise to YYMMDD when possible."""
    value = item.get("ssd")
    if not isinstance(value, str) or not value.strip():
        return None
    text = value.strip()
    digits = "".join(ch for ch in text if ch.isdigit())
    if len(digits) == 8:
        return digits[2:]  # YYYYMMDD → YYMMDD
    if len(digits) == 6:
        return digits
    return text


def extract_toc(item: dict[str, Any]) -> str | None:
    for key in ("toc", "tocCode", "atocCode"):
        value = item.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip().upper()
    return None


def is_lm_relevant(
    payload: dict[str, Any],
    *,
    known_lm_uids: set[str],
    toc: str = "LM",
) -> bool:
    """
    True when the payload mentions TOC LM or a UID already loaded as LM.

    Matches harvest filter used by the POC (national feed, client-side filter).
    """
    for update_type in classify_update_types(payload):
        for item in iter_update_items(payload, update_type):
            item_toc = extract_toc(item)
            if item_toc == toc:
                return True
            uid = extract_uid(item)
            if uid and uid in known_lm_uids:
                return True
    return False
