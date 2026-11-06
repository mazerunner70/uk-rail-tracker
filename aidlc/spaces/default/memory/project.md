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

- Kept the prior test-lock table instead of deleting locks for modules this run did not open. A rebuild from only the four opened modules would drop CFA and service-day behaviour the 2026-10-20 scan recorded. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:reverse-engineering:8f5e48dd2d2e5498cbc765dfdfff1ea5ca8d4eb8549bc2b50145239a8661151a -->

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

- NEVER run `rm -rf` (or equivalent recursive force delete) against a relative path or against a shell variable (`$VAR` / `${VAR}`). (affirmed 2026-10-25)

- NEVER import `schedule_poc` from `live-sched`, or from a successor package that follows the live-sched ideas. (affirmed 2026-10-25)

- NEVER import `schedule_poc` from the local timetable server or UI. (affirmed 2026-10-25)

- NEVER create or rewrite the SQLite file from the local timetable reader. (affirmed 2026-10-25)

- NEVER create `dev`, `staging`, or `prod` for this service. (affirmed 2026-10-25)

- The database is its own container and the SQLite file survives a restart and a rebuild. The reader still does not create or rewrite that file. Live-sched is not involved in any way: it is a proof of concept, and this application will run into the future. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:requirements-analysis:fc8b4318e3581cbdfd1c7a2e603c236239083d84342a78b1765e567fa2dac107 -->

- The earlier rule that the reader never rewrites SQLite still binds TimetableQuery. McaInitialisation is the exception and replaces the file. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:domain-design:e3b7d3dfe935c5db377c85d61e16e8a8f5fb96ad351b217ba4c5a0bcb1626d55 -->

- The database is its own container and the SQLite file survives a restart and a rebuild. The reader still does not create or rewrite that file. Live-sched remains the writer. (learned 2026-10-27) <!-- cid:261025-local-timetable-viewer:requirements-analysis:5ab513ecc0ba6e179aab85f6e3a99b2bb1fda108426099b29d61286947cd7b7b -->

- The human amended that database note: live-sched is not involved in any way, because it is a proof of concept and this application will run into the future. The other five notes stay unchanged. (learned 2026-10-27) <!-- cid:261025-local-timetable-viewer:requirements-analysis:895d41b7cf3d458c4c61f352faa0728c76810ae6d694ada322c84797260e1f9d -->

## Mandated

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: ALWAYS [behavior] (affirmed [date]) -->
<!-- Example: ALWAYS use Result<T,E> for fallible operations in service layer (affirmed 2026-05-17) -->

- ALWAYS pass a fully resolved literal absolute path when running `rm -rf` (or equivalent recursive force delete); never a relative path and never a shell variable. (affirmed 2026-10-20)

- ALWAYS create and use a project-local venv for Python dependency installs so they do not pollute the system interpreter. (affirmed 2026-10-20)

- ALWAYS derive the CFA / timetable feed sequence from the HD Current File Reference (columns 33–39) and refuse when that field has no three digits rather than guessing from the zip basename. (affirmed 2026-10-20)

- ALWAYS allow more than four questions when needed (up to about six), including follow-on questions. (affirmed 2026-10-20)

- ALWAYS pass a fully resolved literal absolute path when running `rm -rf` (or equivalent recursive force delete); never a relative path and never a shell variable. (affirmed 2026-10-25)

- ALWAYS create and use a project-local venv for Python dependency installs so they do not pollute the system interpreter. (affirmed 2026-10-25)

- ALWAYS derive the CFA / timetable feed sequence from the HD Current File Reference (columns 33–39) and refuse when that field has no three digits rather than guessing from the zip basename. (affirmed 2026-10-25)

- ALWAYS allow more than four questions when needed (up to about six), including follow-on questions. (affirmed 2026-10-25)

- ALWAYS put new work on a short-lived branch and squash-merge that branch to `main`. (affirmed 2026-10-25)

