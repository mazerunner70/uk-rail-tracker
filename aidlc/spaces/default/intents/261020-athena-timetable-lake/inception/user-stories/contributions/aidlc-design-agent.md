**Collaborator:** aidlc-design-agent

## Contribution

Round 2: the revised draft is the document-UX contract I asked for. Design and Refined Mockups stay on `docs/athena-timetable-lake.md` information architecture — not maps, dashboards, or Android journeys.

Folded and now checkable in the stories:

1. **Heading map** — AC1.1.5 lists environments, CFA receipt → validate → apply, cheap lookups, Kafka archive/overlay, TOC extension, later package layering, and Deferred / not in this document. US6.1 stays Should Have and remains on the page map.
2. **Document-reader GWT** — preamble plus every Must/Should When is `When I read …` / `When I scan …` / `When I search …`. No lake-operator actor remains.
3. **Overlay bind + miss** — AC4.1.4 names join keys and the miss path (archive kept, no new journey, booked times unchanged).
4. **Empty-lake identity bootstrap** — AC2.1.2 requires how first identities and the CRS/TIPLOC map enter the current plan (supporting MCA baseline or another named path).
5. **One Deferred list** — AC1.1.6 mirrors US7.1–US7.5 (and deferred cloud controls).
6. **Consistent product label** — every story and AC uses `docs/athena-timetable-lake.md`.
7. **Worked examples** — AC3.1.2 (plan lookups) and AC4.1.5 (live overlay) each require one train / one calendar date.
8. **Env-qualified logical names only** — AC1.1.2 / AC2.1.1 / AC4.1.1 leave exact bucket strings as architecture choices.

Leftover Design-stage note (not a story OBJECT): headings in logical order; refuse-versus-accept tables must not use colour alone. AC1.1.5 already requires a text fallback on diagrams.

## Positions

- AGREE: Persona set is Later Implementer only, with traveller and future lake operator explicitly out — matches Q1 A and keeps Design off a traveller UI.
- AGREE: Story titles and US1.1 ACs are document-delivery (open the repo, read the file, no secrets) — matches Q2 A and P1’s actual task.
- AGREE: Breakdown by workflow (env hygiene, CFA, cheap query, overlay) plus the story map — matches how P1 re-enters the file for one construction slice at a time.
- AGREE: Must Have = first ingest + cheap lookups + three overlay types; Should Have = TOC extension and layering — matches Q4 A and P1’s primary build goals.
- AGREE: CIF/CFA/STP/TOC jargon and Won’t Have rows US7.1–US7.5 — right language for High tech comfort, and they tell P1 what not to look for.
- AGREE: Heading map, Deferred section, consistent `docs/athena-timetable-lake.md` label, and worked examples — AC1.1.5, AC1.1.6, AC3.1.2, AC4.1.5 give P1 recognition-not-recall on re-entry.
- OBJECT (resolved): AC2.1.2–AC2.1.4, AC4.1.1–AC4.1.2, AC5.1.1, and AC6.1.3 use lake-operator voice (“when validation runs”, “when it lands”) — Q1 A / Q2 A make P1 a document reader, and that voice invites a fake operator UI in later design. Folded: every listed AC is now a document-read When; preamble states GWT is a document check.
- OBJECT (resolved): AC4.1.4 does not require the document to name overlay bind keys or the overlay-miss path — this is P1’s stated pain “guessing overlay bind keys”, not just a forbidden anti-pattern. Folded: AC4.1.4 names join keys and the miss path.
- OBJECT (resolved): US2.1 specifies empty-store sequence seed `990` but not how first identities enter the lake — this leaves P1’s stated pain “guessing empty-lake bootstrap” (requirements MCA-as-supporting-context never appears in a story). Folded: AC2.1.2 requires the MCA baseline or another named path.
