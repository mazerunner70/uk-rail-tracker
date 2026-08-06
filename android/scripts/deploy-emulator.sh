#!/usr/bin/env bash
# Build, install, and launch the debug app on the Pixel 8 Pro emulator AVD.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AVD_NAME="${AVD_NAME:-ukrail_pixel8pro_api35}"
PACKAGE="com.ukrailtracker.app"
ACTIVITY="${PACKAGE}/.MainActivity"

if [[ -n "${ANDROID_HOME:-}" ]]; then
  export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"
fi

require() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "error: '$1' not found on PATH (set ANDROID_HOME?)" >&2
    exit 1
  }
}

require adb
require emulator

cd "$ROOT"

emulator_serial() {
  adb devices | awk '/^emulator-/{ print $1; exit }'
}

boot_completed() {
  local serial="$1"
  [[ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]
}

SERIAL="$(emulator_serial || true)"
if [[ -z "${SERIAL}" ]]; then
  if ! emulator -list-avds | grep -qx "$AVD_NAME"; then
    echo "error: AVD '$AVD_NAME' not found. Create it or set AVD_NAME." >&2
    echo "available:" >&2
    emulator -list-avds >&2 || true
    exit 1
  fi
  echo "Starting emulator AVD: $AVD_NAME"
  emulator -avd "$AVD_NAME" -netdelay none -netspeed full >/dev/null 2>&1 &
  adb wait-for-device
  SERIAL="$(emulator_serial)"
  echo "Waiting for boot ($SERIAL)…"
  until boot_completed "$SERIAL"; do sleep 2; done
else
  echo "Using running emulator: $SERIAL"
  until boot_completed "$SERIAL"; do sleep 2; done
fi

echo "Installing debug build on $SERIAL…"
ANDROID_SERIAL="$SERIAL" ./gradlew :app:installDebug

echo "Launching $ACTIVITY"
adb -s "$SERIAL" shell am start -n "$ACTIVITY"

echo "Done. Device: $SERIAL"
