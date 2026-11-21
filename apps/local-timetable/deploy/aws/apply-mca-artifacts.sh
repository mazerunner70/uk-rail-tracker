#!/usr/bin/env bash
# On EC2: pull stamped MCA + schematic from S3 artifacts, load sqlite, then install map.
# Env:
#   ARTIFACT_BUCKET  (required)
#   ARTIFACT_PREFIX  e.g. artifacts/20261116  (required)
#   AWS_REGION       (default eu-west-2)
#   MCA_DIR          (default /mca)
#   DB_DIR           (default /db)
#   KEEP_DAYS        (default 2)
#   LOAD_URL         (default http://127.0.0.1:8080/load)
set -euo pipefail

ARTIFACT_BUCKET="${ARTIFACT_BUCKET:?ARTIFACT_BUCKET required}"
ARTIFACT_PREFIX="${ARTIFACT_PREFIX:?ARTIFACT_PREFIX required}"
REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-eu-west-2}}"
MCA_DIR="${MCA_DIR:-/mca}"
DB_DIR="${DB_DIR:-/db}"
KEEP_DAYS="${KEEP_DAYS:-2}"
LOAD_URL="${LOAD_URL:-http://127.0.0.1:8080/load}"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

need() { command -v "$1" >/dev/null 2>&1 || { echo "missing: $1" >&2; exit 1; }; }
need aws
need curl
need python3

mkdir -p "${MCA_DIR}" "${DB_DIR}"

MANIFEST_KEY="${ARTIFACT_PREFIX%/}/manifest.json"
log "fetch s3://${ARTIFACT_BUCKET}/${MANIFEST_KEY}"
aws s3 cp "s3://${ARTIFACT_BUCKET}/${MANIFEST_KEY}" "${WORK}/manifest.json" --region "${REGION}"

MCA_KEY="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["mcaKey"])' "${WORK}/manifest.json")"
SCHEMATIC_KEY="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["schematicKey"])' "${WORK}/manifest.json")"
MCA_DAY="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["mcaDay"])' "${WORK}/manifest.json")"
MCA_NAME="$(basename "${MCA_KEY}")"

log "1/3 MCA → ${MCA_DIR}/${MCA_NAME}"
aws s3 cp "s3://${ARTIFACT_BUCKET}/${MCA_KEY}" "${MCA_DIR}/${MCA_NAME}" --region "${REGION}"
chmod 644 "${MCA_DIR}/${MCA_NAME}"

mapfile -t OLD < <(find "${MCA_DIR}" -maxdepth 1 -type f ! -name '.*' -printf '%T@ %p\n' | sort -nr | tail -n +$((KEEP_DAYS + 1)) | awk '{print $2}')
for f in "${OLD[@]:-}"; do
  [[ -n "${f}" ]] || continue
  log "prune ${f}"
  rm -f "${f}"
done

log "2/3 POST ${LOAD_URL}"
curl -sS -X POST -o "${WORK}/load-start.json" -w "load_http=%{http_code}\n" "${LOAD_URL}" || true
# Poll until terminal
for _ in $(seq 1 180); do
  curl -sS -o "${WORK}/load.json" "${LOAD_URL}" || true
  STATE="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("state",""))' "${WORK}/load.json" 2>/dev/null || true)"
  if [[ "${STATE}" == "succeeded" || "${STATE}" == "failed" ]]; then
    break
  fi
  sleep 5
done
STATE="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("state",""))' "${WORK}/load.json" 2>/dev/null || echo unknown)"
SENTENCE="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("sentence",""))' "${WORK}/load.json" 2>/dev/null || true)"
log "load state=${STATE} ${SENTENCE}"

if [[ "${STATE}" != "succeeded" ]]; then
  echo "load failed; leaving previous schematic in place" >&2
  exit 1
fi

log "3/3 schematic → ${DB_DIR}/network-schematic.json"
aws s3 cp "s3://${ARTIFACT_BUCKET}/${SCHEMATIC_KEY}" "${WORK}/network-schematic.json" --region "${REGION}"
mv -f "${WORK}/network-schematic.json" "${DB_DIR}/network-schematic.json"
chmod 644 "${DB_DIR}/network-schematic.json"
log "done mca_day=${MCA_DAY}"
