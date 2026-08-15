#!/usr/bin/env bash
# Build, install, and launch the debug app on a physical phone (USB or wireless adb).
#
# Usage:
#   ./scripts/deploy-phone.sh              # production-like debug (no simulator)
#   ./scripts/deploy-phone.sh --simulator  # include APS walk-up GPS simulator
#   INCLUDE_SIMULATOR=1 ./scripts/deploy-phone.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="com.ukrailtracker.app"
ACTIVITY="${PACKAGE}/.MainActivity"
WITH_SIMULATOR=0

usage() {
  cat <<'EOF'
Usage: deploy-phone.sh [--simulator|-s] [--help]

  --simulator, -s   Build with the APS walk-up GPS simulator (-PincludeSimulator=true)
  INCLUDE_SIMULATOR=1 in the environment has the same effect.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --simulator|-s) WITH_SIMULATOR=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "error: unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ "${INCLUDE_SIMULATOR:-}" == "1" ]]; then
  WITH_SIMULATOR=1
fi

GRADLE_PROPS=()
if [[ "$WITH_SIMULATOR" -eq 1 ]]; then
  GRADLE_PROPS+=(-PincludeSimulator=true)
  echo "Walk-up GPS simulator: ON"
else
  echo "Walk-up GPS simulator: off (pass --simulator to enable)"
fi

if [[ -n "${ANDROID_HOME:-}" ]]; then
  export PATH="$PATH:$ANDROID_HOME/platform-tools"
fi

require() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "error: '$1' not found on PATH (set ANDROID_HOME?)" >&2
    exit 1
  }
}

require adb

cd "$ROOT"

# Physical devices only — skip emulator-* serials unless ANDROID_SERIAL is set.
pick_phone() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    echo "$ANDROID_SERIAL"
    return
  fi
  local phones
  phones="$(adb devices | awk '/\tdevice$/{ print $1 }' | grep -v '^emulator-' || true)"
  local count
  count="$(printf '%s\n' "$phones" | awk 'NF' | wc -l | tr -d ' ')"
  if [[ "$count" -eq 0 ]]; then
    echo "error: no physical phone in 'adb devices' (enable USB debugging / wireless adb)." >&2
    adb devices >&2
    exit 1
  fi
  if [[ "$count" -gt 1 ]]; then
    echo "error: multiple phones connected; set ANDROID_SERIAL to one of:" >&2
    printf '%s\n' "$phones" >&2
    exit 1
  fi
  printf '%s\n' "$phones" | awk 'NF' | head -n1
}

SERIAL="$(pick_phone)"
echo "Using phone: $SERIAL"

echo "Installing debug build…"
ANDROID_SERIAL="$SERIAL" ./gradlew :app:installDebug "${GRADLE_PROPS[@]+"${GRADLE_PROPS[@]}"}"

echo "Launching $ACTIVITY"
adb -s "$SERIAL" shell am start -n "$ACTIVITY"

echo "Done. Device: $SERIAL"
