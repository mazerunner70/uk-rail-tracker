#!/usr/bin/env bash
# Rebuild frontend dist, rebuild every compose service image, and start the stack.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_compose.sh
source "${DIR}/_compose.sh"

echo "Building frontend (dist must be fresh before the screen image COPY)..."
pnpm -C "${ROOT}/fe" build

echo "Rebuilding and starting all services (db + server + screen)..."
compose up --build -d

echo
echo "Deployed all."
echo "  screen: http://localhost:8081/"
echo "  server: http://localhost:8080/"
echo "Logs:"
echo "  docker compose ${COMPOSE_ARGS[*]} logs -f"
