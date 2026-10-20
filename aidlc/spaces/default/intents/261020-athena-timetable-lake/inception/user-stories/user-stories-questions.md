# User Stories Questions

Stories turn the approved requirements into independently testable slices for the architecture markdown. These four questions settle personas, what a story is about, how we split them, and what is Must Have for this document.

### Question 1: Who are the story personas?

Requirements Q1 said the document is for you as the later implementer. A later operator of the named lake is not in this piece of work, but stories can still name them.

A. Later implementer only — every story is “as the person who will build from `docs/athena-timetable-lake.md`”
B. Later implementer plus a future lake operator (someone who lands a CFA zip or runs an Athena lookup after implementation)
C. Later implementer, future operator, and a traveller / Android user
X. Other (please specify)

[Answer]:A 

### Question 2: What should each story be about?

This intent writes a markdown file. The lake it describes is later work.

A. Document-delivery only — stories are about writing and checking `docs/athena-timetable-lake.md`
B. Lake-behavior only — stories are about CFA apply, Kafka overlay, and cheap lookups; the markdown is how we accept them in this piece of work
C. Both — a small set of document-delivery stories, plus lake-behavior stories whose acceptance is “the markdown specifies this so a later build can pass”
X. Other (please specify)

[Answer]: A

### Question 3: How should we break the stories down?

A. By workflow: CFA intake, Kafka overlay, cheap query, TOC extension, document/env hygiene
B. One story group per FR block (FR1 through FR6)
C. By persona only (implementer stories, then operator stories)
X. Other (please specify)

[Answer]: A

### Question 4: What is Must Have for this document?

Delivery Planning will set the formal MVP line. Story priority here is the input to that.

A. Must Have = first ingest slice (CFA receipt → validate → apply + history) and first cheap-lookup slice, including the three overlay types; Should Have = TOC extension path and later-package layering; Could / Won’t follow requirements out of scope
B. Must Have = every FR1–FR6 and NFR1–NFR4 item in the architecture markdown
C. Must Have = document path and CFA apply only; overlay, cheap lookups, and env naming are Should Have
X. Other (please specify)

[Answer]:A
