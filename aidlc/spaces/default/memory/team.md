# Team-Level Rules

> This team's affirmed practices and corrections. Loaded after `org.md` as
> strict-additive guidance; contradictions with broader policy are rejected.
> Populated by the practices-discovery affirmation gate. Edit at the gate,
> not directly.

## Way of Working

We use trunk-based development. New work for this service goes on a new short-lived branch and is squash-merged to `main` at the end. We do not keep this work on `athena-timetable-lake`. Older milestone branches (`m4`, `m5`, `journey-tracker`, and the like) stay history. We do not invent a `develop` or `release/*` line.

Construction worktrees use `main` as both base and merge target. Each Bolt becomes one squash commit on the trunk.

## Walking Skeleton

We build a thin running slice first: the existing database, one view, and enough of a screen to see it. That is the order of work for this service.

The classic scope leaves the skeleton ceremony off (`skeleton: off`). This service has no separate skeleton checkpoint. The first build is that thin slice. A design review is not the slice.

## Testing Posture

- **Methodology**: test-after
- **Ordering**: Implement each applicable testable layer, then write and run that layer's tests.
- **Coverage**: There is no 80% line-coverage gate until continuous integration exists. The bar for the new reader is that its own suite stays green. The repo has no `coverage.py`, pytest-cov, JaCoCo, or Kover today.
- The new reader gets tests now: unit tests, plus a thin check of the three views (all trains today, trains that deviate, and a chosen pair of stations). This server answers those views. `list_journeys()` does not.
- Screen tests are in scope once the TypeScript UI exists, on that stack. They do not wait on an unnamed toolkit.
- Live-sched tests run only when `server/poc/live-sched` changes. Before an edit there, run `.venv/bin/pytest tests/ -q` and record pass, fail, and skip totals. Re-run after, and treat new failures as regressions.
- Reader tests must not change the SQLite file.
- Given/When/Then on Inception stories is the story format. We do not switch to TDD or BDD, and we do not add Gherkin or feature files.
- Live-sched stays on pytest 8 from a project-local `.venv`. The Android tree keeps JUnit 4 and Robolectric, with instrumented tests off. The screen suite for this service is the TypeScript UI, once that UI exists.
- Timetable tests use synthetic fixtures. They do not open the multi-GB production MCA or a live broker. A real update-zip demo stays later manual ops.

## Guard Policy

<!-- Affirmed by the team. Mode: strict, relaxed, or off. Strict here holds for every intent and cannot be changed from chat. A section under the retired Change Control heading, written by an earlier release, is still read. -->

## Deployment

This service runs as local Docker on this machine only. We do not create `dev`, `staging`, or `prod` for it. We do not add cloud controls. We do not stand up continuous integration for this service.

There is no hosted deploy. This classic workflow skips the CI pipeline and every Operation stage. The analyzed tree has no `.github/workflows`.

## Code Style

The server and the screen are TypeScript. We prefer a functional style where that is possible. We will use this layout for the long term. This monorepo will hold other apps later.

We place this service in:

- `apps/local-timetable/db`
- `apps/local-timetable/server`
- `apps/local-timetable/fe`

The screen is `apps/local-timetable/fe`. The existing Android app is a different surface. It keeps `kotlin.code.style=official`.

The reader stays outside `server/poc/live-sched`. It does not import `schedule_poc`. It does not create or rewrite the SQLite file. If that file is missing, deleted, locked, or an older schema, the request fails. The three views are answered by this server, not by `list_journeys()`.

We keep `service_guid` and the other store names: `train_uid`, `stp_indicator`, `origin_crs`, `dest_crs`, `run_date`, `days_run`. Dates stay `YYMMDD` text. Times stay `HHMM` text. Sequence codes stay zero-padded three-digit strings. CIF record codes and STP letters stay CIF tokens. CFA folder statuses stay `accepted`, `refused`, `failed`, and `nothing`. TypeScript identifiers around those tokens use camelCase. The store tokens keep the spelling above.

The Python `live-sched` package remains the writer of that SQLite file. We do not move it into `apps/`. Python installs for that package use a project-local `.venv` (`python3 -m venv .venv`, then `.venv/bin/pip install -e ".[dev]"`). On that package the observed style stays Python 3.12, `from __future__ import annotations`, PEP 604 unions, frozen dataclasses, snake_case, and `logging.getLogger(__name__)`. Writer policy stays on the writer: ordered CFA intake is fail-closed on the HD sequence. The reader does not inherit that policy, and it does not inherit the live Kafka overlay.

Secrets stay in the gitignored workspace-root `.env` and in Android `local.properties`. We do not hardcode Kafka keys or Darwin API keys. We do not commit CIF dumps matched by the root `.gitignore`. An ignore-file is not a scanner. Architecture text does not list credential values.

We add local secret scanning, dependency checks, and a dynamic check of the local reader now, without standing up continuous integration. Those checks cover this TypeScript server and screen. They are not limited to Python. This pass does not add a formatter or a style linter. None was requested.
## Forbidden

<!-- Team-specific forbidden patterns -->

## Mandated

<!-- Team-specific mandates -->

## Corrections

<!-- Self-learning loop appends here. -->
