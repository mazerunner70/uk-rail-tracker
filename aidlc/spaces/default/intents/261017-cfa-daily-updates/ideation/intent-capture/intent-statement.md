# Intent Statement

## Problem Statement

Live-sched can load schedules, but the daily incremental CFA update path is missing: operators lack a reliable way to take a daily update zip from a watched folder, confirm it is the correct next 3-digit update against the previous load stored in the database, and produce a trustworthy count report for downstream consumers. [desc] [Q1] [Q3]

## Target Customer

- **Live-sched operator** — needs sequence-checked incremental CFA loads instead of error-prone ad-hoc updates. [Q1]
- **Downstream consumer** — needs a trustworthy load report of record counts (total, new, overrides, duplicates). [Q1]

## Success Metrics

Success for this proof of concept means: [Q2]

- A folder is watched for a daily update zip that contains the CFA file [Q2]
- The load runs only when the update’s 3-digit code is the correct next code after the previous update/load code stored in the database [Q2] [Q8]
- Wrong 3-digit codes are clearly refused [Q2]
- After an incremental load, a report counts total records plus new records, overrides, and duplicates [Q2]

**Pass/fail bar:** Demonstrate the path on a real update data zipfile. [Q7]

**3-digit sequence rule:** The expected next code is the previous load’s 3-digit code plus one, modulo 1000 (e.g. 999 → 000). For this proof, **990 is the initial previous-load value stored in the DB**, so the first accepted update zip is **991**. [Q8] [Q10]

## Initiative Trigger

The finished live-sched proof of concept loaded schedules but did not cover the daily CFA update path; this work closes that gap. [Q3]

## Initial Scope Signal

- **Workflow-selected scope:** `poc` [scope]
- **User-confirmed product boundary:** Prove folder watch → daily update zip → CFA load with 3-digit sequence check against the previous DB load, refuse mismatched codes, and emit the load report; not a production service. [Q5]

## Communication

When the proof is complete, the author keeps a short written note covering what was proven and a sample load report; no separate recipient and no ongoing stakeholder reporting cadence. [Q6] [Q9]

## Assumptions & Open Questions

None.
