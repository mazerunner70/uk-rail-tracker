# Practices discovery evidence

Brownfield first run, integrated after the interview. `team.md` sections are still empty (HTML comments only) until the parent affirmation gate promotes these drafts. Reverse-engineering artifacts consumed: `code-structure.md`, `technology-stack.md`, `dependencies.md`, `code-quality-assessment.md`, `architecture.md`, `business-overview.md`.

## Sources

- Active-space memory: `org.md` (framework defaults), `team.md` (empty), `project.md` (learned hard constraints). Phase file `phases/inception.md` applied, not edited.
- Reverse-engineering CodeKB under `aidlc/spaces/default/codekb/uk-rail-tracker/`: `code-structure`, `technology-stack`, `dependencies`, `code-quality-assessment`, `architecture`, `business-overview`.
- Git: `.git/HEAD` (`refs/heads/journey-tracker`), `.git/config` (remote `git@github.com:mazerunner70/uk-rail-tracker.git`; tracked branches `main`, `m4`, `m5`, `sm-1`, `enginev2`, `journey-tracker`). Observed HEAD at draft time: `89d44b5884fd2a46e804e8b3d7189ce5244cea52`, one commit ahead of `main` at `74c3ae2bfd5e4c4fe76f1064d8c2e32a4c56a125`.
- CI/CD: no `.github` directory; `ci.yml` missing. `code-quality-assessment.md` records no repo CI on the prior full scan and none under `server/poc/live-sched`.
- Packaging and style: `server/poc/live-sched/pyproject.toml`, `server/poc/live-sched/README.md`, `server/poc/live-sched/tests/test_cfa_apply.py`, `server/pyrightconfig.json`, `android/gradle.properties`, `android/app/build.gradle.kts`, root `.gitignore`.
- Design-only topology: `server/PLAN.md` §6 S3/Athena layout (`s3://uk-rail-tracker-{env}/` raw / curated / exports).
- Prior intent signal (not this stage’s artifacts): `261017-cfa-daily-updates` had no `practices-discovery/` tree; its build-and-test summary is local PoC, not deployment-ready.
- Classic scope file: `skeleton: off`. Intent state: Brownfield, classic, Standard depth, Standard test strategy; CI pipeline and Operation stages skipped.
- Interview file: `practices-discovery-questions.md` (six answered questions).
- Support contributions: `contributions/aidlc-quality-agent.md`, `contributions/aidlc-developer-agent.md`, `contributions/aidlc-devsecops-agent.md`.

## Interview decisions

Authoritative answers from `practices-discovery-questions.md`:

1. **Way of Working — A.** Short-lived branches on `main`, squash-merge (org default). This overrides the observed long-lived milestone-branch pattern (`m4`, `m5`, `journey-tracker`). Mapped to trunk-based worktrees: base `main`, target `main`, strategy squash.
2. **Walking Skeleton — X.** Human text: “Give me an overall arch and the impl can be in stages.” Produce the overall architecture now; do not require a running intake-to-query slice first; later construction may land in stages against that architecture. This is stronger than a bare skip: architecture is the first artifact, staged implementation is allowed.
3. **Testing — A.** Test-after. **Methodology**: test-after. **Ordering**: Implement each applicable testable layer, then write and run that layer's tests.
4. **Coverage — A.** Suite stays green only, until CI exists. Explicit carve-out of the classic 80% floor for now. Quality’s objection that “suite green” must not become practice by silence is resolved by this choice.
5. **Deployment — B.** Name `dev` / `staging` / `prod` now; staging on merge, production needs a separate approval. Environments are named in the architecture; this workflow still does not stand them up.
6. **Style and safety — A.** Defer to existing config; no new formatter; secrets stay in `.env`; add secret scan, dependency CVE scan, and Python SAST only when CI exists; no live-site scanning until something is deployed. DevSecOps objections about silence on SAST/DAST, ignore-files-as-scanner, and CVE scanning are resolved by this when-CI-exists clause and the explicit DAST deferral.

## Support positions folded in

Findings that do not contradict the interview, folded into `team-practices.md` as extras rather than new ALWAYS/NEVER rules:

