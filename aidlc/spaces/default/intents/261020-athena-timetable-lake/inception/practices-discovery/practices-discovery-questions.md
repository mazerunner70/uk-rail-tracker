# Practices Discovery Questions

Brownfield first run. The draft and reviews could not settle these six points. Suggested answers come from what the repo does today plus the org defaults; they are not team policy until you choose.

### Question 1: How should we land work on `main`?

Git today uses longer-lived milestone branches (`m4`, `m5`, `journey-tracker`) that still merge to `main`. The org default is short-lived branches and squash-merge. This choice becomes the team's way of working.

A. Short-lived branches on `main`, squash-merge (org default)
B. Keep milestone / feature branches as we do now, still merge to `main`
C. Keep milestone branches, and squash-merge when they land on `main`
X. Other (please specify)

[Answer]:A

### Question 2: Build a thin end-to-end slice first? A walking skeleton is a minimal version that runs the whole way through, built first to prove the pieces connect before the real features go in.

This piece of work is a server architecture document. The current workflow already leaves that ceremony off.

A. No — skip it for this work
B. Yes — require a running intake-to-query slice before later design
X. Other (please specify)

[Answer]:X Give me an overall arch and the impl can be in stages

### Question 3: How should we write tests for new lake, ingest, and query work?

live-sched today writes the code, then pytest. There are no Gherkin feature files. Classic default is the same test-after order.

A. Test-after: implement each layer, then write and run that layer's tests
B. Test-first (TDD) for new lake and transform code
C. Mix: scenarios first for the lake contract, unit tests after the implementation
X. Other (please specify)

[Answer]:A

### Question 4: What coverage bar should we adopt?

Classic default is 80% line coverage and CI before merge. There is no coverage tool and no CI today, so that floor cannot run. Quality review says we must choose it or write an explicit carve-out, not leave “suite green” as the practice by silence.

A. Suite stays green only, until CI exists
B. Adopt the 80% floor for new lake / ingest / query code, and add the tooling when that code is first built
C. Adopt the 80% floor now, including the current Python POCs
X. Other (please specify)

[Answer]: A

### Question 5: How should environments and deploy appear in this architecture document?

No S3 buckets, Glue jobs, or Athena workgroup exist yet. This workflow skips CI and the later deploy steps. When we do deploy, the org default is staging on merge and a separate production approval.

A. Design only: name placeholder prefixes, no deploy cadence in this document
B. Name `dev` / `staging` / `prod` now; staging on merge, production needs a separate approval
C. Name those environments, but keep this document design-only (no implied pipeline)
X. Other (please specify)

[Answer]:B  

### Question 6: What style and safety rules should new lake code follow?

live-sched has no Python formatter. Secrets already live in gitignored `.env`. There is no secret scanner, SAST, or dependency lock. This document is markdown-only; we should not add scanner config files here unless you want that as the team practice going forward.

A. Defer to existing config: no new formatter; secrets stay in `.env`; add secret scan, dependency CVE scan, and Python SAST only when CI exists; no live-site scanning until something is deployed
B. Add Ruff (format + lint) when lake code is first written; same secret and scan rules as A
C. Leave style and scanners unset until we write the first lake package
X. Other (please specify)

[Answer]: A

## Learnings keep

Which notes should I keep for next time? (select all that apply)

A. Read Q2 Other as architecture-first with staged implementation
B. Landed the lead drafts after the release engineer's writes were refused, then collected independent quality, developer, and security reviews
C. Kept the classic 80% coverage floor carved out because Q4 chose suite-green until CI exists
D. None
X. Other (please specify)

[Answer]: A, B, C

## Anything to add

Anything to add for next time?

A. Nothing to add
B. Add a note
X. Other (please specify)

[Answer]: A. Nothing to add
