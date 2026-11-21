#!/usr/bin/env bash
# Build/push the schematic-process Lambda image (arm64) and optionally invoke it.
# Works after ECR exists (even before the Lambda function is created).
# Skips build/push when source inputs are unchanged (unless --force).
#
# Usage:
#   pnpm schematic-process:aws
#   pnpm schematic-process:aws -- --force
#   pnpm schematic-process:aws -- --invoke
#   pnpm schematic-process:aws -- --mca-s3-uri s3://bucket/key.txt
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="${ROOT}/deploy/aws/terraform"
SERVER_DIR="${ROOT}/server"
REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-eu-west-2}}"
STAMP_FILE="${SERVER_DIR}/lambda/schematic_process/.last-pushed-hash"
INVOKE=0
FORCE=0
MCA_S3_URI=""

# pnpm/npm forward a literal "--" before script args
if [[ "${1:-}" == "--" ]]; then
  shift
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --invoke) INVOKE=1; shift ;;
    --force) FORCE=1; shift ;;
    --mca-s3-uri) MCA_S3_URI="${2:-}"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 1 ;;
  esac
done

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
need() { command -v "$1" >/dev/null 2>&1 || { echo "missing: $1" >&2; exit 1; }; }
need aws
need docker
need terraform
need python3

BUILDER_NAME="${SCHEMATIC_BUILDX_BUILDER:-urt-lt-arm64}"

# Cross-build arm64 on amd64 hosts (QEMU + buildx container driver).
ensure_arm64_build() {
  if [[ ! -f /proc/sys/fs/binfmt_misc/qemu-aarch64 ]]; then
    log "installing qemu-aarch64 binfmt (one-time)"
    docker run --privileged --rm tonistiigi/binfmt --install arm64 >/dev/null
  fi
  if ! docker buildx inspect "${BUILDER_NAME}" >/dev/null 2>&1; then
    log "creating buildx builder ${BUILDER_NAME}"
    docker buildx create --name "${BUILDER_NAME}" --driver docker-container >/dev/null
  fi
  docker buildx use "${BUILDER_NAME}" >/dev/null
  docker buildx inspect --bootstrap >/dev/null
}

source_hash() {
  SERVER_DIR="${SERVER_DIR}" python3 - <<'PY'
from pathlib import Path
import hashlib
import os

root = Path(os.environ["SERVER_DIR"])
paths = [
    root / "lambda/schematic_process/Dockerfile",
    root / "lambda/schematic_process/requirements.txt",
    root / "layout/requirements.txt",
    root / "layout/metro_layout.py",
    root / "data/station-geo.json",
]
pkg = root / "lambda/schematic_process"
paths.extend(sorted(pkg.glob("*.py")))

h = hashlib.sha256()
for p in paths:
    if not p.is_file():
        continue
    h.update(str(p.relative_to(root)).encode())
    h.update(b"\0")
    h.update(p.read_bytes())
    h.update(b"\0")
print(h.hexdigest())
PY
}

cd "${TF_DIR}"
ECR_URL="$(terraform output -raw ecr_schematic_repository_url 2>/dev/null || true)"
ACCOUNT_ID="$(terraform output -raw account_id 2>/dev/null || true)"
# Prefer live AWS over terraform output — output can exist in config while CreateFunction is still pending.
FN_NAME="$(terraform output -raw schematic_lambda_name 2>/dev/null || true)"
cd "${ROOT}"

if [[ -z "${ECR_URL}" || -z "${ACCOUNT_ID}" ]]; then
  echo "ECR terraform outputs missing — create the repo first:" >&2
  echo "  pnpm terraform:aws -- -target=aws_ecr_repository.schematic -target=aws_ecr_lifecycle_policy.schematic" >&2
  exit 1
fi

# Derive expected name from ECR repo (urt-lt-schematic-process) when TF output is empty.
if [[ -z "${FN_NAME}" ]]; then
  FN_NAME="$(basename "${ECR_URL}")"
fi

lambda_exists() {
  aws lambda get-function --region "${REGION}" --function-name "$1" >/dev/null 2>&1
}

HASH="$(source_hash)"
PREV=""
[[ -f "${STAMP_FILE}" ]] && PREV="$(cat "${STAMP_FILE}")"
IMAGE="${ECR_URL}:latest"

if [[ "${FORCE}" != "1" && "${HASH}" == "${PREV}" ]]; then
  log "schematic image sources unchanged (${HASH:0:12}…); skip build/push"
else
  log "ecr login ${ECR_URL}"
  aws ecr get-login-password --region "${REGION}" \
    | docker login --username AWS --password-stdin "${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

  ensure_arm64_build
  # Lambda rejects OCI image indexes from buildx attestations — single-platform only.
  log "buildx build --platform linux/arm64 --push → ${IMAGE}"
  docker buildx build \
    --platform linux/arm64 \
    --provenance=false \
    --sbom=false \
    --push \
    -f "${SERVER_DIR}/lambda/schematic_process/Dockerfile" \
    -t "${IMAGE}" \
    "${SERVER_DIR}"

  printf '%s\n' "${HASH}" > "${STAMP_FILE}"
  log "image pushed (hash ${HASH:0:12}…)"
fi

# Only retarget Lambda when we actually pushed (or --force path rebuilt).
if [[ "${FORCE}" == "1" || "${HASH}" != "${PREV}" ]]; then
  if lambda_exists "${FN_NAME}"; then
    log "update-function-code ${FN_NAME}"
    aws lambda update-function-code \
      --region "${REGION}" \
      --function-name "${FN_NAME}" \
      --image-uri "${IMAGE}" >/dev/null
    aws lambda wait function-updated --region "${REGION}" --function-name "${FN_NAME}"
    log "lambda points at ${IMAGE}"
  else
    log "Lambda ${FN_NAME} not created yet — image is in ECR; run pnpm terraform:aws next"
  fi
elif ! lambda_exists "${FN_NAME}"; then
  log "Lambda ${FN_NAME} not created yet — image is in ECR; run pnpm terraform:aws next"
fi

if [[ -n "${MCA_S3_URI}" ]]; then
  INVOKE=1
fi

if [[ "${INVOKE}" == "1" ]]; then
  if ! lambda_exists "${FN_NAME}"; then
    echo "cannot --invoke: schematic Lambda not created yet (finish pnpm terraform:aws)" >&2
    exit 1
  fi
  if [[ -n "${MCA_S3_URI}" ]]; then
    PAYLOAD="$(python3 -c 'import json,sys; print(json.dumps({"mcaS3Uri": sys.argv[1]}))' "${MCA_S3_URI}")"
  else
    RECEPTION="s3://raildata-ingest-0701//timetable/timetable_full.zip"
    PAYLOAD="$(python3 -c 'import json,sys; print(json.dumps({"mcaS3Uri": sys.argv[1]}))' "${RECEPTION}")"
  fi
  OUT="$(mktemp)"
  # Sync invoke can run up to Lambda timeout (900s): download zip + HiGHS layout.
  # Default AWS CLI read timeout is ~60s and surfaces as "Read timeout on endpoint URL".
  log "invoke ${FN_NAME} (sync; may take up to ~15 min — watch /aws/lambda/${FN_NAME})"
  aws lambda invoke \
    --region "${REGION}" \
    --function-name "${FN_NAME}" \
    --cli-binary-format raw-in-base64-out \
    --cli-read-timeout 1000 \
    --cli-connect-timeout 60 \
    --payload "${PAYLOAD}" \
    "${OUT}"
  echo
  log "invoke response:"
  cat "${OUT}"
  echo
  rm -f "${OUT}"
fi

log "done"
