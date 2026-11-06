# Discovered hard constraints

Constraints already stamped in project memory, plus the hard constraints from this interview. A functional style where possible, and the fact that no formatter was requested, are not rules here.

## Mandated

- ALWAYS pass a fully resolved literal absolute path when running `rm -rf` (or equivalent recursive force delete); never a relative path and never a shell variable.
- ALWAYS create and use a project-local venv for Python dependency installs so they do not pollute the system interpreter.
- ALWAYS derive the CFA / timetable feed sequence from the HD Current File Reference (columns 33–39) and refuse when that field has no three digits rather than guessing from the zip basename.
- ALWAYS allow more than four questions when needed (up to about six), including follow-on questions.
- ALWAYS put new work on a short-lived branch and squash-merge that branch to `main`.
- ALWAYS write the new server and UI in TypeScript under `apps/local-timetable/`.

## Forbidden

- NEVER run `rm -rf` (or equivalent recursive force delete) against a relative path or against a shell variable (`$VAR` / `${VAR}`).
- NEVER import `schedule_poc` from `live-sched`, or from a successor package that follows the live-sched ideas.
- NEVER import `schedule_poc` from the local timetable server or UI.
- NEVER create or rewrite the SQLite file from the local timetable reader.
- NEVER create `dev`, `staging`, or `prod` for this service.
