#!/usr/bin/env bash
# Dump or follow logcat for com.ukrailtracker.app (crashes by default).
#
# Usage:
#   ./scripts/check-logs.sh              # recent FATAL / app errors
#   ./scripts/check-logs.sh --follow     # live stream (app + runtime)
#   ./scripts/check-logs.sh --all        # recent app lines (not only errors)
#   ./scripts/check-logs.sh --clear      # clear logcat buffer
#   ANDROID_SERIAL=emulator-5554 ./scripts/check-logs.sh
set -euo pipefail

PACKAGE="com.ukrailtracker.app"
MODE="crash"
LINES="${LINES:-4000}"
SERIAL="${ANDROID_SERIAL:-}"

usage() {
  cat <<'EOF'
Usage: check-logs.sh [options]

  (default)           Dump recent FATAL exceptions and app errors
  --follow, -f        Follow live logcat (app + AndroidRuntime)
  --all, -a           Dump recent lines for the app (any level)
  --crash, -c         Same as default: crash / error dump
  --clear             Clear the device logcat buffer, then exit
  --lines N           Buffer size for dump modes (default: 4000, or LINES=)
  --serial SERIAL     Device serial (or set ANDROID_SERIAL)
  --help, -h          Show this help

Examples:
  ./scripts/check-logs.sh
  ./scripts/check-logs.sh --follow
  ./scripts/check-logs.sh --all --lines 5000
  ANDROID_SERIAL=emulator-5554 ./scripts/check-logs.sh --crash
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --follow|-f) MODE="follow"; shift ;;
    --all|-a) MODE="all"; shift ;;
    --crash|-c) MODE="crash"; shift ;;
    --clear) MODE="clear"; shift ;;
    --lines)
      [[ $# -ge 2 ]] || { echo "error: --lines needs a number" >&2; exit 1; }
      LINES="$2"
      shift 2
      ;;
    --serial)
      [[ $# -ge 2 ]] || { echo "error: --serial needs a value" >&2; exit 1; }
      SERIAL="$2"
      shift 2
      ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "error: unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

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

pick_device() {
  if [[ -n "$SERIAL" ]]; then
    echo "$SERIAL"
    return
  fi
  local devices
  devices="$(adb devices | awk '/\tdevice$/{ print $1 }')"
  local count
  count="$(printf '%s\n' "$devices" | awk 'NF' | wc -l | tr -d ' ')"
  if [[ "$count" -eq 0 ]]; then
    echo "error: no device in 'adb devices' (start emulator or connect a phone)." >&2
    adb devices >&2
    exit 1
  fi
  if [[ "$count" -gt 1 ]]; then
    echo "error: multiple devices connected; set ANDROID_SERIAL or pass --serial:" >&2
    printf '%s\n' "$devices" >&2
    exit 1
  fi
  printf '%s\n' "$devices" | awk 'NF' | head -n1
}

# Extract FATAL EXCEPTION blocks for our package (or any if package not named).
extract_fatals() {
  awk -v pkg="$PACKAGE" '
    function flush() {
      if (buf == "") return
      if (pkg == "" || buf ~ pkg || buf ~ /ukrailtracker/) {
        if (printed++) print "----"
        print buf
      }
      buf = ""
      capturing = 0
      blank = 0
    }
    /FATAL EXCEPTION/ {
      flush()
      capturing = 1
      buf = $0
      blank = 0
      next
    }
    capturing {
      # Keep AndroidRuntime / DEBUG stack lines; stop on unrelated tags after a blank.
      if ($0 ~ /AndroidRuntime|DEBUG[[:space:]]*:|^\t|Caused by:|[[:space:]]at /) {
        buf = buf "\n" $0
        blank = 0
        next
      }
      if ($0 ~ /^[[:space:]]*$/) {
        blank++
        if (blank >= 1) flush()
        next
      }
      # New unrelated log line ends the stack.
      flush()
    }
    END { flush() }
  '
}

SERIAL="$(pick_device)"
ADB=(adb -s "$SERIAL")

case "$MODE" in
  clear)
    echo "Clearing logcat on $SERIAL…"
    "${ADB[@]}" logcat -c
    echo "Done."
    ;;
  follow)
    echo "Following logcat on $SERIAL (Ctrl+C to stop)…"
    "${ADB[@]}" logcat -v time \
      "*:S" \
      "AndroidRuntime:E" \
      "System.err:W" \
      "${PACKAGE}:V" \
      | grep --line-buffered -E "FATAL|AndroidRuntime|${PACKAGE}|ukrailtracker|Force finishing|dying"
    ;;
  all)
    echo "Recent app logcat on $SERIAL (last ~${LINES} buffered lines)…"
    "${ADB[@]}" logcat -d -v time -t "$LINES" \
      | grep -E "${PACKAGE}|ukrailtracker|AndroidRuntime" \
      || {
        echo "(no matching lines — try reproducing the issue, or run with --follow)" >&2
        exit 1
      }
    ;;
  crash)
    echo "Recent crashes on $SERIAL…"
    found=0

    # Dedicated crash buffer (survives some main-buffer wrap).
    crash_buf="$("${ADB[@]}" logcat -d -b crash -v time 2>/dev/null || true)"
    if [[ -n "$crash_buf" ]]; then
      filtered="$(printf '%s\n' "$crash_buf" | extract_fatals || true)"
      if [[ -n "$filtered" ]]; then
        echo "=== crash buffer ==="
        printf '%s\n' "$filtered"
        found=1
      elif printf '%s\n' "$crash_buf" | grep -qE "${PACKAGE}|ukrailtracker"; then
        echo "=== crash buffer (app-related) ==="
        printf '%s\n' "$crash_buf" | grep -E "${PACKAGE}|ukrailtracker|AndroidRuntime|FATAL|at |Caused by" || true
        found=1
      fi
    fi

    main_buf="$("${ADB[@]}" logcat -d -v time -t "$LINES" 2>/dev/null || true)"
    if [[ -n "$main_buf" ]]; then
      filtered="$(printf '%s\n' "$main_buf" | extract_fatals || true)"
      if [[ -n "$filtered" ]]; then
        echo "=== main buffer (FATAL) ==="
        printf '%s\n' "$filtered"
        found=1
      fi
    fi

    if [[ "$found" -eq 1 ]]; then
      exit 0
    fi

    # Fallback: process death / app errors without a full FATAL block in buffer.
    if [[ -n "${main_buf:-}" ]]; then
      errors="$(
        printf '%s\n' "$main_buf" | grep -E           "AndroidRuntime|ActivityManager:.*(crash|Force finishing|has died)|E/${PACKAGE}|${PACKAGE}: [EF]"           || true
      )"
      if [[ -n "$errors" ]]; then
        echo "(no FATAL EXCEPTION block found; showing recent app/runtime errors)"
        printf '%s\n' "$errors" | tail -n 120
        exit 0
      fi
    fi

    echo "(no crash found in buffer — reproduce, then re-run; or: ./scripts/check-logs.sh --follow)" >&2
    exit 1
    ;;
esac
