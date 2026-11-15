#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_compose.sh
source "${DIR}/_compose.sh"

compose up -d server

echo "Server is up. Follow logs:"
echo "  docker compose ${COMPOSE_ARGS[*]} logs -f server"
