# Rules — cfa-receipt-apply

```yaml
rules:
  - id: BR1.1
    statement: The product of this unit is the file at docs/athena-timetable-lake.md
    category: constraint
    applies_to: document
    trigger: unit complete
    logic: IF this Bolt is done THEN that path exists
    violation: Bolt is not done
    source: FR1.1
  - id: BR1.2
    statement: Every logical S3, Glue, and Athena object is qualified with {env} in {dev, staging, prod} and is named not created
    category: constraint
    applies_to: Environments
    trigger: read of any logical object
    logic: IF a location is introduced THEN it uses {env} AND does not claim the resource exists
    violation: unnamed or claimed-live resource
    source: FR1.3
  - id: BR1.3
    statement: The file contains no Kafka keys, Darwin keys, or long-lived AWS access keys
    category: policy
    applies_to: document
    trigger: any edit of owned headings
    logic: IF text is written THEN it has no credentials
    violation: reject the edit
    source: FR1.4
  - id: BR1.4
    statement: Merge to main may roll staging; production needs a separate human approval and a rollback path that must be tested before a prod cut
    category: policy
    applies_to: Environments
    trigger: read of promote cadence
    logic: IF production is named THEN a tested rollback obligation is named
    violation: missing cadence
    source: NFR2 NFR3
  - id: BR1.5
    statement: Top-level headings are Environments, CFA receipt → validate → apply, Cheap lookups, Kafka archive/overlay, TOC extension, Later package layering, Deferred / not in this document
    category: constraint
    applies_to: heading map
    trigger: scan of H2 titles
    logic: IF the file is the product THEN those exact titles exist AND diagrams have text fallback AND refuse versus accept is not colour-only
    violation: heading map broken
    source: FR1.2
  - id: BR1.6
    statement: Deferred lists KMS/bucket policies/workgroup encryption, reserved Kafka types, station+time-window cheapest scan, versioned journey history, runtime/CI, and Android / docs/architecture.md pointer
    category: policy
    applies_to: Deferred
    trigger: read of Deferred
    logic: IF an item is out of this document THEN it is listed there
    violation: missing Deferred item
    source: FR1.2
  - id: BR2.1
    statement: Receipt is an env-qualified logical S3 location; example bucket strings are author choice not a lock
    category: constraint
    applies_to: ReceiptZip
    trigger: object arrival
    logic: IF a zip is received THEN it is in the named receipt location AND the raw zip stays for replay
    violation: missing receipt location
    source: FR2.1
  - id: BR2.2
    statement: MCA member means full update (MCA wins if CFA also present); else exactly one CFA member; sequence is HD columns 33–39; empty store seeds 990; expected is previous plus one modulo 1000 zero-padded
    category: validation
    applies_to: ReceiptZip FeedSequence
    trigger: validate
    logic: IF MCA THEN skip CFA sequence checks AND after accept clear FeedSequence. IF CFA THEN require three HD digits AND expected code. IF neither THEN error, plan unchanged
    violation: refuse or error; plan and FeedSequence unchanged except MCA accept clear
    source: FR2.2
  - id: BR2.3
    statement: CFA sequence mismatch refuses; expected and actual codes are recorded; raw zip stays; current plan unchanged
    category: validation
    applies_to: ReceiptZip
    trigger: sequence mismatch
    logic: IF actual ≠ expected THEN status refused AND no PlanQuery entity writes
    violation: refuse path skipped
    source: FR2.3
  - id: BR2.4
    statement: Zero CFA members (no MCA), more than one CFA member (no MCA), HD without three digits, and neither MCA nor CFA each leave the plan unchanged and keep the raw zip; basename and member-name sequence fallbacks are forbidden
    category: validation
    applies_to: ReceiptZip
    trigger: other refuse paths
    logic: IF those reasons THEN refuse or error AND FeedSequence unchanged AND no PlanQuery writes
    violation: plan mutated on refuse
    source: FR2.2 FR2.3
  - id: BR2.5
    statement: CFA accept applies incremental N/R/D for TOC LM only on CIF identity (train_uid, run_date, stp_indicator)
    category: policy
    applies_to: Journey CallingPoint ServiceDay
    trigger: CFA accept
    logic: IF TOC is not LM AND the draft is not a delete THEN skip. IF delete THEN follow identity not TOC
    violation: non-LM persist
    source: FR2.4 FR5.1
  - id: BR2.6
    statement: Apply history records sequence, total/new/override/duplicate/delete counts, and identities changed as CIF triples only; not a versioned copy of every journey
    category: constraint
    applies_to: ApplyHistory
    trigger: accept publish
    logic: IF accept THEN append ApplyHistory with CIF triples in identitiesChanged
    violation: missing history or service_guid used as the identity
    source: FR2.5
  - id: BR2.7
    statement: Accept publish is one atomic current-plan plus history update; mid-apply failure leaves the previous plan queryable and writes no partial grain; overlay is not in this transaction
    category: policy
    applies_to: PlanQuery publish set
    trigger: persist
    logic: IF persist fails mid-apply THEN previous plan remains AND no partial grain from the failed apply
    violation: partial current plan
    source: FR2.5 NFR4
  - id: BR2.8
    statement: ReceiptZip.status uses accepted, refused, failed, nothing; chapter prose may still say refuse/accept
    category: constraint
    applies_to: ReceiptZip
    trigger: record outcome
    logic: IF an outcome is stored THEN it is one of those four values
    violation: free-text status
    source: FR2.3
  - id: BR2.9
    statement: service_guid is SVC- plus {train_uid}-{run_date}-{stp_indicator}; STP winner on service_day is C then O then P then N
    category: calculation
    applies_to: Journey ServiceDay
    trigger: shape grains
    logic: IF a journey is shaped THEN service_guid follows that form AND service_day picks C>O>P>N
    violation: live-sched origin/dest/time guid or wrong STP order
    source: FR3.3
  - id: BR2.10
    statement: CFA accept replaces journey booked identity, calling-point booked times, and service_day, appends history, advances FeedSequence, leaves LocationMap intact, merge-preserves overlay aliases status and forecast; MCA full update also writes LocationMap and clears FeedSequence
    category: policy
    applies_to: publish set
    trigger: accept
    logic: IF CFA accept OR MCA full update THEN apply the named publish set
    violation: overlay wiped or LocationMap written on CFA
    source: FR2.4 FR4.3
  - id: BR5.1
    statement: First queryable population is LM; other TOCs later use the same receipt, HD sequence, CIF identity, calendar/STP, and refuse/accept; no new sequence mechanism
    category: policy
    applies_to: TOC extension
    trigger: persist and TOC heading
    logic: IF a later TOC is added THEN it reuses this intake
    violation: new receipt or sequence door
    source: FR5.1 FR5.2
  - id: BR6.1
    statement: Later lake/transform/ingest code is a sibling package under server/ with its own pyproject.toml and tests; no Athena or S3 clients inside live_sched; no schedule_poc import
    category: policy
    applies_to: Later package layering
    trigger: later implementation
    logic: IF code is written THEN it follows that layering
    violation: import schedule_poc or Athena inside live_sched
    source: FR6.1 FR6.2
  - id: BR6.2
    statement: Object-store and query-engine clients stay out of CIF parsers and the repository layer
    category: policy
    applies_to: CfaIntake
    trigger: later implementation
    logic: IF a parser or repository is written THEN it has no S3/Athena client
    violation: client inside parser
    source: FR6.2
  - id: BR6.3
    statement: CFA/sequence intake is fail-closed; Kafka overlay is fail-open after the archive object exists (overlay specified in U2; this unit records the pair)
    category: policy
    applies_to: error policy
    trigger: failure
    logic: IF CFA/sequence fails THEN refuse, plan unchanged. Overlay fail-open is U2 after archive exists
    violation: fail-open on CFA
    source: FR6.3
  - id: BR7.1
    statement: This Bolt edits only Environments, CFA receipt → validate → apply, TOC extension, Later package layering, Deferred, and the title/diagram shell
    category: constraint
    applies_to: document
    trigger: this Bolt
    logic: IF this Bolt writes THEN cheap-lookups and Kafka archive/overlay headings are untouched
    violation: sibling heading rewrite
    source: FR1.2
```

## Summary

| ID | Category | One-line |
|----|----------|----------|
| BR1.1–BR1.6 | document shell | path, `{env}`, secrets, cadence, headings, Deferred |
| BR2.1–BR2.10 | intake | receipt, MCA/CFA validate, refuse, LM apply, history triples, atomic persist, status enum, guid/STP, publish set |
| BR5.1 | extension | same door for later TOCs |
| BR6.1–BR6.3 | layering | sibling package, client placement, fail-closed |
| BR7.1 | this Bolt | owned headings only |

## Sources

- `stories.md` — US1.1 US2.1 US5.1 US6.1 AC1.1.1–AC6.1.3
- `requirements.md` — FR1 FR2 FR5 FR6
- `functional-design-questions.md` — Q1 A Q2 A Q3 A Q4 C Q6 A Q7 A
- `contract-summary.md` — C1 C2
