# Security test instructions

## Strategy applicability

Under **Standard** test strategy, dedicated **security-test-instructions** are optional unless the team moves to **Comprehensive**.

Local-timetable is a **single-user Docker** slice with **no accounts or hosted API** (requirements NFR1). Automated secret scanning and dependency audits were **not** wired as blocking commands in this Construction pass (requirements NFR5 defers formal pipeline; team may run manual scans).

## Recommended manual checks (non-blocking)

- `pnpm audit --audit-level=moderate` in **server** and **fe** after `pnpm install`.
- Confirm no secrets in committed sources (`.env` remains gitignored per NFR6).

## When comprehensive

Add explicit SAST/DAST or auth tests if the product scope expands beyond local single-user use.
