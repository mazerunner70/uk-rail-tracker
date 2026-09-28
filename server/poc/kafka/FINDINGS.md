# Darwin PubSub Kafka POC — Findings

Summary of what we learned consuming the **RDM Darwin PubSub (Kafka)** product, for incorporation into the server plan. Reference spec: [P75301004 Push Port Data Interface Specification Issue 27](https://www.raildeliverygroup.com/) (Push Port message semantics); transport: [RSPS5053](https://www.rspaccreditation.org/publicDocumentation.php#RSPS5053) (RDM Kafka product).

---

## 1. Transport: RDM Kafka vs direct Push Port STOMP

The main `PLAN.md` describes Darwin as **ActiveMQ/STOMP** (direct Push Port). This POC validates a **different RDM product**: Darwin delivered **via managed Kafka** on the Rail Data Marketplace.

| Aspect | Direct Push Port (STOMP) | RDM PubSub Kafka (this POC) |
|--------|--------------------------|-----------------------------|
| Protocol | STOMP over TCP to Darwin | SASL_SSL + PLAIN to RDM bootstrap |
| Message store | Short reconnect buffer (~5 min) | Kafka retained log (partition watermarks) |
| Encoding | gzip XML (`Pport`) | JSON / XML / Avro topics (separate) |
| Consumer model | Persistent STOMP client | Kafka consumer (`assign` + `poll`) |
| Payload semantics | Push Port §3.3 | **Same** — still `uR`/`sR` + child elements |

**Plan implication:** Live ingest can target **RDM Kafka** instead of (or before) operating a STOMP client. Push Port decode/classify logic is shared; only the transport layer differs. SQS/buffering remains an optional downstream choice.

---

## 2. Kafka message envelope (JSON topic)

Each Kafka record value is JSON. Darwin content is nested:

```text
Kafka record.value
└── { "bytes": "<UTF-8 JSON string>" }   ← sometimes other envelope keys
         └── Push Port payload
              ├── ts          message timestamp (ISO 8601)
              ├── version     schema version (e.g. "18.0")
              └── uR | sR     update | snapshot wrapper
                   ├── updateOrigin, requestSource, requestID  (metadata)
                   └── [update type element(s)]              (data)
```

Observed broker-side fields (when viewing via ActiveMQ-style tooling) such as `messageType: "bytes"`, `deliveryMode`, `priority` are **transport metadata** — not Push Port update types.

**Decoding:** `json.loads(kafka_value)` → `json.loads(envelope["bytes"])`. No Avro/protobuf/decryption on the JSON topic. Payload is plain UTF-8 JSON.

---

## 3. Identifying update types

Section 3.3 lists ~12 categories of Push Port data. **They are not all present in every message.** The spec states:

> *In a typical message there will be just a single item, but clients must handle the presence of any number of items (including zero).*

### How to classify

1. **`uR`** → live update; **`sR`** → snapshot (same child semantics).
2. **Child element names under `uR`/`sR`** → update type(s).
3. **Ignore metadata keys** on the wrapper: `updateOrigin`, `requestSource`, `requestID`.

| Element key | Spec § | Purpose |
|-------------|--------|---------|
| `schedule` | 3.3.1 | Full journey timetable (new/changed) |
| `deactivated` | 3.3.1 | Service ended (RID only; updates only) |
| `association` | 3.3.2 | Splits, joins, associations |
| `scheduleFormations` | 3.3.3 | Train formation / coach makeup |
| `TS` | 3.3.4 | Actual & forecast times, platforms, lateness |
| `formationLoading` | 3.3.5 | Per-coach loading |
| `serviceLoading` | 3.3.6 | Service-level loading |
| `trainOrder` | 3.3.7 | Train order at junctions |
| `OW` | 3.3.8 | Station messages / suppression |
| `trainAlert` | 3.3.9 | Train alerts |
| `trackingID` | 3.3.10 | TD berth corrections (updates only) |
| `alarm` | 3.3.11 | Alarms |
| `stationInformation` | 3.3.12 | Station info (e.g. lifts) |

**Examples observed:** both sample messages were `uR` → **`TS` only**. `updateOrigin: "TD"` vs `"CIS"` indicates **source**, not type.

**Empty `uR`:** valid DCIS acknowledgement with no data change (spec §3.3).

### Classification pseudocode

```python
UPDATE_TYPES = {"schedule", "deactivated", "association", ...}  # see timestamp-consumer.py

def classify(payload: dict) -> list[str]:
    body = payload.get("uR") or payload.get("sR") or {}
    return sorted(k for k in body if k in UPDATE_TYPES)
```

---

## 4. TS updates (most common for live boards)

Section 3.3.4 — actual/forecast information. Key fields:

| Field | Meaning |
|-------|---------|
| `rid` | Darwin Run ID (unique per run) |
| `uid` | Train UID (timetable slot, not unique per run) |
| `ssd` | Schedule start date |
| `LateReason` | Late-running reason code (v17+; lookup in Reference Data) |
| `Location` | One or more location updates |

### Location shape varies by activity

**Passing point** (train does not stop):

```json
{ "tpl": "BAMFORD", "wtp": "14:57:30", "pass": { "et": "14:58", "src": "TD" }, "plat": {...} }
```

**Calling point** (passenger stop):

```json
{
  "tpl": "STPXBOX", "wta": "14:47:30", "wtd": "14:48:30",
  "pta": "14:48", "ptd": "14:48",
  "arr": { "et": "14:57", "src": "Darwin" },
  "dep": { "et": "14:58", "src": "Darwin" },
  "plat": { "platsrc": "A", "conf": "true", "": "B" },
  "length": "8"
}
```

| Field | Passing | Calling |
|-------|---------|---------|
| Working scheduled | `wtp` | `wta`, `wtd` |
| Public scheduled | — | `pta`, `ptd` |
| Forecast | `pass.et` | `arr.et`, `dep.et` |
| Source | `pass.src` | `arr.src`, `dep.src` |

### JSON/XML quirk

Platform numbers from XML element text appear as `"plat": { "": "1" }` or `"": "B"`. Normalise with `plat.get("") or plat.get("#text")`.

### `Location` cardinality

Single location → JSON **object**. Multiple → JSON **array**. Always normalise to a list before processing.

---

## 5. Multi-item messages

Two levels of “batching” — do not conflate:

| Level | What batches | Example |
|-------|--------------|---------|
| **Push Port payload** | Multiple update types in one `uR` | `{ "uR": { "schedule": {...}, "TS": {...} } }` |
| **Kafka fetch** | Multiple Kafka records prefetched by librdkafka | Invisible to app unless using `consume(N)` |

A single Kafka record = one Push Port envelope, but that envelope may contain **0, 1, or many** update elements under `uR`.

Snapshots (`sR`) may collate many services; live updates are typically one element per message.

---

## 6. Consumer design (validated in POC)

### Process one message at a time

Kafka records are atomic units of offset/commit. The POC uses `poll(1.0)` → one message per loop iteration. This matches idempotent upsert processing and clear failure boundaries.

### Fetch in batches underneath

librdkafka prefetches into a local queue. Tune via `fetch.min.bytes`, `fetch.wait.max.ms`, `max.partition.fetch.bytes` for backlog replay — without changing the one-message processing model.

For high-throughput catch-up, `consumer.consume(num_messages=N)` is an alternative at the application layer.

### Offsets

RDM may reject consumer-group joins. POC uses **manual partition assign** + **local offset file** (`.offsets/{format}.json`) rather than Kafka group commits. Production should decide: local offset store vs approved group semantics.

### Partitions & ordering

Kafka topic has **two partitions** (consistent with STOMP Push Port). Messages for the same `rid` may arrive out of order across partitions. Server must:

- Upsert idempotently by `(rid, tpl, …)` or
- Use per-partition ordering + `PushPortSequence` gap detection if strict ordering is required

### Topic encodings

Three structured topics: **json**, **xml**, **avro** (plus base). Pick one for production; JSON is simplest for v1. Avro may need explicit schema from RDM reference material.

---

## 7. Reference data required for display

Raw messages are not board-shaped. Codes need lookup tables (Timetable Reference Data, §4.2.2):

| Code | Example | Lookup |
|------|---------|--------|
| `tpl` | `BAMFORD`, `STPXBOX` | TIPLOC → CRS, name |
| `LateReason` | `841`, `832` | Reason text |
| `platsrc` | `M`, `A` | Manual vs automatic |
| TOC, via texts, CIS instance codes | — | Reference data file |

---

## 8. Server processing pipeline (recommended)

```text
Kafka consumer (poll, one record)
  → decode envelope (JSON/XML/Avro)
  → parse Push Port payload (inner JSON or XML)
  → classify: wrapper (uR/sR) + update types
  → route by type:
       schedule      → timetable / service identity store
       TS            → live state overlay (ETD/ETA, platform, delay)
       deactivated   → mark service inactive
       association   → split/join graph
       …             → as needed for product scope
  → filter (CRS/route allowlist — client-side, national feed)
  → idempotent upsert (Postgres / Redis / S3 raw archive)
  → commit local offset after successful write
```

**State requirement:** Maintain `rid` → `SVC-…` (or internal service key) mapping from schedule messages before TS updates are fully meaningful for a new run.

**Primary live path for boards:** `TS` updates on known services. `schedule` for identity and calling-point structure. `deactivated` for cleanup.

---

## 9. POC artefacts

| File | Role |
|------|------|
| `consume.py` | Full message decode + print (json/xml/avro) |
| `timestamp-consumer.py` | Log `ts` + update types (`TS`, `schedule`, …) per message |
| `metadata.py` | Partition watermarks, retained count, lag |
| `consumer_utils.py` | Assign, poll, local offset resume |
| `config.py` | RDM credentials + topic names from `.env` |

Run:

```bash
python timestamp-consumer.py json          # live stream: timestamp + types
python consume.py json --from-beginning --max-messages 10
python metadata.py json
```

---

## 10. Open questions for plan / Phase 1

1. **Kafka vs STOMP host:** Same Fargate pattern either way. Prefer Kafka; STOMP only if PubSub unavailable. **Fargate remains valid** — see PLAN §4.3 reassessment.
2. **Retention window:** How long does RDM retain Kafka history? Drives `--from-beginning` replay and disaster recovery.
3. **Consumer group policy:** Can production commit offsets to the RDM-managed group, or must we keep a durable external offset store (DynamoDB/S3 — not Fargate local disk)?
4. **JSON schema:** Download official JSON schema from RDM reference material for validation (currently schema-informed parsing only).
5. **Volume metrics:** Run `timestamp-consumer.py` / `metadata.py` over 24h to size processor and confirm TS vs schedule ratio.
6. **Allowlist scope:** National feed — define initial CRS/route filter before Postgres write pressure (per PLAN §4.1a).

---

## 11. PLAN.md sections to update

- **§4.1a** — Add RDM Kafka PubSub as a proven transport alongside STOMP; clarify same Push Port payload semantics. **Done.**
- **§4.1 feeds table** — Row for **Darwin PubSub Kafka (RDM)**. **Done.**
- **§4.1c** — Push Port message format (new). **Done.**
- **§4.3** — Consumer options: `confluent-kafka` on Fargate replaces STOMP-only wording. **Done.**
- **Message types** — Element-key classification (`uR` children) in §4.1c. **Done.**
- **Phase 1 / Appendix A / §15–16** — Kafka path and decode examples. **Done.**
