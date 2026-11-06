# Personas

One persona. The stories are about the person who starts the three containers on this machine and reads the board. `requirements` names that one person. `business-overview` describes the London Midland store they read. `component-inventory` records that the server and the screen do not exist yet. `team-practices` keeps the screen in TypeScript under `apps/local-timetable/fe` and keeps the reader from writing the SQLite file.

## Local reader

- **Role:** The one person on this machine.
- **Goal:** See today's London Midland trains for two stations they choose, where each train is between stops, and which trains have a problem. Also see every London Midland train running today.
- **Pain points:** The store has no GPS track. A journey's overall origin and destination are not the pair they chose. A missing or locked database file must not look like an empty board.
- **Context:** They start the database, the server, and the screen as three Docker containers. The machine is kept on UK time. They have no account. There is no hosted deployment.
- **Priority:** Only persona. Rank 1.

## Relationships

There are no other personas. The same person starts the containers and reads the board.
