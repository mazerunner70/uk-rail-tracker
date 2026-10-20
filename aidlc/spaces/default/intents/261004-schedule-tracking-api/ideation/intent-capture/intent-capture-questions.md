# Intent Capture Questions

## Sources

- [desc] Initial description: "Build a prototype of a schedule tracking service and api"
- [scope] Workflow-selected scope: `poc`.

## Q1. What problem should this prototype solve, and who feels it?

The request names a schedule tracking service and API, and it does not say whose schedule or what goes wrong today. This answer becomes the problem statement and the customer.

A. Someone who relies on a timetable needs to see whether a scheduled service is running as planned, and what changed
B. A team needs a service and API to record and look up schedules, and no specific end user is named yet
C. Not yet defined
D. Not applicable — there is no customer pain to capture for this prototype
E. None
X. Other (please specify)

[Answer]:X the schedule is the uk rail network, composed of daily downloaded standard schedule and a kafka feed of updates. The current per route queries are very inefficient and do not have any kind of stable id for the rail journey, making the client app sometime lose access to train details while teh train is running. I have details of a supporting poc whenever that is appropriate to share.

## Q2. What does a successful prototype look like, and why start it now?

This answer sets the success measure and the reason for doing the work now, rather than later.

A. A locally runnable service and API that can store a schedule and return it when asked
B. A locally runnable service and API that can also show whether a scheduled item is late, changed, or cancelled
C. A short demonstration aimed at one specific question, which I will describe
D. Not yet defined
E. None — no success measure or trigger is needed for this prototype
X. Other (please specify)

[Answer]:C the prototype will provide an inexpensive way to prove the approach to maintaining a schedule without AWS cloud costs. Success in this prototype will lead to another project to take the learnings and algorithms and tranlate that to AWS.

## Q3. Who is involved, who decides what the prototype includes, and is any reporting needed?

This answer fills the stakeholder map. A solo effort is a valid answer.

A. I am the only stakeholder and the only decision-maker, and no reporting cadence is needed
B. I decide what is in or out, and other people should be kept informed (I will name them)
C. Not identified
D. Not applicable
E. None
X. Other (please specify)

[Answer]:A

## Q4. Does a proof of concept match the product you want?

This workflow was started as a proof of concept: a short run aimed at a working prototype, not a production product. Confirming that workflow size is separate from saying what the prototype itself includes.

A. Yes, keep the proof-of-concept workflow. The product boundary is a prototype of a schedule tracking service and API
B. Yes, keep the proof-of-concept workflow, but the product boundary is narrower than a full schedule tracking service and API (I will describe it)
C. No. I want a larger product boundary than a proof of concept (I will describe it)
D. Not yet defined — do not treat the proof of concept as a confirmed product boundary
E. Not applicable
X. Other (please specify)

[Answer]:X the initial prototype will just run on the trains fro one TOC (Train operating Company) a subset of the download and feed. the poc is in subfolder server/poc

## Consolidated Summary Confirmation

- Q1: The schedule is the UK rail network, composed of a daily downloaded standard schedule and a Kafka feed of updates. Current per-route queries are very inefficient and do not have a stable id for the rail journey, so the client app sometimes loses access to train details while the train is running.
- Q2: The prototype will provide an inexpensive way to prove the approach to maintaining a schedule without AWS cloud costs. Success will lead to another project that takes the learnings and algorithms and translates them to AWS.
- Q3: You are the only stakeholder and the only decision-maker, and no reporting cadence is needed.
- Q4: The initial prototype will run on the trains for one TOC, using a subset of the download and feed. The POC is in subfolder `server/poc`.
- Q5: The first-cut TOC is LM (London Midland).
- Q6: Use only what is already under `server/poc` for this workflow.
- Q7: The consumer is the Android app in the `android` subfolder of this monorepo.
- Q8: You remain the only stakeholder and decision-maker.

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

## Assumption Confirmation

Assumptions currently carried in the artifacts:

- Which specific TOC the first cut covers is not yet named.
- Supporting POC details beyond the `server/poc` location may be shared later when appropriate.
- The client app that loses train details mid-journey is named only as "the client app"; its identity and interface are not yet defined.
- No additional stakeholders or influencers were named; if any appear later they are outside the current map.

A. Accept assumptions
B. Convert to follow-up questions

[Answer]: B. Convert to follow-up questions

## Q5. Which Train Operating Company (TOC) should the first cut cover?

This replaces the open assumption that the TOC was unnamed.

A. Name one TOC now (please specify)
B. Keep it as "one TOC" for now and choose the company later
C. Not yet defined
D. Not applicable
E. None
X. Other (please specify)

[Answer]: A "LM" stands for London Midland

## Q6. When should supporting POC details beyond `server/poc` be brought in?

You offered supporting POC detail when appropriate. This decides timing for that material.

A. Use only what is already under `server/poc` for this workflow
B. Share the supporting POC details next, before we go further
C. Share them later when we reverse-engineer or design from the existing code
D. Not yet defined
E. None
X. Other (please specify)

[Answer]: A

## Q7. What is the client app that loses train details mid-journey?

This names the consumer that feels the pain of the missing stable journey id.

A. An existing app in this repo (please name the path or package)
B. A planned or external client that is not in this repo yet
C. Not yet defined — leave the consumer unnamed for this prototype
D. Not applicable
E. None
X. Other (please specify)

[Answer]: the android subfolder in this monorepo covers the android app

## Q8. Are there any other stakeholders or influencers besides you?

Q3 said you are the only stakeholder. This confirms nobody else should appear on the map.

A. No — I remain the only stakeholder and decision-maker
B. Yes — add these people or roles (please specify)
C. Not identified
D. Not applicable
E. None
X. Other (please specify)

[Answer]: A
