# Reverse Engineering Timestamp — uk-rail-tracker

## Run record

- **Performed**: 2026-10-20T14:16:00Z
- **Intent**: `athena-timetable-lake`
- **Depth**: Standard
- **Project type / scope / test strategy**: Brownfield, classic, Standard
- **Store mode**: Focused merge of a STALE store. Prior prose outside `server/poc/live-sched/` is preserved. `analyzed.paths` and `analyzed.components` are this run only. The store's prior analyzed path is demoted into `shallow.paths`.
- **Pre-scan store_generation**: `sha256:6fb75870d12002f8221c65744946615f4ccc2f3c449063f98f801649422da99e`
- **Pre-scan source_fingerprint**: `git:30d3727199bc6b32fb29187ac5e6e9317229febc`
- **Git commit read at synthesis**: `89d44b5884fd2a46e804e8b3d7189ce5244cea52` (`refs/heads/journey-tracker`, via `.git/HEAD`). This is the branch tip visible while writing. It is not a substitute for the snapshot source fingerprint above, which bounds the developer scan.
- **Developer scan handoff**: `aidlc/spaces/default/intents/261020-athena-timetable-lake/inception/reverse-engineering/developer-scan.md`
- **Synthesizing agent**: aidlc-architect-agent
- **Workflow language note**: the workflow language list records TypeScript. The deep-scanned tree is Python 3.12.
- **Publish note**: `fingerprint` is the verbatim stdout of `aidlc engine workspace codekb-scope-diff --repo uk-rail-tracker --mint --paths server/poc/live-sched/`.

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: athena-timetable-lake
fingerprint: 30d3727199bc6b32fb29187ac5e6e9317229febc
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
    - server/poc/schedule/
```
