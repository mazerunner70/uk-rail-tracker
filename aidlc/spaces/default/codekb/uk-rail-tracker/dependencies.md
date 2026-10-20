# Dependencies — uk-rail-tracker

Library versions: see `technology-stack.md`.

## External systems

| Dependency | Consumer | Protocol |
|------------|----------|----------|
| RDM OpenLDBWS | Darwin LDB Clients | HTTPS + `x-apikey` |
| RDM Darwin PubSub | Kafka PubSub POC; Live-sched consume; Schedule harvest optional | Kafka SASL_SSL / PLAIN |
| National Rail CIF MCA | Schedule POC; Live-sched `load`; Timetable samples | File stream (`RJTTF*.txt` / `.mca`). Default live-sched path `server/poc/timetable/RJTTF944MCA.txt` |
| Daily CFA update zip | Live-sched `cfa-update` | Local `*.zip` in `CFA_WATCH_DIR`, stdlib `zipfile`, one CFA member. Not an object-store notification |

## Internal cross-package

```text
android/:app
  └── copies stations.xml → assets (preBuild)
  └── Darwin LDB Clients (in-module)

server/poc/live-sched (live_sched)
  └── sqlite3 schema via db/repository (seven tables, one file)
  └── dotenv → workspace-root .env
  └── confluent-kafka → Darwin PubSub (consume only; lazy import)
  └── stdlib zipfile → CFA inbox (cfa-update)
  └── demo and tests via the package install
  └── scripts/compare_mca_to_db.py duplicates loader flush rules and is excluded from the distribution
  └── does NOT import schedule_poc
  └── no in-repo Python outside this tree imports live_sched

server/poc/schedule (schedule_poc)
  └── sqlite3 schema via db/repository
  └── optional Kafka overlay (classify/harvest/replay)
  └── demo/tests import package via pythonpath
  └── not opened this run

server/poc/kafka
  └── sibling modules: config, consumer_utils, offset_store
  └── no packaging; not imported as a published lib by schedule or live-sched
  └── not opened this run; live_sched/config.py names it as the credential-name source

server/poc/timetable
  └── design/sample only — no code dependency edge into schedule/live-sched
  └── DATA-MODEL.md opening sections skimmed this run

server/PLAN.md
  └── design reference only — no runtime import
```

## Build config files

- Android: `android/{settings,build,app/build}.gradle.kts`, `gradle.properties`, wrapper props (prior scan)
- Live-sched: `server/poc/live-sched/pyproject.toml` (setuptools; no Makefile, Dockerfile, tox, ruff, or mypy in-tree)
- Schedule: `server/poc/schedule/{pyproject.toml,requirements.txt}` (prior scan)
- Kafka: `server/poc/kafka/requirements.txt` (prior scan)
- Typecheck: `server/pyrightconfig.json` (**kafka only** — live-sched excluded; prior scan)
- Ignore: root `.gitignore` (CIF dumps, AI-DLC machine-local, `.env` patterns). Live-sched `.gitignore` excludes SQLite, CSV, and `output/db-changes.md`.

## Secrets hygiene

Kafka, schedule, and live-sched `.env` files are gitignored and must not be committed. Android API keys sit in `local.properties` (prior scan). Live-sched README: project-local `.venv` under `server/poc/live-sched`; do not install into the system Python. Required consume secrets are `KAFKA_CONSUMER_KEY` and `KAFKA_CONSUMER_SECRET` (SASL PLAIN over SSL). They are not written to the Kafka CSV.

## Planned (absent)

`server/{api,ingest,infra}` from PLAN Appendix B — no package edges yet. No S3 bucket, Glue job, or Athena workgroup is declared in the live-sched tree.
