# Integration Test Instructions

## Applicability

**Not generated for execution under Minimal test strategy.**

Unit and package-local boundary coverage for this POC lives in Code Generation
(`server/poc/schedule/tests/`). There is no cross-unit DAG and no HTTP/service
boundary in scope.

## Deferred / out of scope

- Cross-service integration against live RDM Kafka or CIF SFTP
- Android client integration
- AWS-hosted schedule service contracts
