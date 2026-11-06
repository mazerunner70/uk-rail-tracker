**Collaborator:** aidlc-design-agent

## Contribution

The revision settles the screen states the local reader had to guess.

The first box is labelled as the earlier station and the second as the later station. One empty code does not ask. Both non-empty codes ask, and each code is matched to `calling_points.crs` ignoring case. While an ask is in flight the screen shows a loading line, on the pair and on all trains now. An answer with no trains shows the words "No trains". A later failed ask stays a failure and keeps the last good answer, so a wait, an empty board, and a bad file read as three different things.

A control on the pair opens all trains now, and a control on all trains now opens the pair.

A running pair row shows `service_guid`, the two codes, and `calling_points.crs` for the reached stop and the next stop, so the stop text is a code the reader can match to what they typed. No reached stop, and no next stop on the last row, stay absences. A today's cancellation shows `train_uid` and the word `cancelled`, including when `service_guid` is null, with those stops absent, and it is not shown as a running train. All trains now does not list that cancellation. On the pair that row is an issue. On all trains now a problem is only the deactivated-or-forecast rule. The reader can tell a cancelled train from a running one.

A refused interval leaves the number already in use on screen. The body says `missing` only before the first successful read, and `deleted` after one, plus `locked` and `older schema`. US3.1 stays one story with the other three. Its tests and scans run once the views exist.

## Positions

AGREE: Labelled station boxes, the loading line, the words "No trains", and a control each way between the views, as AC1.1.7 and AC1.2.5.

AGREE: Stop text is `calling_points.crs`, a null-`service_guid` cancellation shows `train_uid` and `cancelled`, and `missing` and `deleted` are different words.

AGREE: US3.1 stays one wide story. The four stories match the human's choice, and the test and scan checks run once the views exist.
