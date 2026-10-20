# Security Test Instructions

## Applicability

**No separate SAST/DAST suite under Minimal test strategy** for this offline POC
(no HTTP API, no auth surface, no cloud deploy in scope).

## Lightweight checks performed at Build and Test

- Confirm `.env.example` documents placeholders only (no live secrets committed)
- Confirm pass/fail path uses local fixtures/DB only (NFR1)
- Confirm dependency surface is `pytest` only for the package tests

## Deferred / out of scope

- Full SAST/DAST pipeline gates
- Dependency CVE CI gate beyond noting the tiny requirements set
- Auth/injection testing of an API that is explicitly out of scope
