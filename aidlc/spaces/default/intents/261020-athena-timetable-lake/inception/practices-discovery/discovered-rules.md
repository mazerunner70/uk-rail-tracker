# Discovered hard constraints

Only constraints already stated by the human or locked in `project.md`. No new ALWAYS or NEVER rules from the interview carve-outs or from support reviews. Org conversation-language rules and Inception phase rules stay in their own files; they are not restated here. The coverage carve-out and architecture-first staged implementation live in `team-practices.md` and `evidence.md`, not as Forbidden lines.

## Mandated

- ALWAYS pass a fully resolved literal absolute path when running `rm -rf` (or equivalent recursive force delete); never a relative path and never a shell variable.
- ALWAYS create and use a project-local venv for Python dependency installs so they do not pollute the system interpreter.
- ALWAYS derive the CFA / timetable feed sequence from the HD Current File Reference (columns 33–39) and refuse when that field has no three digits rather than guessing from the zip basename.
- ALWAYS allow more than four questions when needed (up to about six), including follow-on questions.

## Forbidden

- NEVER run `rm -rf` (or equivalent recursive force delete) against a relative path or against a shell variable (`$VAR` / `${VAR}`).
- NEVER import `schedule_poc` from `live-sched`, or from a successor package that follows the live-sched ideas.
