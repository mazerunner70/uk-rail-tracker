# Accessibility checklist

Refined-mockups Q4 answer: `no AA just a usual arch md file`. This is not a WCAG 2.1 AA claim and not a web-app checklist (no focus rings, 44px targets, skip links, or ARIA). It is the usual bar for an architecture markdown file in this repo, aligned with AC1.1.5 and `docs/architecture.md`.

Upstream: `stories.md`, `requirements.md`, `team-practices.md`. No `wireframes` or `user-flow`.

## Usual architecture-markdown checks

Use this list when reviewing `docs/athena-timetable-lake.md` (and these mockups).

| Check | Pass | Fail |
|-------|------|------|
| One H1, then H2 chapters in outline order | Outline matches the heading map in `mockups.md` | Skipped heading level, or FR1–FR6 used as the outline |
| Seven required H2 titles present | Environments; CFA receipt → validate → apply; Cheap lookups; Kafka archive/overlay; TOC extension; Later package layering; Deferred / not in this document | Any of those titles missing or renamed so a jump-to-heading fails |
| Refuse versus accept as words | The words refuse, accept, skip, reserved appear in prose | Status shown only by colour, emoji colour, or a legend that needs a colour key |
| Every mermaid has a text fallback | Numbered steps or a `text` tree immediately below the fence | Diagram-only chapter |
| Tables are readable as text | Header row names the columns; cells are words, not colour fills | A table that only makes sense when rendered with colour |
| No secrets | No Kafka keys, Darwin keys, or long-lived AWS access keys (NFR3, AC1.1.3) | Any credential value |
| Scannable re-entry | Story-to-heading table in these mockups; later file keeps the same H2 titles | Important rules buried only in a diagram |

## Out of this checklist

Do not add these as acceptance for this intent:

- WCAG 2.1 AA conformance statement
- Keyboard focus, skip links, 44×44 targets
- Screen-reader / ARIA specifications
- Contrast ratios for a rendered theme
- Lake-operator or Athena console accessibility

Those belong to a later UI intent if one exists.

## How to check

1. Open the markdown as source (not only a preview).
2. Scan the H2 outline against the heading map.
3. Search for refuse, accept, and reserved.
4. For each ` ```mermaid ` fence, confirm the next block is prose or a `text` tree.
5. Search for key-like strings (`AKIA`, `password`, broker secrets). None should appear.

## Sources

- Refined-mockups Q4 — usual architecture markdown, not AA
- `stories.md` AC1.1.5 — heading set, diagram fallback, refuse versus accept not colour-only
- `requirements.md` NFR3 — no secrets
- `design-system-mapping.md` — existing `docs/` patterns
