# Story Map

Each story is listed with the units that realize it. This map is checked against components, decisions, requirements, and stories.

## Assignments

| Story | Units | Inside the unit |
| --- | --- | --- |
| US1.1 | screen, timetable-query | The screen paints the station pair. The query builds that list. |
| US1.2 | screen, timetable-query | The screen paints all trains now. The query builds that list. |
| US2.1 | screen, timetable-query | The screen owns the interval, load progress polls, and comms-lost paint. The query serves progress and train lists. |
| US3.1 | mca-load, timetable-query, screen | The MCA load replaces the file. The query reads it and does not replace it. The screen runs in its own container. The database container holds the file and is not a unit. |

## Within each unit

| Unit | Stories, in the order that unit checks them |
| --- | --- |
| mca-load | US3.1 |
| timetable-query | US3.1, US1.1, US1.2, US2.1 |
| screen | US3.1, US2.1, US1.1, US1.2 |

## Coverage

| Unit ID | Name | Stories |
| --- | --- | --- |
| U1 | mca-load | US3.1 |
| U2 | timetable-query | US1.1, US1.2, US2.1, US3.1 |
| U3 | screen | US1.1, US1.2, US2.1, US3.1 |

Every story is assigned. Every unit has at least one story. US1.1, US1.2, US2.1, and US3.1 each span more than one unit. US3.1 also depends on the database container, which holds the file and is not a unit.
