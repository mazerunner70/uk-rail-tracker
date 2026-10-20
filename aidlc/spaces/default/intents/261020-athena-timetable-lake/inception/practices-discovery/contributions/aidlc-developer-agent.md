**Collaborator:** aidlc-developer-agent

## Contribution

Developer review of the release-engineer brownfield draft, limited to naming, layer boundaries, error handling, file organization, and code-style conventions. Sources: the four lead drafts, `code-structure.md`, `api-documentation.md`, `dependencies.md`, `architecture.md`, `project.md`, plus the live-sched tree those artifacts describe (`server/poc/live-sched/`).

The draft’s **Code Style** section is directionally right (defer to what the repo already configures; do not invent a formatter in this intent) but it is too thin for Construction. Live-sched already has a working style even without Ruff/Black/mypy: Python 3.12, `from __future__ import annotations`, PEP 604 unions (`str | None`), frozen dataclasses for records, snake_case identifiers, and exceptions raised in domain modules then mapped at the CLI. That is the convention a lake successor should copy. “No linter config” is not “no style.”

### Naming

Keep language-idiomatic names as the draft says. Also lock the domain vocabulary already in the store, or later Glue/Athena columns will drift from the POC:

- Python / SQLite columns: `train_uid`, `service_guid`, `stp_indicator`, `origin_crs`, `dest_crs`, `run_date`, `days_run`. Dates are `YYMMDD` text; times are `HHMM` text. Sequence codes are zero-padded three-digit strings (`990`, `999` → `000`), not integers.
- Stable identity string: `SVC-{originCrs}{destCrs}{runDateYYMMDD}{originStdHHMM}`. The Python identifier and SQLite column are `service_guid`. Comments and `PLAN.md` REST paths still say `serviceGUID`. New lake tables should use `service_guid`; keep `serviceGUID` only if a future HTTP surface is specified. Do not mix both spellings in one schema.
- CIF record codes (`HD`, `BS`, `BX`, `LO`, `LI`, `LT`, `ZZ`), STP letters (`C` / `O` / `P` / `N`), and TOC `LM` stay as CIF tokens.
- CFA folder statuses are the CLI contract: `accepted`, `refused`, `failed`, `nothing`.
- Kafka apply types stay `TS` / `schedule` / `deactivated`; classifier names such as `scheduleFormations` and `trackingID` are Push Port JSON keys, not Python identifiers.

Do not rename those tokens in this intent unless the interview explicitly asks for a rename.

### Layer boundaries

Live-sched is one setuptools package of **flat modules by concern**, not Android’s `ui` / `domain` / `data` packages and not the unimplemented `server/{api,ingest,infra}` tree in `PLAN.md`. Observed import DAG (no cycles, from `code-structure.md`):

- Process boundary: `cli` (exit codes, argparse, stdlib logging).
- Use-case modules: `cif_loader`, `cfa_apply`, `consumer` / `apply`.
- Domain/stdlib: `identity`, `feed_sequence`, `service_day`, `kafka_classify`, `cfa_zip`.
- Persistence: `ScheduleRepository` over `db.py` schema (seven tables, `CREATE TABLE IF NOT EXISTS`, no migration version).
- Side effects at the edge: `csv_archive`, `change_log`, `docs_gen`; `confluent-kafka` imported only inside consume so injected-message tests never load it.

Raw intake is already a separate layer from queryable state: MCA path, zip left in the watch folder, Kafka CSV on disk; queryable state is `journeys` / `calling_points` / `service_day`. A later S3/Glue/Athena design should keep that split. Do not put object-store or query-engine clients inside parsers or the repository.

Full-load parsing (`cif_loader.py`) and CFA parsing (`cfa_apply.py`) are duplicated on purpose so the package never imports `schedule_poc`. A third copy lives in `scripts/compare_mca_to_db.py` and is excluded from the wheel. Do not “clean that up” by importing the sibling. A shared column-layout module *inside* a successor package is a later design choice, not a practices default.

`cfa_apply.py` reaches `repo._conn` three times (`# noqa: SLF001`). That is a persistence leak. New lake/ingest code should expose feed-meta on the repository (or an explicit unit of work) instead of touching `_conn`. Do not copy the noqa.

Android layering is not the template for this intent’s lake work. This run did not reopen `android/`; do not apply Kotlin package layout to Python.

### Error handling

There is no `Result[T, E]` type and no HTTP error envelope (no first-party HTTP API). Observed policy is **exceptions in domain, catch at the process boundary**:

- Validation / refuse: `ValueError` (`feed_sequence`, `identity`, sequence mismatch, missing HD, malformed YYMMDD/HHMM/CRS). Sequence mismatch raises *before* `begin_batch`, so schedule rows and the stored code stay unchanged.
- Missing Kafka env or topic describe: `RuntimeError`; CLI `cmd_consume` logs and returns `1`.
- Missing MCA file: CLI returns `1` without raising. Missing watch folder: `FolderRunResult` with `status: failed`, exit `1`.
- After the sequence gate, any later error rolls the deferred batch back, including the sequence write in that batch.
- CFA folder run: a `ValueError` whose text contains `sequence refuse` or `HD` is `refused`; other `ValueError` / `OSError` / `sqlite3.Error` / `KeyError` is `failed`. Later zips in lexicographic order are still attempted. Exit `1` if any zip was refused or failed; empty folder is `0` (`nothing`).
- Kafka overlay is fail-open after archive: CSV row is written first; JSON decode failure returns `None` and the message is counted ignored; unknown types are skipped; `TS` / `schedule` / `deactivated` apply only when a journey already resolves. Ctrl+C is a successful stop (`consume` exit `0`).
- CFA bytes decode as UTF-8 with replacement characters, so a bad byte becomes U+FFFD rather than failing the zip closed.

