#!/usr/bin/env bash
# Build a fresh debug APK, replace whatever the emulator/snapshot already has,
# then run Maestro smoke. AVD snapshots keep the previously installed Click app,
# so assembleDebug alone is not enough.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

APP_ID="compose.project.click.click"
APK="$ROOT/composeApp/build/outputs/apk/debug/composeApp-debug.apk"

if ! command -v adb >/dev/null; then
  echo "adb not on PATH. Set ANDROID_HOME to the Android SDK." >&2
  exit 1
fi
if ! command -v maestro >/dev/null; then
  echo "maestro not on PATH. Install the Maestro CLI (see README)." >&2
  exit 1
fi

./gradlew :composeApp:assembleDebug

if [[ ! -f "$APK" ]]; then
  echo "Expected APK at $APK" >&2
  exit 1
fi

if ! adb devices | awk 'NR>1 && $2=="device" {found=1} END {exit found?0:1}'; then
  echo "No Android device/emulator in 'device' state. Boot one first:" >&2
  echo "  maestro start-device --platform android" >&2
  echo "  # or: emulator -avd Pixel_9_Pro" >&2
  exit 1
fi

adb uninstall "$APP_ID" >/dev/null 2>&1 || true
adb install "$APK"
maestro test .maestro --include-tags smoke "$@"
