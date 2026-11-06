# CI/CD pipeline — mca-load

There is no continuous integration and no hosted deploy for this service. Delivery is: build and start the three containers on this machine from `apps/local-timetable/compose.yaml`.

## Pipeline

| Stage | What runs | Gate |
| --- | --- | --- |
| Hosted CI | none | The tree has no `.github/workflows` for this service. None is added. |
| Environment promotion | none | No `dev` / `staging` / `prod`. |
| Blue-green / canary / rolling | none | One compose project on this machine. |
| Artifact registry | none | Images are built locally by Compose. |
| Secrets in CI | none | This unit has no Kafka or Darwin secrets. Compose does not inject `.env` into mca-load. |

## Local delivery

1. Put today's MCA under host `apps/local-timetable/mca` (unique filename containing today's `YYYYMMDD`).
2. From the repo: `docker compose -f apps/local-timetable/compose.yaml up --build`.
3. The empty screen's load button starts this unit. Progress is polled through the query.
4. A change is a new build of the containers together. There is no version number and no rollback procedure beyond starting the previous compose build the person still has.

## Tests in this unit

TypeScript tests with a synthetic MCA and a throwaway SQLite path. They run on the developer machine, not in a pipeline. They must not unlink host `apps/local-timetable/db/timetable.sqlite`. The exact runner is chosen at Code Generation.

Local secret scanning, dependency checks, and a dynamic check of this TypeScript unit exist as NFR5.1. They are not a CI job.

## Rollback

Stop the server container, keep `db` and the host file, fix, rebuild `server`. If `timetable.sqlite` is missing after a failed replace, put a day's MCA in `apps/local-timetable/mca` and start the load again. There is no automated rollback of the SQLite file.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/security-design.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/reliability-design.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/tech-stack-decisions.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/domain-design/components.md`

## Assumptions & Open Questions

None.
