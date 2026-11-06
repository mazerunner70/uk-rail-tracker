**Collaborator:** aidlc-developer-agent

## Contribution

Developer review of naming, layer boundaries, error handling, file organization, and code style for the local timetable viewer. Sources are the four lead drafts, `code-structure.md`, `architecture.md`, `technology-stack.md`, and `docs/future-intents/local-timetable.md`. No new ALWAYS or NEVER line. `discovered-rules.md` correctly restates only the constraints already stamped in `project.md`.

### Naming

These tokens already match the SQLite file and the page, and they stay: `train_uid`, `service_guid`, `stp_indicator`, `origin_crs`, `dest_crs`, `run_date`, `days_run`; dates as `YYMMDD` text; times as `HHMM` text; feed sequence as a zero-padded three-digit string; CIF record codes and STP letters as CIF tokens; CFA folder statuses `accepted`, `refused`, `failed`, `nothing`. The page's `service_guid` shape `SVC-{originCrs}{destCrs}{runDateYYMMDD}{originStdHHMM}` is the existing identity string. The camelCase fragments inside that string belong to that formula.

`server/PLAN.md` uses `serviceGUID` for a future bridge. The reader and the page use `service_guid`. The interview keeps `service_guid` as the reader's public name. UI identifier case waits until the toolkit is chosen. SQLite column names stay as they are.

### Layer boundaries

`architecture.md` records the reader as a separate process, absent from `server/poc/live-sched/`. The three views (LM trains today, an ordered station pair, latest stop and next stop) are reads the current `ScheduleRepository` does not offer: it cannot list a calendar day, and it cannot find a journey by an ordered pair of CRS codes.

The interview locks this boundary:

- The reader is its own tree, with its own entry point, its own `pyproject.toml`, and its own tests when the server is Python. The grain matches `live-sched` beside `schedule_poc`.
- The home path is still open: `server/poc/<reader>/` or `server/<reader>/`. The UI is its own tree. `android/` stays the existing client unless the human chooses that app as this UI.
- The three views stay out of `cif_loader.py`, `cfa_apply.py`, and `repository.py`. The reader does not import `schedule_poc`.
- Calendar-day and ordered station-pair reads are new reader code. `list_journeys()` scans every journey and can bind a `rid` to the wrong STP row (`architecture.md`). That scan stays out of these views.
- `cfa_apply.py` reaches `ScheduleRepository._conn` under `# noqa: SLF001`. New code calls a public repository method. The reader has no feed-meta write. The private-connection ban is a live-sched defect, so it stays in the style section after Athena modules, object-store clients, and S3 arrival drop out as lake policy.

### Error handling

Writer policy stays on the writers. Ordered CFA intake is fail-closed on the HD sequence. Live Kafka overlay is fail-open after the raw archive exists. The reader inherits neither policy. Mapping domain exceptions to CLI exit codes is the live-sched edge. The reader has no CLI. The interview names the reader's edge: report failure at the request boundary, and keep going only for a failure the human has called recoverable.

The reader is read-only against the existing file. These writer side effects are already in `architecture.md`:

- `load` deletes the SQLite file when the path is an existing file.
- `connect` creates a missing database file.
- `get_feed_sequence` commits seed `990` when the key is absent.

A read path that calls those helpers writes. The interview states how the reader reports four cases: the file is missing, `load` has deleted it, the file is locked, and the schema is older than the one `cfa-update` knows (`cfa-update` leaves an older file's columns unchanged). Those four reports are this service's error policy.

The stamped HD sequence rule stays a writer rule. Current code can still take a sequence from the inner member name when the HD field has no three digits (`architecture.md`). That is a writer defect. This review leaves the stamped rule as it is and gives the reader no sequence policy.

### File organization

`code-structure.md` shows `server/poc/live-sched/` as one package plus `tests/`, `fixtures/`, `demo/`, `scripts/`, `data/`, and `output/`. `technology-stack.md` records no Dockerfile, Makefile, or lockfile in that tree. The page requires a local Docker run and gives no image recipe. The image file, the server tree, and the UI tree sit beside `live-sched`, outside that package and outside `android/`, once the interview picks the paths. This service adds nothing under `live_sched/`.

Python style, if the reader is Python, copies the modules already read: Python 3.12, `from __future__ import annotations`, PEP 604 unions, frozen dataclasses for records, snake_case, `logging.getLogger(__name__)`. Live-sched has no Ruff, Black, Flake8, or mypy config. `server/pyrightconfig.json` covers `poc/kafka` only. With no linter config, the observed style still applies. Adding a formatter or linter is an interview choice, because this intent writes a server and a UI. The markdown-only sentence in the baseline leaves that choice open.

Secrets stay in the gitignored workspace-root `.env`. The reader does not print Kafka or Darwin keys. Architecture text does not list them. Installs use a project-local `.venv`.

### What the interview still decides

1. Server language and UI toolkit.
2. Paths for the server tree, the UI tree, and the Docker file. The reader stays outside `live_sched/`. The UI stays outside `android/` unless the human chooses the Android app.
3. The read-only rule, and the four reports (missing file, file deleted by `load`, lock, older schema) at the request boundary.
4. Calendar-day and ordered station-pair reads live in the new reader. They stay off `list_journeys()` and off `repo._conn`.
5. `service_guid` and the other store tokens are the reader's public names. UI case waits on the toolkit.
6. Formatter and linter config stay absent while CI is absent, or the human adds one for the new language.
7. Android `kotlin.code.style=official` stays out of this service unless the UI is that app.

## Positions

AGREE: `discovered-rules.md` restates only the hard constraints already stamped in `project.md` — a support review does not mint a new ALWAYS or NEVER line.
AGREE: Store tokens, secrets in gitignored `.env`, the project-local venv, and the ban on importing `schedule_poc` still bind a new reader.
AGREE: The Python copy-set (3.12, future annotations, PEP 604 unions, frozen dataclasses, snake_case, `logging.getLogger(__name__)`) applies only when the reader is Python.
AGREE: CFA fail-closed and Kafka fail-open stay writer policy; the draft is right that the reader does not inherit them by silence.
AGREE: Marking formatter silence and the markdown-only "no new linter" sentence as **Re-decide** is right, because this intent writes a server and a UI and live-sched has no formatter config.
OBJECT: The layering note still describes a lake sibling and folds the private-connection ban into that lake sentence — the interview must keep the reader as its own process and tree, outside `live_sched/`, and must keep new code off `repo._conn`.
OBJECT: Reader error handling is unnamed — the interview must state a read-only rule and how a missing, deleted, locked, or older-schema SQLite file is reported at the request boundary.
OBJECT: The three views have no repository query today — the interview must place calendar-day and ordered station-pair reads in the new reader and must keep `list_journeys()` out of that design.
OBJECT: Public naming for the new surface is only half set — the interview must keep `service_guid` (the page token) as the reader name and leave UI identifier case until the toolkit is chosen.
