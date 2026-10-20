# Security Test Instructions

## Status

Lightweight checklist only (Minimal / poc). No SAST/DAST suite.

## Checklist

| Check | How |
|-------|-----|
| Secrets not committed | Root `.env` gitignored; `.env.example` placeholders only |
| No `schedule_poc` import | `rg schedule_poc server/poc/live-sched/live_sched server/poc/live-sched/tests` empty |
| CFA path does not wipe DB | `cfa-update` never unlinks SQLite (contrast MCA `load`) |

## Commands

```bash
rg -n 'schedule_poc' /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched/live_sched \
  /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched/tests || true
```
