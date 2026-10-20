# Requirements — CFA Daily Updates (live-sched)

## Intent analysis

Extend the existing `server/poc/live-sched` proof of concept so operators can apply a **daily CFA update** from a watched folder: open the update zip, extract the CFA file, accept it only when its 3-digit feed code is the correct next value after the previous load stored in SQLite, refuse mismatches, apply incremental changes without wiping the DB, and print a load report (total / new / override / duplicate). Success is demonstrated on a **real** update data zipfile. This is a PoC extension, not a production service.

## Functional requirements

### FR1 — Watch folder and zip intake

- **FR1.1** The system shall provide a one-shot CLI command that scans a configured watch folder once, identifies eligible daily update zip files, processes them according to FR2–FR5, and then exits.
- **FR1.2** The watch-folder path and related CFA-update settings shall be configured via the root workspace `.env` / `.env.example` (same pattern as the existing live-sched POC).
- **FR1.3** The system shall open the update zip and locate the CFA file inside it for processing.
- **FR1.4** After a successful or refused load, the zip file shall remain in the watch folder (operator manages files); the PoC shall not move or delete it.

### FR2 — Feed sequence gate

- **FR2.1** The system shall persist the last successfully applied 3-digit feed/update code in the live-sched SQLite database.
- **FR2.2** For this PoC, the initial previous-load value stored in the DB shall be **990**, so the first accepted update zip’s expected code is **991**.
- **FR2.3** The expected next code shall be previous + 1, modulo 1000 (e.g. 999 → 000).
- **FR2.4** If the CFA/update’s 3-digit code is not the expected next code, the system shall refuse the load, leave the DB unchanged for that apply, and surface a clear refuse reason (including expected vs actual code).
- **FR2.5** On a successful apply, the system shall update the stored previous-load code to the accepted 3-digit code.

### FR3 — Incremental CFA apply (no wipe)

- **FR3.1** CFA incremental load shall never unlink, delete, or recreate the SQLite database file; it shall only apply sequence-gated incremental changes.
- **FR3.2** Existing `live-sched load` MCA full-refresh behaviour (DB unlink before reload) remains separate and must not be used as the CFA incremental path.
- **FR3.3** The apply path shall process CFA content as incremental schedule updates on top of the existing LM schedule store (journeys / calling points / related tables as implemented by live-sched).

### FR4 — Load report

- **FR4.1** At the end of an accepted incremental load, the system shall report counts of: total records processed, new records, overrides, and duplicates.
- **FR4.2** Report mapping shall follow CIF BS transaction semantics: New (`N`) → new; Revise (`R`) → override; same identity already present without a revise → duplicate; Delete (`D`) counted separately (or clearly labeled under apply rules if folded into overrides — prefer a distinct delete count when deletes are applied).
- **FR4.3** On a refused load (FR2.4), the system shall still emit a clear outcome (refuse) and shall not claim a successful apply report for that zip.

### FR5 — Proof of concept acceptance

- **FR5.1** The PoC is accepted when the path is demonstrated on at least one **real** daily update data zipfile: correct sequence loads, mismatched code refused when tested, and a successful-load report printed.

## Non-functional requirements

- **NFR1** Reliability: a refused sequence check must not partially commit schedule mutations for that zip (atomic refuse or rollback of that apply).
- **NFR2** Operability: refuse and success outcomes must be readable from CLI stdout/stderr (or equivalent CLI logging) without a separate UI.
- **NFR3** Maintainability: CFA daily-update behaviour lives in `server/poc/live-sched` and must not import `schedule_poc`.
- **NFR4** Configuration: new env keys for watch folder (and any sequence/bootstrap knobs) documented in root `.env.example`.

## Constraints

- Scope is **poc** — prove the path; not a production deployment, long-running daemon, or multi-tenant service.
- Brownfield: reuse live-sched SQLite + CIF/repository patterns; do not replace the MCA load or Kafka consume commands unless required to wire the new CLI entrypoint.
- Package must not import `schedule_poc` (project practice from prior live-sched work).

## Assumptions

- The CFA file inside the zip is identifiable by naming/convention already used by Network Rail daily updates (exact filename pattern may be refined in Code Generation from a sample zip).
- “Total records” means CFA schedule records processed in that load (e.g. BS/associated schedule records), consistent with the report mapping in FR4.2.
- Delete (`D`) applies are in scope for counting when present in the CFA; if a sample zip has no deletes, the delete count may be zero.

## Out of scope

- Long-running folder watcher/daemon (one-shot CLI only for this PoC)
- Moving/deleting zips after processing
- Production hardening, auth, multi-TOC feeds, or AWS deployment
- Changing Android client or Darwin LDB paths
- Replacing MCA full load or Kafka overlay features

## Open questions

- Exact CFA-in-zip filename pattern on real sample data (resolve during Code Generation against a real zip).
- Whether Delete (`D`) appears as its own report column or is labeled under overrides — prefer distinct delete count (FR4.2).
