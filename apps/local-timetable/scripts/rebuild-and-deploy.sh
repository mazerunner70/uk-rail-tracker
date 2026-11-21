#!/usr/bin/env bash
# Alias for rebuild-and-deploy-all.sh (FE + full compose stack).
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${DIR}/rebuild-and-deploy-all.sh"
