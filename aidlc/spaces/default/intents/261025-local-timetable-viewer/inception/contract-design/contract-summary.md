# Contract Summary

The screen talks to the query over REST on the Docker network. The query starts the MCA load in the same process. The SQLite file is the store the load replaces and the query reads. There is no public API. These contracts follow unit-of-work, unit-of-work-dependency, components, and requirements.

## Contracts

| # | Provider Unit | Consumer | Mechanism | Owner |
| --- | --- | --- | --- | --- |
| 1 | timetable-query | screen | REST JSON on the Docker network, compressed when the body is large enough | timetable-query |
| 2 | mca-load | timetable-query | Function call in the same process | mca-load |
| 3 | mca-load | timetable-query | SQLite file in the database container | mca-load |

A SQL client on this machine opens the same file. That is not a contract we publish.

## 1. Screen to query

REST on the Docker network. JSON bodies where an ask has fields. Transport compression when the body is large enough. The query unit owns this spec. There is no version number. A change is a new build of the containers together.

The four asks:

- Start the load
- Poll load progress
- Network list
- Station-pair list

A hang, a closed connection, or any response the screen cannot read is a failed ask only after the call has finished. While the call is still open it is not yet a failed ask. When it fails, the held train list stays. The status bar says `comms is lost`. There is no retry. The next interval is the next ask. A later success clears that status line.

A missing, deleted, locked, or older-schema file is a failed train ask. The body names which of those four it is. A successful empty list is still a success. The list is empty.

```yaml
openapi: 3.0.3
info:
  title: timetable-query
  version: unversioned
servers:
  - url: http://server:8080
paths:
  /load:
    post:
      operationId: startLoad
      summary: Start the MCA load
      responses:
        "202":
          description: Load accepted, already running, or refused (see LoadProgress.state)
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/LoadProgress"
    get:
      operationId: getLoadProgress
      summary: Poll load progress
      responses:
        "200":
          description: Current load progress
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/LoadProgress"
  /network:
    get:
      operationId: getNetwork
      summary: Network list for today
      responses:
        "200":
          description: Running London Midland journeys for today
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/NetworkAnswer"
        "503":
          description: File missing, deleted, locked, or older schema
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Failure"
  /pair:
    post:
      operationId: getStationPair
      summary: Station-pair list
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [earlierCrs, laterCrs]
              properties:
                earlierCrs:
                  type: string
                laterCrs:
                  type: string
      responses:
        "200":
          description: Running journeys and cancellation rows for those two codes
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/StationPairAnswer"
        "503":
          description: File missing, deleted, locked, or older schema
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Failure"
components:
  schemas:
    LoadProgress:
      type: object
      required: [state, sentence]
      properties:
        state:
          type: string
          enum: [idle, running, succeeded, failed]
        howFar:
          type: string
          description: Required when state is running; text from 0 to 100
        sentence:
          type: string
    NetworkAnswer:
      type: object
      required: [calendarDate, trains]
      properties:
        calendarDate:
          type: string
        trains:
          type: array
          items:
            type: object
            required: [serviceGuid, problem]
            properties:
              serviceGuid:
                type: string
              reachedCrs:
                type: string
                nullable: true
              nextCrs:
                type: string
                nullable: true
              problem:
                type: boolean
    StationPairAnswer:
      type: object
      required: [calendarDate, earlierCrs, laterCrs, trains]
      properties:
        calendarDate:
          type: string
        earlierCrs:
          type: string
        laterCrs:
          type: string
        trains:
          type: array
          items:
            type: object
            required: [status, issue]
            properties:
              serviceGuid:
                type: string
                description: Required on running rows; null on cancellation-only rows
              trainUid:
                type: string
                description: Required when serviceGuid is null (FR1.6, FR3.3)
              cancellationLabel:
                type: string
                description: Present when the row carries the word cancelled and is not a running train
              status:
                type: string
              reachedCrs:
                type: string
                nullable: true
              nextCrs:
                type: string
                nullable: true
              issue:
                type: boolean
    Failure:
      type: object
      required: [reason]
      properties:
        reason:
          type: string
          enum: [missing, deleted, locked, older-schema]
```

The screen sends `Accept-Encoding` and the query may compress the JSON. The screen does not call `/network` or `/pair` until load progress is `succeeded`. It polls `GET /load` on the same interval that starts at 30 seconds.

## 2. Query to load

A function call in the same process. Not a published REST spec. The query unit is the only caller.

```yaml
kind: in-process
provider: mca-load
consumer: timetable-query
calls:
  - name: start
    returns: LoadProgress
    note: Returns promptly with idle, running, or failed while replace work continues asynchronously
  - name: progress
    returns: LoadProgress
```

## 3. SQLite file

The MCA load replaces this file. The query only reads it. The database container holds it at `apps/local-timetable/db/timetable.sqlite` on this machine. Seven tables. Dates `YYMMDD`. Times `HHMM`. London Midland only after a load.

```yaml
kind: shared-schema
store: sqlite
path: apps/local-timetable/db/timetable.sqlite
writer: mca-load
reader: timetable-query
tables:
  - name: journeys
    identifier: service_guid
    attributes:
      - service_guid
      - train_uid
      - run_date
      - date_runs_to
      - days_run
      - origin_crs
      - dest_crs
      - origin_std
      - toc
      - stp_indicator
      - status
  - name: calling_points
    identifier: service_guid, seq
    attributes:
      - service_guid
      - seq
      - tiploc
      - crs
      - public_arr
      - public_dep
      - forecast_arr
      - forecast_dep
      - activity
  - name: service_day
    identifier: train_uid, calendar_date
    attributes:
      - train_uid
      - calendar_date
      - service_guid
      - cancelled
  - name: service_aliases
    identifier: alias_type, alias_value
    attributes:
      - alias_type
      - alias_value
      - service_guid
  - name: feed_meta
    identifier: key
    attributes:
      - key
      - value
  - name: kafka_offsets
    identifier: topic, partition
    attributes:
      - topic
      - partition
      - next_offset
      - updated_at
  - name: applied_updates
    identifier: id
    attributes:
      - id
      - service_guid
      - update_type
      - rid
      - payload_json
      - applied_at
      - source_offset
older_schema: >
  Any of the seven FR7 tables missing, or any column the views read missing:
  forecast_arr, forecast_dep, public_arr, public_dep, seq, crs, status, toc,
  calendar_date, cancelled (FR6.3)
```

## Contract ownership rules

- timetable-query owns the REST spec. Additive JSON fields are ignored by the screen.
- mca-load owns the SQLite file shape. The query fails an older schema and names it.
- mca-load owns the two in-process calls. They are not versioned.
- A breaking change is a new build of the containers together. There is no version field on the wire.
- No secret belongs in these contracts.

## Open questions

| Contract | Question | Blocks |
| --- | --- | --- |
| None | None | None |