- ALWAYS write the new server and UI in TypeScript under `apps/local-timetable/`. (affirmed 2026-10-25)

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
- Did not re-ask walking skeleton or parallel Bolts. Practices already skip a running intake-to-query slice; the unit DAG has no parallel set; Construction is already recorded serial unit-major. The six questions cover Bolt size, first-Bolt proof, the existing architecture file, external holds, the top worry, and Definition of Done. (learned 2026-10-20) <!-- cid:261020-athena-timetable-lake:delivery-planning:12157e1ac812d2e4eb962cb58894798cea2c01a88b9d26236a2bcc0a97d1399d -->
- Architect view: three Bolts one Unit each in DAG order is the only valid topology; C1/C2 provider is Bolt 1, C3 provider is Bolt 2, Bolt 3 consumes both. No topological deviation to justify. Remaining Construction is Functional Design only, so each Bolt's demo is a document check of owned headings. (learned 2026-10-20) <!-- cid:261020-athena-timetable-lake:delivery-planning:a21fc7ba74a9db314046060e075ba969f5cddd38e6ae2c2949be44d26b7fa7c2 -->
- Folded WSJF into Question 2 option C instead of a standalone scoring-weight question. Three spec chapters of one markdown file do not need a weight-split unless the human asks for formal scoring. (learned 2026-10-20) <!-- cid:261020-athena-timetable-lake:delivery-planning:4e29d2cd31a5158c0860dcfca5ad4ddd95c47dbcb959d241b958ebb8d2c3de7e -->
- Question 4 answer was "A sample data is available if questions arise, look in the pdfs for details". Treated as A (nothing outside this session holds the chapters) plus on-hand sample/PDF references, not as a live-zip or spec-at-hand gate (B/C). (learned 2026-10-20) <!-- cid:261020-athena-timetable-lake:delivery-planning:9c82093995b43ee9ff582373c223825c3ef7390de8a10df92615452afe46ecc9 -->
- Question 3 A (edit the architecture file in place) vs Question 5 A (top worry is overwriting that file). Kept in-place chapter lock rather than leaving the file untouched or replacing it from blank. Each Bolt may edit only its owned headings against C1–C3. (learned 2026-10-20) <!-- cid:261020-athena-timetable-lake:delivery-planning:0166c57734fde61a18d83ecde9dab16a5fab286f0e565130262e960c4e5f363f -->
- Five questions at Minimal depth: who it is for, what done means, why now, who decides, and whether this pass stays the two documents. The request already names the two files and the three views. (learned 2026-10-25) <!-- cid:261025-local-timetable:intent-capture:633fd6bf05682aef0f316a807b83065855eaa4908fd669f23fbeb3f386b08182 -->
- Two questions only: file paths, and what is in versus out. Who it is for, the three views, and docs-only were already answered. (learned 2026-10-25) <!-- cid:261025-local-timetable:scope-definition:4fcfb81d2255debcd6074eff69edd3dfd7ab3107cfcf633010605648e8da5b7e -->
- File answer says one document under docs/future-intents named lcocal-timetable.md. Intent says two pages, future-intents note and local-timetable.md. Asked which files and which name before writing the scope. (learned 2026-10-25) <!-- cid:261025-local-timetable:scope-definition:c5ab6fc330379c4e5429ce8048fbf75d7e39f4639784b112df0c94b9ae93aee7 -->
- Confirmed one file, docs/future-intents/local-timetable.md, holding both the future-intents note and the service description. Ranked the page with MoSCoW because this pass is one fixed page and there are no usage numbers to score. (learned 2026-10-25) <!-- cid:261025-local-timetable:scope-definition:a7ea2bdfcfe3beb46f1cd15396e262239493b9290732d153533f6e62f29f7b9c -->
- One question only: what the future-intents part of the page lists. The file path, the local service, the three views, and what is out were already decided. (learned 2026-10-25) <!-- cid:261025-local-timetable:requirements-analysis:96abb20c09cd0a50465855d0a1ce4b3b2b3887e4a6b68b5f1a765938d608fa5e -->
- Wrote the page from the live-sched store: LM only, deviation is forecast versus booked public time or deactivated, and a station pair is two calling points in order. The store has no train GPS, so where means the latest stop and the next stop. (learned 2026-10-25) <!-- cid:261025-local-timetable:requirements-analysis:849dc6f1a1a2f6621ea445f2998827ef5342b523fe0d0ff17ee984fa81a9944b -->
- human chose Focused scan. Snapshot paths server/poc/live-sched, store_generation sha256:125731cd588e65aa541d751cf6f93b0f83e2fad194ce475567120ae7e4a9a540, source_fingerprint git:99dd493302d01daca4b62fdaa26dee42ac41aca2. Developer scan limited to that path. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:reverse-engineering:8a67f6e7fb686572423089e254ba6199cbccc48100f3dc53114abf25e111a6f5 -->
- Kept server/poc/live-sched as the only analyzed path and did not also list it under shallow (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:reverse-engineering:c4ad73276594ae16cdc6f9ed546ed72217bd0eae05b37c88e96a62030abbeba0 -->
- Preserved 2026-10-20 live-sched prose this run did not re-read, and labeled it. That includes the per-module test locks for modules inventoried by name only, and the full-MCA counts. This run counted 45 def test_ functions. The prior store said 44. Which function was added was not reconciled. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:reverse-engineering:a86779abeb856cfda4445b426f158b74de470d143530144745eba143ccc44ed7 -->
- Recorded the local timetable server and UI as absent. The page's three views are described against the existing SQLite file. No new component was invented for them. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:reverse-engineering:9dce66d1df4b998693e7d1cbb49257661c8f09a5579bb88b3a696f3f2029532e -->
- minted the scope fingerprint in the parent session after the architect shell could not run aidlc (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:reverse-engineering:48863823d6df8c72c563a01952167cc979fb4d99f194109962e40a9d694d131b -->
- Did not run codekb-scope-diff --compare or codekb-publish (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:reverse-engineering:db9a824c551cd95cba404f7dac21ac2675f205e9a10d7234618d77c4d339d6cb -->
- Left fingerprint empty. The mint command was blocked by the delegated-agent shell guard before it started. It did not fail for a missing repo flag. No hash was invented. Architecture security text does not name Kafka credential environment variables (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:reverse-engineering:b2b654c43a2995f264af255a56afa68dc8b23cb87a785dd74115c547a183e3be -->
- team.md already has affirmed practices from earlier classic work, so this pass treats those sections as the baseline and only reopens what the local Docker timetable service does not settle. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:practices-discovery:14c5401d897ec1ff2d2efe1f08511cee047cde1366e91724ed0ad00dc7cb61a5 -->
- Read the two Other answers as a short-lived branch squash-merged to main, and as TypeScript under apps/local-timetable/db, server, and fe (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:practices-discovery:ded76a6605d5c1e301842379d9b3c99365457fdf996e99b6c25d218205c6304e -->
- Left the quality review's tighter view cases and a local Docker smoke out of the hard rules (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:practices-discovery:ac494bce4cf72cfd6cc3dcc15b9b73beca36db8fc9a5066b3685dab75b0cf8ca -->
- five questions only: first view, which SQLite file, refresh, clock, and how Docker runs the parts. The page and the affirmed practices already settle the three views, London Midland only, TypeScript layout, and a read-only database. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:requirements-analysis:056997801081cfa54d4c312cae8c4aebc842445e4a979bdee437c34886b4cc4d -->
- Question 4a answer C means this machine is kept on UK time, so Europe/London and the machine clock are the same clock for today and now. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:requirements-analysis:0226c3b4cf4e72c4d3e013cadd798c16cca80a879eccadca77d54a8ef9603542 -->
- The screen asks again every 30 seconds while a view is open. A control on the screen changes that interval. Reloading the page starts again at 30 seconds. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:requirements-analysis:c4dbc847b168c6006e2cae5b797ae6926f07af1a6be66d84e9ce1ac3a391c78c -->
- Where the train is uses forecast departure, then forecast arrival, then booked public departure, then booked public arrival. A stop time earlier than the origin stop's time is the next calendar day. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:requirements-analysis:520b27034d4fef9ad3d53f881145a090a57b77bed350666ed77aaba791943ddd -->
- Stated the stop-time order in the requirements so a later build does not guess which clock column means "at or before now". Forecast departure wins, then forecast arrival, then the booked public times in that same order. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:requirements-analysis:a77b4b0e5d14eaa07f5a1fc57e6fba3c3a56bd0db8a0639ef6d135a27139cf04 -->
- Execute user stories. The service is a screen for one person, so stories add the person, the benefit, and the Given/When/Then checks the requirements do not name. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:user-stories:d8841fde6d98dee37054fa60337d3fa44ab667497767498e696b6736646af3d4 -->
- Two questions only: who the stories are about, and how small each story is. The views, the UK clock, the 30-second refresh, and the three containers are already in the approved requirements. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:user-stories:aed30ab0ccdf1d4900b4b590271d8a50ee0e1c62a67ed51cfa6f894fdc89b2bb -->
- One persona, the local reader, who starts the containers and reads the board. Four wide stories: the station pair, all trains now, the refresh, and the three containers. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:user-stories:52825169eba87f2c7c4afacaa1226440d80db04ef964b00577b09f80501b44be -->
- The human added screen wording now: labelled station boxes, a loading line, the words No trains, and a control on each view that opens the other. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:user-stories:66a05c24c117f9bb30c3d40e214748f736a20ac71856344939202754136526e8 -->
- The human pointed at docs/local-timetable/rail controller dashboard.jpeg and docs/local-timetable/station-pair.png as the two mockups. The old loading and navigation questions were rewritten around those pictures. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:refined-mockups:7aaf4e51bed6eef57b95c15487890c1419fda4b3cfb3a1a93a6d4c50e5bcc395 -->
- Keep every panel from the two pictures. A panel the file cannot fill says it has no data. A station change asks at once. The UI reads a data layer and stays as it is until that layer changes. A failed retrieval does not change the layer. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:refined-mockups:cb46b3372fc63ac70ad115697ef35a045cd031f0104771843db877a371930025 -->
- Pictured panels the file cannot fill stay on the page and say they have no data. Counts the story rules can compute are filled from the data layer. Footer counts stay unfilled until the first success so a zero does not look like an empty board. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:refined-mockups:6d1eb6110e13e125095b3a19689f7b590f8b4c3427848620fc4d4111fa68e969 -->
- A stop time earlier than the origin stop is the next calendar day. The rule stays because a schedule can run for a couple of hours after midnight. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:refined-mockups:34ae4fd835de94741b6f82281cd5d3c6f95cd254a44e530b5eb6de51210c3d5b -->
- MFA was a typo for MCA. Four blocks: McaInitialisation replaces the SQLite file from that day's MCA and applies no CFA (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:domain-design:fec8c3a9685c8cbc25538a0b447eeac02eb15f05dff3e5955b2f71682b2dd54c -->
- NetworkPage asks on open and on each interval even when the station boxes are empty. StationPairPage asks only when both boxes are non-empty. Unmatched codes are a successful empty list. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:domain-design:d7af657a417b29d2e90fc0d57d178b37d1483adcca5d0ba2c1a57c3de1bcaca4 -->
- The pair page reads StationSelection and RefreshInterval owned by NetworkPage. That reference is not a call, so the call graph stays acyclic. (learned 2026-10-25) <!-- cid:261025-local-timetable-viewer:domain-design:7b265f833b3c0131312e46b95497c8b5f8afa361e4d4ae7e63e86eb7cefd659f -->
- Three containers and four code blocks are already decided. The open points are how those blocks sit in the containers, and when the MCA replace runs. (learned 2026-10-26) <!-- cid:261025-local-timetable-viewer:units-generation:a842400653129d122517070b9fea77175a9bfc4e7a19f459b8a0575740d8e398 -->
- Q1 D: the MCA load is its own unit in the server container, the query is the other server unit, both pages are one screen unit, and the database container holds only the file. Q2 Other: the screen starts empty, a button starts the MCA load, and the screen polls the server for progress. (learned 2026-10-26) <!-- cid:261025-local-timetable-viewer:units-generation:9e5ca0bb19ef3da8d50359615afb34b8f190836449a8921ff73dadfeeb0be22f -->
- Q3 B: the screen calls only the query unit, and that unit starts the MCA load and reports progress. Q4 B: the empty screen uses the same 30 second interval for load progress, and it asks for trains only after the load succeeds. (learned 2026-10-26) <!-- cid:261025-local-timetable-viewer:units-generation:77cf65a39ef1d412adf4b3eae8347d644cbc8f2f45692eb4ddc1874c0050b8b7 -->
- Plan approved. Three units: mca-load and timetable-query share the server container. screen is its own container. The database container is not a unit. It keeps the SQLite file when the server stops, and the file is bind-mounted so a SQL client opens the host path. (learned 2026-10-26) <!-- cid:261025-local-timetable-viewer:units-generation:f28d5f024ff99f23e586bc285b7ee2cbe717444f1038d68059e29e43d7dd19dc -->
- Two inter-unit edges to pin: screen to timetable-query, and timetable-query to mca-load. The SQLite file is the shared store the load writes and the query reads. There is no public API. (learned 2026-10-26) <!-- cid:261025-local-timetable-viewer:contract-design:6db2fb7abfd4813463cf5bb4f8ff903c4e2649fc50680d8a82e118b43387cb20 -->
- Screen to query is REST with JSON bodies as needed and transport compression as needed. Query starts the load by a function call in the same process. Query owns the REST spec. MCA load owns the SQLite file shape. A hang is a failed ask, held trains stay, status bar says comms is lost, no retry. Progress is idle/running/succeeded/failed plus how far and a sentence. No contract version number. (learned 2026-10-26) <!-- cid:261025-local-timetable-viewer:contract-design:d657c96757378a5f571006286287895a02526043725668a3fe2dfac6f91e7521 -->
- Three units in one chain. Serial construction already chosen. Skeleton ceremony is off. First slice is still the database, one view, and enough screen, now with the empty screen and load button. (learned 2026-10-26) <!-- cid:261025-local-timetable-viewer:delivery-planning:d1b1c8d7bfad2ea07e66e74175176f593c720afd716b63258dcf8199acb0c4c1 -->
- Q1 A thin slice is empty screen, load button, pair page. Q2 A no formal score. Q3 B first Bolt cuts across the three units. Q4 A nothing outside holds us. Q5 D nothing in particular. (learned 2026-10-26) <!-- cid:261025-local-timetable-viewer:delivery-planning:d7ce85f45384df08f57755a2838ee90691b4ba825d3ab72e9815852313fe96b2 -->
- Q6 A: one Bolt holds all three units. Done includes the network page. No later Bolt. (learned 2026-10-26) <!-- cid:261025-local-timetable-viewer:delivery-planning:300fd2b13fd9d9f482de8c8b133d6c621cb19e4f429889bba134dcb209ebd041 -->
- Construction check deferred until the first unit is built. No command recorded. (learned 2026-10-26) <!-- cid:261025-local-timetable-viewer:delivery-planning:fc4c6cddc41158548a8165e83b1a919b03caf364c41fe3f8d3656d15b14c397b -->
- Construction is staffed from this session. One person, one chain. No separate team owns a unit. (learned 2026-10-26) <!-- cid:261025-local-timetable-viewer:delivery-planning:eb937ee7057da7ac26772ac391c2e7593ce6bbbb0faadb3f82a0944f57c3512c -->
- Modify: keep the existing FR1–FR6 numbers and the three views (learned 2026-10-27) <!-- cid:261025-local-timetable-viewer:requirements-analysis:c4b68aebe8ef8220c787f80b654198ff9e85260bd8dc02e8abcae89aeaec8940 -->
- Chat: apply all seven change items (MCA writer, empty screen, split never-writes, done includes network, FR7–FR9, held-answer screen rules, unique day's MCA). Live-sched is not the writer. (learned 2026-10-27) <!-- cid:261025-local-timetable-viewer:requirements-analysis:d8429494ba188bb970197906dbb1d6ffaf137abd6b86879a10a4435a9bdd3f75 -->
- Keep after the requirements rewrite leaves the four wide stories as a reader of a pre-filled database. The product lead's advisory review says they do not cover the empty screen or that day's MCA replace. (learned 2026-11-01) <!-- cid:261025-local-timetable-viewer:user-stories:ab3bbd0e4af9808df6cd7f28ff5bd4ce45c8e88f11dc342c5906bc8cf576ff05 -->
- Keep: accept the existing stories, personas, assessment, and traceability as they are after the requirements rewrite. No regeneration. (learned 2026-11-01) <!-- cid:261025-local-timetable-viewer:user-stories:29a8b208383a5a487db364180f3145abfdd1bed92e96bb4f3d9c660957a4f3a5 -->
- Modify after FR7–FR9: TimetableQuery starts McaInitialisation in-process and owns LoadProgress; NetworkPage owns empty screen, load button, and RefreshInterval validation; US2.1 traces to NetworkPage; US3.1 reader unchanged traces to TimetableQuery. (learned 2026-11-01) <!-- cid:261025-local-timetable-viewer:domain-design:1e366fb0f48115824edffe6b6a5fa26fa927261b115ab19fde220f32a8e1b4c6 -->
- Plan approved. Three units: mca-load and timetable-query share the server container. screen is its own container. The database container is not a unit. (learned 2026-11-01) <!-- cid:261025-local-timetable-viewer:units-generation:a74a12d6b54c224f24a81ee9611d7cc7abdb3811f732412c074a5529c3b76871 -->
- Modify after domain design: same three-unit chain; component mapping, comms is lost and interval refusal on screen, traceability U1–U3, db packaging under apps/local-timetable/db. (learned 2026-11-01) <!-- cid:261025-local-timetable-viewer:units-generation:84d94473975c0136e3f4ac7a93cc721b8152285373998acba929926a6adc23c1 -->
