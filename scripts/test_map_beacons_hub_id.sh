#!/usr/bin/env bash
# Delegate to click-web (source of truth for this migration).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_ROOT="${CLICK_WEB_ROOT:-$(cd "$ROOT/../click-web" 2>/dev/null && pwd || true)}"
TARGET="${WEB_ROOT}/scripts/test_map_beacons_hub_id.sh"

if [[ -z "${WEB_ROOT}" || ! -f "$TARGET" ]]; then
  echo "click-web test script not found. Set CLICK_WEB_ROOT or check out ../click-web." >&2
  exit 1
fi

exec bash "$TARGET" "$@"
