# Functional Design Questions — cfa-receipt-apply

This Bolt locks the CFA chapter and the shared file shell of `docs/athena-timetable-lake.md` in place. It does not rewrite overlay or cheap-lookups headings. Remaining Construction is Functional Design only. No UI in this unit.

Already locked and not re-asked: walking skeleton off; serial unit-major; service_guid is `SVC-` plus `{train_uid}-{run_date}-{stp_indicator}`; HD sequence columns 33–39; empty store seeds `990`; LM persist; STP winner C then O then P then N; MCA full update vs CFA incremental; fail-closed refuse; atomic PlanQuery persist; no `schedule_poc`.

Upstream: `unit-of-work.md` U1, `unit-of-work-story-map.md` US1.1 US2.1 US5.1 US6.1 US7.1–US7.5, `requirements.md`, `components.md` CfaIntake, `contract-summary.md` C1 C2.

### Question 1: What does this Bolt do to the existing file?

A. Keep the current CFA, Environments, TOC extension, Later package layering, and Deferred headings as the lock — only edit if they drift from U1 boundaries or C1/C2
B. Tighten the CFA chapter in place against C1/C2 even if that means rewriting paragraphs inside those owned headings
C. Replace the owned headings from a blank draft
X. Other (please specify)

[Answer]:A

### Question 2: Which headings may this Bolt edit?

A. Only Environments, CFA receipt → validate → apply, TOC extension, Later package layering, Deferred / not in this document (and the title/diagram shell U1 owns)
B. Those plus the cheap-lookups Glue table list, because U1 names plan grains
C. The whole file
X. Other (please specify)

[Answer]:A

### Question 3: How is a receipt zip’s outcome recorded?

Team code style already uses folder statuses `accepted` / `refused` / `failed` / `nothing`. The CFA chapter today uses refuse / accept / error-log wording.

A. Map onto `accepted` / `refused` / `failed` / `nothing` in the entities (ReceiptZip.status); the chapter wording can stay refuse/accept as the human-readable path
B. Keep only refuse / accept / error in the chapter and the entity; do not add `nothing` or `failed`
C. Record status as free text
X. Other (please specify)

[Answer]:A

### Question 4: How does apply history name the identities that changed?

A. CIF triple `(train_uid, run_date, stp_indicator)` plus derived `service_guid`
B. `service_guid` only
C. CIF triple only
X. Other (please specify)

[Answer]:C

### Question 5: Mid-apply failure — what stays queryable?

A. The previous published current plan and history; no partial grain from the failed apply (matches AC2.1.7)
B. Allow partial grain writes as long as FeedSequence does not advance
C. Leave this as an implementer choice in Deferred
X. Other (please specify)

[Answer]:C

### Question 6: Confirm C1/C2 names this Bolt must not change

Provider lock: `{env}` in `{dev, staging, prod}`; `service_guid` as already written; grains journey / calling_points / service_day / location_map; Glue names `journey`, `calling_points`, `service_day`, `apply_history`, `location_map`; STP winner C O P N; bind keys rid via service_aliases (consumer writes) and `(train_uid, calendar date)` via service_day; forbidden UID-wide scan and creating a journey on bind miss.

A. Confirm — do not rename or remove those in this Bolt
B. Change a name (please specify which)
X. Other (please specify)

[Answer]:A

### Question 7: Mid-apply failure vs the existing story

Question 5 C leaves mid-apply failure as an implementer choice in Deferred. Story US2.1 already says: when I read publish and failure, an accepted apply is one atomic published current-plan plus history update, and a mid-apply failure leaves the previous plan queryable (AC2.1.7).

A. Keep AC2.1.7 — previous plan stays queryable; no partial grain (same as Question 5 A)
B. Defer AC2.1.7 — add mid-apply atomicity to Deferred / not in this document and treat it as later implementer choice
X. Other (please specify)

[Answer]: A

## Sources

- `docs/athena-timetable-lake.md` — existing CFA chapter and file shell
- `unit-of-work.md`, `unit-of-work-story-map.md` — U1 stories
- `contract-summary.md` — C1 C2
- `components.md` — CfaIntake, ReceiptZip, FeedSequence
- `requirements.md` — FR1 FR2 FR5 FR6
- `delivery-planning-questions.md` — Q3 A in-place; Q5 A overwrite worry

## Assumptions & Open Questions

- Overlay and cheap-lookups headings wait for later Bolts.
- Question 5 C is overridden by Question 7 A: keep AC2.1.7.
