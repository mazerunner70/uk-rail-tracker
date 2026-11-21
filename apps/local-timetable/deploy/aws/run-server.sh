#!/usr/bin/env bash
# Build and (re)run the local-timetable server container on the EC2 host.
# Expects sources at /opt/urt-lt/app/apps/local-timetable/server (or pass SERVER_DIR).
set -euo pipefail

NAME="${NAME:-urt-lt-server}"
IMAGE="${IMAGE:-urt-lt-server:local}"
SERVER_DIR="${SERVER_DIR:-/opt/urt-lt/app/apps/local-timetable/server}"
ENV_FILE="${ENV_FILE:-/opt/urt-lt/env/server.env}"
DB_DIR="${DB_DIR:-/db}"
MCA_DIR="${MCA_DIR:-/mca}"
HOST_PORT="${HOST_PORT:-8080}"

if [[ ! -f "${SERVER_DIR}/Dockerfile" ]]; then
  echo "missing Dockerfile at ${SERVER_DIR}" >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "missing env file ${ENV_FILE} (create from SSM / secrets first)" >&2
  exit 1
fi

docker build -t "${IMAGE}" "${SERVER_DIR}"

if docker container inspect "${NAME}" >/dev/null 2>&1; then
  docker stop "${NAME}" >/dev/null || true
  docker rm "${NAME}" >/dev/null || true
fi

# Bind all interfaces so API Gateway can reach the public IP; SG + Cognito JWT lock origin.
# Prefer external schematic (Python Lambda) on AWS; override in ENV_FILE if needed.
docker run -d \
  --name "${NAME}" \
  --restart unless-stopped \
  -p "${HOST_PORT}:8080" \
  -v "${DB_DIR}:/db" \
  -v "${MCA_DIR}:/mca:ro" \
  --env-file "${ENV_FILE}" \
  -e TZ=Europe/London \
  -e METRO_LAYOUT_ENGINE="${METRO_LAYOUT_ENGINE:-external}" \
  "${IMAGE}"

# Host-side apply script for SSM (MCA + schematic from S3 artifacts).
APPLY_SRC="${SERVER_DIR}/../deploy/aws/apply-mca-artifacts.sh"
if [[ -f "${APPLY_SRC}" ]]; then
  cp -f "${APPLY_SRC}" /opt/urt-lt/apply-mca-artifacts.sh
  chmod +x /opt/urt-lt/apply-mca-artifacts.sh
fi

echo "running ${NAME} on 0.0.0.0:${HOST_PORT} (METRO_LAYOUT_ENGINE=${METRO_LAYOUT_ENGINE:-external})"
