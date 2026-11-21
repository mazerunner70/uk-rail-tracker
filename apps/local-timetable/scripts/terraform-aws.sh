#!/usr/bin/env bash
# Terraform init + apply for the local-timetable AWS stack.
# Extra args are passed to `terraform apply` (e.g. -auto-approve, -var=...).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="${ROOT}/deploy/aws/terraform"
REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-eu-west-2}}"

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

need() { command -v "$1" >/dev/null 2>&1 || { echo "missing: $1" >&2; exit 1; }; }
need terraform
need aws

export AWS_REGION="${REGION}"
export AWS_DEFAULT_REGION="${REGION}"

[[ -d "${TF_DIR}" ]] || { echo "missing ${TF_DIR}" >&2; exit 1; }
cd "${TF_DIR}"

if [[ ! -f terraform.tfvars && -f terraform.tfvars.example ]]; then
  log "note: no terraform.tfvars (optional) — copy from terraform.tfvars.example to set email/flags"
fi

# pnpm/npm forward a literal "--" before script args; terraform rejects it.
if [[ "${1:-}" == "--" ]]; then
  shift
fi

log "terraform init (${TF_DIR})"
terraform init

log "terraform apply $*"
terraform apply "$@"
