#!/usr/bin/env bash
# Rebuild FE + server sources and deploy to the Terraform-managed AWS stack.
# Prerequisites: terraform apply done; awscli + terraform; SSM access to the instance.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="${ROOT}/deploy/aws/terraform"
REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-eu-west-2}}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

need() { command -v "$1" >/dev/null 2>&1 || { echo "missing: $1" >&2; exit 1; }; }
need aws
need terraform
need pnpm
need tar

[[ -d "${TF_DIR}" ]] || { echo "missing terraform dir: ${TF_DIR}" >&2; exit 1; }

log "reading terraform outputs"
cd "${TF_DIR}"
INSTANCE_ID="$(terraform output -raw instance_id)"
FE_BUCKET="$(terraform output -raw s3_fe_bucket)"
DEPLOY_BUCKET="$(terraform output -raw s3_deploy_bucket)"
CF_DOMAIN="$(terraform output -raw cloudfront_domain 2>/dev/null || true)"
COGNITO_DOMAIN="$(terraform output -raw cognito_hosted_ui_domain 2>/dev/null || true)"
COGNITO_CLIENT_ID="$(terraform output -raw cognito_client_id 2>/dev/null || true)"
cd "${ROOT}"

[[ -n "${INSTANCE_ID}" && "${INSTANCE_ID}" != "null" ]] || { echo "no instance_id from terraform" >&2; exit 1; }
[[ -n "${FE_BUCKET}" ]] || { echo "no s3_fe_bucket from terraform" >&2; exit 1; }
[[ -n "${DEPLOY_BUCKET}" ]] || { echo "no s3_deploy_bucket from terraform" >&2; exit 1; }

log "instance=${INSTANCE_ID} fe_bucket=${FE_BUCKET} deploy_bucket=${DEPLOY_BUCKET}"

# Schematic Lambda image (skip if sources unchanged; SCHEMATIC_FORCE=1 to rebuild)
log "schematic-process Lambda image"
if [[ "${SCHEMATIC_FORCE:-0}" == "1" ]]; then
  bash "${ROOT}/scripts/schematic-process-aws.sh" --force
else
  bash "${ROOT}/scripts/schematic-process-aws.sh"
fi

log "building frontend"
pnpm -C "${ROOT}/fe" build
[[ -d "${ROOT}/fe/dist" ]] || { echo "fe build produced no dist/" >&2; exit 1; }

INDEX="${ROOT}/fe/dist/index.html"
if [[ -n "${COGNITO_DOMAIN}" && "${COGNITO_DOMAIN}" != "null" && -n "${COGNITO_CLIENT_ID}" && "${COGNITO_CLIENT_ID}" != "null" ]]; then
  CF_URL="https://${CF_DOMAIN}/"
  log "injecting Cognito config into index.html (domain=${COGNITO_DOMAIN})"
  python3 - <<PY
from pathlib import Path
path = Path("${INDEX}")
html = path.read_text()
replacements = {
    'name="api-base" content=""': 'name="api-base" content="/api"',
    'name="api-base" content="/api"': 'name="api-base" content="/api"',
    'name="cognito-domain" content=""': 'name="cognito-domain" content="${COGNITO_DOMAIN}"',
    'name="cognito-client-id" content=""': 'name="cognito-client-id" content="${COGNITO_CLIENT_ID}"',
    'name="cognito-redirect-uri" content=""': 'name="cognito-redirect-uri" content="${CF_URL}"',
    'name="cognito-logout-uri" content=""': 'name="cognito-logout-uri" content="${CF_URL}"',
}
for old, new in replacements.items():
    if old in html:
        html = html.replace(old, new)
path.write_text(html)
PY
else
  log "WARN: cognito outputs missing — FE will not redirect to Hosted UI"
fi

log "syncing FE → s3://${FE_BUCKET}/"
aws s3 sync "${ROOT}/fe/dist/" "s3://${FE_BUCKET}/" --region "${REGION}" --delete

if [[ -n "${CF_DOMAIN}" && "${CF_DOMAIN}" != "null" ]]; then
  DIST_ID="$(aws cloudfront list-distributions --query "DistributionList.Items[?DomainName=='${CF_DOMAIN}'].Id | [0]" --output text)"
  if [[ -n "${DIST_ID}" && "${DIST_ID}" != "None" ]]; then
    log "invalidating CloudFront ${DIST_ID} (/*)"
    aws cloudfront create-invalidation --distribution-id "${DIST_ID}" --paths "/*" >/dev/null
  else
    log "CloudFront distribution id not found for ${CF_DOMAIN}; skip invalidation"
  fi
fi

