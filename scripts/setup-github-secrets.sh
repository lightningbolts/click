#!/usr/bin/env bash
# Upload Android signing secrets to GitHub Actions for this repo.
# Requires: gh auth login (once)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

GH="${GH:-gh}"
if ! command -v "$GH" >/dev/null 2>&1; then
  echo "Install GitHub CLI (gh) and run: gh auth login" >&2
  exit 1
fi

if ! "$GH" auth status >/dev/null 2>&1; then
  echo "Not logged in. Run: gh auth login" >&2
  exit 1
fi

KEYSTORE="composeApp/keystore/release.jks"
PROPS="keystore.properties"

if [[ ! -f "$KEYSTORE" ]]; then
  echo "Missing $KEYSTORE — copy ~/Documents/keystore.jks there first." >&2
  exit 1
fi

if [[ ! -f "$PROPS" ]]; then
  echo "Missing $PROPS — copy from keystore.properties.example and fill in values." >&2
  exit 1
fi

store_password=$(grep '^storePassword=' "$PROPS" | cut -d= -f2-)
key_alias=$(grep '^keyAlias=' "$PROPS" | cut -d= -f2-)
key_password=$(grep '^keyPassword=' "$PROPS" | cut -d= -f2-)

echo "Setting ANDROID_KEYSTORE_BASE64..."
"$GH" secret set ANDROID_KEYSTORE_BASE64 --body "$(base64 -w0 "$KEYSTORE")"

echo "Setting ANDROID_KEYSTORE_PASSWORD..."
"$GH" secret set ANDROID_KEYSTORE_PASSWORD --body "$store_password"

echo "Setting ANDROID_KEY_ALIAS..."
"$GH" secret set ANDROID_KEY_ALIAS --body "$key_alias"

echo "Setting ANDROID_KEY_PASSWORD..."
"$GH" secret set ANDROID_KEY_PASSWORD --body "$key_password"

echo "Done. Re-run the Android Release workflow on GitHub."
