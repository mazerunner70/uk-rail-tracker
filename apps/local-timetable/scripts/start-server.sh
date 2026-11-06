#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE="${ROOT}/compose.yaml"

docker compose -f "${COMPOSE}" up -d server

echo "Server is up. Follow logs:"
echo "  docker compose -f ${COMPOSE} logs -f server"
