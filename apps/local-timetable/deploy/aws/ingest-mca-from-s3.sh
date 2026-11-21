#!/usr/bin/env bash
# Two-step MCA intake on the EC2 host (EBS):
#
#   1) Reception → EBS zip  — copy s3://…/timetable/timetable_full.zip onto EBS
#      only when the object ETag / LastModified is newer than what we already have.
#   2) Zip → /mca          — extract *-MCA*.txt into /mca, naming it with
#      YYYYMMDD = (Europe/London calendar date of the zip's LastModified) + 1 day
#      because the full timetable arrives the day before the operating day.
#
# Usage (root / SSM):
#   ingest-mca-from-s3.sh
#   RECEPTION_S3_URI=s3://raildata-ingest-0701//timetable/timetable_full.zip ingest-mca-from-s3.sh
#
# Note: do not extract under /tmp — on AL2023 it is often a small tmpfs (~400–500MB);
# the MCA alone is ~700MB+. Staging uses /opt/urt-lt/mca-extract on EBS.
#
set -euo pipefail

REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-eu-west-2}}"
# RailData keys are literally prefixed with "/" (URL shows …amazonaws.com//timetable/…).
RECEPTION_S3_URI="${RECEPTION_S3_URI:-s3://raildata-ingest-0701//timetable/timetable_full.zip}"
RECEPTION_DIR="${RECEPTION_DIR:-/opt/urt-lt/reception}"
ZIP_PATH="${RECEPTION_DIR}/timetable_full.zip"
META_PATH="${RECEPTION_DIR}/timetable_full.meta"
MCA_DIR="${MCA_DIR:-/mca}"
KEEP_DAYS="${KEEP_DAYS:-2}"
TRIGGER_LOAD="${TRIGGER_LOAD:-1}"
TMP_DIR="${TMP_DIR:-/opt/urt-lt/mca-extract}"

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

if [[ "$(id -u)" -ne 0 ]]; then
  echo "run as root (SSM / sudo)" >&2
  exit 1
fi

command -v aws >/dev/null || dnf install -y awscli
command -v unzip >/dev/null || dnf install -y unzip
command -v file >/dev/null || dnf install -y file
command -v python3 >/dev/null || dnf install -y python3

mkdir -p "${RECEPTION_DIR}" "${MCA_DIR}"

read_meta() {
  local key="$1"
  [[ -f "${META_PATH}" ]] || return 0
  awk -F= -v k="$key" '$1==k { print substr($0, index($0,"=")+1); exit }' "${META_PATH}"
}

write_meta() {
  local etag="$1" last_modified="$2"
  cat > "${META_PATH}" <<EOF
ETag=${etag}
LastModified=${last_modified}
DownloadedAt=$(date -u +%Y-%m-%dT%H:%M:%SZ)
Source=${RECEPTION_S3_URI}
EOF
}

# MCA operating-day stamp: London date of LastModified, plus one calendar day.
mca_day_from_last_modified() {
  local last_modified="$1"
  python3 - "$last_modified" <<'PY'
import sys
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

raw = sys.argv[1].strip()
dt = datetime.fromisoformat(raw.replace("Z", "+00:00"))
london = dt.astimezone(ZoneInfo("Europe/London"))
op = london.date() + timedelta(days=1)
print(op.strftime("%Y%m%d"))
PY
}

# =============================================================================
# Step 1 — reception S3 → EBS zip (only if newer)
# =============================================================================
# s3://bucket//timetable/… → key="/timetable/…"
RECEPTION_BUCKET="$(python3 -c 'import sys; u=sys.argv[1][5:]; print(u.split("/",1)[0])' "${RECEPTION_S3_URI}")"
RECEPTION_KEY="$(python3 -c 'import sys; u=sys.argv[1][5:]; print(u.split("/",1)[1] if "/" in u else "")' "${RECEPTION_S3_URI}")"

log "step1: head s3://${RECEPTION_BUCKET}/${RECEPTION_KEY} (key=${RECEPTION_KEY})"
HEAD_JSON="$(aws s3api head-object --region "${REGION}" --bucket "${RECEPTION_BUCKET}" --key "${RECEPTION_KEY}" --output json)"
REMOTE_ETAG="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["ETag"].strip("\""))' <<<"${HEAD_JSON}")"
REMOTE_LM="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["LastModified"])' <<<"${HEAD_JSON}")"
REMOTE_LM_ISO="$(python3 -c 'import sys; from datetime import datetime; print(datetime.fromisoformat(sys.argv[1].replace("Z","+00:00")).isoformat())' "${REMOTE_LM}")"

