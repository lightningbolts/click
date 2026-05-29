#!/usr/bin/env bash
# Prints the base64 keystore payload for GitHub secret ANDROID_KEYSTORE_BASE64.
# Source keystore: ~/Documents/keystore.jks (copied locally to composeApp/keystore/release.jks).

set -euo pipefail

KEYSTORE="composeApp/keystore/release.jks"

if [[ ! -f "$KEYSTORE" ]]; then
  echo "Missing $KEYSTORE" >&2
  exit 1
fi

echo "Add these repository secrets in GitHub (Settings → Secrets and variables → Actions):"
echo "  ANDROID_KEYSTORE_BASE64  — paste the single line below"
echo "  ANDROID_KEYSTORE_PASSWORD"
echo "  ANDROID_KEY_ALIAS          (key0)"
echo "  ANDROID_KEY_PASSWORD"
echo
echo "ANDROID_KEYSTORE_BASE64 value:"
base64 -w0 "$KEYSTORE"
echo
