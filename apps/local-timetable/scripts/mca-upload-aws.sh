#!/usr/bin/env bash
# Zip a local MCA file, upload to the MCA S3 bucket, SSM-ingest on EC2, trigger load.
# Usage:
#   pnpm mca-upload:aws
#   pnpm mca-upload:aws -- path/to/RJTTF021MCA-20261115.txt
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="${ROOT}/deploy/aws/terraform"
REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-eu-west-2}}"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
need() { command -v "$1" >/dev/null 2>&1 || { echo "missing: $1" >&2; exit 1; }; }
need aws
need terraform
need zip

cd "${TF_DIR}"
INSTANCE_ID="$(terraform output -raw instance_id)"
MCA_BUCKET="$(terraform output -raw s3_mca_bucket)"
cd "${ROOT}"

MCA_SRC="${1:-}"
if [[ -z "${MCA_SRC}" ]]; then
  # Newest *MCA* file under apps/local-timetable/mca
  MCA_SRC="$(ls -1t "${ROOT}/mca/"*MCA* 2>/dev/null | head -n 1 || true)"
fi
[[ -n "${MCA_SRC}" && -f "${MCA_SRC}" ]] || {
  echo "no MCA file found (pass a path or place one in apps/local-timetable/mca/)" >&2
  exit 1
}

BASE="$(basename "${MCA_SRC}")"
ZIP_NAME="${BASE%.txt}.zip"
ZIP_PATH="${WORK}/${ZIP_NAME}"
log "zipping ${MCA_SRC} → ${ZIP_NAME} (large; may take a few minutes)"
zip -j -q "${ZIP_PATH}" "${MCA_SRC}"
log "zip size $(du -h "${ZIP_PATH}" | awk '{print $1}')"

S3_KEY="incoming/${ZIP_NAME}"
LATEST_KEY="incoming/latest.zip"
log "uploading s3://${MCA_BUCKET}/${S3_KEY}"
aws s3 cp "${ZIP_PATH}" "s3://${MCA_BUCKET}/${S3_KEY}" --region "${REGION}"
aws s3 cp "${ZIP_PATH}" "s3://${MCA_BUCKET}/${LATEST_KEY}" --region "${REGION}"

# Push ingest script + run it
INGEST_LOCAL="${ROOT}/deploy/aws/ingest-mca-from-s3.sh"
aws s3 cp "${INGEST_LOCAL}" "s3://${MCA_BUCKET}/scripts/ingest-mca-from-s3.sh" --region "${REGION}"

REMOTE="${WORK}/remote.sh"
cat > "${REMOTE}" <<EOF
set -euo pipefail
aws s3 cp "s3://${MCA_BUCKET}/scripts/ingest-mca-from-s3.sh" /opt/urt-lt/ingest-mca-from-s3.sh --region "${REGION}"
chmod +x /opt/urt-lt/ingest-mca-from-s3.sh
command -v unzip >/dev/null || dnf install -y unzip file
MCA_BUCKET="${MCA_BUCKET}" AWS_REGION="${REGION}" TRIGGER_LOAD=1 \
  /opt/urt-lt/ingest-mca-from-s3.sh "s3://${MCA_BUCKET}/${LATEST_KEY}"
EOF

PARAMS="${WORK}/ssm-params.json"
python3 - <<PY
import json
from pathlib import Path
Path("${PARAMS}").write_text(json.dumps({"commands": Path("${REMOTE}").read_text().splitlines()}))
PY

log "SSM ingest on ${INSTANCE_ID}"
COMMAND_ID="$(aws ssm send-command \
  --region "${REGION}" \
  --instance-ids "${INSTANCE_ID}" \
  --document-name "AWS-RunShellScript" \
  --comment "urt-lt mca ingest ${BASE}" \
  --parameters "file://${PARAMS}" \
  --timeout-seconds 3600 \
  --query 'Command.CommandId' --output text)"

log "ssm command-id=${COMMAND_ID}; waiting (MCA extract/load can take a while)…"
set +e
aws ssm wait command-executed \
  --region "${REGION}" \
  --command-id "${COMMAND_ID}" \
  --instance-id "${INSTANCE_ID}"
WAIT_RC=$?
set -e

STATUS="$(aws ssm get-command-invocation --region "${REGION}" --command-id "${COMMAND_ID}" --instance-id "${INSTANCE_ID}" --query 'Status' --output text)"
STDOUT="$(aws ssm get-command-invocation --region "${REGION}" --command-id "${COMMAND_ID}" --instance-id "${INSTANCE_ID}" --query 'StandardOutputContent' --output text)"
STDERR="$(aws ssm get-command-invocation --region "${REGION}" --command-id "${COMMAND_ID}" --instance-id "${INSTANCE_ID}" --query 'StandardErrorContent' --output text)"
printf '%s\n' "${STDOUT}"
[[ -n "${STDERR}" && "${STDERR}" != "None" ]] && printf 'stderr:\n%s\n' "${STDERR}" >&2

if [[ "${STATUS}" != "Success" ]]; then
  echo "SSM status=${STATUS} wait_rc=${WAIT_RC}" >&2
  exit 1
fi

log "MCA ingest complete — watch load progress in the UI or: aws ssm start-session --target ${INSTANCE_ID}"
echo "  file: ${BASE}"
echo "  s3:   s3://${MCA_BUCKET}/${S3_KEY}"
