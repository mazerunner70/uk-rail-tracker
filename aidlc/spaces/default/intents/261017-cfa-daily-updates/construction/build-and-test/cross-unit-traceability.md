# Cross-Unit Traceability — CFA Daily Updates

**Verdict**: PASS  
**Scope**: zero-Unit / stage-level Code Generation  
**Source coverage**: `construction/code-generation/traceability.json`  
**Stories**: user-stories stage skipped — no AC IDs to enumerate

## Enumerated requirements

| ID | Coverage status | Owning stage / Unit | Target file | Target exists |
|----|-----------------|---------------------|-------------|---------------|
| FR1 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cli.py` | Yes |
| FR1.1 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cli.py` | Yes |
| FR1.2 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/config.py` | Yes |
| FR1.3 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cfa_zip.py` | Yes |
| FR1.4 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cfa_apply.py` | Yes |
| FR2 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/feed_sequence.py` | Yes |
| FR2.1 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/feed_sequence.py` | Yes |
| FR2.2 | OK | code-generation / stage-level | `server/poc/live-sched/tests/test_feed_sequence.py` | Yes |
| FR2.3 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/feed_sequence.py` | Yes |
| FR2.4 | OK | code-generation / stage-level | `server/poc/live-sched/tests/test_cfa_apply.py` | Yes |
| FR2.5 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cfa_apply.py` | Yes |
| FR3 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cfa_apply.py` | Yes |
| FR3.1 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cli.py` | Yes |
| FR3.2 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cli.py` | Yes |
| FR3.3 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/repository.py` | Yes |
| FR4 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cfa_report.py` | Yes |
| FR4.1 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cfa_report.py` | Yes |
| FR4.2 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cfa_apply.py` | Yes |
| FR4.3 | OK | code-generation / stage-level | `server/poc/live-sched/tests/test_cfa_cli.py` | Yes |
| FR5.1 | OK | code-generation / stage-level | `server/poc/live-sched/tests/test_cfa_apply.py` | Yes |
| NFR1 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cfa_apply.py` | Yes |
| NFR2 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cfa_report.py` | Yes |
| NFR3 | OK | code-generation / stage-level | `server/poc/live-sched/live_sched/cfa_apply.py` | Yes |
| NFR4 | OK | code-generation / stage-level | `.env.example` | Yes |

## Uncovered elements

None.

## Notes

- FR5.1 demo evidence in Build and Test used synthetic CFA/zip fixtures (no real Network Rail zip in workspace); see `test-results.md`.
