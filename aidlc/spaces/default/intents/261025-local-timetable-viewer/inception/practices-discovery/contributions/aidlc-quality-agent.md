**Collaborator:** aidlc-quality-agent

## Contribution

Quality review of the practices-discovery draft for the local timetable viewer. Evidence is the lead draft, the affirmed baseline in `team.md` and `project.md`, the Inception phase rules, `docs/future-intents/local-timetable.md`, and the brownfield store (`code-quality-assessment.md`, `technology-stack.md`, `dependencies.md`). The suite was not executed for this review. No new `ALWAYS` or `NEVER` line is proposed. Hard constraints stay the four mandated rules and two forbidden rules already stamped in `project.md`.

### Testing posture that still fits

- **Methodology**: test-after.
- **Ordering**: Implement each applicable testable layer, then write and run that layer's tests.
- Given/When/Then in `phases/inception.md` is the story format. It does not switch this methodology to BDD, and it does not require Gherkin or feature files.
- Tooling already in the live-sched tree: pytest 8 (`>=8.0,<9`) from a project-local `.venv`, command `.venv/bin/pytest tests/ -q` in `server/poc/live-sched`. Android JUnit 4 and Robolectric stay on the Android tree. Instrumented Android tests stay off.
- For a change that only touches `server/poc/live-sched`, the existing suite is that package's pytest. Record pass/fail/skip totals before the edit and again after. New failures are regressions.
- The focused scan counted 45 `def test_` functions and did not run them. The prior store said 44. The unused `.venv` sits on a `python3.14` path and was not executed. Those counts are an inventory, not a green baseline. The first recorded pytest run is the baseline.
- Patterns worth copying into a successor package: in-memory SQLite `repo` fixture, shared `conftest.py`, synthetic builders, fixture isolation, no live broker, no multi-GB production MCA.
- Patterns the successor leaves behind: the untested XML and Avro-via-JSON path, the multi-member zip gap, the delete-cascade gap on `applied_updates`, the UID-wide `list_journeys()` scan, and duplicated CIF parsers with no shared layout table.
- Unit tests that touch this timetable keep synthetic CFA, zip, and HD fixtures. A real update-zip demo stays later manual ops.
- This classic workflow skips the CI pipeline and every Operation stage. The live-sched tree has no `.github` workflow, no coverage tool, and no Dockerfile. Delivery confidence for the existing package is local pytest only.
- SLO, soak, and Athena query-latency suites stay out of this affirmation. This service has no hosted deployment and no Athena workgroup.

### Sentences the standing posture must replace

The standing `## Testing Posture` still says this intent produces no runtime tests, that new tests are for lake, transform, ingest, or query code, and that Standard volume applies once tables exist. It also says later stages must not invent a different floor while the carve-out stands.

Those sentences described the timetable-lake markdown pass. This intent builds a local Docker service: the existing live-sched SQLite file, a new reader server, and a new UI (`docs/future-intents/local-timetable.md`). The seven tables already exist. The reader and the UI do not. `code-quality-assessment.md` gives the server and the UI no health rating because they are absent. `dependencies.md` records no consumer edge from a local timetable server.

If those sentences are promoted as written, Construction can treat the viewer as exempt from tests, and Build and Test cannot set a floor later because the carve-out forbids inventing one. The interview is the point that replaces them. Proposed replacement, for the lead to integrate only after the human answers:

- Tests are a first-class deliverable for the new reader. This intent produces runtime code. The Kafka POC's missing tests do not license skipping reader tests.
- The SQLite tables already exist. Standard volume applies to the reader when that package exists: unit tests plus a thin query-contract layer against the in-memory SQLite fixture. "Once tables exist" is already true and is not a deferral.
- The 80% line-coverage floor stays carved out only if the human says so again for this service. The carve-out was affirmed on 2026-10-20 for a markdown lake pass while CI was absent (`project.md` learning). CI is still absent, and the repo still has no `coverage.py`, pytest-cov, JaCoCo, or Kover. Absence of a coverage tool is a statement about measurement. It is not a statement that the reader ships without tests.
- While the carve-out stands, the bar is that the applicable suite stays green. The interview must say that in words that name the new package. A later stage of this intent does not add pytest-cov or an 80% gate on its own.
- UI test tooling waits until the page's toolkit choice exists. Android JUnit is the Android app's suite. The new local UI is a different surface. The Android JVM suite runs only when the change touches `android/`.

### Query-contract the interview should lock

`code-quality-assessment.md` records the reader-facing gap: no index on `service_day.calendar_date` or `calling_points.crs`, and no repository method for a calendar day or an ordered CRS pair. The local timetable page needs both reads. A thin integration layer that only retests CIF load, CFA apply, or Kafka overlay does not cover this service.

The query-contract, on synthetic in-memory rows, should lock the pass/fail criteria already on the page:

