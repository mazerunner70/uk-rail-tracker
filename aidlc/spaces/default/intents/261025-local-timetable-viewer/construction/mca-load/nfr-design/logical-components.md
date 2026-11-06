# Logical components — mca-load

Where the NFR patterns sit. This unit runs in the server container. The SQLite file lives in the database container. There is no AWS plane.

## Components

| Component | Failure domain | What it does | Blast radius |
| --- | --- | --- | --- |
| LoadProgressStore | server process | Holds the one LoadProgress value. `start` and `progress` read it. | This process. Death resets to idle. |
| DayMcaLocator | host path `apps/local-timetable/mca` | Finds the unique filename containing today's `YYYYMMDD`. | Fail before replace. SQLite unchanged. |
| HeaderGate | same stream | Reads CIF columns 33–39. Refuses a bad sequence. | Fail before replace. SQLite unchanged. |
| McaStream | same stream | After unlink, reads remaining bytes. howFar = bytes read / file size. Filters London Midland as journeys appear. | Memory stays bounded. |
| SqliteReplacer | host path `apps/local-timetable/db/timetable.sqlite` | Immediately after the header is accepted: unlink the old file, then write the seven tables as journeys arrive from McaStream. | After unlink, a write fail is a missing file until the next successful load. |

Shared with the query (not this unit's writer): the same `timetable.sqlite` path, opened read-only by the query.

## Isolation

- MCA directory and SQLite file are separate bind-mounts.
- This unit does not listen on the Docker network.
- Tests use a synthetic MCA and a throwaway SQLite path, so they cannot unlink the host file.

```mermaid
flowchart LR
    Query[TimetableQuery] -->|start progress| Store[LoadProgressStore]
    Store --> Locator[DayMcaLocator]
    Locator --> Gate[HeaderGate]
    Gate --> Unlink[SqliteReplacer unlink]
    Unlink --> Stream[McaStream and table write]
    Stream --> File[timetable.sqlite]
```

Text fallback: The query calls start and progress on the in-process store. A load locates today's MCA, checks the header, deletes the old SQLite file, then streams the remainder into a new `timetable.sqlite`. A refuse or miss stops before any delete.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/tech-stack-decisions.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/performance-requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/security-requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/scalability-requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/reliability-requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/observability-requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/domain-design/components.md`

## Assumptions & Open Questions

None.
