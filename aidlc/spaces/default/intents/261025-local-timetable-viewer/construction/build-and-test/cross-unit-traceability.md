# Cross-unit traceability

## Verdict

**PASS.** Every functional requirement id (`FR*`), non-functional requirement id (`NFR*`) listed in `inception/requirements-analysis/requirements.md`, and every three-segment acceptance criterion (`AC*.*.*`) listed in `inception/user-stories/stories.md`, has at least one `coverage` row with `"status": "OK"` across the union of:

- `construction/mca-load/code-generation/traceability.json`
- `construction/timetable-query/code-generation/traceability.json`
- `construction/screen/code-generation/traceability.json`

Target files were validated to exist under the workspace at verification time.

## Coverage notes

- **Split by unit:** MCA load work maps to **mca-load**; read-only HTTP/SQLite query work maps to **timetable-query**; polling/UI behaviour maps to **screen**.
- **Refresh in Build and Test:** `timetable-query` and `screen` traceability files were expanded so previously missing `FR`/`NFR`/`AC` ids from the requirements and stories inventories are explicitly covered (Build and Test reconciliation), without changing application code.

## Reference counts (automated check)

- Requirements `FR`/`NFR` headings: **46** tokens  
- Stories `AC` headings: **23** rows  
- Merged `OK` coverage ids across three units: **73** (includes BR/NFR rows carried by mca-load traceability)

All enumerated ids from requirements and stories are subsets of the merged `OK` set.
