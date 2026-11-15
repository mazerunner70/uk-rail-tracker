#!/usr/bin/env bash
# Shared compose paths and flags for apps/local-timetable/scripts.
# Source from another script; do not run directly.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE="$(cd "${ROOT}/../.." && pwd)"
COMPOSE="${ROOT}/compose.yaml"
ENV_FILE="${ENV_FILE:-${WORKSPACE}/.env}"

COMPOSE_ARGS=(-f "${COMPOSE}")
if [[ -f "${ENV_FILE}" ]]; then
  COMPOSE_ARGS+=(--env-file "${ENV_FILE}")
fi

compose() {
  docker compose "${COMPOSE_ARGS[@]}" "$@"
}
