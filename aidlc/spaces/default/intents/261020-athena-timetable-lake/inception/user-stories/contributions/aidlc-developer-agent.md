**Collaborator:** aidlc-developer-agent

## Contribution

Round 2 (implementability). Both round-1 objections are folded. The Must Have set remains a markdown-only spec: every AC is checkable by reading `docs/athena-timetable-lake.md`. No Glue jobs, buckets, or ingest code.

Confirming the folds:

- US3.1 is now a plan-query chapter. Overlay lookup lives on US4.1 AC4.1.5 (partition keys and projected columns for forecast versus booked and deactivated on `(train_uid, calendar_date)`, plus a worked example). US3.1 AC3.1.3 only enumerates the four-item cheapest-scan list and points overlay columns at US4.1. Layout is partition keys, prefix/grain, and projected columns (want-line, INVEST, AC3.1.1). Columnar format is not story done. US3.1 does not need `Depends on: US4.1`.
- US1.1, US2.1 AC2.1.1, and US4.1 AC4.1.1 use env-qualified logical names and an `{env}` pattern only. Exact bucket or prefix strings and shared-versus-separate buckets stay architecture choices. US1.1 can stay Independent without inventing the strings architecture still owns.

Sizing is unchanged and still right for this document:

- US1.1, US5.1, US6.1 remain small independent notes.
- US2.1 stays one CFA-intake workflow. The packed apply/refuse AC is now AC2.1.3–AC2.1.7 (sequence refuse, other refuses, LM apply, history, atomic publish). That split helps later construction map tests 1:1; it does not split the story. A later construction intent must still break ingest / validate / apply before anyone writes Python.
- US4.1 stays one overlay workflow: archive-first, three apply types, reserved-type list, named bind keys (at least one of Darwin `rid` alias or `(train_uid, scheduled start date)` via `service_day`), no UID-wide scan.

Atomic publish (AC2.1.7) and overlay bind (AC4.1.4) remain spec obligations. The story still does not pick Glue/Iceberg/overwrite mechanics or a single bind key.

No new implementability objections. Sibling round-1 notes that landed (document-check Whens, empty-lake identity path in AC2.1.2, overlay-miss Then on AC4.1.4) make the slices easier to implement later; they are not new disputes from this seat.

## Positions

- AGREE: Document-delivery Must Have set US1.1–US4.1 matching Q4 A — each is a specifiable markdown slice, not a runtime build.
- AGREE: US2.1 as one CFA-intake story — too large for later code units, right size for this document chapter; refuse stays a separate AC (now AC2.1.3–AC2.1.4).
- AGREE: US5.1 / US6.1 as Should Have and US7.4 as Won't Have runtime — keeps this intent markdown-only; layering and TOC extension stay later notes.
- OBJECT: US3.1 packs overlay lookup (AC3.1.3) and “file layout” into cheap-query — **resolved**. Overlay lookup is US4.1 AC4.1.5; US3.1 restricts layout to partition/prefix/grain; columnar format stays an architecture choice.
- OBJECT: US1.1 AC1.1.2 (and US2.1 AC2.1.1 / US4.1 AC4.1.1) as Independent first-story naming of every object — **resolved**. Those ACs require env-qualified logical names only; exact bucket strings and shared-versus-separate buckets are architecture choices.
