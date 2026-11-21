#!/usr/bin/env bash
# One-shot: create shared Terraform remote state (S3 + DynamoDB lock) if missing.
# Safe to re-run. Uses awscli only.
#
# Recommended layout (account-wide, many stacks later):
#   S3 bucket:  tfstate-<account_id>
#     keys:     urt/<stack>/<env>/terraform.tfstate
#               e.g. urt/local-timetable/aws/terraform.tfstate
#   DynamoDB:   tfstate-lock-<account_id>  (partition key LockID String)
#               one table for every stack; Terraform locks by bucket/key
#
set -euo pipefail

REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-eu-west-2}}"
BUCKET_PREFIX="${TF_STATE_BUCKET_PREFIX:-tfstate}"
# Table name set after ACCOUNT_ID is known (default: tfstate-lock-<account>).

log()  { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
ok()   { log "OK  $*"; }
skip() { log "SKIP $*"; }
fail() { log "ERR $*"; exit 1; }

need() { command -v "$1" >/dev/null 2>&1 || fail "missing command: $1"; }

need aws
need jq

log "resolving caller identity"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
ARN="$(aws sts get-caller-identity --query Arn --output text)"
BUCKET="${BUCKET_PREFIX}-${ACCOUNT_ID}"
TABLE_NAME="${TF_STATE_LOCK_TABLE:-tfstate-lock-${ACCOUNT_ID}}"

log "account=${ACCOUNT_ID} arn=${ARN} region=${REGION}"
log "bucket=${BUCKET} table=${TABLE_NAME}"

# --- S3 bucket ---
if aws s3api head-bucket --bucket "${BUCKET}" 2>/dev/null; then
  skip "s3 bucket already exists: s3://${BUCKET}"
else
  log "creating s3 bucket s3://${BUCKET} in ${REGION}"
  if [[ "${REGION}" == "us-east-1" ]]; then
    aws s3api create-bucket --bucket "${BUCKET}" --region "${REGION}" >/dev/null
  else
    aws s3api create-bucket \
      --bucket "${BUCKET}" \
      --region "${REGION}" \
      --create-bucket-configuration "LocationConstraint=${REGION}" >/dev/null
  fi
  ok "created s3://${BUCKET}"
fi

log "ensuring public access block"
aws s3api put-public-access-block \
  --bucket "${BUCKET}" \
  --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
ok "public access blocked"

log "ensuring versioning"
aws s3api put-bucket-versioning \
  --bucket "${BUCKET}" \
  --versioning-configuration Status=Enabled
ok "versioning Enabled"

log "ensuring default encryption (AES256)"
aws s3api put-bucket-encryption \
  --bucket "${BUCKET}" \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"},"BucketKeyEnabled":true}]}'
ok "encryption AES256"

OWNERSHIP="$(aws s3api get-bucket-ownership-controls --bucket "${BUCKET}" --query 'OwnershipControls.Rules[0].ObjectOwnership' --output text 2>/dev/null || true)"
if [[ "${OWNERSHIP}" == "BucketOwnerEnforced" ]]; then
  skip "ownership already BucketOwnerEnforced"
else
  log "setting ownership BucketOwnerEnforced"
  aws s3api put-bucket-ownership-controls \
    --bucket "${BUCKET}" \
    --ownership-controls 'Rules=[{ObjectOwnership=BucketOwnerEnforced}]'
  ok "ownership BucketOwnerEnforced"
fi

log "ensuring bucket tags"
aws s3api put-bucket-tagging \
  --bucket "${BUCKET}" \
  --tagging 'TagSet=[{Key=Project,Value=tfstate},{Key=Purpose,Value=terraform-remote-state},{Key=ManagedBy,Value=bootstrap-tf-backend.sh}]'
ok "bucket tagged"

# Deny non-TLS access (idempotent put)
log "ensuring bucket policy (DenyInsecureTransport)"
POLICY="$(jq -n \
  --arg bucket "${BUCKET}" \
  '{
    Version: "2012-10-17",
    Statement: [
      {
        Sid: "DenyInsecureTransport",
        Effect: "Deny",
        Principal: "*",
        Action: "s3:*",
        Resource: ["arn:aws:s3:::\($bucket)", "arn:aws:s3:::\($bucket)/*"],
        Condition: { Bool: { "aws:SecureTransport": "false" } }
      }
    ]
  }')"
aws s3api put-bucket-policy --bucket "${BUCKET}" --policy "${POLICY}"
ok "bucket policy applied"

# --- DynamoDB lock table ---
if aws dynamodb describe-table --table-name "${TABLE_NAME}" --region "${REGION}" >/dev/null 2>&1; then
  skip "dynamodb table already exists: ${TABLE_NAME}"
  KEY_SCHEMA="$(aws dynamodb describe-table --table-name "${TABLE_NAME}" --region "${REGION}" \
    --query 'Table.KeySchema' --output json)"
  echo "${KEY_SCHEMA}" | jq -e '.[] | select(.AttributeName=="LockID" and .KeyType=="HASH")' >/dev/null \
    || fail "table ${TABLE_NAME} exists but partition key is not LockID (String) — Terraform requires LockID"
  ok "lock table schema has LockID HASH"
else
  log "creating dynamodb table ${TABLE_NAME} (PAY_PER_REQUEST, LockID HASH)"
  aws dynamodb create-table \
    --region "${REGION}" \
    --table-name "${TABLE_NAME}" \
    --attribute-definitions AttributeName=LockID,AttributeType=S \
    --key-schema AttributeName=LockID,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --tags Key=Project,Value=tfstate Key=Purpose,Value=terraform-state-lock Key=ManagedBy,Value=bootstrap-tf-backend.sh \
    >/dev/null
  log "waiting for table ACTIVE"
  aws dynamodb wait table-exists --table-name "${TABLE_NAME}" --region "${REGION}"
  ok "created ${TABLE_NAME}"
fi

cat <<EOF

$(log "bootstrap complete")

Use this backend in each stack (different key per config):

  terraform {
    backend "s3" {
      bucket         = "${BUCKET}"
      key            = "urt/<stack>/<env>/terraform.tfstate"   # e.g. urt/local-timetable/aws/terraform.tfstate
      region         = "${REGION}"
      dynamodb_table = "${TABLE_NAME}"
      encrypt        = true
    }
  }

Then: terraform init

Suggested key layout over time (project prefix in the key, not the bucket):
  urt/local-timetable/aws/terraform.tfstate
  urt/<other-app>/dev/terraform.tfstate
  otherproj/<app>/prod/terraform.tfstate

EOF
