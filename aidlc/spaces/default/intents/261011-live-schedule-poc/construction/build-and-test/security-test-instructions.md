# Security Test Instructions

## Applicability

Minimal strategy: no SAST/DAST suite. Lightweight checklist only (executed
manually during Build and Test).

## Checklist

1. Workspace-root `.env` remains gitignored (`**/.env`).
2. Root `.env.example` uses placeholders only (no live secrets).
3. Package code does not hardcode Kafka credentials.
4. Package does not import `schedule_poc` (isolation / blast-radius bound).
5. Unit tests do not require network or live credentials.

## Commands / checks used

```bash
rg -n "KAFKA_" .env.example
git check-ignore -v .env
rg -n "schedule_poc|from schedule" server/poc/live-sched/live_sched/ server/poc/live-sched/tests/
```
