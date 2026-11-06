# Performance test instructions

## Strategy applicability

The active test strategy is **Standard** (`aidlc-state.md`). Per Build-and-Test stage rules, **performance-test-instructions** are generated only for **Comprehensive** strategy when NFR performance requirements exist.

This intent defers quantitative load or benchmark checks to a later **performance-validation** operation stage if scheduled. No performance harness was added in Construction.

## When comprehensive

If the strategy is raised to Comprehensive, add commands here for load/latency checks against the Docker local stack and update the Target Verification Matrix in `build-and-test-summary.md`.
