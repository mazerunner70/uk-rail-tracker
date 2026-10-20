# Project-Level Rules

> Project-specific specialisation and corrections. Loaded after `org.md` and
> `team.md` as strict-additive guidance; contradictions with broader policy
> are rejected. Populated by practices-discovery and the self-learning loop.
>
> Use sparingly: most teams don't need a project layer. Reach for it
> only when this specific project needs stable, durable guidance beyond the
> team practice (for example, package-specific release checks or an additional
> regression suite for a legacy component).

## Way of Working

<!-- Project-specific specialisation. Example: -->
<!-- This monorepo requires package-scoped branch names and a package owner -->
<!-- review in addition to the team's normal merge policy. -->

## Walking Skeleton

<!-- Project-specific specialisation. Example: -->
<!-- The walking skeleton must exercise the legacy service adapter as well -->
<!-- as the new service boundary. -->

## Testing Posture

<!-- Project-specific specialisation. -->

- venv should be built correctly when needed so dependency install does not pollute the system (learned 2026-10-11) <!-- cid:261004-schedule-tracking-api:build-and-test:264f2a7b00c58cb0128febe74ebce148cd633d0f799ef99559b1a9ef297db827 -->

- Kept the classic 80% coverage floor carved out because Q4 chose suite-green until CI exists; that is an explicit team carve-out, not silence. (learned 2026-10-20) <!-- cid:261020-athena-timetable-lake:practices-discovery:b694c5b506a94c2c796ad928366189de1c4dc2f3c093c97982f6a37394c82836 -->

## Guard Policy

<!-- Project-specific. Mode: strict, relaxed, or off. Strict here holds for every intent and cannot be changed from chat. A section under the retired Change Control heading, written by an earlier release, is still read. -->

## Deployment

<!-- Project-specific specialisation. -->

## Code Style

<!-- Project-specific specialisation. -->

## Tech Stack

<!-- Technology choices locked for this project. -->

## Decided

<!-- Decisions made in earlier stages that should not be re-asked. -->
<!-- Format: DECIDED: [decision] (Stage [slug], [date]) -->

## Scope Overrides

<!-- Custom scope rules for this project. -->

## Forbidden

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: NEVER [behavior] (affirmed [date]) -->
<!-- Example: NEVER throw exceptions across service layer boundaries (affirmed 2026-05-17) -->
- NEVER run `rm -rf` (or equivalent recursive force delete) against a relative path or against a shell variable (`$VAR` / `${VAR}`); always pass a literal fully resolved absolute path in the command. Relative or variable-based deletes are a critical violation. (learned 2026-10-04)

- NEVER run `rm -rf` (or equivalent recursive force delete) against a relative path or against a shell variable (`$VAR` / `${VAR}`). (affirmed 2026-10-20)

- NEVER import `schedule_poc` from `live-sched`, or from a successor package that follows the live-sched ideas. (affirmed 2026-10-20)

## Mandated

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: ALWAYS [behavior] (affirmed [date]) -->
<!-- Example: ALWAYS use Result<T,E> for fallible operations in service layer (affirmed 2026-05-17) -->

- ALWAYS pass a fully resolved literal absolute path when running `rm -rf` (or equivalent recursive force delete); never a relative path and never a shell variable. (affirmed 2026-10-20)

- ALWAYS create and use a project-local venv for Python dependency installs so they do not pollute the system interpreter. (affirmed 2026-10-20)

- ALWAYS derive the CFA / timetable feed sequence from the HD Current File Reference (columns 33–39) and refuse when that field has no three digits rather than guessing from the zip basename. (affirmed 2026-10-20)

- ALWAYS allow more than four questions when needed (up to about six), including follow-on questions. (affirmed 2026-10-20)

## Corrections

