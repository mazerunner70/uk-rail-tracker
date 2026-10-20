"""Write schedule-code summary and algorithm markdown under docs/."""

from __future__ import annotations

from pathlib import Path

from live_sched.cif_loader import HANDLED_CODES

ALGORITHM_TEXT = """# Schedule-code algorithms — live-sched MCA load

These algorithms describe how `live_sched.cif_loader` processes each CIF record
code during an MCA stream parse. Only London Midland (`LM`) schedules are
persisted; other TOCs are parsed far enough to skip cleanly.

## HD — Header

- Detected when the first two characters are `HD`.
- Counted for the summary; no location or journey state is mutated.
- Present on full Network Rail MCA files; optional on tiny fixtures.

## TI — TIPLOC insert

- Columns (0-based slices used by the loader): TIPLOC `[2:9]`, CRS `[53:56]`.
- When both TIPLOC and a three-letter alphabetic CRS are present, store
  `tiploc → crs` in an in-memory map used to resolve calling-point CRS codes.
- Invalid CRS fragments are ignored.

## BS — Basic schedule

- Starts a new in-memory schedule draft (flushing any previous draft first).
- Extracts `train_uid` `[3:9]`, `date_from` `[9:15]`, `date_to` `[15:21]`,
  `days_run` `[21:28]`, and STP indicator `[79:80]` (default `P`).
- Flush of the previous draft persists only when its TOC equals the filter
  (default `LM`) and origin/destination CRS plus origin public departure exist.

## BX — Basic schedule extended

- Attached to the current draft.
- TOC / ATOC code from `[11:13]` (for example `LM`).

## LO — Origin location

- Appends an origin calling point: TIPLOC `[2:9]`, public/working departure via
  public time columns with working-time fallback, activity `origin`.
- CRS resolved from the TI map when available.

## LI — Intermediate location

- Appends an intermediate point.
- Public arrival `[25:29]` and departure `[29:33]` preferred; otherwise working
  times; if neither public nor working times exist, activity is `pass`.
- Otherwise activity is `call`.

## LT — Terminating location

- Appends a destination point with public/working arrival and activity
  `destination`.

## ZZ — Trailer / end of file

- Stops the stream loop after counting the record.
- Any open LM draft is flushed after the loop when TOC matches the filter.

## Persistence identity

- Surviving LM drafts become a journey with
  `service_guid = SVC-{originCrs}{destCrs}{runDateYYMMDD}{originStdHHMM}`.
- CIF identity is `(train_uid, date_runs_from, stp_indicator)`; `run_date` stores
  `date_runs_from`, plus `date_runs_to` and `days_run`.
- Permanent and overlay rows for the same UID/start date are both stored.
- After the stream, `service_day` is materialised: each `(train_uid, calendar_date)`
  points at the winning STP schedule (`C` cancels; else `O`; else `P`; else `N`).
- Calling points hold booked public times; Kafka `TS` writes forecast columns only.
"""


def write_schedule_code_docs(
    docs_dir: str | Path,
    code_counts: dict[str, int],
    *,
    journeys_loaded: int,
) -> tuple[Path, Path]:
    """Write summary + algorithms markdown; return their paths."""
    out = Path(docs_dir)
    out.mkdir(parents=True, exist_ok=True)
    summary_path = out / "schedule-code-summary.md"
    algorithms_path = out / "schedule-code-algorithms.md"

    lines = [
        "# Schedule-code summary",
        "",
        f"Journeys persisted (LM): **{journeys_loaded}**",
        "",
        "| Code | Count |",
        "|------|------:|",
    ]
    # Prefer handled codes first, then any others seen in the file.
    ordered = list(HANDLED_CODES) + [
        code for code in sorted(code_counts) if code not in HANDLED_CODES
    ]
    seen: set[str] = set()
    for code in ordered:
        if code in seen:
            continue
        seen.add(code)
        count = code_counts.get(code, 0)
        lines.append(f"| {code} | {count} |")
    lines.append("")
    lines.append(f"Total records counted: **{sum(code_counts.values())}**")
    lines.append("")

    summary_path.write_text("\n".join(lines), encoding="utf-8")
    algorithms_path.write_text(ALGORITHM_TEXT, encoding="utf-8")
    return summary_path, algorithms_path
