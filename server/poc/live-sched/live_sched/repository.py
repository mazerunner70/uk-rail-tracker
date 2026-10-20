"""Data-access helpers over the live-sched SQLite store."""

from __future__ import annotations

import json
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

from live_sched.service_day import ScheduleWindow, resolve_service_days


@dataclass(frozen=True)
class Journey:
    service_guid: str
    train_uid: str
    run_date: str
    origin_crs: str
    dest_crs: str
    origin_std: str
    toc: str
    date_runs_to: str = ""
    days_run: str = "0000000"
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
    forecast_arr: str | None = None
    forecast_dep: str | None = None


@dataclass(frozen=True)
class ServiceDay:
    train_uid: str
    calendar_date: str
    service_guid: str | None
    cancelled: bool = False


def _row_to_journey(row: sqlite3.Row) -> Journey:
    return Journey(
        service_guid=row["service_guid"],
        train_uid=row["train_uid"],
        run_date=row["run_date"],
        origin_crs=row["origin_crs"],
        dest_crs=row["dest_crs"],
        origin_std=row["origin_std"],
        toc=row["toc"],
        date_runs_to=row["date_runs_to"],
        days_run=row["days_run"],
        stp_indicator=row["stp_indicator"],
        status=row["status"],
    )


def _row_to_service_day(row: sqlite3.Row) -> ServiceDay:
    guid = row["service_guid"]
    return ServiceDay(
        train_uid=row["train_uid"],
        calendar_date=row["calendar_date"],
        service_guid=str(guid) if guid is not None else None,
        cancelled=bool(row["cancelled"]),
    )


