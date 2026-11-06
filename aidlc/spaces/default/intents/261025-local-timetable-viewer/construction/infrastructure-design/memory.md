<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
2026-10-27T19:52:00Z — Infrastructure questions for mca-load are compose-file location and what process holds the database container's bind-mount. No cloud, no CI, no SQLite network server unless chosen.
2026-10-27T19:55:00Z — Q1 A: compose file is `apps/local-timetable/compose.yaml`. Q2 A: the database container is a small keep-alive (`alpine:3.20` with `sleep infinity`) that only holds the bind-mount; no SQLite network server. In-container mount paths are left to Code Generation; host paths stay `apps/local-timetable/mca` and `apps/local-timetable/db/timetable.sqlite`.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
2026-10-27T19:55:00Z — Option A names a class of image, not a tag. Locked `alpine:3.20` + `sleep infinity` as the keep-alive so Code Generation is not left to pick pause vs busybox vs alpine. The host bind-mount is what survives a rebuild; the keep-alive service exists so the three-container layout stays up when the server container stops.

## Open questions
2026-10-27T20:00:23Z — Advisory R-01 Minor: compose volume sources should be `./mca` and `./db` beside `apps/local-timetable/compose.yaml`, not the repo-root-relative paths copied into the volume line. Left on the record; not repaired this pass.
