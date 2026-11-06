# Build instructions

## Prerequisites

- **Node.js** 22.x (matches `package.json` engines and unit-test instructions; use nvm path if default `node` is older).
- **pnpm** via Corepack (`corepack enable`).
- Repo root: `/home/william/code/personal/2025/uk-rail-tracker`.

## Server package (`apps/local-timetable/server`)

### Install dependencies

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server
corepack enable
pnpm install
```

### Compile (TypeScript)

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server
PATH=/home/william/.nvm/versions/node/v22.18.0/bin:$PATH pnpm run build
```

Expected: `tsc` completes with exit code 0; emits `dist/`.

## Front-end package (`apps/local-timetable/fe`)

### Install dependencies

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/fe
corepack enable
pnpm install
```

### Bundle static assets

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/fe
PATH=/home/william/.nvm/versions/node/v22.18.0/bin:$PATH pnpm run build
```

Expected: `esbuild.config.mjs` completes with exit code 0; writes `dist/`.

## Environment notes

- Ensure **`node` is on PATH** when invoking `pnpm` scripts. If the shell default is Node 20, prepend nvm’s Node 22 bin as in the commands above.
- `pnpm` may warn about ignored build scripts for `esbuild`; builds succeeded in this run with Node 22.

## Troubleshooting

- **`node: not found` during `pnpm run build`:** Export `PATH` to include the Node 22 binary before `pnpm`.
- **`better-sqlite3` native build issues:** Run `pnpm install` from `server/` with the same Node version used for tests.