- **Quality (AGREE):** test-after matches live-sched pytest and Android JUnit with no Gherkin; Kafka’s missing tests do not license skipping tests on new lake code; synthetic CFA/zip fixtures versus later real-zip ops (FR5.1); walking-skeleton skip for classic `skeleton: off`; discovered-rules stay limited to locked constraints; 80% and CI-before-merge cannot be enforced today.
- **Quality (folded, not contradicted):** brownfield pytest baseline before and after edits to `server/poc/live-sched`; “existing suite” for server-only work is that package’s pytest; Standard volume is unit plus a thin query-contract layer once tables exist, not “markdown-only therefore no tests” as a lasting team practice; do not copy live-sched test gaps (XML/Avro-via-JSON, multi-member zip, delete cascade, UID-wide scan, parser copies). Quality’s request to promote “unit tests do not open the production MCA or a live broker” to Mandated was not stated by the human in ALWAYS/NEVER language; it stays a Testing Posture extra.
- **Developer (AGREE):** defer formatter; keep existing tokens (`train_uid`, `service_guid`, `stp_indicator`, `YYMMDD`, `HHMM`); secrets in `.env` / `local.properties`; project-local `.venv`; no `schedule_poc` import; HD columns 33–39 only for sequence.
- **Developer (folded, not contradicted):** live-sched already has a style without a linter (typing, frozen dataclasses, exceptions at the CLI); new lake code is a sibling package under `server/`; do not copy `repo._conn`; observed fail-closed CFA versus fail-open Kafka; PLAN.md `server/{api,ingest,infra}` is not an existing layout.
- **DevSecOps (AGREE):** no formatter to add in this markdown-only intent; secrets not hardcoded; no deploy or CI today; discovered-rules stay limited to `rm -rf`, venv, HD sequence, and no `schedule_poc` import; DAST waits for a runtime.
- **DevSecOps (folded via Q6):** secret scanning, dependency CVE scan, and Python SAST are named future CI gates, not implied by `.gitignore`. Architecture must not embed credentials. DAST and image scanning stay deferred.

Support OBJECT lines that the interview settled are closed (coverage carve-out; scans when CI exists; DAST deferred; architecture-first staged impl). Support OBJECT lines the interview did not ask remain as uncertainty below.

## Remaining uncertainty

These were raised by support reviews and were not on the six-question interview. They are not team policy. Later design or Construction should ask or decide them; they are not new hard rules.

1. Package home for the first lake/ingest code: sibling under `server/poc/`, or start the PLAN.md `server/ingest` tree as a new empty package.
2. Identity column spelling in new lake tables: `service_guid` (match SQLite) versus `serviceGUID` (match PLAN REST paths). Existing store tokens stay; this is only the new schema spelling.
3. HD header with no three-digit run: keep the implemented inner-CFA-member-name fallback, or refuse the object. The locked rule already forbids guessing from the zip basename; it does not itself retire the member-name fallback.
4. Lake object arrival policy: CFA fail-closed (refuse, leave object, continue later keys) versus Kafka fail-open (archive then ignore). S3/Athena intake should name which one when that design is written.
5. Whether to require a shared CIF layout golden test if parsers stay duplicated inside a successor package.
6. Lockfile, pin, and SBOM when Python lake or ingest packaging starts. Q6 named a dependency CVE scan when CI exists; it did not name a lockfile or SBOM.
7. What “existing suite” means if a change spans more than one Python POC, or later crosses Android. Server-only work is recorded as that package’s pytest; a cross-tree change is unset.

Observed versus inferred (pre-interview facts that the answers now override where they conflict):

| Topic | Observed evidence | After interview |
|-------|-------------------|-----------------|
| Branch lifetime | Milestone names `m4`, `m5`, `sm-1`, `enginev2`, `journey-tracker` | Short-lived trunk-based branches on `main`, squash-merge |
| Merge style | Linear `main` plus fast-forward; squash vs merge-commit unknown | Squash-merge |
| Skeleton | Classic `skeleton: off` | Overall architecture first; no running slice; staged impl allowed |
| Tests | pytest after code; no Gherkin | Test-after Methodology and Ordering affirmed |
| Coverage / CI | No coverage tool; no workflow files | Suite green only until CI exists; 80% carved out |
| Deploy | Local SQLite + Android debug; PLAN.md `{env}` design-only | Name `dev` / `staging` / `prod`; staging on merge; prod approval |
| Style / safety | No Python formatter; `.env` gitignored; no scanners | Defer formatter; scans when CI exists; no DAST until deployed |
| Isolation | live-sched does not import `schedule_poc` (`code-structure.md`, `business-overview.md`) | Locked project rule, unchanged |
| Sequence gate | HD Current File Reference columns 33–39 (`architecture.md`, `project.md`) | Locked project rule, unchanged |
| Deletes / venv | `project.md` Forbidden + Testing Posture learnings | Locked project rules, unchanged |
