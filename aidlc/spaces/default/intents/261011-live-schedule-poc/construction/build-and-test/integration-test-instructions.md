# Integration Test Instructions

## Applicability

**Not generated for execution under Minimal test strategy.**

Unit and package-local boundary coverage for this POC lives in Code Generation
(`server/poc/live-sched/tests/`). There is no cross-unit DAG and no HTTP/service
boundary in scope.

## Deferred / out of scope

- Live Kafka broker integration (ops path: `live-sched consume`)
- Full multi-GB MCA load in CI
- Cross-package integration with `schedule_poc` (forbidden by FR1.3)
