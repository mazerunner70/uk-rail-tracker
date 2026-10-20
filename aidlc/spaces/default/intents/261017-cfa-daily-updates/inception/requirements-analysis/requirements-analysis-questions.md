# Requirements Analysis Questions

Context already locked in Intent Capture: folder watch → daily update zip → CFA; sequence +1 mod 1000 with DB previous-load seed 990 (first accepted zip 991); refuse wrong codes; report total / new / override / duplicate; prove on a real update zip; PoC in `server/poc/live-sched` (not production). Reverse engineering shows live-sched today is MCA full load (DB unlink) + Kafka overlay only — no CFA/zip/sequence path.

## Q1. How should the watched folder be checked for the daily update zip?

A. One-shot CLI command: scan a configured folder once, process any eligible zip, then exit
B. Long-running watcher/poller that keeps checking the folder until stopped
C. Both — one-shot for the PoC demo, with a clear extension point for a long-running watcher later
D. Not yet defined
X. Other (please specify)

[Answer]: A 

## Q2. Where is the watch-folder path (and related settings) configured?

A. Root workspace `.env` / `.env.example` (same pattern as the live-sched POC)
B. Package-local env under `server/poc/live-sched/` only
C. CLI flags required each run (env optional)
D. Not yet defined
X. Other (please specify)

[Answer]:A

## Q3. For the load report, how should “new”, “overrides”, and “duplicates” map to CFA apply behaviour?

A. Align with CIF BS transaction semantics: New (`N`) → new; Revise (`R`) → override; Duplicate/same identity already present without revise → duplicate; Delete (`D`) counted separately or as overrides per apply rules
B. Count only journey/row outcomes after apply (inserted / updated / skipped-as-duplicate), without requiring BS N/R/D labels in the report
C. Not yet defined — propose a mapping in requirements and confirm at review
X. Other (please specify)

[Answer]: A

## Q4. Must the CFA incremental load leave the existing SQLite DB intact (unlike `live-sched load`, which deletes the DB before MCA reload)?

A. Yes — CFA incremental must never unlink/wipe the DB; only apply sequence-gated changes
B. Allow an explicit opt-in wipe flag for operators; default is no wipe
C. Not yet defined
X. Other (please specify)

[Answer]:A 

## Q5. After a successful (or refused) load, what should happen to the zip file in the watch folder?

A. Leave the zip in place (operator manages files)
B. Move processed zips to a sibling `processed/` (or similar) folder; leave refused zips in place or under `rejected/`
C. Delete after successful load only
D. Not yet defined
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1: One-shot CLI scans a configured folder once, processes eligible zips, then exits
- Q2: Watch-folder path and related settings in root workspace `.env` / `.env.example`
- Q3: Report maps CIF BS semantics — N→new, R→override, duplicate when same identity present without revise; D counted separately or per apply rules
- Q4: CFA incremental must never unlink/wipe the DB
- Q5: Leave the zip in place after success or refuse (operator manages files)

Does this all look correct before I generate the requirements artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
