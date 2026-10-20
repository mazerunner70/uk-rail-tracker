# Intent Statement

## Problem Statement

The UK rail network schedule is composed of a daily downloaded standard schedule and a Kafka feed of updates. [Q1] Current per-route queries are very inefficient and do not provide a stable id for a rail journey, so the Android app in this monorepo's `android` subfolder sometimes loses access to train details while the train is running. [Q1][Q7] This initiative builds a prototype of a schedule tracking service and API to prove a better approach to maintaining that schedule. [desc][Q2]

## Target Customer

You are the sole customer and decision-maker for this prototype. [Q3][Q8] The immediate consumer of the schedule service is the Android app under `android`. [Q7] The prototype maintains schedule state for London Midland (LM) trains, using a subset of the download and feed. [Q4][Q5] A later AWS-facing project is expected to reuse the learnings and algorithms if this prototype succeeds. [Q2]

## Success Metrics

- The prototype runs without AWS cloud costs and demonstrates an inexpensive way to prove the schedule-maintenance approach. [Q2]
- The prototype covers London Midland (LM) trains using a subset of the daily schedule download and the Kafka update feed. [Q4][Q5]
- Existing exploratory work under `server/poc` is the material used for this workflow; no additional supporting POC detail is required beyond that folder. [Q4][Q6]
- Success of this prototype is the gate for a follow-on project that translates the learnings and algorithms to AWS. [Q2]

## Initiative Trigger

The work is starting now to prove the approach cheaply before committing to an AWS delivery. [Q2] The current query model is inefficient and lacks a stable journey id, which already causes the Android app to lose train details mid-journey. [Q1][Q7]

## Initial Scope Signal

- Workflow-selected scope: `poc`. [scope]
- User-confirmed product boundary: a proof-of-concept schedule tracking service and API for London Midland (LM), using a subset of the UK rail schedule download and Kafka update feed, with existing work in `server/poc` as the local starting point for this workflow. [Q4][Q5][Q6]

## Assumptions & Open Questions

None.
