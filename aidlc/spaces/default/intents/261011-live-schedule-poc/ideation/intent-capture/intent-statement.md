# Intent Statement

## Problem Statement

This prototype demonstrates the moving parts of running a schedule: fully parse the MCA file, store only London Midland company schedules, wait for and parse incoming Kafka messages, and apply those messages correctly to a schedule. [Q1][Q2] The proof of concept is what allows a later move to a production database that maintains a full schedule. [Q1]

## Target Customer

You are the only person this prototype is for. [Q4] The demonstration is for you: parse the timetable, keep London Midland schedules, and apply live Kafka updates to that stored schedule. [Q1][Q2][Q4]

## Success Metrics

- The MCA file `server/poc/timetable/RJTTF944MCA.txt` is fully parsed, and only London Midland schedules are stored in a database. [Q1][Q2][Q3]
- A summary counts the schedule codes processed, such as LI, and a markdown note states the algorithm for each code. [Q3]
- A Kafka consumer runs for one hour, stores every message received in a CSV file for review, applies those changes to the database, and writes a short markdown log of each database change. [Q3]
- Credentials for the live update stay in a `.env` file. [Q3]
- Incoming Kafka messages are parsed and applied correctly to the stored schedule. [Q1]

## Initiative Trigger

The timetable file and the live feed are the next thing to prove. [Q3]

## Initial Scope Signal

- Workflow-selected scope: `poc`. [scope]
- User-confirmed product boundary: a proof of concept in `server/poc/live-sched` that loads London Midland schedules from `server/poc/timetable/RJTTF944MCA.txt`, documents each schedule code, and applies one hour of Kafka updates to the database, a CSV, and a markdown change log. [Q2][Q5]

## Assumptions & Open Questions

None.
