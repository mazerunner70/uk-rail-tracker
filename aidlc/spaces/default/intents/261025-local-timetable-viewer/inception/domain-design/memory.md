<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
2026-10-25T20:18:00Z — MFA was a typo for MCA. Four blocks: McaInitialisation replaces the SQLite file from that day's MCA and applies no CFA; TimetableQuery owns the two answers and only reads; NetworkPage and StationPairPage call only TimetableQuery.
2026-10-25T20:18:00Z — NetworkPage asks on open and on each interval even when the station boxes are empty. StationPairPage asks only when both boxes are non-empty. Unmatched codes are a successful empty list.

## Deviations
2026-10-25T20:18:00Z — The earlier rule that the reader never rewrites SQLite still binds TimetableQuery. McaInitialisation is the exception and replaces the file.

## Tradeoffs
2026-10-25T20:18:00Z — The pair page reads StationSelection and RefreshInterval owned by NetworkPage. That reference is not a call, so the call graph stays acyclic.

## Interpretations
2026-11-01T09:25:00Z — Modify after FR7–FR9: TimetableQuery starts McaInitialisation in-process and owns LoadProgress; NetworkPage owns empty screen, load button, and RefreshInterval validation; US2.1 traces to NetworkPage; US3.1 reader unchanged traces to TimetableQuery.

## Open questions

