# Infrastructure specification — mca-load

Local Docker on this machine. No AWS, no VPC, no hosted database. The compose file is `apps/local-timetable/compose.yaml`. This unit runs inside the server container.

## Deployment

| Facet | Choice | Rationale |
| --- | --- | --- |
| Compute model | Three Docker Compose services on this machine: `db`, `server`, `screen` | Locked layout. This unit lives in `server` with the query. |
| Networking topology | One Compose default network. This unit binds no port. The query (same container) listens as `http://server:8080` for the screen only. `db` publishes nothing. | NFR1.7: mca-load has no public listener. No ingress, no TLS terminator, no load balancer. |
| Storage strategy | Host bind-mounts, not named volumes. In `compose.yaml`, volume sources are `./mca` and `./db` (directories beside that file, i.e. repo paths `apps/local-timetable/mca` and `apps/local-timetable/db`). Mount `./mca` into `server` read-only and `./db` into `server` read-write and into `db` so the mount stays attached when `server` stops. File name `timetable.sqlite`. | Compose resolves relative paths from the compose file directory; repo-relative paths must not be copied literally into volume lines. |
| Environments | None | No `dev` / `staging` / `prod`. One compose file, this machine. |
| IaC approach | The compose file is the definition. Written at Code Generation. No CDK, Terraform, or CloudFormation. | Local only. |
| Resource sizing | No CPU or memory reservations. Stream the day's MCA; do not size for a whole-file buffer. | NFR1.3, NFR1.12. One person, this machine. |
| Time zone | `TZ=Europe/London` on `server` (and `db` if it ever prints dates) | Today's `YYYYMMDD` and service_day follow the machine's UK clock. |
| Restart | `unless-stopped` on all three services | A Docker daemon reboot brings the three containers back. Process death in `server` still clears the in-process load flag (NFR1.13). The SQLite file remains on the host bind-mount. |

## Infrastructure Services

| Service | Role | Configuration | Notes |
| --- | --- | --- | --- |
| `db` | database (file holder, not a server) | Image `alpine:3.20`. Command `sleep infinity`. Bind-mount `./db` from the compose file directory. No SQLite package, no published port, no healthcheck that talks SQL. | Q2 A. Keeps the three-container layout when `server` is down. Durability is the host file, not this process. |
| `server` | application compute | TypeScript runtime image chosen at Code Generation. Bind-mounts: `./mca` read-only, `./db` read-write. Contains mca-load and timetable-query. | Only this unit unlinks or creates `timetable.sqlite`. Exact in-container paths are chosen at Code Generation; the process still only opens those two trees. |
| `screen` | application compute | Named here because compose starts the three together. Image and ports belong to the screen unit. | Does not mount MCA or SQLite. Calls the query over the Compose network. |
| Cache | none | — | No ElastiCache, Redis, or in-memory cache service. |
| Queue | none | — | No Kafka, SQS, or local broker for this unit. |
| Search | none | — | — |
| CDN | none | — | Desktop only, this machine. |
| DNS | none | Compose service names (`server`, `db`, `screen`) | No Route 53, no public hostname. |
| Load balancer | none | — | One `server` replica. |

## Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
| --- | --- | --- | --- |
| `apps/local-timetable/compose.yaml` | mca-load specifies location and the `db` / `server` mounts this unit needs | timetable-query (same `server` service), screen (`screen` service) | This machine. One file starts all three. |
| Host path `apps/local-timetable/db/timetable.sqlite` | mca-load (writer) | timetable-query (read-only, same container); a SQL client on the host is not a published contract | `server` read-write bind-mount; `db` holds the same host directory; `screen` does not mount it. |
| Host path `apps/local-timetable/mca` | mca-load | none | `server` read-only bind-mount. Unique filename containing today's `YYYYMMDD`. |
| Compose default network | shared by the three services | screen → `http://server:8080`; mca-load uses no network call | No published port for this unit. No cloud VPC. |

A person watching the load uses `docker compose -f apps/local-timetable/compose.yaml logs -f server`. That is optional stdout (NFR1.15), not a metrics backend.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/performance-design.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/security-design.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/scalability-design.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/reliability-design.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/observability-design.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/logical-components.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/infrastructure-design/infrastructure-design-questions.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/domain-design/components.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`

## Assumptions & Open Questions

None. `alpine:3.20` is the concrete keep-alive for Q2 A. In-container mount paths wait for Code Generation.
