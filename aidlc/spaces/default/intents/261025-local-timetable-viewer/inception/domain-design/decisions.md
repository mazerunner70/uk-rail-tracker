# Architecture Decision Records

## ADR-001: Four blocks

### Context

The screen is two pictures, the answers are two lists, and the day's timetable arrives as an MCA that must become the SQLite file. A reader that both replaced the file and served the lists would mix a write with the read the screens wait on.

### Decision

There are four blocks. `McaInitialisation` replaces the SQLite file from that day's MCA. `TimetableQuery` builds the two answers and only reads the file. `NetworkPage` and `StationPairPage` each call `TimetableQuery`.

### Consequences

The screens cannot see a half-written file from their own ask, because they never call the block that replaces the file. The pair page and the network page can change on different answers. Starting the day is a separate step from opening a page.

### Alternatives Rejected

One block for the file and both pages was rejected because the two pictures ask on different rules. Putting the MCA load inside the query block was rejected because that block must only read. One shared screen block was rejected because each picture is its own page.

## ADR-002: The query block owns the answers, and only the initialisation block replaces the file

### Context

The stored timetable already has a fixed shape in the SQLite file. The new code has to own the answers the pages show. An earlier rule said the reader never creates or rewrites that file. The day's source is an MCA, and there is no CFA for this viewer.

### Decision

`TimetableQuery` owns `NetworkAnswer`, `StationPairAnswer`, and `LoadProgress`. The seven stored tables stay the SQLite file, which is a dependency. `McaInitialisation` is the one block that replaces that file, and it loads London Midland from that day's MCA. It does not apply a CFA. `TimetableQuery` does not create or rewrite the file for a train ask; it starts `McaInitialisation` in the same process when the screen asks to load.

### Consequences

A failed ask cannot blank the file, because the query block cannot write it. Replacing the file is a deliberate start-of-day step. The earlier read-only rule still binds the query block. It does not bind the initialisation block.

### Alternatives Rejected

Treating the stored tables as entities the query block owns was rejected because those rows already live in the file. Checking that today's file is present, without loading an MCA, was rejected because the initialisation has to take that day's MCA.

## ADR-003: When each page asks

### Context

The network page is useful with empty station boxes. The pair page needs two codes. Codes that match nothing are a different case from a file that cannot be read.

### Decision

`NetworkPage` asks on open and on each interval even when the station boxes are empty. `StationPairPage` asks only when both boxes are non-empty. Codes that match no calling point are a successful empty list, and the list says "No trains". A missing, deleted, locked, or older-schema file is a failed ask. With no held answer, that failure says "No data can be retrieved".

### Consequences

Opening the pair page with one box empty paints the page and does not send an ask. A typo in a code does not look like a broken file. A broken file does not look like an empty timetable once an answer is already held, because a failed ask leaves the held answer unchanged.

### Alternatives Rejected

Asking for a pair as soon as the page opens was rejected because an empty box is not a pair. Treating an unmatched code as "No data can be retrieved" was rejected because the file was read and the list is empty.

## ADR-004: The pages call the query block synchronously

### Context

The page changes only when the held answer or load progress changes. Train asks read a local file. The empty screen starts the day's MCA through the server, not from the screen calling the writer block directly.

### Decision

`NetworkPage` and `StationPairPage` call `TimetableQuery` synchronously. `TimetableQuery` calls `McaInitialisation` synchronously when the screen starts a load. No screen block calls `McaInitialisation`.

### Consequences

Load progress and train answers share one server entry point. The page waits for each ask, then updates held data or progress. A slow read or load holds the ask that started it.

### Alternatives Rejected

An asynchronous event from the query block was rejected because the page already decides when to ask. A call from a page to the initialisation block was rejected because FR9 keeps the screen on the query unit only.

## ADR-005: Empty screen, interval, and pair context

### Context

Approved FR9 requires an empty first screen, load button, progress polling on the same interval, and no train asks until load succeeds. US2.1 requires interval validation on the screen. The pair page shares station codes and the interval without calling the network page.

### Decision

`NetworkPage` owns the empty-first-screen behaviour, `RefreshInterval`, and `StationSelection`. It refuses blank, fraction, or non-positive interval values. `StationPairPage` owns `PairViewContext`, which references those entities on `NetworkPage`. `StationPairAnswer` carries `trainUid` and `cancellationLabel` for cancelled rows without a journey.

### Consequences

US2.1 traces to `NetworkPage`. The pair page follows the same tick rules through entity references, not a second interval owner.

### Alternatives Rejected

Putting load progress paint only on a fifth block was rejected because the empty screen and network page share one container unit in the approved plan. Hanging cross-page references on `TimetableQuery` entities was rejected because the pair page writes station codes, not the query block.

## ADR-006: Reader unchanged versus MCA replace

### Context

US3.1 AC3.1.3 requires the reader to leave the SQLite file byte-for-byte unchanged. FR8 makes the MCA load the only writer. Those both hold if train asks and query tests never write, while load tests use a throwaway path per NFR4.

### Decision

Trace US3.1 reader unchanged to `TimetableQuery`. File replace and container persistence stay with `McaInitialisation` and the database container described in Units Generation.

### Consequences

Story coverage names the query block for the read-only guarantee. Container topology is not duplicated here.

### Alternatives Rejected

Tracing US3.1 only to `McaInitialisation` was rejected because AC3.1.3 is about the reader, not the load test path.
