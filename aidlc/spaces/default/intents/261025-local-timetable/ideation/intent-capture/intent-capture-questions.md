# Intent Capture Questions

## Sources

- [desc] Initial description: "give me a future-intents doc for this repo, and create a doc called local-timetable.md. I want this doc to describe a service that I can deploy into docker on my local machine that will have a server component, a db component and a ui. This service will allow me to visualise all the trains on the network currently, identify those trains that are deviating from their published timetable and allow a pair of stations to be chosen to see a visualisation of the trains on that route and where they are, indicating any issues found there. use knowledge gained from the pocs done to determine how to do this. use aidlc to build this doc, but ensure that the shortest possible workflow is chosen so that the process is complete in one hour"
- [scope] Workflow-selected scope: `local-timetable-docs`.



## Q1. Who is this for, and what problem are they hitting today?

The pages have to name who benefits. The request is for a service you can run on your own machine. It does not say whether anyone else uses that view.

A. Just me, on my own machine, so I can see the live network and timetable problems without a hosted service
B. Me, plus other people who would use the same local view
C. A later reader of the repo who needs the design, not someone running the view today
D. Not yet defined
X. Other (please specify)

[Answer]: A

## Q2. What does success look like for this pass?

This pass writes documents. The service itself is not built here. The pages need a clear “done”.

A. The future-intents note and local-timetable.md exist and describe the local Docker server, database, and UI well enough to build later
B. Those two pages, and they must match what the proofs of concept already showed
C. A short sketch is enough; detail can wait
D. Not yet defined
X. Other (please specify)

[Answer]: A

## Q3. Why write this now?

The request asks for the pages in this sitting. It does not say what made this the moment to write them.

A. The proofs of concept are far enough along that a local design can be written from them
B. I need the design recorded before any more build work
C. I want the idea saved so it is not lost, with no build planned yet
D. Not yet defined
X. Other (please specify)

[Answer]: A and B

## Q4. Who decides what these pages include, and who else should hear about them?

The stakeholder list will only include people you name. This question also covers whether anyone needs a status update.

A. I decide alone. No one else is involved, and no status updates are needed
B. I decide, and the pages are only for me to read later
C. Other people care, but I have not named them yet
D. Not identified
X. Other (please specify)

[Answer]: A

## Q5. What boundary should these pages keep?

The plan for this pass is docs only (`local-timetable-docs`): write the future-intents note and `local-timetable.md`, and do not build the service. That plan is separate from how big the service described in the page should be.

A. Confirm both: this pass only writes the two documents, and the page describes a local Docker service with a server, a database, and a UI for the network view, timetable deviations, and a chosen station pair
B. This pass only writes the two documents, but the service described in the page should be smaller than that
C. This pass only writes the two documents, but the service described in the page should be larger than that
D. Use a different product than the local Docker service, and I will describe that product in the answer
E. Not yet defined
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

## Requested Changes Feedback

What should change?

[Answer]: all looks correct
