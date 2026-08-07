#!/usr/bin/env bash
# Resolve Swift package dependencies for iosApp (LiveKit + GoogleSignIn).
# Retries through common SPM failures: network drops, corrupt artifact caches,
# and optional git submodule checkouts that are not required to build.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
PROJECT="iosApp.xcodeproj"
SCHEME="iosApp"
MAX_ATTEMPTS="${1:-5}"

# Prefer a full Xcode install over Command Line Tools.
if [[ -z "${DEVELOPER_DIR:-}" ]]; then
  for candidate in \
    /Applications/Xcode.app/Contents/Developer \
    /Applications/Xcode-beta.app/Contents/Developer; do
    if [[ -d "$candidate" ]]; then
      export DEVELOPER_DIR="$candidate"
      break
    fi
  done
fi

# LiveKit / SwiftProtobuf ship .gitmodules that SPM does not need for the
# Swift products. Disabling recurse avoids "Couldn't update repository submodules".
export GIT_CONFIG_COUNT=1
export GIT_CONFIG_KEY_0=submodule.recurse
export GIT_CONFIG_VALUE_0=false

clear_spm_artifact_traps() {
  local artifacts="${HOME}/Library/Caches/org.swift.swiftpm/artifacts"
  [[ -d "$artifacts" ]] || return 0
  # SPM can leave empty/partial dirs that then fail with "already exists in file system".
  find "$artifacts" -maxdepth 1 \( -iname '*livekit*' -o -iname '*webrtc*' -o -iname '*uniffi*' \) \
    -exec rm -rf {} + 2>/dev/null || true
}

attempt=1
while [[ "$attempt" -le "$MAX_ATTEMPTS" ]]; do
  echo "SwiftPM resolve attempt ${attempt}/${MAX_ATTEMPTS} (DEVELOPER_DIR=${DEVELOPER_DIR:-default})..."
  if xcodebuild -project "$PROJECT" -scheme "$SCHEME" -resolvePackageDependencies -quiet; then
    echo "Package dependencies resolved."
    exit 0
  fi
  clear_spm_artifact_traps
  if [[ "$attempt" -lt "$MAX_ATTEMPTS" ]]; then
    sleep $((attempt * 8))
  fi
  attempt=$((attempt + 1))
done

echo "Resolve failed after ${MAX_ATTEMPTS} attempts. Try: stable Wi‑Fi/Ethernet, VPN off, then:" >&2
echo "  rm -rf ~/Library/Caches/org.swift.swiftpm/artifacts" >&2
echo "  rm -rf ~/Library/Caches/org.swift.swiftpm/repositories" >&2
echo "  rm -rf ~/Library/Developer/Xcode/DerivedData/iosApp-*" >&2
echo "  sudo xcode-select -s /Applications/Xcode-beta.app/Contents/Developer   # or Xcode.app" >&2
echo "  ./resolve-swift-packages.sh" >&2
exit 1
