"""Data-access helpers over the schedule POC SQLite store."""

from __future__ import annotations

import json
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any


@dataclass(frozen=True)
class Journey:
    service_guid: str
    train_uid: str
    run_date: str
    origin_crs: str
    dest_crs: str
    origin_std: str
    toc: str
    stp_indicator: str = "P"
    status: str = "published"


@dataclass(frozen=True)
class CallingPoint:
    service_guid: str
    seq: int
    tiploc: str
    crs: str | None
    public_arr: str | None
    public_dep: str | None
    activity: str = "call"


def _row_to_journey(row: sqlite3.Row) -> Journey:
    return Journey(
        service_guid=row["service_guid"],
        train_uid=row["train_uid"],
        run_date=row["run_date"],
        origin_crs=row["origin_crs"],
        dest_crs=row["dest_crs"],
        origin_std=row["origin_std"],
        toc=row["toc"],
        stp_indicator=row["stp_indicator"],
        status=row["status"],
    )


class ScheduleRepository:
    def __init__(self, conn: sqlite3.Connection) -> None:
        self._conn = conn

    def upsert_journey(self, journey: Journey) -> None:
        self._conn.execute(
            """
            INSERT INTO journeys (
                service_guid, train_uid, run_date, origin_crs, dest_crs,
                origin_std, toc, stp_indicator, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(service_guid) DO UPDATE SET
                train_uid=excluded.train_uid,
                run_date=excluded.run_date,
                origin_crs=excluded.origin_crs,
                dest_crs=excluded.dest_crs,
                origin_std=excluded.origin_std,
                toc=excluded.toc,
                stp_indicator=excluded.stp_indicator,
                status=excluded.status
            """,
            (
                journey.service_guid,
                journey.train_uid,
                journey.run_date,
                journey.origin_crs,
                journey.dest_crs,
                journey.origin_std,
                journey.toc,
                journey.stp_indicator,
                journey.status,
            ),
        )
        self._conn.commit()

    def replace_calling_points(
        self, service_guid: str, points: list[CallingPoint]
    ) -> None:
        self._conn.execute(
            "DELETE FROM calling_points WHERE service_guid = ?", (service_guid,)
        )
        self._conn.executemany(
            """
            INSERT INTO calling_points (
                service_guid, seq, tiploc, crs, public_arr, public_dep, activity
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    p.service_guid,
                    p.seq,
                    p.tiploc,
                    p.crs,
                    p.public_arr,
                    p.public_dep,
                    p.activity,
                )
                for p in points
            ],
        )
        self._conn.commit()

    def get_journey(self, service_guid: str) -> Journey | None:
        row = self._conn.execute(
            "SELECT * FROM journeys WHERE service_guid = ?", (service_guid,)
        ).fetchone()
        return _row_to_journey(row) if row else None

    def get_journey_by_uid_date(self, train_uid: str, run_date: str) -> Journey | None:
        row = self._conn.execute(
            "SELECT * FROM journeys WHERE train_uid = ? AND run_date = ?",
            (train_uid, run_date),
        ).fetchone()
        return _row_to_journey(row) if row else None

    def list_journeys(self, toc: str | None = None) -> list[Journey]:
        if toc:
            rows = self._conn.execute(
                "SELECT * FROM journeys WHERE toc = ? ORDER BY service_guid", (toc,)
            ).fetchall()
        else:
            rows = self._conn.execute(
                "SELECT * FROM journeys ORDER BY service_guid"
            ).fetchall()
        return [_row_to_journey(r) for r in rows]

    def list_calling_points(self, service_guid: str) -> list[CallingPoint]:
        rows = self._conn.execute(
            """
            SELECT * FROM calling_points
            WHERE service_guid = ?
            ORDER BY seq
            """,
            (service_guid,),
        ).fetchall()
        return [
            CallingPoint(
                service_guid=r["service_guid"],
                seq=r["seq"],
                tiploc=r["tiploc"],
                crs=r["crs"],
                public_arr=r["public_arr"],
                public_dep=r["public_dep"],
                activity=r["activity"],
            )
            for r in rows
        ]

    def set_alias(self, alias_type: str, alias_value: str, service_guid: str) -> None:
        self._conn.execute(
            """
            INSERT INTO service_aliases (alias_type, alias_value, service_guid)
            VALUES (?, ?, ?)
            ON CONFLICT(alias_type, alias_value) DO UPDATE SET
                service_guid=excluded.service_guid
            """,
            (alias_type, alias_value, service_guid),
        )
        self._conn.commit()

    def resolve_alias(self, alias_type: str, alias_value: str) -> str | None:
        row = self._conn.execute(
            """
            SELECT service_guid FROM service_aliases
            WHERE alias_type = ? AND alias_value = ?
            """,
            (alias_type, alias_value),
        ).fetchone()
        return str(row["service_guid"]) if row else None

    def update_journey_status(self, service_guid: str, status: str) -> None:
        self._conn.execute(
            "UPDATE journeys SET status = ? WHERE service_guid = ?",
            (status, service_guid),
        )
        self._conn.commit()

    def overlay_calling_point_times(
        self,
        service_guid: str,
        tiploc: str,
        *,
        public_arr: str | None = None,
        public_dep: str | None = None,
    ) -> bool:
        """Apply live public time overlay to a calling point; returns True if matched."""
        row = self._conn.execute(
            """
            SELECT id, public_arr, public_dep FROM calling_points
            WHERE service_guid = ? AND tiploc = ?
            """,
            (service_guid, tiploc),
        ).fetchone()
        if row is None:
            return False
        new_arr = public_arr if public_arr is not None else row["public_arr"]
        new_dep = public_dep if public_dep is not None else row["public_dep"]
        self._conn.execute(
            """
            UPDATE calling_points
            SET public_arr = ?, public_dep = ?
            WHERE id = ?
            """,
            (new_arr, new_dep, row["id"]),
        )
        self._conn.commit()
        return True

    def record_applied_update(
        self,
        *,
        service_guid: str | None,
        update_type: str,
        payload: dict[str, Any],
        rid: str | None = None,
        source_offset: int | None = None,
        applied_at: str | None = None,
    ) -> None:
        when = applied_at or datetime.now(timezone.utc).isoformat()
        self._conn.execute(
            """
            INSERT INTO applied_updates (
                service_guid, update_type, rid, payload_json, applied_at, source_offset
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                service_guid,
                update_type,
                rid,
                json.dumps(payload),
                when,
                source_offset,
            ),
        )
        self._conn.commit()

    def count_applied_updates(self, service_guid: str | None = None) -> int:
        if service_guid is None:
            row = self._conn.execute("SELECT COUNT(*) AS c FROM applied_updates").fetchone()
        else:
            row = self._conn.execute(
                "SELECT COUNT(*) AS c FROM applied_updates WHERE service_guid = ?",
                (service_guid,),
            ).fetchone()
        return int(row["c"])

    def known_lm_uids(self) -> set[str]:
        rows = self._conn.execute(
            "SELECT DISTINCT train_uid FROM journeys WHERE toc = 'LM'"
        ).fetchall()
        return {str(r["train_uid"]) for r in rows}
