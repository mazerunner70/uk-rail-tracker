# Tech stack decisions — mca-load

Choices that this unit must honour. Language, store, and layout are already locked. This file records them for the load, not for the screen.

## Decisions

| Area | Choice | Why |
| --- | --- | --- |
| Language | TypeScript, functional style where possible | Locked for the server under `apps/local-timetable/server` |
| Store | SQLite file `timetable.sqlite` in the database container, host path `apps/local-timetable/db/timetable.sqlite` | One person, one day's LM, replace-then-read. The file survives a restart and a rebuild of that container. |
| MCA input | Host directory `apps/local-timetable/mca` | Locked. Unique filename containing today's `YYYYMMDD`. |
| Runtime | Same server container as the query. In-process start and progress | The screen never calls this unit. |
| Tests | TypeScript tests with a synthetic MCA | No production MCA, no live broker, no line-coverage percentage gate. The exact runner is chosen at Code Generation. |
| Not used | `schedule_poc`, `live-sched` as this app's writer, Kafka client, Darwin keys, hosted API framework | This unit replaces SQLite from a day's MCA. It does not consume a live feed. |

## Justification against NFRs

- NFR1 / NFR1.5–NFR1.8: TypeScript in a local Docker server container, no public listener.
- NFR2 / NFR2.1: SQLite on a bind-mounted path in its own container.
- NFR3 / NFR3.1: this unit writes; the query only reads.
- NFR4 / NFR4.1: synthetic fixtures.
- NFR5 / NFR5.1: local scans of this TypeScript unit, no CI pipeline.
- NFR6 / NFR6.1: no Kafka or Darwin values in this unit.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/codekb/uk-rail-tracker/technology-stack.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/units-generation/unit-of-work.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`

## Assumptions & Open Questions

None.
