# User Stories Assessment

## Decision

Execute.

## Rationale

This service is a screen one person uses on this machine. The requirements describe views, a refresh control, failures, and three containers. Stories turn those into checks a later build can pass or fail. Requirements alone do not name the person, the benefit, or the Given/When/Then checks.

## Factors considered

- The project is a brownfield reader of an existing timetable store, with a new server and a new screen.
- The work is user-facing. It is not a refactor, a bug fix, or developer tooling.
- There is one reader. The business rules for a station pair, where a train is, and what counts as a problem are specific enough to test.

## Where stories add the most value

- The first slice: a pair of stations, the trains on that route, where they are, and the issues.
- The network view of London Midland trains running today.
- The screen asking again, and showing a failed ask as a failure.
- The database container keeping the SQLite file across a restart and a rebuild.
