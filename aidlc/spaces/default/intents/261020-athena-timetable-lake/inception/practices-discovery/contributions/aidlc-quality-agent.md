**Collaborator:** aidlc-quality-agent

## Contribution

Independent quality review of the release-engineer first-run brownfield draft for intent `261020-athena-timetable-lake`. Scope: testing posture, coverage tooling, CI quality gates, test/code patterns, and interview gaps. Team Testing Posture in `team.md` is empty; the only populated testing line in `project.md` is the learned venv rule. Org classic default still names test-after, an 80% line-coverage floor, and CI execution before merge. This intent's Test Strategy is Standard (unit plus integration volume). The workflow skips the CI pipeline and Operation stages; the deliverable is architecture markdown, not a running lake.

### Testing posture

Observed cadence matches the draft's **Methodology: test-after** and **Ordering: implement each applicable testable layer, then write and run that layer's tests.** Live-sched locks behaviour with 44 pytest functions after the code exists; Android uses JUnit 4 plus Robolectric JVM tests with instrumented tests off (`enableAndroidTest = false`); the Kafka POC has no automated tests. No Gherkin or feature files appear in the quality scan. Do not reclassify this as TDD or BDD without an interview decision. Inception Given/When/Then on later user stories is acceptance-criteria format, not a BDD test methodology.

Tests as a first-class Bolt deliverable is the right org default to keep. The Kafka gap does not license skipping tests on new lake, transform, or ingest code. Because this intent may produce no Python, the interview must still affirm the posture that later Construction will inherit: Standard remains unit plus a thin integration/query-contract layer once tables exist, not "markdown-only, therefore no tests" as a team practice.

Keep the prior CFA split: synthetic HD/CFA/zip fixtures in unit tests; the real multi-GB update-zip demo stays later manual ops. Unit tests must not open the production MCA or a live broker. That split is already a project learning (`FR5.1`); the interview should say whether to promote it to a Mandated rule.

### Coverage tooling and CI quality gates

The draft is factually right that no coverage tooling or CI exists: no `coverage.py`, pytest-cov, JaCoCo, or Kover; no `[tool.coverage]` in live-sched `pyproject.toml`; no `.github/workflows`. Delivery confidence today is local pytest (`.venv/bin/pytest tests/ -q` from `server/poc/live-sched`). The classic 80% floor and CI-before-merge gate cannot run. "Existing suite stays green" is the only observed bar.

That observation must not become the affirmed team practice by silence. Org classic plus Standard still name an 80% line-coverage floor and merge-time CI. Build and Test is not allowed to weaken an affirmed floor to make a step pass. The interview has to pick one of:

1. Affirm the 80% line-coverage floor as team policy; install pytest-cov (and an Android analogue only if that tree is in scope) when Construction first writes lake or transform Python; until a pipeline exists, the recorded construction check command runs tests plus coverage locally.
2. Write an explicit project carve-out: "suite green only until a pipeline exists," so later stages cannot invent a different bar.

Do not apply an Android coverage or instrumented-test mandate to this Athena-lake intent. Do not treat PLAN.md `{env}` or Fargate/RDS/Redis as a live staging gate: there is no deploy, so there is no integration or release quality gate today. The draft's future success rule (smoke checks, not apply-exit-zero) is sound when deploy exists; it is not a current gate.

### Test and code patterns to keep or refuse to copy

Keep: in-memory SQLite `repo` fixture, shared `conftest.py`, synthetic CFA/zip builders, no live broker, pytest as the Python runner, project-local `.venv` so installs do not pollute the system interpreter. Independence and fixture isolation in live-sched are a good unit-test pattern for later lake transforms (factories or builders for partition keys and CIF slices; never shared mutable state).

Do not copy these live-sched gaps into lake, Glue, or Athena work without tests:

- `iter_kafka_messages` untested beyond the pure offset helper.
- `--format xml` / `--format avro` select a topic then pass bytes through a JSON decoder; no XML or Avro coverage.
- No test for a zip with more than one CFA member.
- `applied_updates` does not cascade on journey delete; that path is untested.
- `apply.py` `resolve_service_guid` falls back to `list_journeys()` and can bind a `rid` to the wrong STP row; not a lake read pattern.
- CIF fixed-width parsing is copied across `cif_loader.py`, `cfa_apply.py`, and `scripts/compare_mca_to_db.py` with no shared layout table. A shared layout golden test is the check that does not collapse the `schedule_poc` isolation rule.

Brownfield protocol the draft omits: before any Construction edit to `server/poc/live-sched`, run the existing pytest suite and record totals (pass/fail/skip); re-run after and treat new failures as regressions. "Suite green" for server-only work means that package's pytest, not Android JVM tests, unless the change actually crosses the client.

Schedule POC also has pytest (loader, db, identity, harvest_replay). New lake code should follow the live-sched automated pattern, not the Kafka POC's manual-only pattern.

No performance, soak, or Athena query-latency suite exists. Do not affirm SLO or load-test practices in this stage; NFR stages own those targets. For this classic skipped-ops workflow, Standard does not require e2e or perf suites.

### Interview must resolve (quality)

Additive to the draft's testing questions; quality-owned:

1. Confirm test-after for new transform and query work, versus TDD on the lake layout. Default suggestion: keep test-after.
2. Coverage floor: (a) affirm 80% line coverage as the team target, with pytest-cov from the first lake/transform Construction, local coverage in the check command until CI exists; or (b) explicit project carve-out of "suite green only" until a pipeline exists. Do not leave the org classic floor as an unenforced suggestion.
3. What "existing suite" means for a server-only change: live-sched pytest only, every Python POC, or Android as well.
4. How Standard maps to this markdown-only intent versus later lake code: no new tests until runtime exists, versus unit tests on the first transform code plus a thin query-contract layer once Athena-backing tables exist.
5. Promote "unit tests do not open the production MCA or a live broker" to Mandated, or keep it as the existing project learning only.
6. If CIF parsers stay duplicated, require a shared layout golden test as the drift check.

## Positions

- AGREE: test-after Methodology and Ordering — live-sched and Android suites are post-implementation pytest/JUnit with no Gherkin or TDD artefacts.
- AGREE: synthetic CFA/zip fixtures versus later real-zip ops — matches the FR5.1 project learning and keeps unit tests off the multi-GB MCA and live broker.
- AGREE: Kafka's missing tests do not license skipping tests on new lake, transform, or ingest code — Standard still requires unit plus integration volume on new work.
- AGREE: 80% and CI-before-merge cannot be enforced today — no coverage.py, pytest-cov, JaCoCo, Kover, or workflow files; local pytest is the only observed bar.
- AGREE: walking-skeleton skip for this classic intent — `skeleton: off`; architecture markdown is not a running slice.
- AGREE: discovered-rules stay limited to already-locked constraints — do not invent a coverage ALWAYS until the interview affirms a floor.
- OBJECT: treating "existing suite stays green" as the implied team practice after interview — classic plus Standard still name an 80% floor; the interview must affirm that floor (tooling deferred) or write an explicit project carve-out.
- OBJECT: interview list does not map Standard volume onto this markdown-only intent versus later lake code — unit-plus-query-contract versus "no tests because the deliverable is a document" will otherwise leak into Construction.
- OBJECT: draft extras omit the brownfield pytest baseline and the live-sched gaps that lake tests must not copy — untested XML/Avro-via-JSON, multi-member zip, delete cascade, UID-wide scan, and parser copies without a layout golden.
