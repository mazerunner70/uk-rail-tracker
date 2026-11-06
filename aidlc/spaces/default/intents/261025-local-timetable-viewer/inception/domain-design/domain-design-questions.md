# Domain Design Questions

The service is already three containers: the SQLite file, the TypeScript reader, and the TypeScript screen. A component here is code we write. The SQLite file is something a component uses. It is not a component. These questions are the boundaries the catalogue cannot take from the stories alone.

## Question 1: Which code blocks do we write?

A. Two blocks. The reader answers the network list and the station pair. The screen paints the two pages and calls the reader. The SQLite file is a dependency of the reader.
B. Three blocks. Split the reader into one block that opens the file and one block that builds the two answers. The screen is the third.
C. One block that both reads the file and paints the pages.
X. Other (please specify)

[Answer]: B

## Question 2: What does the reader own?

The seven stored tables already exist in the SQLite file. The reader does not create them.

A. The reader owns the two answers it returns: the network list and the station-pair list. The stored tables stay a dependency, not entities the reader owns.
B. The reader owns the stored journey, calling point, and service-day rows, even though it only reads them.
X. Other (please specify)

[Answer]: X The reader owns the two answers it returns, and alsothere needs to be an initialisation function that will take in a fresh MFA for that day. There is no need fro a CFA as the MFA will be for that day

## Question 3: Is the screen one block?

A. One screen block, with the network page and the station-pair page inside it. Both pages call the reader.
B. Two screen blocks, one for each page. Each calls the reader on its own.
X. Other (please specify)

[Answer]: B

## Question 4: When does the pair page ask?

The pages were approved with this left open. The reader behaviour has to name it.

A. The network page asks on open and on each interval, even when the station boxes are empty. The pair page asks only when both boxes are non-empty. Codes that match no calling point are a successful empty list, and the list says "No trains".
B. The pair page asks whenever it is open, including when a box is empty, and an empty or unmatched pair says "No data can be retrieved".
X. Other (please specify)

[Answer]: A

## Question 5: What does the day's file initialisation do?

You asked for an initialisation function that takes a fresh MFA for that day, and no CFA, because that file is already the day's timetable. In this project the full timetable file is an MCA, and a CFA is the later change file. The reader, as previously decided, does not create or rewrite the SQLite file.

A. MFA means that day's MCA. Initialisation is its own block. It replaces the SQLite file from the MCA. The query block only reads the file afterwards. The two screen blocks call only the query block.
B. MFA means that day's MCA. Initialisation is the file-opening block from question 1. That block replaces the SQLite file, and the query block reads it.
C. Initialisation only checks that today's SQLite file is present. It does not load an MCA and it does not rewrite the file.
X. Other (please specify)

[Answer]: A. MFA was a typo for MCA. Initialisation is its own block. It replaces the SQLite file from that day's MCA. The query block only reads the file afterwards. The two screen blocks call only the query block.

## Modify after requirements rewrite (FR7–FR9)

The catalogue was updated in place to match approved FR8–FR9 and the prior architecture review.

1. **Load path** — `TimetableQuery` starts `McaInitialisation` in-process; screens never call the writer block (FR9.2).
2. **Empty screen** — `NetworkPage` owns first paint, load button, progress polls on the 30-second interval until success, then train asks (FR9.1–FR9.6).
3. **Interval** — `RefreshInterval` validation and US2.1 retarget to `NetworkPage`; `PairViewContext` references shared entities (review R-02, R-03).
4. **Pair rows** — `trainUid` and `cancellationLabel` on `StationPairAnswer` (review R-04).
5. **Traceability** — US2.1 → `NetworkPage`; US3.1 reader unchanged → `TimetableQuery` (ADR-006).

[Answer]: Applied as listed above.

## Learnings keep

Which notes should I keep for next time?

A. MFA was a typo for MCA. Four blocks: McaInitialisation replaces the SQLite file from that day's MCA and applies no CFA; TimetableQuery owns the two answers and only reads; NetworkPage and StationPairPage call only TimetableQuery.
B. NetworkPage asks on open and on each interval even when the station boxes are empty. StationPairPage asks only when both boxes are non-empty. Unmatched codes are a successful empty list.
C. The earlier rule that the reader never rewrites SQLite still binds TimetableQuery. McaInitialisation is the exception and replaces the file.
D. The pair page reads StationSelection and RefreshInterval owned by NetworkPage. That reference is not a call, so the call graph stays acyclic.
E. Modify after FR7–FR9: TimetableQuery starts McaInitialisation in-process and owns LoadProgress; NetworkPage owns empty screen, load button, and RefreshInterval validation; US2.1 traces to NetworkPage; US3.1 reader unchanged traces to TimetableQuery.
F. Keep none
X. Other (please specify)

[Answer]: A, B, C, D, E

## Anything to add

Anything to add for next time?

A. Nothing to add
B. Add a note
X. Other (please specify)

[Answer]: A. Nothing to add