log "packing server + deploy helpers"
TAR="${WORK}/urt-lt-server-${STAMP}.tar.gz"
tar -C "${ROOT}" -czf "${TAR}" \
  --exclude='server/node_modules' \
  --exclude='server/dist' \
  server \
  deploy/aws/run-server.sh \
  deploy/aws/setup-host.sh \
  deploy/aws/apply-mca-artifacts.sh \
  deploy/aws/ingest-mca-from-s3.sh

S3_KEY="releases/urt-lt-server-${STAMP}.tar.gz"
log "uploading s3://${DEPLOY_BUCKET}/${S3_KEY}"
aws s3 cp "${TAR}" "s3://${DEPLOY_BUCKET}/${S3_KEY}" --region "${REGION}"
aws s3 cp "${TAR}" "s3://${DEPLOY_BUCKET}/releases/urt-lt-server-latest.tar.gz" --region "${REGION}"

# Remote script for SSM (avoids brittle inline JSON escaping)
REMOTE="${WORK}/remote.sh"
cat > "${REMOTE}" <<EOF
set -euo pipefail
if [[ ! -f /opt/urt-lt/bootstrap.ok ]]; then
  dnf install -y docker git awscli
  systemctl enable --now docker
  mkdir -p /db /mca /opt/urt-lt/env /opt/urt-lt/app
  echo ok > /opt/urt-lt/bootstrap.ok
fi
command -v aws >/dev/null || dnf install -y awscli
aws s3 cp "s3://${DEPLOY_BUCKET}/releases/urt-lt-server-latest.tar.gz" /tmp/urt-lt-server-latest.tar.gz --region "${REGION}"
rm -rf /opt/urt-lt/app/apps
mkdir -p /opt/urt-lt/app/apps/local-timetable
tar -xzf /tmp/urt-lt-server-latest.tar.gz -C /opt/urt-lt/app/apps/local-timetable
chmod +x /opt/urt-lt/app/apps/local-timetable/deploy/aws/*.sh
if [[ ! -f /opt/urt-lt/env/server.env ]]; then
  printf 'TZ=Europe/London\\n' > /opt/urt-lt/env/server.env
  echo "WARN: created /opt/urt-lt/env/server.env — add KAFKA_* before enabling consumer"
fi
/opt/urt-lt/app/apps/local-timetable/deploy/aws/run-server.sh
EOF

# Build SSM parameters JSON: commands = remote script lines (RunShellScript runs as root on AL2023)
PARAMS="${WORK}/ssm-params.json"
python3 - <<PY
import json
from pathlib import Path
lines = Path("${REMOTE}").read_text().splitlines()
Path("${PARAMS}").write_text(json.dumps({"commands": lines}))
PY

log "SSM: pull release, extract, rebuild container on ${INSTANCE_ID}"
COMMAND_ID="$(aws ssm send-command \
  --region "${REGION}" \
  --instance-ids "${INSTANCE_ID}" \
  --document-name "AWS-RunShellScript" \
  --comment "urt-lt rebuild-and-deploy-all:aws ${STAMP}" \
  --parameters "file://${PARAMS}" \
  --query 'Command.CommandId' --output text)"

log "ssm command-id=${COMMAND_ID}; waiting…"
set +e
aws ssm wait command-executed \
  --region "${REGION}" \
  --command-id "${COMMAND_ID}" \
  --instance-id "${INSTANCE_ID}"
WAIT_RC=$?
set -e

STATUS="$(aws ssm get-command-invocation \
  --region "${REGION}" \
  --command-id "${COMMAND_ID}" \
  --instance-id "${INSTANCE_ID}" \
  --query 'Status' --output text)"
STDOUT="$(aws ssm get-command-invocation \
  --region "${REGION}" \
  --command-id "${COMMAND_ID}" \
  --instance-id "${INSTANCE_ID}" \
  --query 'StandardOutputContent' --output text)"
STDERR="$(aws ssm get-command-invocation \
  --region "${REGION}" \
  --command-id "${COMMAND_ID}" \
  --instance-id "${INSTANCE_ID}" \
  --query 'StandardErrorContent' --output text)"

printf '%s\n' "${STDOUT}"
if [[ -n "${STDERR}" && "${STDERR}" != "None" ]]; then
  printf 'stderr:\n%s\n' "${STDERR}" >&2
fi

if [[ "${STATUS}" != "Success" ]]; then
  echo "SSM command status: ${STATUS} (wait rc=${WAIT_RC})" >&2
  exit 1
fi

log "deployed all (aws)"
if [[ -n "${CF_DOMAIN}" && "${CF_DOMAIN}" != "null" ]]; then
  echo "  screen: https://${CF_DOMAIN}/"
fi
echo "  server (on box): curl http://127.0.0.1:8080/network"
echo "  release: s3://${DEPLOY_BUCKET}/${S3_KEY}"
