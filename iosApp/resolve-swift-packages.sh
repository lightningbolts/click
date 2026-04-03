#!/usr/bin/env bash
# Retry Swift package resolution for large LiveKit binary artifacts (WebRTC / UniFFI).
# Use when xcodebuild fails with downloadError('The network connection was lost.').
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
PROJECT="iosApp.xcodeproj"
SCHEME="iosApp"
MAX_ATTEMPTS="${1:-5}"
attempt=1
while [[ "$attempt" -le "$MAX_ATTEMPTS" ]]; do
  echo "SwiftPM resolve attempt ${attempt}/${MAX_ATTEMPTS}..."
  if xcodebuild -project "$PROJECT" -scheme "$SCHEME" -resolvePackageDependencies -quiet; then
    echo "Package dependencies resolved."
    exit 0
  fi
  if [[ "$attempt" -lt "$MAX_ATTEMPTS" ]]; then
    sleep $((attempt * 8))
  fi
  attempt=$((attempt + 1))
done
echo "Resolve failed after ${MAX_ATTEMPTS} attempts. Try: stable Wi‑Fi/Ethernet, VPN off, then:" >&2
echo "  rm -rf ~/Library/Caches/org.swift.swiftpm/repositories" >&2
echo "  rm -rf ~/Library/Developer/Xcode/DerivedData/*" >&2
exit 1
