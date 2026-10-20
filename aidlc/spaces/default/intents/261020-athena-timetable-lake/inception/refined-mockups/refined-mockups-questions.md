# Refined Mockups Questions

There is no traveller UI and no Ideation wireframes (classic skip). The Later Implementer’s surface is `docs/athena-timetable-lake.md`. These four questions settle how we mock that file.

### Question 1: What should the mockups show?

Stories are document-delivery. Design already treated the architecture file as the only product surface.

A. Information architecture of `docs/athena-timetable-lake.md` only — heading map, per-section sketches, and scan paths for each Must Have story
B. That document IA, plus later lake-operator or Athena query screens
C. An OpenAPI / CLI developer-experience spec instead of a document IA
X. Other (please specify)

[Answer]: A

### Question 2: How should a Later Implementer enter the file?

US1.1 already requires a stable heading set: environments, CFA receipt → validate → apply, cheap lookups, Kafka archive/overlay, TOC extension, later package layering, Deferred / not in this document.

A. That heading map, plus a story-to-heading table so each US points at one section
B. The heading map only — no story table
C. Sequential FR1–FR6 numbering instead of the workflow headings
X. Other (please specify)

[Answer]:A

### Question 3: What document conditions must the mockups show?

There are no UI screens. This is leftover UX wording for “what conditions can a reviewer see in the file.” Stories already require those as document checks (missing heading, refuse versus accept as words, Deferred list present, diagram plus text fallback).

A. Those document conditions only — no invented console or app screens
B. Invent app-console states (loading spinner, empty table, toast errors) even though this intent ships no UI
X. Other (please specify)

[Answer]:A

### Question 4: What accessibility bar applies?

A. WCAG 2.1 AA as it applies to a markdown file: heading order, no colour-only status, text fallback for every diagram
B. Full web-app AA (focus rings, 44px targets, skip links) even though this intent ships no UI
X. Other (please specify)

[Answer]: no AA just a usual arch md file

