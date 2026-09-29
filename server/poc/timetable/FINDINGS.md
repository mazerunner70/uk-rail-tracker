# Timetable feed — SFTP receipt & CIF parse notes

**Status:** design notes for `poc/timetable/`  
**Spec:** [RSPS5046 P-04-02](./RSPS5046%20P-04-02%20Timetable%20Information%20Data%20Feed%20Interface%20Specification.pdf) (*Timetable Information Data Feed Interface Specification*, RSP / DTD, 03-Jun-2025)  
**Sample:** `RJTTF944MCA.txt` — full Basic Timetable Detail (CIF) refresh, sequence **944**  
**Related:** live Darwin Push Port / RDM Kafka in `poc/kafka/`; server identity design in `PLAN.md` §3

---

## 1. What we know about receiving via SFTP

### Who sends what

| Item | Detail |
|------|--------|
| **Publisher** | RDG **DTD** (Data Transformation and Distribution Service) |
| **Product** | CIF Timetable Data Feed (not Darwin Push Port XML) |
| **Transport** | **SFTP** only — FTP removed in P-04-00 (§3.1, §7) |
| **Modes** | **SFTP Pull** from `dtd.atocrsp.org`, or **SFTP Push** to the recipient’s SFTP server (§7.1.2) |
| **Config** | DTD Web Portal: https://dtdportal.atocrsp.org/ (IPs, push target, resilience) |
| **Packaging** | Zip 2.0 containing the day’s file set; `RJTTFnnn.DAT` / `RJTTCnnn.DAT` is the **manifest** (§7.2) |

### Our intended path (inbound push → S3)

DTD **SFTP Push** into an SFTP endpoint we control, backed by S3 (e.g. **AWS Transfer Family** → bucket). That avoids pulling from DTD on a schedule and matches “we maintain a bucket for them to send into.”

```text
DTD (nightly)  --SFTP Push-->  Transfer Family / SFTP
                                 └─► s3://…/timetable/raw/seq=nnn/*.zip
                                       └─► EventBridge / S3 notify
                                             └─► unpack → parse MCA (+ MSN) → curated
```

### Cadence (expect, don’t hard-code)

- Normal: ~**22:30–01:00** (§7.3.1)
- Latest attempt: **16:00**; after that an **Empty** feed (full refresh files from last good day + empty change files) (§7.3.2)
- New daily recipients start with a **full refresh** (§7.6)
- Weekly = Wednesday full refresh; monthly = first Wednesday of period (§7.7)

### File set (what lands in the zip)

| Extension | Role | Typical name |
|-----------|------|----------------|
| `.DAT` | Manifest of other filenames | `RJTTFnnn.DAT` (full) / `RJTTCnnn.DAT` (updates) |
| **`.MCA`** | **Full CIF refresh** (Basic Timetable Detail) | `RJTTFnnn.MCA` |
| `.CFA` | Daily **updates** to apply on top of last MCA | `RJTTCnnn.CFA` |
| `.MSN` | Master Station Names (CRS, interchange, etc.) | always full |
| `.FLF` / `.ALF` | Fixed links | always full |
| `.TSI` | TOC-specific interchange times | |
| `.ZTR` | Bus/ferry “Z-trains” (quasi-CIF) | refresh only |
| `.REJ` | Schedules rejected by DTD validation | |
| `.SET` | CIF set details (mostly fixed) | |
| `.ZIP` | Compressed bundle | |

`nnn` is the DTD sequence (001–999, then rolls).

**POC priority:** ingest **`.MCA`** (+ **`.MSN`** for station names if CRS mapping from TI is thin). Defer `.CFA` incremental apply until full refresh load is proven.

---

## 2. Sample file snapshot (`RJTTF944MCA.txt`)

| Metric | Value |
|--------|-------|
| Size | ~699 MB / **8 522 608** lines |
| Header | `HD` … Update Indicator **`F`** (full extract) |
| Trailer | `ZZ` on last line |
| Record counts | `LI` 6.7M · `BS`/`BX`/`LO`/`LT` ~402k schedules · `CR` 97k · `TI` 12 085 · **`AA` 5 842** · `HD`/`ZZ` 1 |

Observed file order matches §5.4.3:

```text
HD → all TI → all AA → all schedules (BS,BX,LO,LI*,CR?,LT)* → ZZ
```

First schedule in this file (~line 17929): Paddington → Heathrow T5 (`BS`/`BX`/`LO`/`LI*`/`LT`).

---

## 3. What does the **AA** line id mean?

**`AA` = Association record** (CIF record identity, first two characters of the 80-byte line).

It is **not** a station, TIPLOC, or “line id” in the railway sense. It links **two train UIDs** (join / split / next-previous) at a TIPLOC for a date/days range.

Do not confuse with the early file lines that look like `TIAACHEN…`:

| Prefix | Meaning |
|--------|---------|
| **`TI`** | TIPLOC **Insert** — location reference (`AACHEN` is the TIPLOC code) |
| **`AA`** | **Association** between two trains |

### AA field layout (RSPS5046 §5.5.8)