HD Current File Reference columns 33–39 is the mandated source; the zip basename is never used. After that field has no three digits, `resolve_feed_sequence` still falls back to the *inner CFA member name*. That is weaker than a hard refuse. It is implemented, tested, and allowed by the current correction (basename only is forbidden). The interview must say whether lake ingest keeps the member-name fallback or refuses that header.

Do not introduce a Result type, HTTP error envelope, or fail-closed Kafka overlay unless the interview asks. Do record two policies: **fail-closed on ordered CFA/sequence intake**, **fail-open on live overlay after the raw archive exists**. S3 object arrival for the timetable lake should follow the CFA policy, not the Kafka one.

### File organization

- New lake / transform / ingest code belongs in a **sibling package under `server/`**, same grain as `live-sched` versus `schedule_poc`: own `pyproject.toml`, own tests, no import of `schedule_poc` or of `live_sched` from outside that tree. Do not add modules inside `live_sched` for Athena.
- `PLAN.md` `server/{api,ingest,infra}` and `s3://uk-rail-tracker-{env}/` (raw / curated / Athena-results) are design-only. They are not an existing layout. The interview should choose POC-sibling under `server/poc/` versus standing up the PLAN tree before any Construction file plan treats either as established.
- Tests sit in `tests/test_<module>.py` beside fixtures (`tests/cfa_fixtures.py`, `tests/conftest.py`), not in a nested `__tests__` tree. Wheel excludes `tests*`, `demo*`, `data*`, `output*`, `fixtures*`, `docs*`. Keep operational scripts that duplicate parser rules out of the published package.
- One primary concern per module. Do not regroup live-sched by technical layer (`models/`, `services/`, `handlers/`).
- Python installs stay in a project-local `.venv` (`python3 -m venv .venv` then `.venv/bin/pip install -e ".[dev]"`). Secrets stay in gitignored workspace-root `.env` and Android `local.properties`.

### Code style (what to add to the draft)

Keep the draft’s “defer to existing config; do not add a formatter in this draft.” Add these observed conventions so Code Generation does not invent new ones:

- Kotlin: `kotlin.code.style=official` only; no Detekt/ktlint in-repo (prior scan). Out of scope for this lake intent.
- Python: no Ruff, Black, Flake8, or mypy in `live-sched/pyproject.toml`. `server/pyrightconfig.json` is scoped to `poc/kafka` only. Three `# noqa: SLF001` comments prove a flake8-private-member plugin was used at least once; there is still no project config. Adding Ruff/Black/mypy for new lake code is an interview choice, not a default.
- Typing and records as above; logging via `logging.getLogger(__name__)`; CFA operator output is labeled `print` lines from `cfa_report.format_outcome`, not structured JSON logs.
- `dev` extra is pytest 8 only. Do not add pytest-cov unless coverage tooling is affirmed.

### Interview gaps to add (developer)

1. Python formatter/linter for new lake code: leave live-sched unconstrained, or add Ruff/Black (and optionally mypy) for the successor package only.
2. Package home: sibling under `server/poc/`, or start `server/ingest` (PLAN tree) as a new empty package.
3. Identity column spelling in lake tables: `service_guid` (match SQLite) versus `serviceGUID` (match PLAN REST).
4. HD with no three-digit run: keep inner-member fallback, or refuse the object.
5. Lake object arrival: CFA fail-closed (refuse, leave object, continue later keys) versus Kafka fail-open (archive then ignore).

Hard constraints in `discovered-rules.md` correctly restate existing `project.md` rules only. Do not promote the member-name fallback, the `_conn` leak, or a new formatter to Mandated/Forbidden without a human statement.

## Positions

- AGREE: defer to existing linter/formatter config and do not add one in this draft — live-sched has none; inventing Black/Ruff here would be a new practice.
- AGREE: language-idiomatic naming plus existing tokens (`train_uid`, `service_guid`, `stp_indicator`, `YYMMDD`, `HHMM`) — those are already the store and CLI contract.
- AGREE: secrets in gitignored workspace-root `.env` and Android `local.properties`; never hardcode Kafka or Darwin keys; never commit CIF dumps.
- AGREE: project-local `.venv` for Python installs — matches README and the learned venv rule.
- AGREE: NEVER import `schedule_poc` from live-sched or a successor that follows its ideas — locked in `project.md` and observed in the import graph.
- AGREE: ALWAYS derive CFA sequence from HD columns 33–39 and never from the zip basename — locked and implemented in `feed_sequence.py`.
- AGREE: Way of Working, Walking Skeleton skip, test-after, and “no deploy in this intent” as interview suggestions — they do not conflict with code-style evidence.
- OBJECT: Code Style omits layering, error policy, typing/dataclass conventions, and identity dualism — Construction will copy live-sched; “no formatter” is not enough style.
- OBJECT: treating PLAN.md `server/{api,ingest,infra}` or `{env}` prefixes as file-organization fact — those trees are absent; the interview must choose POC-sibling versus PLAN layout.
- OBJECT: copying `repo._conn` / `# noqa: SLF001` into new lake adapters — persistence must stay behind the repository.
- OBJECT: leaving the HD member-name fallback unasked — it is weaker than a hard refuse and will reappear in object-store ingest unless the human picks.
- OBJECT: one error policy for all ingest — CFA is fail-closed before mutate; Kafka is fail-open after archive; S3/Athena intake should name which one.
