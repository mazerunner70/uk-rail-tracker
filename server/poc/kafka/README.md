# Darwin PubSub Kafka POC

Proof-of-concept for consuming the **Darwin Real Time Train Information** PubSub product on the [Rail Data Marketplace](https://raildata.org.uk/) via Kafka.

Based on [RSPS5053 P-01-01](https://www.rspaccreditation.org/publicDocumentation.php#RSPS5053) (*Darwin Publish and Subscribe via RDM*) and the official Python snippet at [raildatamarketplace/rdm-darwin-kafka-client](https://github.com/raildatamarketplace/rdm-darwin-kafka-client).

**Findings for server plan:** see [FINDINGS.md](./FINDINGS.md) (incorporated into `server/PLAN.md` §4.1a/c).

## What this gives you

| Capability | Tool | Notes |
|------------|------|-------|
| Live message stream | `consume.py` | Polls Kafka and prints messages as they arrive |
| Topic metadata | `metadata.py` | Partition count, low/high offsets, retained message count, consumer lag |
| JSON / XML / Avro | `--format` flag | One topic encoding at a time (per RSPS5053 §4.2.2) |

## Setup

1. Subscribe to the Darwin PubSub product in RDM and open the **Pub/Sub** tab.
2. Copy credentials and connection settings into `.env`:

```bash
cd server/poc/kafka
cp .env.example .env
# edit .env — bootstrap server, consumer key/secret, topic names
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Required from RDM (same credentials for all topics):

- Bootstrap server
- Consumer key / consumer secret (SASL username / password)
- Security protocol: `SASL_SSL`
- SASL mechanism: `PLAIN`
- Topic names for JSON, XML, and Avro variants

## Usage

### Check topic metadata first

```bash
# One encoding
python metadata.py json

# All configured topics
python metadata.py all
```

This prints:

- **Retained messages** — messages still in the Kafka log per partition (`high − low` watermark). This is the closest equivalent to “how many messages are waiting in the topic”, subject to Kafka retention policy.
- **Consumer lag** — how many messages your consumer group has not yet read. A new group with `latest` offset shows ~0 lag until you start consuming; use `--from-beginning` on `consume.py` to replay retained history.

Kafka is a **retained log**, not a queue that drains when you read. Messages remain until retention expires.

### Stream live messages

```bash
# JSON topic — live only (default)
python consume.py json

# XML topic
python consume.py xml

# Avro topic
python consume.py avro

# Replay retained history then continue live
python consume.py json --from-beginning

# Print 5 messages then exit
python consume.py xml --from-beginning --max-messages 5
```

Each format uses the **same** consumer group from RDM (`KAFKA_GROUP_ID`). Do not suffix or change it — RDM rejects unauthorized group names. Run one consumer at a time per subscription.

## RSPS5053 notes

- Four topic encodings exist: **base**, **JSON**, **Avro**, **XML**. This POC covers the three structured formats you asked for; the base topic carries wrapped string payloads.
- XSD files for the XML topic: [RSPS5051](https://www.rspaccreditation.org/publicDocumentation.php#RSPS5051)
- JSON schemas: RDM product **Reference Material** section
- SSL: most Linux installs work without `KAFKA_SSL_CA_LOCATION`; set it if you see certificate errors

## Avro decoding

The Avro topic may use Confluent wire format (5-byte header) or raw Avro. `consume.py` attempts both via `fastavro`. For production use, download the Avro schema from RDM reference material and plug in explicit schema decoding if needed.
