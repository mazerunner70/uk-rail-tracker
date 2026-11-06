# Stories

One persona, the local reader. Four stories, one for each area they asked to keep wide: the station-pair screen, all trains now, how often the screen asks, and the three containers. Priority is MoSCoW. Acceptance criteria use Given/When/Then. The source is `requirements`. The store those stories read is described in `business-overview`. `component-inventory` records that this server and screen are not built yet. `team-practices` sets the test bar and the read-only SQLite file.

The first running slice is US1.1, with the database already holding the timetable. US1.2, US2.1, and US3.1 are still Must Have for the whole service.

## US1.1 Station pair

**Priority:** Must Have

As a local reader, I want to see today's London Midland trains that call at two stations I choose, in that order, so that I can tell where each train is and which ones have a problem.

**Depends on:** US3.1, because the screen reads the SQLite file the database container holds.

### Acceptance criteria

**AC1.1.1**
Given the station-pair screen is open
When one of the two station codes is empty
Then the screen does not ask the server
When both codes are non-empty
Then the screen asks the server
And each code is compared, ignoring case, with `calling_points.crs`

**AC1.1.2**
Given both station codes are filled in
When the screen asks the server
Then a train is included only when `toc` is `LM` and one journey's `calling_points.crs`, ordered by `seq`, has the first code on an earlier row than the second
And every journey that meets that rule and the today rule in AC1.1.3 is included
And a match on `origin_crs` and `dest_crs` alone does not include the train

**AC1.1.3**
Given today is the Europe/London date of the machine running the server, written as `YYMMDD`
When the server builds the pair answer
Then it joins `service_day` on `train_uid` and that date
And a running journey is included only when that row's `service_guid` equals the journey and `cancelled` is not 1

**AC1.1.4**
Given an included journey
When the screen shows where it is
Then the reached stop is the greatest `seq` whose stop time is at or before the server's Europe/London `HHMM`
And the next stop is the following `seq`
And the screen shows `calling_points.crs` for those stops
And it does not invent a point between them
And the stop time is the first present value among `forecast_dep`, `forecast_arr`, `public_dep`, and `public_arr`
And a `HHMM` earlier than the lowest-`seq` row's stop time is the next calendar day, because a schedule can run for a couple of hours after midnight
And when no row is at or before now, there is no reached stop and the next stop is the lowest `seq`
And when the reached stop is the last row, there is no next stop

**AC1.1.5**
Given an included running journey
When the screen lists it
Then the row shows `service_guid`, the two station codes, the reached stop's `crs`, the next stop's `crs`, and whether it has an issue
And an issue is `journeys.status` of `deactivated`, or any calling point whose `forecast_arr` differs from `public_arr`, or whose `forecast_dep` differs from `public_dep`
And a null forecast does not differ from the booked time
And dates in the answer are `YYMMDD` text and times are `HHMM` text

**AC1.1.6**
Given a `service_day` row for today with `cancelled` = 1, joined on `train_uid`, and that `train_uid` has a journey whose ordered `calling_points.crs` include the pair
When the screen shows the pair
Then that row is an issue only when that journey has `toc` = `LM`
And the row shows `train_uid` and the word `cancelled`
And the reached stop and the next stop are absent
And it is included when `service_day.service_guid` is null
And it is not shown as a train that is running

**AC1.1.7**
Given the station-pair screen
Then the first box is labelled as the earlier station and the second box is labelled as the later station
And those boxes are the same two codes named at the top of all trains now
When this screen has no held answer and an attempt is in flight
Then the screen says "Loading"
When that first attempt fails
Then the screen says "No data can be retrieved"
When the answer has no trains
Then the screen shows the words "No trains"
And a control on this view opens all trains now

### INVEST

Independent of US1.2. It needs the database container from US3.1. Negotiable on layout. Valuable as the first screen. Estimable as one view. Small enough for one slice. Testable from the criteria above.

## US1.2 All trains now

**Priority:** Must Have

As a local reader, I want to see the London Midland trains running today, so that I can see the whole network, where each train is, and which of those trains have a problem.

**Depends on:** US3.1. It does not depend on US1.1.

### Acceptance criteria

**AC1.2.1**
Given today is the Europe/London date of the machine running the server, written as `YYMMDD`
When I open all trains now
Then the list is exactly the journeys with `toc` = `LM` whose `service_day` row for that `train_uid` and date has `service_guid` equal to the journey and `cancelled` not 1

