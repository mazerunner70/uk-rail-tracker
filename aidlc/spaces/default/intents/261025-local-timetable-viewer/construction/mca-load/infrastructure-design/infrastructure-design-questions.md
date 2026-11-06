# Infrastructure Design Questions — mca-load

This pass maps the MCA load onto local Docker. It does not design AWS, a hosted pipeline, or the screen container's UI toolkit.

Already locked and not re-asked: three containers on this machine; no `dev`/`staging`/`prod`; no cloud; no CI pipeline; SQLite file `apps/local-timetable/db/timetable.sqlite` survives restart and rebuild; MCA directory `apps/local-timetable/mca`; this unit runs in the server container with the query; no accounts; no Kafka or Darwin secrets in this unit.

## Question 1: Where does the Docker Compose file live?

The three containers start together. The compose file has to sit somewhere this person can run.

A. `apps/local-timetable/compose.yaml`
B. The repository root
X. Other (please specify)

[Answer]: A

## Question 2: What keeps the database container running?

SQLite is a file, not a server. The container exists so the file can stay up when the server container stops, and so a SQL client can open the host path.

A. A small keep-alive image that only holds the bind-mount (no SQLite network server).
B. An image you specify (write the image name).
X. Other (please specify)

[Answer]: A
