<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
- 2026-10-17T18:05:00Z — HD sequence from Current File Reference only (cols 33–39); refuse when that field has no three digits rather than guessing from the zip basename.

## Deviations
- 2026-10-17T18:05:00Z — FR5.1 real update zip demo left for Build and Test / manual ops; unit tests use synthetic CFA/zip fixtures as the plan allowed.

## Tradeoffs
- 2026-10-17T18:05:00Z — CFA apply duplicates CIF schedule parsing instead of sharing with cif_loader to keep MCA full-load and incremental paths independent for this PoC.

## Open questions
- 2026-10-17T18:05:00Z — Confirm real CFA HD Current File Reference embeds the 3-digit sequence the same way as the synthetic TTIS{nnn} fixtures when a live zip is available.
