# Local timetable — AWS deployment plan

**Status:** **Approved** (2026-11-15) — buildout via **Terraform**  

**Date:** 2026-11-15  
**Region:** `eu-west-2` (London) — **confirmed**  
**Authority:** [ADR-002](https://mazer92.atlassian.net/wiki/spaces/URT/pages/98371/ADR-002+Least-cost+AWS+deployment+for+always-on+server+and+Darwin+Kafka+client) (Confluence)

This plan turns ADR-002 into a buildable checklist. It is intentionally sequential so we can validate cost, auth, and path mapping before any long-lived spend.

**IaC:** `apps/local-timetable/deploy/aws/terraform/` — all AWS resources are created with Terraform (`terraform apply`). Do **not** create stack resources with ad-hoc `aws` CLI. On-box helpers only: `deploy/aws/setup-host.sh`, `deploy/aws/run-server.sh` (AL2023 `docker` RPM; no Compose).

---

## 1. Target architecture

```text
Browser
  → Cognito Hosted UI (login once)
  → https://d….cloudfront.net/           → S3 (screen static, OAC)
  → https://d….cloudfront.net/api/*      → API Gateway HTTP API + JWT
                                           → EC2 t4g.micro :8080 (origin locked; scale-down to nano later)
                                                ├── EBS      /db (timetable.sqlite, network-schematic.json)
                                                ├── Docker   server (+ optional db holder)
                                                └── egress   TLS → Darwin RDM Kafka
                                                     (NOT a CloudFront path)
```

### CloudFront behaviors (locked)

| Path pattern | Origin | Notes |
|---|---|---|
| Default `*` | S3 bucket (FE build) | Assigned `*.cloudfront.net` DNS; no Route 53 in v1 |
| `/api*` | API Gateway HTTP API | FE uses same-origin `/api/…` |
| *(none)* | — | Kafka is **outbound from EC2 only** |

### What stays on EC2 vs what moves off-box

| Today (Compose) | In AWS (revised) |
|---|---|
| `db` volume holder | EBS `/db` (sqlite from `/load`; schematic from Lambda artifact) |
| `server` | Docker on EC2: API + Kafka + CIF→SQLite (`METRO_LAYOUT_ENGINE=external`) |
| Octilinear map | **Python Lambda** → `network-schematic.json` (+ stamped MCA artifact) |
| `screen` nginx `:8081` | **S3 + CloudFront** (not public nginx) |

```text
s3://raildata-ingest-0701//timetable/timetable_full.zip
  → Python Lambda (arm64, ~3 GB, ≤15 min)
       1. GetObject zip (only Lambda reads reception zip)
       2. extract + stamp MCA (London LastModified + 1 day)
       3. MILP + post-process → network-schematic.json (stations, corridors, lines)
       4. put s3://urt-lt-mca-*/artifacts/{YYYYMMDD}/{MCA,network-schematic.json,manifest.json}
  → SSM apply-mca-artifacts.sh on EC2 (ordered):
       1. cp MCA → /mca
       2. POST /load (sqlite only)
       3. on success: cp schematic → /db/network-schematic.json

Manual: invoke with { "mcaS3Uri": "s3://…" } then same apply path.
Ops: `pnpm schematic-process:aws` (hash-skip; `--force` / `--invoke`); also run from `rebuild-and-deploy-all:aws`. See `scripts/README.md`.
```

---

## 2. Cost envelope (verify before create)

Assumptions: always-on, 730 h/mo, one public IPv4, 8 GB gp3 root, hobby API volume, CloudFront Free, no Route 53 / NAT / ALB.

**Instance choice: `t4g.micro`** (1 GiB) — enough for Docker + Node + Kafka once MCA parse/MILP leave the box. Optional later scale-down to `t4g.nano` after a proving week. **Do not** size EC2 for HiGHS; that runs in Lambda.

| Resource | Spec | Est. US$/mo (micro) | Est. after scale-down (nano) |
|---|---|---|---|
| EC2 | `t4g.micro` → optional `t4g.nano` | ~6.86 | ~3.43 |
| EBS | 8 GB gp3 root | ~0.74 | ~0.74 |
| Public IPv4 | 1 address | ~3.65 | ~3.65 |
| Cognito | 1 MAU Essentials/Lite | 0.00 | 0.00 |
| API Gateway HTTP API | ~100k req | ~0.12 | ~0.12 |
| CloudFront Free + S3 | assigned DNS | ~0.00 | ~0.00 |
| **Total** | | **~$11.40** | **~$7.90–8.00** |

**Do not create:** NAT Gateway, ALB/NLB, RDS, EKS, Secrets Manager (prefer SSM Parameter Store / env file on instance).

---

## 3. Gaps vs current code (must fix in buildout)

These are known mismatches; the plan schedules them before cutover.

1. **API path prefix** — Node serves `/load`, `/network`, `/network-map`, `/pair` today (no `/api` prefix). FE `createApiClient(baseUrl)` already supports a base URL.

   **Approach:** FE `baseUrl = "/api"`; API Gateway route `/api/{proxy+}` with parameter mapping so origin sees `/{proxy}` (no Node path rewrite required). Confirm strip/mapping in the HTTP API integration.
2. **Auth** — No Cognito/Bearer handling in FE or Node today.

   **Approach (decided):** **Gateway JWT + Node Cognito JWT** — FE sends `Authorization: Bearer` after Hosted UI; API Gateway JWT authorizer validates first; Gateway **forwards** the same header to EC2; Node verifies Cognito JWTs (JWKS) again so a direct hit on `:8080` still needs a valid user token. **No shared-secret header.**
3. **Screen packaging** — FE is Compose/nginx today.

   **Approach:** static build → S3; CloudFront default origin.
4. **MCA intake + layout** — **Python Lambda schematic** + **EC2 sqlite** (single MCA artifact).

   **Decided (2026-11-16):**
   1. **Trigger** — S3 ObjectCreated on owned `raildata-ingest-0701` (`/timetable/*.zip`) → schematic Lambda. ETag dedupe via `artifacts/latest/manifest.json`.
   2. **Python Lambda** (`server/lambda/schematic_process/`): GetObject zip → stamp MCA → MILP + corridor/line post-process → upload `artifacts/{YYYYMMDD}/{MCA,network-schematic.json,manifest.json}`. **Only Lambda** reads the reception zip. Solver ≤14 min; function 15 min / 3008 MB / 10 GB `/tmp`.
   3. **EC2 ordered apply** (`deploy/aws/apply-mca-artifacts.sh` via SSM): pull MCA → `/mca` → `POST /load` (sqlite; `METRO_LAYOUT_ENGINE=external`) → on success pull schematic → `/db`.
   4. **Manual:** invoke with `{ "mcaS3Uri": "s3://…" }`; ops `pnpm schematic-process:aws` (folded into `rebuild-and-deploy-all:aws` with source-hash skip).
   5. **Local Compose:** `milp`/`geo` unchanged.
   6. **Later:** CFA; Fargate if Lambda times out.

   **Non-goals:** always-on larger EC2 for MILP; EC2 re-download of reception zip on the daily path.

5. **Secrets** — `KAFKA_`* from workspace `.env`.

   **Approach:** SSM Parameter Store (SecureString) or encrypted file on EBS; inject at container start. Not Secrets Manager in v1.

---

## 4. Phased delivery

### Phase 0 — Prerequisites (no AWS spend yet)

- [ ] Confirm AWS account, IAM user/role for deploy, billing alarm (e.g. $15–20).
- [ ] Confirm RDM Kafka credentials still valid; note group id exclusivity (stop local `live-sched` / local server when cloud consumes).
- [ ] Freeze ADR-002 acceptance (or note deltas here).
- [x] Decide AMI: Amazon Linux 2023 **arm64** (Graviton), **standard** (not Minimal).
- [x] Docker on box: Amazon **`docker` RPM** via `dnf` (`systemctl enable --now docker`). Plain `docker build` / `docker run` only — **no Compose required** in v1; no docker.com Engine install.
- [x] Image delivery (v1): **rebuild on box** from `apps/local-timetable/server/Dockerfile` (ECR later if updates get annoying).
- [x] Host process: small `run-server.sh` (`docker build` / `docker run --restart unless-stopped`); no Compose in v1.
- [x] Sketch resource names: `urt-lt-*` (see Terraform `local.name` / buckets `urt-lt-{fe,mca,deploy}-<account>`).
- [x] IaC: Terraform under `deploy/aws/terraform/` (not imperative CLI).

### Phase 1 — Account guardrails

- [ ] Budget / billing alarm.
- [ ] Optional: AWS Free Tier / credits awareness (public IPv4 and micro/nano still bill).

### Phase 2 — Network + compute (Kafka-capable box)

- [ ] VPC: default VPC OK for v1 (public subnet).
- [ ] Security group `urt-lt-ec2`:
  - Inbound: **none** from `0.0.0.0/0` on 8080/8081 (SSH optional: your IP `/32` on 22, or prefer SSM only).
  - Outbound: 443 (and whatever RDM Kafka port needs — typically TLS Kafka; confirm from RDM docs).
- [ ] Launch **`t4g.micro`** (initial), arm64, 8 GB gp3, **Elastic IP** (accepted ~$3.65/mo IPv4).
- [ ] IAM instance role: SSM Session Manager, read SSM params, read **MCA zip S3 bucket**, optional CloudWatch agent.
- [ ] Install Docker via AL2023 `dnf install docker`; enable/start the service; create directories `/db`, `/mca` on the root volume.
- [ ] **Checkpoint:** SSM shell works; instance can reach internet (`curl` outbound).

### Phase 3 — Server container on EC2

- [ ] On-box: get server sources onto the instance → `docker build` from `apps/local-timetable/server/Dockerfile` (no ECR in v1).
- [ ] `docker run` `server` with mounts `/db`, `/mca`, env from SSM/`KAFKA_*`, `TZ=Europe/London` (Compose optional; not required).
- [ ] **Checkpoint (local to instance):** `curl http://127.0.0.1:8080/network` returns JSON (may be empty/failure without MCA — still proves listen).
- [ ] Leave **8080 closed** to the world.

### Phase 4 — Auth front door

- [ ] Cognito User Pool (Essentials or Lite); create **one** user (you); app client for Hosted UI (PKCE / public SPA).
- [ ] Callback / sign-out / allowed origins: placeholder until CloudFront domain exists; update in Phase 6.
- [ ] API Gateway **HTTP API**:
  - JWT authorizer → Cognito issuer / audience.
  - Route `ANY /api/{proxy+}` → HTTP integration `http://<ec2-ip>:8080/{proxy}` (exact mapping to be validated).
  - **Forward** `Authorization` to the origin (required for Node JWT check).
- [ ] Node: Cognito JWT verification middleware (JWKS) on API routes — **code change**; reject missing/invalid Bearer.
- [ ] **Checkpoint:** without token → 401 at Gateway; forged/direct call to `:8080` without valid JWT → 401 from Node; with valid token → 200/503 from Node.

### Phase 5 — Static FE + CloudFront

- [ ] S3 bucket for FE assets (block public access; OAC only).
- [ ] Build FE with API base `/api`; wire Cognito login (Hosted UI) + Bearer on `fetch`.
- [ ] CloudFront Free distribution:
  - Alternate domain: **none** (use assigned `d….cloudfront.net`).
  - Default `*` → S3 OAC.
  - `/api*` → API Gateway origin (HTTPS).
  - Caching: static long TTL; `/api*` caching **disabled**.
- [ ] Update Cognito callbacks to `https://d….cloudfront.net/…`.
- [ ] **Checkpoint:** open CloudFront URL → login → UI loads from S3 → `/api/network` authorized → data or expected empty/error without MCA.

### Phase 6 — Data path (MCA + Kafka) — **Lambda schematic + EC2 sqlite**

- [x] Reception object locked: `s3://raildata-ingest-0701//timetable/timetable_full.zip` (key `/timetable/timetable_full.zip`).
- [x] Python package: `server/lambda/schematic_process/` (extract, layout, corridors/lines post-process).
- [x] Terraform: ECR + schematic Lambda + IAM + optional S3 notification on reception bucket.
- [x] EC2: `apply-mca-artifacts.sh` (MCA→`/mca` → `/load` → schematic→`/db`); `METRO_LAYOUT_ENGINE=external`.
- [x] Ops: `pnpm schematic-process:aws` (build/push/invoke; hash skip; called from `rebuild-and-deploy-all:aws`). Scripts guide: `scripts/README.md`.
- [ ] `terraform apply` + first image push; confirm S3 trigger / manual invoke.
- [ ] Enable Kafka env; confirm overlays advance after artifact apply.
- [ ] **Deferred:** CFA; Fargate if Lambda times out; retire zip-centric `ingest-mca-from-s3.sh` as primary.

### Phase 7 — Hardening + ops runbook

- [ ] Document SSM connect, MCA upload, restart compose, log locations.
- [ ] Confirm single consumer: local machines not consuming same group id.
- [ ] Optional: CloudWatch logs for container; disk alarm on EBS.
- [ ] Snapshot EBS once after a successful load (optional ~cents).

---

## 5. Suggested code / config changes (implementation backlog)

| Item | Why |
|---|---|
| FE: Cognito Hosted UI + token storage + `Authorization` header | **Done** (PKCE; ID token Bearer; metas injected on AWS deploy) |
| FE: default `baseUrl` `/api` for Cloud builds | **Done** (`meta api-base=/api`; local nginx proxies `/api/`) |
| FE build → S3 publish script | **Done** (`rebuild-and-deploy-all:aws`) |
| Server: verify Cognito JWT (JWKS) on requests | Origin lock without shared secret; Gateway forwards `Authorization` |
| Server: health path e.g. `/healthz` (optional) | Simpler probes without SQLite |
| Deploy docs: env var list + SSM paths | Match `KAFKA_*`, `METRO_LAYOUT_*` |
| Optional: strip `/api` in Node if Gateway mapping is awkward | Fallback |

---

## 6. Origin lock (decided)

API Gateway → EC2 HTTP integration does **not** give a stable security-group source IP the way an ALB does. IAM cannot restrict TCP to `:8080`.

**v1 (decided):**

1. SG: prefer no world-open 8080; tighten as practical.
2. Prefer **SSM-only** admin (no SSH).
3. **Gateway JWT authorizer** + **Node verifies Cognito JWT** (same Bearer; forwarded). No shared-secret header.
4. Public IPv4 kept for **Kafka egress** and so API Gateway can reach the HTTP origin.

Later (out of v1): VPC Link / private EC2 if we accept NAT/NLB cost.

---

## 7. Acceptance criteria

- [ ] Only Cognito user can call `/api/*` via CloudFront.
- [ ] Static UI served from `https://d….cloudfront.net/` without EC2 nginx exposure.
- [ ] EC2 `:8080` not world-open; Kafka consumer runs and advances offsets after MCA load.
- [ ] Monthly cost within ~$11 (micro) or ~$8 (if scaled to nano); IPv4 + compute dominate; no NAT/ALB.
- [ ] Documented ops path for MCA replace and instance reboot (SQLite on EBS survives).

---

## 8. Explicit non-goals (v1)

- Custom domain / Route 53  
- Multi-AZ, autoscaling, blue/green  
- Android app talking to this API  
- Replacing SQLite with RDS  
- CloudFront path for Kafka

---

## 9. Review checklist (before any `aws` create)

| #   | Question                                       | Decision                                                                                                                      |
| --- | ---------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| 1   | Region `eu-west-2`?                            | **Yes**                                                                                                                       |
| 2   | Accept ~$3.65/mo public IPv4?                  | **Yes**                                                                                                                       |
| 3   | Assigned CloudFront DNS (`d….cloudfront.net`)? | **Yes**                                                                                                                       |
| 4   | API Gateway `/api/{proxy+}` → Node `/{proxy}`? | **Yes**                                                                                                                       |
| 5   | Origin lock                                    | **Gateway JWT + Node Cognito JWT** (no shared secret)                                                                         |
| 6   | Instance size                                  | **`t4g.micro`** (API + Kafka); optional nano after proving week; **not** sized for MILP                                      |
| 7   | MCA delivery                                   | **Python Lambda** owns zip→stamped MCA + schematic artifacts; **EC2** pulls MCA→`/mca`, `/load` sqlite, then schematic→`/db` |
| 8   | Docker / image                                 | **AL2023 `docker` RPM** for API; **ECR** for schematic Lambda                                                                |
| 9   | Layout engine on AWS                           | **MILP in Python Lambda**; EC2 `METRO_LAYOUT_ENGINE=external`                                                                 |

Implementation proceeds Phase 0 → 7 without reopening ADR-002 unless a phase fails a checkpoint.
