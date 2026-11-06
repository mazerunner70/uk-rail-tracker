# Intent Statement

## Problem Statement

The person asking for this wants a local view of the rail network: all trains as they are now, which of those trains are off their published timetable, and a chosen pair of stations showing the trains on that route, where they are, and any issues found there. They cannot do that today on their own machine without a hosted service. [desc][Q1]

## Target Customer

The customer is the person asking for this, alone, on their own machine. They want to see the live network and timetable problems without a hosted service. [Q1]

## Success Metrics

This pass is done when a future-intents note for this repo and `local-timetable.md` exist, and those pages describe the local service well enough to build later. [Q2]

The service those pages describe has a server, a database, and a UI, and it runs in Docker on the local machine. The UI shows all trains on the network now, trains that deviate from the published timetable, and a chosen station pair with the trains on that route, where they are, and any issues found there. [desc][Q5]

## Initiative Trigger

Write the pages now because the proofs of concept are far enough along that a local design can be written from them, and because the design should be recorded before any more build work. [Q3]

## Initial Scope Signal

The workflow-selected scope is `local-timetable-docs`. [scope]

The confirmed boundary for this pass is the two documents only. The service is not built here. The page describes a local Docker service with a server, a database, and a UI for the network view, timetable deviations, and a chosen station pair, using knowledge from the proofs of concept already done. [Q5][desc]

## Assumptions & Open Questions

None.