<!-- Project-specific corrections from human feedback. -->
<!-- Format: NEVER/ALWAYS [behavior] (learned [date]) -->
- Happy with more than 4 questions when needed (up to about 6), and follow-on questions are fine. (learned 2026-10-04) <!-- cid:261004-schedule-tracking-api:requirements-analysis:3e35001fd27e9b84fd1ddfb54a9cc32df7770dca87f4c1a91b409d684b6882cc -->
- NEVER run `rm -rf` against a relative path or a shell variable; always use a literal absolute path for recursive force deletes. (learned 2026-10-04)
- The request already names the outputs. The open points are who it is for, what “LM” and the MCA file refer to, whether those outputs are the success measure, who decides, and whether a proof of concept is the right size. (learned 2026-10-11) <!-- cid:261011-live-schedule-poc:intent-capture:8e135882ac4c3c30122a2c9f2ec086d7c14fa5cf9bc07bc81dfb8c1dbbd51b3c -->
- Q1 names a later production database for a full schedule. Q3 and Q5 keep this piece of work as the prototype outputs only, so that later database is the reason to do the proof, not part of it. (learned 2026-10-11) <!-- cid:261011-live-schedule-poc:intent-capture:31d37881d9311d25b0cc18b2225bb84dc38d4bf0a18dc3e4c563e8b72907df9d -->
- Intent already lists MCA→LM DB, code summary, algorithm MD, 1h Kafka→CSV, apply+DB-change MD, .env, and server/poc/live-sched. Open gaps are reuse of schedule_poc, DB choice, output paths, Kafka env/capture, and which message types must mutate the DB. (learned 2026-10-11) <!-- cid:261011-live-schedule-poc:requirements-analysis:c3ff096ec52a8c68b258449ba0232454ed085d15d270dcdb9cd90524989c905c -->
- Q4 answer mixed Kafka POC in-place reuse with “.env at the root”; follow-up Q6 asks for the exact path. (learned 2026-10-11) <!-- cid:261011-live-schedule-poc:requirements-analysis:bc33d5244a41ebb37226bb3c6f3fe4a9c987e47b6e8428e28d65227fce344982 -->
- stage-level zero-Unit Code Generation for live-sched; plan embeds Testing Contract and omits API/Frontend layers as out of requirements; package must not import schedule_poc. (learned 2026-10-16) <!-- cid:261011-live-schedule-poc:code-generation:164b593b31aee02677eb13fce41304a4de9d247d642a983f169f4d06f8aeede4 -->
- HD sequence from Current File Reference only (cols 33–39); refuse when that field has no three digits rather than guessing from the zip basename. (learned 2026-10-17) <!-- cid:261017-cfa-daily-updates:code-generation:de84844870f4d6198ba852628d0dae3e6af4ccee136113a37619407205b0dcb6 -->
- FR5.1 real update zip demo left for Build and Test / manual ops; unit tests use synthetic CFA/zip fixtures as the plan allowed. (learned 2026-10-17) <!-- cid:261017-cfa-daily-updates:code-generation:1660827bbfc0c4823797268c470c7bb66d7266aa220f4d6a9982b9d779c238cb -->
- CFA apply duplicates CIF schedule parsing instead of sharing with cif_loader to keep MCA full-load and incremental paths independent for this PoC. (learned 2026-10-17) <!-- cid:261017-cfa-daily-updates:code-generation:2aa56a901a3db0ff37c245a8b7e20a105da055fa8d565bcb763dc3ba3c1b9f50 -->
- Focused the scan on server/poc/live-sched; the human chose a focused scan and the request follows the live-sched proof of concept, so deep coverage stayed on that tree. (learned 2026-10-20) <!-- cid:261020-athena-timetable-lake:reverse-engineering:d6c4b30a900f8c2edcf8916e8d89f93c38a2791c7c947e5631aec41dee8c8ef0 -->
- Treated this as a project-root scan with a stale code knowledge base; the intent has no registered repo list, and the existing store is STALE because server/poc/live-sched changed after the 261017-cfa-daily-updates scan, so reuse is not offered. (learned 2026-10-20) <!-- cid:261020-athena-timetable-lake:reverse-engineering:9a5a156df3047982c091c06974a6a2226d3322b70e1690d8ba7caf20b51a0e62 -->
- Minted the scope fingerprint in the parent session and spelled the analyzed path server/poc/live-sched without a trailing slash; the architect's shell could not run the mint command, and the slash form made the same directory look narrower than the previous store. (learned 2026-10-20) <!-- cid:261020-athena-timetable-lake:reverse-engineering:393c0026f586058c33fdfbb0009cc59dacb46562e5a9ea2aca1cae5e34f426a2 -->
- Read Q2 Other as architecture-first with staged implementation; the human asked for an overall architecture now and said implementation can land in stages, so we skip a running intake-to-query slice for this work. (learned 2026-10-20) <!-- cid:261020-athena-timetable-lake:practices-discovery:c534f29cb300b22bd786f909bc031e44cb5b749b5787f097b1528e772d482a50 -->
- Landed the lead drafts after the release engineer's writes were refused, then collected independent quality, developer, and security reviews; interview asks only the six points those drafts could not settle (branching, thin slice, test order, coverage, environments, style and later scans). (learned 2026-10-20) <!-- cid:261020-athena-timetable-lake:practices-discovery:283690c99e335e33976b4ca6d475b23854a2ad18e79ab595539974e85acf14cc -->