- Network view: LM journeys that `service_day` says run today and that are not cancelled.
- A train deviates when a calling point's forecast time differs from its booked public time, or the journey status is `deactivated`. Booked times stay the published timetable.
- Station pair: both CRS codes appear in ordered `calling_points`, the first before the second. Origin and destination on the journey row are not enough.
- Where: the latest calling point at or before now, and the next calling point. The view names those two stops. It does not invent a point between them.
- Issues on that pair add a today-row in `service_day` marked cancelled.

The network and pair reads must not be implemented as a UID-wide `list_journeys()` scan. That scan orders by `service_guid`, has no date predicate, and can attach a message to the wrong schedule when several STP rows share a UID.

The reader is a reader. `connect` creates a missing database file, and `get_feed_sequence` commits the seed when the key is absent. A query test that calls those helpers can write. Reader tests should open a fixture database and assert the source file is unchanged: no new file, no feed-sequence seed, no cleared forecasts, no dropped offsets.

Live-sched pytest still runs before and after any edit under `server/poc/live-sched`. A new reader package does not satisfy that ritual by borrowing the live-sched totals. Its existing suite is its own tests, once those tests exist.

### CI quality gates and local smoke

No workflow exists to enforce Gate-style checks (unit tests on every change, coverage not decreased, integration on merge, e2e before release). This scope skips the CI pipeline, so this stage does not add a workflow, a coverage tool, or scanner config.

The quality gate until CI exists is the recorded local pytest result for the package that changed. Secret scanning, a dependency CVE scan, and Python SAST stay "when CI exists." DAST stays off while nothing is hosted.

The service page says Docker on this machine and gives no image recipe. The live-sched tree has no Dockerfile. Container-image scanning was deferred until a runtime exists; a local container is a runtime and this workflow still has no CI. Image scanning stays deferred with CI unless the human pulls it forward.

Hosted `dev` / `staging` / `prod` gates, Glue, Athena, and S3 are lake and harvest design. They are not the quality gate for this service. If the human wants a local-run check, one smoke is enough: the container starts, one of the three views returns a known synthetic result, and the container stops with the SQLite file unchanged. That smoke is a manual or scripted check, not a coverage floor and not a soak test. Rollback for a local container is "stop it and leave the SQLite file as it was." A successful local run is one whose smoke result matches the fixture, not one whose process start command exited zero.

### Interview gaps

The draft's question 3 asks whether the new reader and UI share test-after ordering, the suite-green floor, and Standard volume, and whether "existing suite" means the new package. Keep that question, and split it so the human can answer the reader and the UI separately. Add these quality points, which the draft does not yet name:

1. Replace the markdown-only and "once tables exist" sentences before affirmation. Confirm whether the 80% carve-out still covers the new reader, given that this is the last stage that can set a floor.
2. Lock the five query-contract cases above as the thin integration layer, on synthetic in-memory rows, with the source database left unchanged.
3. Confirm the UI has no test framework until a toolkit is chosen, and that the Android suite is out of this service unless `android/` changes.
4. Confirm the 45-test inventory is not a green baseline. The first Construction edit to `server/poc/live-sched` records a real pytest total.
5. Confirm the local Docker check is the single smoke above, or that this service affirms no smoke. Leave soak, SLO, and Athena latency to a later NFR stage.

## Positions

AGREE: Methodology stays test-after and the ordering sentence stays as affirmed — Inception's Given/When/Then stories are a format, and the repo has no Gherkin.
AGREE: Coverage tooling is absent and this classic workflow has no CI gate — the store shows no coverage.py, pytest-cov, JaCoCo, Kover, or `.github` workflow, so this stage adds none.
AGREE: Live-sched test patterns to copy are the in-memory SQLite fixture, shared conftest, synthetic builders, and isolation from the live broker and the multi-GB MCA — the store documents those fixtures and the gaps to leave behind.
AGREE: A live-sched edit records pytest pass/fail/skip before and after — the 45-function count was not executed and is an inventory, not a green baseline.
AGREE: Discovered rules stay the constraints a human has already stamped — this review adds no new ALWAYS or NEVER line for coverage, Docker, or the three views.
OBJECT: The standing Testing Posture still says this intent produces no runtime tests and that Standard volume waits until tables exist — the tables exist, the reader and UI do not, and promoting that sentence lets Construction skip reader tests.
OBJECT: The 80% carve-out is restated as already covering this service, including the ban on a later floor — it was affirmed for a markdown lake pass, and this interview is the last point that can say whether the new reader stays on suite-green.
OBJECT: The draft never names the three views as the query-contract — a thin integration layer can otherwise retest CIF load and miss today's LM journeys, deviation, the ordered station pair, where-as-two-stops, and the cancelled service_day row.
OBJECT: The draft never requires reader tests to leave the SQLite file unchanged — `connect` and `get_feed_sequence` write, so a query test can mutate the database it claims to read.
OBJECT: Reader tests and UI tests are one interview choice — the UI has no toolkit, so its test tool cannot be named yet, and the Android JVM suite is a different surface.
