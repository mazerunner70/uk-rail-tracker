**Collaborator:** aidlc-quality-agent

## Contribution

Round 2 testability check against the revised `stories.md`. Judge remains: can QA tick pass/fail by reading `docs/athena-timetable-lake.md` in this intent (Q2 A). Later automation stays the NFR1 query-contract once tables exist (Standard volume; not this markdown).

Every Must/Should When is now a document check (`When I read …` / `When I open the repo` / `When I search` / `When I scan`). Lake-runtime Whens from round 1 are gone. AC IDs remain `AC{group}.{seq}.{n}` on US1.1–US6.1; US7.1–US7.5 still need none.

Folds that land as readable ACs:

| Prior gap | Where it landed |
|-----------|-----------------|
| Scan claims not pass/fail by reading | AC3.1.1 / AC3.1.3 name keys, grain, and an exact four-item list; NFR1 is AC3.1.4 (the file *names* the later fixture query) |
| Vacuous overlay-bind When | AC4.1.4 `When I read overlay bind` requires named join keys and forbids UID-wide `list_journeys()` |
| Zero / multi-member CFA refuse | AC2.1.4 |
| Overlay miss (FR4.2) | AC4.1.4 miss Then: archive kept, no new journey, booked times unchanged |
| Packed CFA apply | AC2.1.5 apply / AC2.1.6 history / AC2.1.7 atomic publish + mid-apply failure |
| “Tested” rollback unreadable | AC1.1.4 names the obligation; does not prove a test ran |
| NFR3 deferred controls | AC1.1.6 |
| FR2.5 replay source | AC2.1.3 |

Residual (not an OBJECT): AC2.1.3 When still says “sequence or HD refuse path” while HD-with-no-three-digit-run is asserted under AC2.1.4. Both Thens are still grep-able; no sad path is missing.

What QA can automate later is unchanged: path exists, secrets scan, reserved-type list, query-contract on named keys, apply/refuse tests once tables exist.

## Positions

- AGREE: AC IDs on US1.1–US6.1 — every Must/Should criterion already has `AC{n}.{m}.{seq}`; no ID gaps to mint from scratch
- AGREE: AC1.1.1–AC1.1.3 and AC4.1.3 — pass/fail by path, text search, or an exact reserved-type list
- AGREE: AC2.1.3 sequence refuse and AC2.1.7 mid-apply failure — sad paths exist for those cases and map to NFR4
- AGREE: US7.1–US7.5 table — negative scope is explicit; QA will not invent ACs for Won’t Have
- AGREE: revised draft is document-checkable (Q2 A) — every remaining When is a file read, not a lake-runtime
- RESOLVED: AC3.1.1 and AC3.1.3 scan claims — keys/grain/four-item list are readable now; NFR1 is AC3.1.4 (named later query-contract, not a live scan)
- RESOLVED: AC4.1.4 vacuous When — overlay bind is a required read; Then names join keys and forbids `list_journeys()`
- RESOLVED: AC2.1.3 sad-path gap — AC2.1.4 covers zero members, more than one CFA member, and HD with no three-digit run
- RESOLVED: AC4.1.2 sad-path gap — AC4.1.4 miss Then keeps archive, creates no journey, leaves booked times unchanged
- RESOLVED: Lake-runtime Whens on AC2.1.2–AC2.1.4, AC4.1.1–AC4.1.2, AC5.1.2 — all rewritten as `When I read …`
- RESOLVED: AC2.1.4 packed compound — split to AC2.1.5 / AC2.1.6 / AC2.1.7 so later apply and history tests can map 1:1
- RESOLVED: AC1.1.4 “tested rollback” — file names the tested-before-prod-cut obligation; it does not prove a test already ran
- RESOLVED: NFR3 deferred cloud controls — AC1.1.6 names KMS, bucket policies, and workgroup encryption as later work
