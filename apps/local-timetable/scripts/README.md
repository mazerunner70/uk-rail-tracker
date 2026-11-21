# local-timetable scripts

Run from `apps/local-timetable` via the `pnpm` scripts in `package.json`, or call the shell files directly.

## When to use what

### Local Docker Compose (day-to-day dev)

| Script / pnpm | Use when |
| --- | --- |
| `pnpm rebuild-server` | Only the Node server image changed; rebuild that service. |
| `pnpm start-server` | Start/restart the server container without rebuilding. |
| `pnpm rebuild-and-start-server` | Rebuild server image and start it. |
| `pnpm rebuild-and-deploy-all` (alias: `rebuild-and-deploy`) | Full local stack: FE build + rebuild/start db, server, screen. |
| `pnpm mca-load` | Load MCA into the local compose server (optional `MCA_CALENDAR_DATE`). |
| `pnpm logs:server` | Tail server container logs. |
| `show-service.sh` | Debug one `service_guid` against a local `timetable.sqlite` (not wired in package.json). |

`_compose.sh` is shared helpers — do not run it directly.

### AWS — infrastructure

| Script / pnpm | Use when |
| --- | --- |
| `pnpm terraform:aws` | Create/update the AWS stack (EC2, S3, CloudFront, Cognito, ECR, schematic Lambda, …). Interactive apply. |
| `pnpm terraform:aws:auto` | Same with `-auto-approve`. |

One-time / rare: `deploy/aws/bootstrap-tf-backend.sh` (remote state bucket) lives under `deploy/aws/`, not here.

### AWS — application deploy

| Script / pnpm | Use when |
| --- | --- |
| `pnpm rebuild-and-deploy-all:aws` | Ship FE + server to the live stack **and** refresh the schematic Lambda image if its sources changed. Prefer this for a normal “push everything” deploy. |
| `pnpm schematic-process:aws` | Build/push the schematic image to ECR. Updates Lambda code when the function exists; safe for the first push when only ECR is applied. Use `--invoke` after the Lambda exists. |

**Schematic image caching:** `schematic-process-aws.sh` hashes Dockerfile, requirements, Python package, `metro_layout.py`, and `station-geo.json`. If the hash matches `.last-pushed-hash`, it skips docker build/push. Force a rebuild with `--force` or `SCHEMATIC_FORCE=1` when calling the all-aws deploy. Cross-builds arm64 via QEMU + `docker buildx` (builder `urt-lt-arm64`) so amd64 laptops can push Lambda images:

```bash
pnpm schematic-process:aws -- --force
SCHEMATIC_FORCE=1 pnpm rebuild-and-deploy-all:aws
pnpm schematic-process:aws -- --invoke
pnpm schematic-process:aws -- --mca-s3-uri 's3://raildata-ingest-0701//timetable/timetable_full.zip'
```

`--invoke` is **synchronous** and can take up to ~15 minutes (zip download + HiGHS). The script sets a 1000s CLI read timeout so the AWS CLI does not abort early. Watch progress in CloudWatch: `/aws/lambda/urt-lt-schematic-process`. Do not re-invoke while one is still running — timed-out CLI calls leave the Lambda running and retries stack concurrent jobs.


Terraform owns the Lambda **resource** (IAM, memory, trigger). The **container image** is shipped by the script above (not by `terraform apply`).

### AWS — MCA / timetable data

Prefer the Lambda path in production: new reception zip → schematic Lambda → artifacts in S3 → ordered SSM apply on EC2 (MCA + `network-schematic.json`). Use the scripts below for ops/fallback.

| Script / pnpm | Use when |
| --- | --- |
| `pnpm mca-sync:aws` | Pull the reception zip from `raildata-ingest-0701` onto EC2 and trigger load (**EC2 still does layout** unless artifacts were applied separately). Fallback / legacy intake. |
| `pnpm mca-upload:aws` | Push a **local** MCA file to the MCA bucket and SSM-ingest on EC2. Dev/test with a file you already have. |

For layout off-box, invoke the schematic Lambda (`schematic-process:aws -- --invoke` or wait for the S3 ObjectCreated trigger) so EC2 loads with `METRO_LAYOUT_ENGINE=external`.

## Typical sequences

**First AWS bring-up (schematic Lambda)**

1. Create empty ECR: `pnpm terraform:aws -- -target=aws_ecr_repository.schematic -target=aws_ecr_lifecycle_policy.schematic`
2. Build/push image: `pnpm schematic-process:aws`
3. Create Lambda + S3 notification: `pnpm terraform:aws`
4. Deploy app (if needed): `pnpm rebuild-and-deploy-all:aws`
5. Smoke: wait for a new reception zip, or `pnpm schematic-process:aws -- --invoke`

**Routine code change (FE / server / schematic Python)**

- `pnpm rebuild-and-deploy-all:aws` — FE + EC2 server always; schematic image only if hash changed.

**Only schematic / layout code changed**

- `pnpm schematic-process:aws` (add `--invoke` if you also want a reprocess now)

**Only local compose**

- `pnpm rebuild-and-deploy-all`