LOCAL_ETAG="$(read_meta ETag || true)"
log "remote ETag=${REMOTE_ETAG} LastModified=${REMOTE_LM_ISO}"
log "local  ETag=${LOCAL_ETAG:-<none>}"

COPIED=0
if [[ -f "${ZIP_PATH}" && -n "${LOCAL_ETAG}" && "${LOCAL_ETAG}" == "${REMOTE_ETAG}" ]]; then
  log "step1: EBS zip already current — skip copy"
else
  log "step1: copying zip → ${ZIP_PATH}"
  aws s3api get-object --region "${REGION}" --bucket "${RECEPTION_BUCKET}" --key "${RECEPTION_KEY}" "${ZIP_PATH}" >/dev/null
  write_meta "${REMOTE_ETAG}" "${REMOTE_LM_ISO}"
  COPIED=1
  log "step1: copied ($(du -h "${ZIP_PATH}" | awk '{print $1}'))"
fi

LM_FOR_DATE="$(read_meta LastModified)"
LM_FOR_DATE="${LM_FOR_DATE:-$REMOTE_LM_ISO}"
MCA_DAY="$(mca_day_from_last_modified "${LM_FOR_DATE}")"
log "MCA operating day (LastModified London + 1 day)=${MCA_DAY}"

# =============================================================================
# Step 2 — EBS zip → /mca (*-MCA*.txt dated MCA_DAY)
# =============================================================================
log "step2: extract MCA from ${ZIP_PATH}"

# Clear staging dirs (including any leftover failed extract on tmpfs /tmp)
rm -rf "${TMP_DIR}" /tmp/urt-lt-mca-extract
mkdir -p "${TMP_DIR}" "${MCA_DIR}"

MCA_MEMBER="$(unzip -Z1 "${ZIP_PATH}" | grep -iE 'MCA.*\.txt$|MCA$' | head -n 1 || true)"
if [[ -z "${MCA_MEMBER}" ]]; then
  echo "step2: no *MCA*.txt member in zip:" >&2
  unzip -Z1 "${ZIP_PATH}" >&2 || true
  exit 1
fi

BASE="$(basename "${MCA_MEMBER}")"
EXT="${BASE##*.}"
STEM="${BASE%.*}"
STEM="$(echo "${STEM}" | sed -E 's/-[0-9]{8}$//')"
DEST_NAME="${STEM}-${MCA_DAY}.${EXT}"
DEST="${MCA_DIR}/${DEST_NAME}"

# Extract only the MCA member onto EBS (not tmpfs /tmp), non-interactive.
log "step2: unzip -j ${MCA_MEMBER} → ${TMP_DIR} then install as ${DEST_NAME}"
set +e
unzip -o -j -q "${ZIP_PATH}" "${MCA_MEMBER}" -d "${TMP_DIR}"
UNZIP_RC=$?
set -e
if [[ "${UNZIP_RC}" -ne 0 ]]; then
  echo "step2: unzip failed rc=${UNZIP_RC}" >&2
  df -h / /tmp /opt "${MCA_DIR}" >&2 || true
  exit "${UNZIP_RC}"
fi

MCA_SRC="$(find "${TMP_DIR}" -type f -name "$(basename "${MCA_MEMBER}")" | head -n 1 || true)"
if [[ -z "${MCA_SRC}" ]]; then
  MCA_SRC="$(find "${TMP_DIR}" -type f | head -n 1 || true)"
fi
[[ -n "${MCA_SRC}" && -f "${MCA_SRC}" ]] || { echo "step2: extracted MCA missing" >&2; exit 1; }

mv -f "${MCA_SRC}" "${DEST}"
chmod 644 "${DEST}"
log "step2: installed ${DEST} ($(du -h "${DEST}" | awk '{print $1}'))"

mapfile -t OLD < <(find "${MCA_DIR}" -maxdepth 1 -type f ! -name '.*' -printf '%T@ %p\n' | sort -nr | tail -n +$((KEEP_DAYS + 1)) | awk '{print $2}')
for f in "${OLD[@]:-}"; do
  [[ -n "${f}" ]] || continue
  log "step2: pruning ${f}"
  rm -f "${f}"
done

rm -rf "${TMP_DIR}"
log "step2: /mca listing:"
ls -lh "${MCA_DIR}"

if [[ "${TRIGGER_LOAD}" == "1" ]]; then
  log "triggering POST http://127.0.0.1:8080/load (MCA_CALENDAR_DATE=${MCA_DAY} if server env set)"
  curl -sS -X POST -o /tmp/urt-lt-load.json -w "load_http=%{http_code}\n" http://127.0.0.1:8080/load || true
  head -c 400 /tmp/urt-lt-load.json 2>/dev/null; echo
fi

log "done (copied=${COPIED} mca_day=${MCA_DAY})"
