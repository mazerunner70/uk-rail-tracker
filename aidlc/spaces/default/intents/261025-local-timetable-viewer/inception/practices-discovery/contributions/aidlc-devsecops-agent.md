**Collaborator:** aidlc-devsecops-agent

## Contribution

Security controls for the local timetable viewer, assessed from the lead draft, the affirmed baseline, and the code knowledge. This review records no credential values. No new ALWAYS or NEVER line: scanner timing, image provenance, and the viewer secret boundary are still interview choices.

### Lint and format

Live-sched has no Ruff, Black, Flake8, or mypy section. `server/pyrightconfig.json` covers `poc/kafka` only. Kotlin uses the official style setting and has no Detekt or ktlint config. Live-sched carries `# noqa` comments with no configured linter to enforce them. The org default expects a linter to fail the merge. The affirmed baseline adds no formatter or linter in the current piece of work, and this classic workflow skips CI.

The draft is right to mark that deferral for re-decision. It was written for a markdown-only lake pass. This intent adds a reader and a UI, and the service page chooses neither a server language nor a UI toolkit. Formatter absence and security-scanner absence are separate decisions. A missing style-linter config is not a static-analysis control. Android lint stays out of this service unless the interview chooses the existing Android app as the UI.

The interview still has to decide whether the new server and the new UI get a formatter or style linter while CI is absent, or whether style stays "copy the observed conventions" with no new config file. That choice does not decide SAST.

### SAST

The analyzed tree has no static application security testing. The baseline adds Python SAST when CI exists. This workflow skips the CI pipeline, so that sentence installs no control on this intent. The new code's language is still open, so "Python SAST" must not be copied onto a UI that may be another language.

The interview still has to decide whether this intent accepts no SAST, or requires a local scanner without standing up CI. Name the scanner only after the server language and UI toolkit are chosen. This practices pass adds no scanner config file.

### DAST

The live-sched tree has no HTTP server. The service page adds a local reader and a local UI, for one person, with no accounts. The draft treats dynamic testing as staying off because nothing is hosted. A container on this machine is still a runtime. Hosted and production dynamic testing stay out. That does not decide a check against the local process.

The interview still has to decide whether any dynamic test of the local reader is in scope, or explicitly out for a single-user process. Cloud dynamic scanning stays out unless a host is brought back.

### Secrets

Gitignore for workspace-root env files, Android `local.properties`, and CIF dumps still fits. The draft is right that an ignore file is not a scanner. No secret scanner and no pre-commit hook are recorded. Architecture text keeps credential values out. This review does not repeat consume or API credential names or values.

The new reader uses the SQLite file. It does not need broker or API credentials. The interview still has to decide:

- Whether secret scanning stays "when CI exists" (a stage this intent skips), becomes a local check, or is an accepted gap on this one-person tree.
- That the viewer process and its image do not receive broker or API credentials, and do not log environment values.

### Dependency and supply-chain controls

Live-sched declares version ranges in `pyproject.toml` and has no lockfile. No dependency CVE scanner, Dependabot, Renovate, or software bill of materials is recorded. A project-local virtualenv keeps installs off the system interpreter. It does not pin transitive versions. The page says run in Docker and gives no image recipe, so there is no base-image pin, digest, or signature. Image scanning was deferred until a runtime exists. The runtime this page names is that local image, and CI is still skipped.

The interview still has to decide:

- Whether each new server or UI package pins dependencies with a lockfile before install, and whether a CVE check is a local command or stays deferred while CI is skipped.
- Whether image scanning stays deferred with CI, and whether the local image practice includes a pinned base image.
- Whether the image build omits the Kafka client and other consume-only dependencies. The affirmed ban on importing `schedule_poc` does not by itself keep those libraries out of the viewer.
- If a local run is affirmed, whether smoke for that run includes a read-only mount of the SQLite file and a listener bound to this machine only. Those are local-runtime controls, not cloud controls.

Cloud controls named on the lake baseline (KMS, GuardDuty, bucket policies, workgroup encryption) stay out of this service unless the human brings a host back into scope.

## Positions
AGREE: Cloud controls stay out of this service unless a host is brought back — the page is a local Docker run with no hosted deployment, and the analyzed tree has no cloud client.
AGREE: Ignore-files stay a commit convention, not a secret scanner, and this review adds no credential values — gitignore coverage is recorded and is not a detector.
AGREE: Secret scanning, dependency CVE scanning, and SAST stay open because "when CI exists" does not run on an intent that skips CI — the draft correctly refuses to treat that sentence as already in force here.
AGREE: No new ALWAYS or NEVER line belongs in the hard-constraint draft — only a person's stated constraint is promoted, and these scan choices are still open.
OBJECT: Dynamic testing is written as settled off because nothing is hosted — a local reader in Docker is a runtime, so the interview still chooses whether a local dynamic check is in or out.
OBJECT: Image scanning is still framed as waiting for a runtime, and the open points omit image pin and the viewer secret boundary — this page's run mode is a local image with no recipe, and the reader does not need broker credentials.
OBJECT: Scanner config is bundled with formatter config, and static analysis is named as Python-only — style lint and security scan are separate, and the UI toolkit is not chosen.
OBJECT: Supply-chain pinning is missing from the open points — live-sched has range pins and no lockfile, and a project-local virtualenv does not pin transitive dependencies or the image base.
