**Collaborator:** aidlc-devsecops-agent

## Contribution

Independent security review of the release-engineer brownfield draft for intent `261020-athena-timetable-lake`. Scope is lint and format rules, SAST/DAST, secret and dependency scanning, and supply-chain controls. Sources: the four lead drafts plus `code-quality-assessment.md`, `dependencies.md`, `technology-stack.md`, `org.md`, and `project.md`. On-disk confirmation (contents of secret files not opened): no `.github` tree; no root pre-commit, gitleaks, Dependabot, Renovate, Snyk, or Bandit config; `server/poc/live-sched/pyproject.toml` uses open version ranges and has no lockfile; root `.gitignore` includes `**/.env`; `android/.gitignore` lists `/local.properties`; `android/local.properties` exists locally as expected.

### Lint and format

The draft is right that Kotlin already sets `kotlin.code.style=official` and that live-sched has no Ruff, Black, Flake8, or mypy project config. The three `# noqa: SLF001` comments in `cfa_apply.py` show informal Ruff use without a committed config. Org Code Style still expects a linter that blocks merge in CI. This classic intent produces architecture markdown and skips the CI pipeline stage, so do not add formatter or SAST config files as part of this piece of work. The interview must still separate that local fact from the affirmed team practice: either keep “no Python linter” as a temporary observed gap, or name Ruff (including its security rules) as the Python merge gate once lake/ingest code and CI exist. Leaving “defer to whatever this repo already configures” as the affirmed Code Style section would lock in an empty control.

### SAST and DAST

The draft does not mention either. Observed: no Bandit, Semgrep, SonarQube, CodeGuru, Detekt, or ktlint; live-sched has no web framework, AWS SDK, Glue/Athena/S3 client, or Dockerfile, so there is no running HTTP surface to DAST. DAST against a future Athena/S3 lake should stay deferred until something is deployed. SAST should not stay unspoken: for new lake or ingest Python, the default practice is static analysis (Ruff security rules and/or Bandit) as a merge gate once CI exists; until then the only observed bar is local pytest. Do not invent a DAST tool, Inspector continuous scan, or production Security Hub control as a Mandated rule in this draft.

### Secrets

Agree that Kafka and Darwin keys stay in gitignored workspace-root `.env` and Android `local.properties`, that those values are never hardcoded, and that CIF dumps matched by root `.gitignore` are never committed. Ignore rules are not detection. There is no gitleaks, git-secrets, or truffleHog hook and no CI secret scan, so an accidental add of `KAFKA_CONSUMER_KEY` / `KAFKA_CONSUMER_SECRET` or a Darwin `x-apikey` would not be blocked. Architecture markdown must not embed those values or long-lived AWS access keys; lake design should assume IAM roles when AWS is specified later. Interview: adopt a secret-scan control (pre-commit and/or CI) before ingest or lake code lands, with revoke-then-rotate if a secret is committed; or explicitly accept ignore-files-only until CI exists.

### Dependency and supply-chain

live-sched declares `python-dotenv>=1.0,<2`, `confluent-kafka>=2.3,<3`, `pytest>=8.0,<9`, and `setuptools>=68` with no lockfile, no pip-audit/Snyk/Inspector job, no SBOM, and no Dependabot or Renovate. `confluent-kafka` is a native extension, so the library supply chain is not “pure Python wheels.” CIF/CFA zips and Kafka payloads are a second supply chain: untrusted National Rail feed bytes, already gated by the HD Current File Reference refuse rule. This intent must not stand up a scanner inside the architecture document. The interview should record: when Python lake or ingest code is added, lock or pin install-time dependencies and fail the future CI job on Critical/High known CVEs; emit an SBOM when packaging starts; keep treating CIF/CFA/Kafka bytes as hostile input. Container image scanning is not applicable (no image).

### What to fold into the four drafts (lead only)

- **team-practices.md — Code Style:** keep defer-to-repo for files this intent writes. Add a security practice the interview can affirm: secret scanning and dependency locking are explicit controls, not implied by `.gitignore`. Do not treat “no new formatter in this draft” as “the team has no Python linter.”
- **team-practices.md — Deployment:** agree there is no deploy today and that production remains human-approved when deploy exists. Add that architecture text must not list credentials, and that DAST and image scanning wait until a runtime exists.
- **discovered-rules.md:** do not add new ALWAYS/NEVER from this review. The existing literal-absolute `rm -rf`, project-local venv, HD sequence refuse, and no `schedule_poc` import already reduce destructive, interpreter, integrity, and cross-package risk.
- **evidence.md — Interview must resolve:** add (7) secret scanning vs ignore-files-only, (8) lockfile plus CVE scan when new Python lands, (9) SAST (Ruff security/Bandit) as a future CI gate vs none until CI exists, (10) DAST explicitly deferred until a deployed query surface exists.

Cloud controls (KMS, GuardDuty, bucket policies, Athena workgroup encryption) belong in later NFR and infrastructure design, not as practices invented here.

## Positions

- AGREE: Kotlin official style and no live-sched Ruff/Black/mypy config — that is what the tree actually has; this markdown-only intent should not add a formatter.
- AGREE: secrets live in gitignored `.env` / `local.properties` and must not be hardcoded or committed with CIF dumps — that matches `dependencies.md` and the ignore files.
- AGREE: no deploy, no CI, no Glue/Athena/S3 client today; PLAN.md `{env}` is design-only; production stays human-approved when deploy exists.
- AGREE: discovered-rules stay limited to existing human/project constraints (`rm -rf`, venv, HD sequence, no `schedule_poc` import) — do not mint new ALWAYS/NEVER from this review.
- AGREE: walking-skeleton skip for classic `skeleton: off` — a design document is not a running security boundary.
- OBJECT: Code Style “defer to existing config” as the affirmed team practice — it would lock in empty lint, SAST, and CI-before-merge controls that org Code Style still expects once code lands.
- OBJECT: silence on SAST/DAST — SAST for new lake/ingest Python should be a named future CI gate (Ruff security and/or Bandit); DAST must be explicitly deferred, not omitted.
- OBJECT: ignore-files treated as sufficient secret control — without gitleaks or a CI secret scan, Kafka/Darwin credentials can still be committed; the interview must choose a scanner or accept ignore-only as a time-boxed gap.
- OBJECT: open Python ranges with no lockfile, SBOM, or CVE scan left off the interview list — `confluent-kafka` and any future AWS SDK need pin/lock plus Critical/High fail-closed scanning when that code exists.
- OBJECT: Interview must resolve stops at branching, coverage, Python style, environments, test-after, and skeleton — it must also resolve secret scanning, SAST-when-CI, dependency locking/CVE scan, and DAST-deferred.
