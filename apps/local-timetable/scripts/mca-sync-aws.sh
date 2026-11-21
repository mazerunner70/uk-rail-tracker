#!/usr/bin/env bash
# Trigger the two-step MCA intake on EC2 via SSM (reception S3 → EBS zip → /mca).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="${ROOT}/deploy/aws/terraform"
REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-eu-west-2}}"
# Leading slash in the object key (s3://bucket//timetable/…).
RECEPTION_S3_URI="${RECEPTION_S3_URI:-s3://raildata-ingest-0701//timetable/timetable_full.zip}"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
need() { command -v "$1" >/dev/null 2>&1 || { echo "missing: $1" >&2; exit 1; }; }
need aws
need terraform

cd "${TF_DIR}"
INSTANCE_ID="$(terraform output -raw instance_id)"
DEPLOY_BUCKET="$(terraform output -raw s3_deploy_bucket)"
cd "${ROOT}"

INGEST_LOCAL="${ROOT}/deploy/aws/ingest-mca-from-s3.sh"
log "uploading ingest script to s3://${DEPLOY_BUCKET}/scripts/ingest-mca-from-s3.sh"
aws s3 cp "${INGEST_LOCAL}" "s3://${DEPLOY_BUCKET}/scripts/ingest-mca-from-s3.sh" --region "${REGION}"

REMOTE="${WORK}/remote.sh"
cat > "${REMOTE}" <<EOF
set -euo pipefail
aws s3 cp "s3://${DEPLOY_BUCKET}/scripts/ingest-mca-from-s3.sh" /opt/urt-lt/ingest-mca-from-s3.sh --region "${REGION}"
chmod +x /opt/urt-lt/ingest-mca-from-s3.sh
export AWS_REGION="${REGION}"
export RECEPTION_S3_URI="${RECEPTION_S3_URI}"
export TRIGGER_LOAD=1
/opt/urt-lt/ingest-mca-from-s3.sh
EOF

PARAMS="${WORK}/ssm-params.json"
python3 - <<PY
import json
from pathlib import Path
Path("${PARAMS}").write_text(json.dumps({"commands": Path("${REMOTE}").read_text().splitlines()}))
PY

log "SSM two-step ingest on ${INSTANCE_ID} from ${RECEPTION_S3_URI}"
COMMAND_ID="$(aws ssm send-command \
  --region "${REGION}" \
  --instance-ids "${INSTANCE_ID}" \
  --document-name "AWS-RunShellScript" \
  --comment "urt-lt mca sync from reception" \
  --parameters "file://${PARAMS}" \
  --timeout-seconds 7200 \
  --query 'Command.CommandId' --output text)"

log "ssm command-id=${COMMAND_ID}; waiting…"
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
log "mca sync complete"