| Pos | Len | Field | Notes |
|-----|-----|-------|-------|
| 1–2 | 2 | Record identity | **`AA`** |
| 3 | 1 | Transaction | `N` / `D` / `R` |
| 4–9 | 6 | **Base UID** | Through train (not the joining/splitting portion) |
| 10–15 | 6 | **Assoc UID** | Other train |
| 16–21 | 6 | Assoc start | `yymmdd` |
| 22–27 | 6 | Assoc end | `yymmdd` |
| 28–34 | 7 | Assoc days | Mon–Sun flags |
| 35–36 | 2 | Assoc category | **`JJ`** join · **`VV`** divide · **`NP`** next/prev (**ignore for passenger**) |
| 37 | 1 | Date indicator | `S` standard · `N`/`P` over midnight |
| 38–44 | 7 | Assoc location | TIPLOC |
| 45–46 | 1+1 | Location suffixes | |
| 47 | 1 | Diagram type | `T` |
| 48 | 1 | Association type | **`P`** passenger · **`O`** operating (ignore if blank/`O`) |
| 80 | 1 | STP indicator | `P` permanent · `N`/`O`/`C` STP variants |

Example from the sample:

```text
AANC04446C043602605172608300000001   SHEFFLD  T                                C
│││└UID──┘└UID──┘└dates──┘└days─┘││ └TIPLOC┘
││N=new                          ││
AA                               VV/JJ etc. often here; STP at col 80
```

Associations are **independent of schedule blocks** (§5.3.4): marry by UID + overlapping dates yourself. For v1 passenger boards, keep **`JJ`/`VV` + type `P`**; drop **`NP`** / operating.

---

## 4. Sensible parse approach

### Principles

1. **Stream** the MCA — never load ~700 MB into memory; line-at-a-time, fixed-width slices.
2. **Dispatch on bytes 1–2** (`HD`, `TI`, `AA`, `BS`, `BX`, `LO`, `LI`, `CR`, `LT`, `ZZ`).
3. **Honour file order** — build TIPLOC map first, then associations, then schedules as groups.
4. **One schedule = one group** starting at `BS` until the next `BS`/`ZZ` (§5.4.3 / §5.4.5).
5. Prefer **public** times on `LO`/`LI`/`LT` for display and `serviceGUID` (§5.4.13).
6. Identity key for a schedule: **`(UID, Date Runs From, STP indicator)`** (§5.3.3.3).

### Recommended pipeline (POC → production)

```text
S3 object created (zip or .MCA)
  → validate manifest (.DAT) if present
  → gunzip/unzip
  → pass 1 optional: load TI (+ MSN) → tipoc→crs dict
  → pass 2: stream MCA
        AA  → association store (filter P + JJ/VV)
        BS  → flush previous schedule; start new
        BX/LO/LI/CR/LT → append to current schedule
  → CRS allowlist filter on any public calling TIPLOC
  → emit curated rows + draft serviceGUID (PLAN §3.1)
  → later: apply .CFA (N/D/R transactions) between full refreshes
```

### Record types that matter for us

| ID | Keep for v1? | Use |
|----|--------------|-----|
| `HD` | yes | Full vs update (`F`/`U`), sequence / extract metadata |
| `TI` | yes | TIPLOC → CRS / name (supplement with `.MSN`) |
| **`AA`** | yes (filtered) | Join/split graph for identity edge cases |
| `BS`+`BX` | yes | UID, dates, days, TOC, headcode, status |
| `LO`/`LI`/`LT` | yes | Calling pattern + public times |
| `CR` | optional | Mid-journey category/TOC change |
| `TA`/`TD` | CFA only | TIPLOC amend/delete |
| `ZZ` | yes | Completeness check |
| `TN`/`LN` | no | Not implemented (§5.4.7) |

### Full refresh vs daily update

| Feed | File | Strategy |
|------|------|----------|
| Full | `RJTTFnnn.MCA` | Replace published timetable snapshot for the extract window |
| Update | `RJTTCnnn.CFA` | Apply `N`/`R`/`D` on `BS`/`AA`/`TI` by UID+dates+STP; empty file = `HD`+`ZZ` only (§5.4.6) |

Until CFA apply is tested, **re-parse each full MCA** after nightly push is enough.

### Mapping to harvester identity

| CIF | Darwin / PLAN |
|-----|----------------|
| Train UID (`BS` 4–9) | `uid` / `train_uid` |
| Date Runs From + days-run | which calendar `run_date`s the schedule covers |
| `LO` public departure | origin STD → `serviceGUID` HHMM |
| TIPLOC via `TI`/`MSN` → CRS | `origin_crs` / `dest_crs` |
| Darwin `rid` | **not in CIF** — appears later from Push Port / Kafka when the run activates |

CIF is the **published timetable base**; Kafka `schedule`/`TS` overlay live state and `rid`.

### POC success checks

1. Stream-parse MCA without OOM; counts ≈ header/trailer complete.  
2. Resolve TIPLOC→CRS for allowlisted stations (e.g. MLK / APS / EUS).  
3. Emit a small JSONL of journeys touching that set with draft `serviceGUID`.  
4. `--prove APS`: intermediate stop shares UID/origin with MLK→EUS schedule.  
5. Parse a handful of `AA` `JJ`/`VV` rows and attach to UIDs (no need to expand fully in v1).

---

## 5. Open / operational notes

- Header extract dates on **full** refreshes can be a hardcoded quirk (§5.5.1.2) — don’t trust them as the timetable validity window alone; use `BS` date ranges.  
- Only **valid advertised passenger** trains and associations are in this feed (§5.4.9); freight/ECS absent.  
- Z-trains (`.ZTR`) are separate quasi-CIF — ignore for train boards v1.  
- Confirm with DTD portal: push credentials, path prefix, and whether we get **full daily** vs update-only zip.

---

## 6. Answer in one line

**`AA` means Association** — a fixed-width CIF record that links a base train UID to an associated UID (join `JJ`, divide `VV`, or operating next/prev `NP`) at a TIPLOC for given dates; it is not a location code.
