# Intent Capture Questions

## Sources

- [desc] Initial description: "extend the live-sched poc to take teh daily updates cfa file. I like the idea of a folder being checked fro the daily update zip file and within the zip will be the cfa file. confirm the update is the correct 3 digit code by knowing the 3 digit code of the previous update / load (store this in db). provide a report at the end ofthe increment load that counts numbers of records and the the number of new records, overrides and duplicates"
- [scope] Workflow-selected scope: `poc`.

## Q1. Who is the primary customer for this daily CFA update load, and what pain are they experiencing today?

A. The developer / operator of live-sched — manual or ad-hoc incremental CFA loads are error-prone without sequence checking
B. An internal schedule / data team that needs reliable daily incremental updates into the live-sched database
C. Both the live-sched operator and a downstream consumer who needs a trustworthy load report
D. Not yet defined
E. Not applicable — this is a personal proof spike with no named customer beyond the author
X. Other (please specify)

[Answer]: C

## Q2. What does success look like for this piece of work?

A. A watched folder accepts a daily update zip, loads the CFA inside it only when the 3-digit code follows the previous load stored in the DB, and prints a report of total / new / override / duplicate counts
B. Same as A, plus a clear refuse path when the 3-digit code does not match the expected next update
C. Proof that sequence checking and the load report work on at least one real or sample daily update zip (folder watch can be minimal)
D. Not yet defined
X. Other (please specify)

[Answer]: B

## Q3. What is the trigger for doing this now?

A. Need to prove daily incremental CFA updates before relying on live-sched for ongoing schedule freshness
B. Gap left by the finished live-sched POC, which loaded schedules but did not cover the daily CFA update path
C. Operational pressure to stop unsafe / out-of-order incremental loads
D. Not yet defined
X. Other (please specify)

[Answer]: B

## Q4. Who are the key stakeholders, and who decides whether this proof is good enough?

A. Author is both stakeholder and decision-maker
B. Author plus an internal data / schedule reviewer who must accept the load report
C. A named product or ops owner decides; author implements
D. Not identified
X. Other (please specify)

[Answer]: A

## Q5. The workflow was started as a proof of concept (`poc`). Does that match the product boundary you want?

A. Yes — confirm workflow-selected scope `poc`: prove folder watch → zip → CFA load with sequence check and load report; not a production service
B. No — define a different product boundary (please specify what is in or out)
C. Not yet defined
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

## Assumption Confirmation

Assumptions to confirm:

- Formal communication requirements (cadence and channel) for the downstream consumer and other stakeholders are not yet defined.

A. Accept assumptions
B. Convert to follow-up questions

[Answer]: B. Convert to follow-up questions

## Q6. What communication is required for this proof of concept?

A. None — author self-reviews the load report; no separate stakeholder reporting cadence
B. Share the load report with the downstream consumer after each successful incremental load (ad hoc / as-needed)
C. A short written note when the proof is complete (what was proven and sample report), then done
D. Not yet defined
X. Other (please specify)

[Answer]: C

## Q7. What is the measurable pass/fail bar for this proof of concept? (R-04)

A. Demonstrate success once on a real or sample daily update zip (correct sequence loads; mismatched code refused; report printed)
B. Demonstrate both a successful sequential load and at least one refused mismatched 3-digit code, with the report printed on the successful load
C. Defer a numeric bar to requirements analysis; keep capability checks only for now
D. Not yet defined
X. Other (please specify)

[Answer]: X demonstrate on real update data zipfile 

## Q8. What does “the correct next” 3-digit update code mean? (R-05)

A. Strict +1 modulo 1000 from the previous load’s 3-digit code stored in the DB (e.g. 999 → 000)
B. Strict +1 with no wrap; refuse if previous is 999 and the file is not a special reset case (reset out of scope for this PoC)
C. Exact match to an expected next code we compute/store; refuse any other value (define computation in requirements)
D. Not yet defined
X. Other (please specify)

[Answer]: X take A plus start at 990

## Q9. Who receives the short written completion note? (R-02)

A. Author only (self-kept note; no separate recipient)
B. Downstream consumer
C. Author and downstream consumer
D. Not yet defined
X. Other (please specify)

[Answer]: A

## Q10. What does “start at 990” mean for the previous-load value in the DB? (R-06)

A. 990 is the initial previous-load value stored in the DB; the first accepted update zip is 991
B. 990 is the first accepted update code; the DB seed previous-load value is 989
C. Not yet defined
X. Other (please specify)

[Answer]: A
