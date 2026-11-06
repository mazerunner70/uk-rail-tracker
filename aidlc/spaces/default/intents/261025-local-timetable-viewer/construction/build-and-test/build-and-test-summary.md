# Build and Test Summary

## Overview

Build and Test consolidated the three Construction units (**mca-load**, **timetable-query**, **screen**): TypeScript server (`apps/local-timetable/server`), static FE (`apps/local-timetable/fe`), and shared `compose.yaml`. **Node 22.18.0** was used on PATH for `pnpm` scripts and test runners.

- **Build status:** Pass — `pnpm run build` in **server** (`tsc`) and **fe** (`esbuild.config.mjs`) both exit 0.
- **Test status:** Pass — server `pnpm test` (69 tests) and FE `pnpm test` (9 tests) all green on 2026-11-06.
- **Traceability refresh:** Per-unit `construction/*/code-generation/traceability.json` were extended so every enumerated `FR` / `NFR` from `requirements.md` and every three-segment `AC` from `stories.md` has at least one `OK` row across the three units (see `cross-unit-traceability.md`).

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning stage | Verdict |
|---|---|---|---|---|---|---|
| Build server | build-instructions.md | `tsc` exit 0 | exit 0 | This summary + test-results.md | build-and-test | Met |
| Build fe | build-instructions.md | esbuild exit 0 | exit 0 | This summary + test-results.md | build-and-test | Met |
| Server automated tests | unit-test-instructions (all units) + `pnpm test` | All pass | 69 pass, 0 fail | test-results.md | build-and-test | Met |
| FE automated tests | screen unit-test-instructions + `pnpm test` | All pass | 9 pass, 0 fail | test-results.md | build-and-test | Met |
| NFR5 (no CI in this pass) | requirements.md | No pipeline added in Construction | No CI workflow added | requirements.md out-of-scope list | inception | Met (scope) |

Readiness: **build-ready**, **test-ready**. Deployment / production hosting remains **out of scope** per requirements (NFR1).
