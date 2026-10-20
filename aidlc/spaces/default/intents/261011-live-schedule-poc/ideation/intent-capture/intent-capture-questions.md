# Intent Capture Questions

## Sources

- [desc] Initial description: "start an aidlc live schedule poc next - I want the mca file read - and the LM data updated into a database. provide a summary of the schedule codes (eg LI) processed, and create a md doc as output that shows the algorithm for processing each. Next output is a live update function using a kafka consumer. creds will be stored in an .env file for this and all messages recieved for a 1 hour period will be stored in a csv file for review, and changes applied to the db. have a log file md that has a couple of lines summary for the db change fro each message. Put thi snew poc in teh poc folder as live-sched"
- [scope] Workflow-selected scope: `poc`.

## Q1. Who is this prototype for, and what problem does it solve?

The request lists outputs (a loaded database, a schedule-code summary, an algorithm note, and a one-hour Kafka capture) and does not name who is blocked today. This answer becomes the problem statement and the customer.

A. I am proving, for myself, that a downloaded timetable can be stored and then updated from the live feed
B. Someone who relies on a timetable needs a stored schedule that stays correct while the train is running
C. Not yet defined
D. Not applicable — there is no customer pain to capture for this prototype
E. None
X. Other (please specify)

[Answer]: X it demostrates the moving parts to running a schedule, the ability to fully parse the mca file (though only store LM company schedules), to await and parse incoming kafka messages, and to apply those messages correctly to a schedule. The POC will allow a move to a production database that will maintain a full schedule.

## Q2. What does “LM data” mean, and which timetable file should be read?

“LM” might be an operator, or it might be a schedule record code like the LI example. The file to read is also unnamed. Loading the wrong subset would make the database and the code summary describe the wrong trains.

A. LM means the London Midland operator. Read `server/poc/timetable/RJTTF944MCA.txt`, keep that operator’s schedules, and store them in a database
B. LM is a schedule record code, in the same family as LI. I will name the MCA file
C. LM means something else, and I will name both the meaning and the MCA file
D. Not yet defined
E. Not applicable
X. Other (please specify)

[Answer]: A

## Q3. What does success look like, and why start this now?

The request already lists the outputs. This answer decides whether those outputs are the success measure, and what made this the next piece of work.

A. Success is those outputs: a count of schedule codes such as LI, a markdown note of the algorithm for each code, the chosen schedules stored in a database, then a Kafka consumer that runs for one hour, stores every message in a CSV, applies the changes to the database, and writes a short markdown log of each database change. Credentials stay in a `.env` file. Start now because the timetable file and the live feed are the next thing to prove
B. Those outputs, plus a success measure I will describe
C. A short demonstration aimed at one question, which I will describe
D. Not yet defined
E. None — no success measure or trigger is needed
X. Other (please specify)

[Answer]: A

## Q4. Who is involved, who decides what the prototype includes, and is any reporting needed?

This answer fills the stakeholder map. A solo effort is a valid answer.

A. I am the only stakeholder and the only decision-maker, and no reporting is needed
B. I decide what is in or out, and other people should be kept informed (I will name them and how often)
C. Not identified
D. Not applicable
E. None
X. Other (please specify)

[Answer]: A

## Q5. Does a proof of concept match the product you want?

This was started as a proof of concept: a short run aimed at a working prototype, not a production product. Confirming that size is separate from saying what the prototype itself includes.

A. Yes, keep the proof-of-concept size. The product is a new prototype in `server/poc/live-sched` that loads the chosen schedules from the MCA file, documents each schedule code, and applies one hour of Kafka updates to the database, a CSV, and a markdown change log
B. Yes, keep the proof-of-concept size, but the product is narrower than that (I will describe it)
C. No. I want a larger product than a proof of concept (I will describe it)
D. Not yet defined — do not treat the proof of concept as a confirmed product boundary
E. Not applicable
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
