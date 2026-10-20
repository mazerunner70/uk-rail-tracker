# Team practices

Integrated after the Practices Discovery interview for intent `261020-athena-timetable-lake`. These five sections are the team's affirmed practices, ready for promotion into `memory/team.md`. They replace the first-run suggestions.

## Way of Working

We use trunk-based development. All work lands on `main` through short-lived branches and squash-merge. Construction worktrees use `main` as both base and merge target; each Bolt becomes one squash commit on the trunk.

We do not keep long-lived milestone or feature branches (`m4`, `m5`, `journey-tracker`, and the like) as the way of working going forward. Those branches exist in this clone as history; new work does not extend that pattern. We do not invent a `develop` or `release/*` line.

## Walking Skeleton

We skip a running intake-to-query slice for this piece of work. The first artifact is the overall architecture. Later construction may land in stages against that architecture.

A first design review is not a working skeleton, and we do not require one before later design or staged implementation. The active classic scope already leaves the skeleton ceremony off (`skeleton: off`). If a later piece of work turns the skeleton on, that later intent can require the smallest slice that actually runs through (intake → validate → queryable lake tables).

## Testing Posture

- **Methodology**: test-after
- **Ordering**: Implement each applicable testable layer, then write and run that layer's tests.
- **Coverage**: the classic 80% line-coverage floor is carved out until CI exists. The bar until then is that the existing suite stays green. Later stages must not invent a different floor while this carve-out stands. There is no `coverage.py`, pytest-cov, JaCoCo, or Kover in the repo today.
- Tests are a first-class deliverable. New lake, transform, ingest, or query code still gets tests when that code exists. The Kafka POC's missing tests do not license skipping them. This markdown-only intent produces no runtime tests; Standard volume (unit plus a thin integration or query-contract layer) applies once tables exist.
- Tooling we already use: pytest 8 from a project-local `.venv` (`.venv/bin/pytest tests/ -q` in `server/poc/live-sched`); Android JUnit 4 plus Robolectric JVM tests, with instrumented tests off. No Gherkin or feature files. We do not switch to TDD or BDD.
- For a server-only change, “existing suite” means that package's pytest, not the Android JVM suite, unless the change actually crosses the client.
- Before any Construction edit to `server/poc/live-sched`, run the existing pytest suite and record pass/fail/skip totals; re-run after and treat new failures as regressions.
- Unit tests use synthetic CFA, zip, and HD fixtures. They do not open the multi-GB production MCA or a live broker. A real update-zip demo stays later manual ops (project learning FR5.1).
- Keep live-sched patterns worth copying: in-memory SQLite `repo` fixture, shared `conftest.py`, synthetic builders, no live broker, fixture isolation. Do not copy the untested XML/Avro-via-JSON path, multi-member zip gap, delete-cascade gap, UID-wide `list_journeys()` scan, or duplicated CIF parsers without a shared layout table inside the successor package.
- We do not affirm SLO, soak, or Athena query-latency suites here. NFR stages own those targets.

## Deployment

We name `dev`, `staging`, and `prod` now in the architecture. When we deploy, merge to `main` may roll staging automatically; production needs a separate human approval. Every production cut needs a tested rollback path. A successful deploy is one whose smoke checks pass, not one whose apply command exited zero.

There is no deploy today. This classic workflow skips the CI pipeline and every Operation stage. No `.github/workflows`, Glue job, Athena workgroup, or S3 bucket exists in the analyzed tree. Architecture markdown names the three environments and the implied cadence; it does not stand those resources up. `server/PLAN.md`'s `s3://uk-rail-tracker-{env}/` prefixes and Fargate/RDS/Redis sketch remain design text, now mapped onto the named `dev` / `staging` / `prod` tiers rather than treated as a live topology.

Architecture text must not list credentials or long-lived AWS access keys. DAST and container-image scanning wait until a runtime exists. Cloud controls (KMS, GuardDuty, bucket policies, Athena workgroup encryption) belong in later NFR and infrastructure design.

## Code Style

We defer to whatever this repo already configures. We do not add a new formatter or linter in this piece of work.

- Kotlin: `kotlin.code.style=official` in `android/gradle.properties`. No Detekt or ktlint project config. Android style is out of scope for this lake intent.
- Python: live-sched has no Ruff, Black, Flake8, or mypy config. `server/pyrightconfig.json` is scoped to `poc/kafka` only. Observed conventions a successor should copy: Python 3.12, `from __future__ import annotations`, PEP 604 unions, frozen dataclasses for records, snake_case, `logging.getLogger(__name__)`, exceptions raised in domain modules and mapped at the CLI. “No linter config” is not “no style.”
- Naming: language-idiomatic (snake_case in Python, camelCase in Kotlin). Existing store and CLI tokens stay unless a later intent renames them: `train_uid`, `service_guid`, `stp_indicator`, `origin_crs`, `dest_crs`, `run_date`, `days_run`; dates `YYMMDD` text; times `HHMM` text; sequence codes as zero-padded three-digit strings; CIF record codes and STP letters as CIF tokens; CFA folder statuses `accepted` / `refused` / `failed` / `nothing`.
- Layering: new lake, transform, or ingest code is a sibling package under `server/` with its own `pyproject.toml` and tests — the same grain as `live-sched` versus `schedule_poc`. Do not add Athena modules inside `live_sched`. Do not import `schedule_poc`. Do not put object-store or query-engine clients inside parsers or the repository. Do not copy `repo._conn` / `# noqa: SLF001` into new adapters; expose feed-meta on the repository instead. Observed error policy is fail-closed on ordered CFA/sequence intake and fail-open on live Kafka overlay after the raw archive exists; S3 object arrival should name which of those two it follows when that design is written.
- Secrets stay in gitignored workspace-root `.env` and Android `local.properties`. Never hardcode Kafka keys or Darwin API keys. Never commit CIF dumps matched by root `.gitignore`.
- Python installs use a project-local `.venv` (`python3 -m venv .venv` then `.venv/bin/pip install -e ".[dev]"`) so they do not pollute the system interpreter.
- When CI exists, add secret scanning, a dependency CVE scan, and Python SAST. Do not add live-site (DAST) scanning until something is deployed. Ignore-files are not a scanner. Until CI exists, we do not add scanner or formatter config files for this markdown-only intent.
