# Intent Backlog

Prioritized with MoSCoW. This pass is one fixed page for one person, with no usage numbers to score, so MoSCoW is the fit. Items are proto-units for the page named in the scope document. The intent statement is the upstream source for who the page is for and what done means.

## Must Have

| ID | Proto-unit | Why it is a must |
|----|------------|------------------|
| U1 | Write `docs/future-intents/local-timetable.md` | This pass is done when that one file exists. [Q3] |
| U2 | Future-intents note for this repo, inside that file | The note is part of the same file as the service description. [Q3][intent-statement] |
| U3 | Local service description: Docker on the reader's machine, with a server, a database, and a UI, written from the proofs of concept well enough to build later | Without this, the page does not describe the service. [Q2][intent-statement] |
| U4 | Three views in that description: all trains on the network now; trains that deviate from the published timetable; a chosen station pair with the trains on that route, where they are, and any issues | Those views are the reason the page exists. [Q2][intent-statement] |

U2, U3, and U4 are sections of U1. They ship in the same file, in this pass.

## Should Have

None.

## Could Have

None.

## Won't Have

| ID | Proto-unit | Why it is out |
|----|------------|---------------|
| U5 | Build or run the service in this pass | The pass writes the page only. [Q2] |
| U6 | Hosted or cloud deployment | The page describes the local machine only. [Q2] |
| U7 | Step-by-step build guide | The page describes the service well enough to build later. It is not a build guide. [Q2] |
| U8 | A second file for the future-intents note | The note lives inside `docs/future-intents/local-timetable.md`. [Q3] |

## Sequence

Write U1 once, with U2, U3, and U4 inside it. U5 through U8 stay out. The proofs of concept already in the repo are the input to U3 and U4. [intent-statement]
