**Collaborator:** aidlc-developer-agent

## Contribution

The revision is specific enough to code in TypeScript. Reached and next stops are `calling_points.crs`. A today cancellation whose `service_guid` is null shows `train_uid` and the word `cancelled`, with the reached stop and the next stop absent, and it is not a running train. `missing` means this process has not read the SQLite file successfully. `deleted` means the file disappeared after a successful read. Locked and older schema stay their own words. Station codes match `calling_points.crs` by case-insensitive equality. AC1.1.7 and AC1.2.5 add the earlier-station and later-station labels, a loading line while an ask is in flight, the words "No trains" for an empty answer, and a control on each view that opens the other. US1.1 stays the first slice, with the database already holding the timetable. "No trains" is that empty answer. A failed ask stays a failure and keeps the last good answer. US3.1 stays one story. The three containers, the surviving file, and the read-only open are what the first slice needs. AC3.1.4 and AC3.1.5 stay in that story and run once the views exist. The `service_day` join on `train_uid`, the greatest `seq`, and the 30 seconds counted from the previous ask being sent are the rules the queries and the timer can follow.

## Positions

AGREE: Reached and next stops show `calling_points.crs`. The greatest `seq` at or before the server `HHMM` is the reached stop, the following `seq` is the next stop, and the no-reached and no-next cases are stated.
AGREE: A null `service_guid` cancellation shows `train_uid` and the word `cancelled`, with both stops absent, and is not shown as a running train.
AGREE: `missing` is a process that has not read the file successfully, and `deleted` is a disappearance after a successful read. A failure is separate from the words "No trains".
AGREE: Each station code is compared, ignoring case, with `calling_points.crs`.
AGREE: AC1.1.7 and AC1.2.5 are the earlier and later labels, a loading line, the words "No trains", and a control between the two views.
AGREE: US3.1 stays one story. The containers, the surviving file, and the read-only open support US1.1. AC3.1.4 and AC3.1.5 run once the views exist.
AGREE: The `service_day` join, the timer counted from the ask being sent, and a positive whole number replacing the wait already in progress are one screen slice for US2.1.
