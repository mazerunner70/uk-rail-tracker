# Delivery Planning Questions

A Bolt is one build pass over a piece of the work, ending in something that runs. The three units already form one chain: the MCA load, then the query, then the screen. Construction already runs one unit at a time. The first running slice is still meant to be the database, one view, and enough of a screen to see it. The screen now starts empty, with a button that starts the load.

## Question 1: What should the first Bolt prove?

A. The empty screen, the load button, and the pair page after a successful load. That is the thin slice: three containers, the file, and one view.
B. The MCA load on its own. The query and the screen wait until a later Bolt.
C. The pair page first, with a SQLite file already in the database container. The load button waits until a later Bolt.
X. Other (please specify)

[Answer]: A

## Question 2: Should we score the Bolts with a formal model?

A formal model would rank each Bolt by value, urgency, and size. The chain is already one line.

A. No formal score. First Bolt is the thin slice from question 1. The rest follow the chain.
B. Yes. Score each Bolt with equal weight on value, urgency, and size.
X. Other (please specify)

[Answer]: A

## Question 3: How much work is in one Bolt?

A. One unit each. Three Bolts: mca-load, then timetable-query, then screen.
B. The first Bolt cuts across the three units far enough to show the empty screen, the load, and the pair page. Later Bolts finish the network page and the rest of the screen.
C. One Bolt for the whole service.
X. Other (please specify)

[Answer]: B

## Question 4: Is anything outside this session going to hold a Bolt up?

A. Nothing. The day's MCA is on this machine. Docker is local.
B. The day's MCA is not on this machine yet. The first Bolt that loads it waits on that file.
X. Other (please specify)

[Answer]: A

## Question 5: What worries you most about this build?

A. The MCA load replacing the file while DBeaver or the query has it open.
B. The empty screen and the train pages disagreeing about when the load is done.
C. The SQLite file not surviving a rebuild of the database container.
D. Nothing in particular. Follow the thin slice.
X. Other (please specify)

[Answer]: D

## Question 6: The network page is the same screen unit as the pair page. How should later work land?

Construction already builds one unit at a time. The pair page and the network page are both the screen unit. A later Bolt cannot finish the network page unless that page is its own unit.

A. Keep one screen unit. One Bolt holds all three units. Done means the empty screen, the load, the pair page, and the network page.
B. Keep one screen unit. One Bolt holds all three units. Done means the empty screen, the load, and the pair page. The network page ships in that same pass because it is the same unit.
[Answer]: A

## Refresh after contract design (2026-11-01)

Prior answers kept. Bolt plan checked against the approved contract-summary (REST load/progress/network/pair, in-process load, SQLite path).

[Answer]: Confirmed.
