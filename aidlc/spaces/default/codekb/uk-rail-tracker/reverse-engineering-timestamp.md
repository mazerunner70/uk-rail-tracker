# Reverse Engineering Timestamp — uk-rail-tracker

## Run record

- **Performed**: 2026-10-25
- **Intent**: `local-timetable-viewer`
- **Depth**: Standard
- **Project type**: Brownfield
- **Store mode**: Focused merge of a STALE store. Prose outside `server/poc/live-sched` is preserved. Prose about that path is updated from the 2026-10-25 scan and still carries 2026-10-20 detail this scan did not re-read, labeled where that matters. `analyzed.paths` and `analyzed.components` are this run only.
- **Prior analyzed path**: `server/poc/live-sched`. This run verified that same directory, so it remains the only analyzed path. It is not copied into `shallow.paths`, because listing it there would mark the verified directory as skim-only. Prior shallow paths are kept. This run adds `server/poc/live-sched/fixtures/`, `server/poc/live-sched/data/`, `server/poc/live-sched/output/`, and `server/poc/live-sched/docs/`. `server/poc/live-sched/.venv/`, `server/poc/live-sched/.pytest_cache/`, and `server/poc/schedule/` were already shallow. The repeated `server/poc/schedule/` entry in the prior block is kept once.
- **Pre-scan store_generation**: `sha256:125731cd588e65aa541d751cf6f93b0f83e2fad194ce475567120ae7e4a9a540`
- **Pre-scan source_fingerprint**: `git:99dd493302d01daca4b62fdaa26dee42ac41aca2`
- **Commit**: `99dd493302d01daca4b62fdaa26dee42ac41aca2`, read from that snapshot fingerprint. This link did not run `git rev-parse`.
- **Developer scan handoff**: `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/reverse-engineering/developer-scan.md`
- **Synthesizing agent**: aidlc-architect-agent
- **Conversation language**: English
- **Workflow language note**: the workflow language list records TypeScript. The deep-scanned tree is Python `>=3.12`.
- **Constraint**: do not import `schedule_poc` from `live-sched` or from a successor package. `server/poc/schedule` was not opened.
- **Mint command**: `aidlc engine workspace codekb-scope-diff --mint --paths server/poc/live-sched`
- **Mint result**: `99dd493302d01daca4b62fdaa26dee42ac41aca2`

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: local-timetable-viewer
fingerprint: 99dd493302d01daca4b62fdaa26dee42ac41aca2
analyzed:
  paths:
    - server/poc/live-sched
  components:
    - Live-sched POC
shallow:
  paths:
    - ./
    - docs/
    - android/docs/
    - android/app/src/main/java/com/ukrailtracker/app/ui/
    - android/app/src/main/java/com/ukrailtracker/app/domain/
    - android/app/src/test/
    - server/
    - stations.xml
    - aidlc/
    - .cursor/
    - server/poc/kafka/.venv/
    - server/poc/kafka/.offsets/
    - server/poc/timetable/
    - server/poc/schedule/
    - android/
    - server/poc/live-sched/.venv/
    - server/poc/live-sched/live_sched.egg-info/
    - server/poc/live-sched/.pytest_cache/
    - server/poc/timetable/DATA-MODEL.md
    - server/poc/kafka/
    - server/poc/live-sched/fixtures/
    - server/poc/live-sched/data/
    - server/poc/live-sched/output/
    - server/poc/live-sched/docs/
```