**AC1.2.2**
Given a journey on that list
When the screen shows the row
Then the row shows `service_guid`, the reached stop's `crs`, the next stop's `crs`, and whether the journey has a problem
And the reached stop is the greatest `seq` whose stop time is at or before the server's Europe/London `HHMM`
And the next stop is the following `seq`
And the screen shows `calling_points.crs` for those stops
And it does not invent a point between them
And the stop time is the first present value among `forecast_dep`, `forecast_arr`, `public_dep`, and `public_arr`
And a `HHMM` earlier than the lowest-`seq` row's stop time is the next calendar day, because a schedule can run for a couple of hours after midnight
And when no row is at or before now, there is no reached stop and the next stop is the lowest `seq`
And when the reached stop is the last row, there is no next stop
And a problem is `journeys.status` of `deactivated`, or a `forecast_arr` that differs from `public_arr`, or a `forecast_dep` that differs from `public_dep`
And a null forecast is not a problem
And a today cancellation is not a problem on this view

**AC1.2.3**
Given a `service_day` row for today with `cancelled` = 1
When I open all trains now
Then that `train_uid` is not on the list

**AC1.2.4**
Given either view
When the server builds the answer
Then it does not call `list_journeys()`

**AC1.2.5**
Given all trains now is open
Then the earlier station and the later station are named at the top of this screen
And a control there opens the station-pair screen with those codes
When this screen has no held answer and an attempt is in flight
Then the screen says "Loading"
When that first attempt fails
Then the screen says "No data can be retrieved"
When the answer has no trains
Then the screen shows the words "No trains"

### INVEST

Independent of the pair screen. Valuable as the network picture. Estimable as one list. Testable from the criteria above.

## US2.1 How often the screen asks

**Priority:** Must Have

As a local reader, I want an open view to ask again on a timer I can change, so that the board stays current without me opening it again.

**Depends on:** US1.1 or US1.2, because a view has to be open.

### Acceptance criteria

**AC2.1.1**
Given a view stays open and I have not changed the interval
When 30 seconds have passed since the previous ask was sent
Then the screen attempts to load again
And that attempt does not change the screen

**AC2.1.2**
Given the screen shows the interval as a whole number of seconds
When I change it to a positive whole number
Then that number replaces the wait already in progress, measured from the moment of the change
And the next ask uses it
When I enter a blank, a fraction, or a non-positive value
Then the change is refused and the interval already in use stays

**AC2.1.3**
Given I changed the interval
When I reload the page
Then the interval starts at 30 seconds again

**AC2.1.4**
Given the screen is showing a successful answer
When the next attempt fails
Then the held answer stays as it is
And the screen does not change
And the failure is not shown as an empty board of trains

**AC2.1.5**
Given a view asks the server
When this process has not yet read the SQLite file successfully
Then the request fails and the body says `missing`
When the file disappears after a successful read
Then the request fails and the body says `deleted`
When the file is locked
Then the request fails and the body says `locked`
When the file lacks one of the seven tables `journeys`, `calling_points`, `service_day`, `service_aliases`, `feed_meta`, `kafka_offsets`, and `applied_updates`
Then the request fails and the body says `older schema`

### INVEST

Depends on a view being open. Valuable because the board is about trains now. Testable with a clock and a forced failure.

## US3.1 Three containers

**Priority:** Must Have

As a local reader, I want the database, the server, and the screen to run as three containers on this machine, so that a restart or a rebuild of the database container still shows the same trains.

**Depends on:** none. US1.1 and US1.2 read the file this story keeps.

### Acceptance criteria

**AC3.1.1**
Given I start the service on this machine
When the containers are up
Then one container holds the SQLite file, one runs the server, and one runs the screen
And there is no account and no hosted or cloud deployment

**AC3.1.2**
Given the database container holds a SQLite file with journey rows
When that container is restarted, and when its image is rebuilt
Then the same file and the same journey rows are still there

**AC3.1.3**
Given the reader handles a view request, or a reader test runs
When that request or test finishes
Then the SQLite file is byte-for-byte unchanged
And the reader did not create it

**AC3.1.4**
Given the reader is tested
When the suite runs
Then it includes unit tests and a thin check of the station pair, all trains now, and problem marking
And the screen has tests once the TypeScript UI exists
And the fixtures are synthetic
And there is no line-coverage percentage gate

**AC3.1.5**
Given the TypeScript server and screen
When the local checks run
Then they include secret scanning, dependency checks, and a dynamic check of the local reader
And this service does not add a continuous-integration pipeline

**AC3.1.6**
Given the server, the screen, and these stories
When they are read
Then they do not contain Kafka or Darwin secret values

### INVEST

This is the base the views need, so those views are not fully independent of it. The containers, the surviving file, and the read-only open are the part the first slice needs. The test and scan checks in AC3.1.4 and AC3.1.5 stay in this same story and are run once those views exist. Valuable because the trains survive a rebuild. Testable by restarting the database container and comparing the file.

## Out of the stories

These stay out, matching `requirements`: accounts, a hosted deployment, a GPS position between stops, operators other than London Midland, importing `schedule_poc`, the reader creating or rewriting the SQLite file, the Android app, a continuous-integration pipeline, and `list_journeys()` as the reader.