class ScheduleRepository:
    def __init__(self, conn: sqlite3.Connection) -> None:
        self._conn = conn
        self._auto_commit = True

    def _commit(self) -> None:
        if self._auto_commit:
            self._conn.commit()

    def begin_batch(self) -> None:
        """Defer commits until ``commit_batch`` (for atomic CFA apply)."""
        self._auto_commit = False

    def commit_batch(self) -> None:
        self._conn.commit()
        self._auto_commit = True

    def rollback_batch(self) -> None:
        self._conn.rollback()
        self._auto_commit = True

    def upsert_journey(self, journey: Journey) -> None:
        date_to = journey.date_runs_to or journey.run_date
        days_run = journey.days_run or "0000000"
        try:
            self._conn.execute(
                """
                INSERT INTO journeys (
                    service_guid, train_uid, run_date, date_runs_to, days_run,
                    origin_crs, dest_crs, origin_std, toc, stp_indicator, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(service_guid) DO UPDATE SET
                    train_uid=excluded.train_uid,
                    run_date=excluded.run_date,
                    date_runs_to=excluded.date_runs_to,
                    days_run=excluded.days_run,
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
                    date_to,
                    days_run,
                    journey.origin_crs,
                    journey.dest_crs,
                    journey.origin_std,
                    journey.toc,
                    journey.stp_indicator,
                    journey.status,
                ),
            )
            self._commit()
        except sqlite3.IntegrityError:
            self._conn.rollback()
            self._auto_commit = True
            raise

    def replace_calling_points(
        self, service_guid: str, points: list[CallingPoint]
    ) -> None:
        self._conn.execute(
            "DELETE FROM calling_points WHERE service_guid = ?", (service_guid,)
        )
        self._conn.executemany(
            """
            INSERT INTO calling_points (
                service_guid, seq, tiploc, crs, public_arr, public_dep,
                forecast_arr, forecast_dep, activity
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    p.service_guid,
                    p.seq,
                    p.tiploc,
                    p.crs,
                    p.public_arr,
                    p.public_dep,
                    p.forecast_arr,
                    p.forecast_dep,
                    p.activity,
                )
                for p in points
            ],
        )
        self._commit()

    def get_journey(self, service_guid: str) -> Journey | None:
        row = self._conn.execute(
            "SELECT * FROM journeys WHERE service_guid = ?", (service_guid,)
        ).fetchone()
        return _row_to_journey(row) if row else None

    def query_journeys_by_uid_date(
        self, train_uid: str, run_date: str
    ) -> list[dict[str, Any]]:
        """CIF schedules sharing UID and date_runs_from (may differ by STP)."""
        rows = self._conn.execute(
            "SELECT * FROM journeys WHERE train_uid = ? AND run_date = ?",
            (train_uid, run_date),
        ).fetchall()
        return [dict(row) for row in rows]

    def get_service_day(
        self, train_uid: str, calendar_date: str
    ) -> ServiceDay | None:
        row = self._conn.execute(
            """
            SELECT * FROM service_day
            WHERE train_uid = ? AND calendar_date = ?
            """,
            (train_uid, calendar_date),
        ).fetchone()
        return _row_to_service_day(row) if row else None

    def get_journey_by_uid_date(self, train_uid: str, run_date: str) -> Journey | None:
        """Resolved plan for UID on a calendar date via ``service_day``."""
        day = self.get_service_day(train_uid, run_date)
        if day is None or day.cancelled or not day.service_guid:
            return None
        return self.get_journey(day.service_guid)

    def get_journey_by_cif_identity(
        self, train_uid: str, run_date: str, stp_indicator: str
    ) -> Journey | None:
        """Lookup by CIF identity ``(train_uid, date_runs_from, stp)``."""
        row = self._conn.execute(
            """
            SELECT * FROM journeys
            WHERE train_uid = ? AND run_date = ? AND stp_indicator = ?
            """,
            (train_uid, run_date, stp_indicator or "P"),
        ).fetchone()
        return _row_to_journey(row) if row else None

    def delete_journey(self, service_guid: str) -> bool:
        """Delete a journey and dependent calling points / service_day rows."""
        cur = self._conn.execute(
            "DELETE FROM journeys WHERE service_guid = ?", (service_guid,)
        )
        self._commit()
        return cur.rowcount > 0

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
                forecast_arr=r["forecast_arr"],
                forecast_dep=r["forecast_dep"],
            )
            for r in rows
        ]

    def rematerialize_service_days(self, train_uids: set[str] | None = None) -> int:
        """
        Rebuild ``service_day`` rows from stored CIF windows.

        When ``train_uids`` is set, only those UIDs are rebuilt. Returns the
        number of service_day rows written.
        """
        if train_uids is not None and not train_uids:
            return 0
        if train_uids is None:
            journeys = self.list_journeys()
            self._conn.execute("DELETE FROM service_day")
        else:
            placeholders = ",".join("?" for _ in train_uids)
            self._conn.execute(
                f"DELETE FROM service_day WHERE train_uid IN ({placeholders})",
                tuple(sorted(train_uids)),
            )
            rows = self._conn.execute(
                f"SELECT * FROM journeys WHERE train_uid IN ({placeholders})",
                tuple(sorted(train_uids)),
            ).fetchall()
            journeys = [_row_to_journey(r) for r in rows]

        windows = [
            ScheduleWindow(
                service_guid=j.service_guid,
                train_uid=j.train_uid,
                date_runs_from=j.run_date,
                date_runs_to=j.date_runs_to or j.run_date,
                days_run=j.days_run or "0000000",
                stp_indicator=j.stp_indicator or "P",
            )
            for j in journeys
        ]
        resolved = resolve_service_days(windows)
        self._conn.executemany(
            """
            INSERT INTO service_day (
                train_uid, calendar_date, service_guid, cancelled
            ) VALUES (?, ?, ?, ?)
            """,
            [
                (uid, day, guid, 1 if cancelled else 0)
                for (uid, day), (guid, cancelled) in resolved.items()
            ],
        )
        self._commit()
        return len(resolved)

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
        self._commit()

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
        self._commit()

    def overlay_calling_point_times(
        self,
        service_guid: str,
        tiploc: str,
        *,
        public_arr: str | None = None,
        public_dep: str | None = None,
    ) -> bool:
        """Apply live forecast times; booked public times stay unchanged."""
        row = self._conn.execute(
            """
            SELECT id, forecast_arr, forecast_dep FROM calling_points
            WHERE service_guid = ? AND tiploc = ?
            """,
            (service_guid, tiploc),
        ).fetchone()
        if row is None:
            return False
        new_arr = public_arr if public_arr is not None else row["forecast_arr"]
        new_dep = public_dep if public_dep is not None else row["forecast_dep"]
        self._conn.execute(
            """
            UPDATE calling_points
            SET forecast_arr = ?, forecast_dep = ?
            WHERE id = ?
            """,
            (new_arr, new_dep, row["id"]),
        )
        self._commit()
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
        self._commit()

    def count_applied_updates(self, service_guid: str | None = None) -> int:
        if service_guid is None:
            row = self._conn.execute(
                "SELECT COUNT(*) AS c FROM applied_updates"
            ).fetchone()
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

    def kafka_offsets_for_topic(self, topic: str) -> dict[int, int]:
        """Next offset to read per partition. Missing partitions have no row."""
        rows = self._conn.execute(
            """
            SELECT partition, next_offset FROM kafka_offsets
            WHERE topic = ?
            """,
            (topic,),
        ).fetchall()
        return {int(row["partition"]): int(row["next_offset"]) for row in rows}

    def save_kafka_next_offset(
        self, topic: str, partition: int, next_offset: int
    ) -> None:
        """Record the next offset to read after a message has been handled."""
        when = datetime.now(timezone.utc).isoformat()
        self._conn.execute(
            """
            INSERT INTO kafka_offsets (topic, partition, next_offset, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(topic, partition) DO UPDATE SET
                next_offset = excluded.next_offset,
                updated_at = excluded.updated_at
            """,
            (topic, partition, next_offset, when),
        )
        self._commit()
