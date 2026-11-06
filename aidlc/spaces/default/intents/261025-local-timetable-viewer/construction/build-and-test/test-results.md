# Test Results

## Environment

- **Date:** 2026-11-06 (UTC)
- **Node:** v22.18.0 (`PATH` includes `/home/william/.nvm/versions/node/v22.18.0/bin`)
- **pnpm:** 10.12.1 (Corepack)

## Server (`apps/local-timetable/server`)

### Commands run

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server
pnpm install
pnpm run build
pnpm test
```

### Outcomes

| Step | Result | Notes |
|------|--------|------|
| `pnpm run build` (tsc) | **Pass** | Exit code 0 |
| `pnpm test` | **Pass** | 69 tests, 0 failed (mca-load + timetable-query globs per `package.json`) |

## Front-end (`apps/local-timetable/fe`)

### Commands run

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/fe
pnpm install
pnpm run build
pnpm test
```

### Outcomes

| Step | Result | Notes |
|------|--------|------|
| `pnpm run build` (esbuild) | **Pass** | Exit code 0 |
| `pnpm test` | **Pass** | 9 tests, 0 failed |

## Target Verification Matrix (final)

Aligned with `build-and-test-summary.md`: all executable targets **Met**. No `Not Met` or `Unverified` rows for commands executed in this stage.

## Loop-Back Log

Not used (no failure ladder triggered).
