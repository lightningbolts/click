#!/usr/bin/env bash
# Fail if mirrored supabase paths diverge from click-web (single source of truth).
#
# Layout expectation (local / CI checkout-of-sibling):
#   <parent>/click
#   <parent>/click-web
#
# Shared paths that must stay identical when both trees are present:
#   supabase/functions/bind-proximity-connection/index.ts
#   overlapping migration filenames under supabase/migrations/
#
# Mobile-only Edge Functions (send-push-notification, expire-*, verify-hub-proximity)
# are intentionally owned by this repo and are not compared.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WEB_ROOT="${CLICK_WEB_ROOT:-$(cd "$ROOT/../click-web" 2>/dev/null && pwd || true)}"

if [[ -z "${WEB_ROOT}" || ! -d "${WEB_ROOT}/supabase" ]]; then
  echo "check-supabase-drift: sibling click-web not found at ${ROOT}/../click-web"
  echo "  Set CLICK_WEB_ROOT to the click-web checkout, or skip in isolated CI."
  if [[ "${REQUIRE_CLICK_WEB:-0}" == "1" ]]; then
    exit 1
  fi
  echo "  Skipping drift check (REQUIRE_CLICK_WEB!=1)."
  exit 0
fi

failed=0

compare_file() {
  local rel="$1"
  local a="${ROOT}/${rel}"
  local b="${WEB_ROOT}/${rel}"
  if [[ ! -f "$a" ]]; then
    echo "MISSING in click: ${rel}"
    failed=1
    return
  fi
  if [[ ! -f "$b" ]]; then
    echo "MISSING in click-web: ${rel}"
    failed=1
    return
  fi
  if ! diff -q "$a" "$b" >/dev/null; then
    echo "DRIFT: ${rel}"
    diff -u "$b" "$a" | head -80 || true
    failed=1
  else
    echo "OK: ${rel}"
  fi
}

compare_file "supabase/functions/bind-proximity-connection/index.ts"

# Overlapping migration filenames must match click-web byte-for-byte.
while IFS= read -r name; do
  if [[ -f "${WEB_ROOT}/supabase/migrations/${name}" ]]; then
    compare_file "supabase/migrations/${name}"
  fi
done < <(ls "${ROOT}/supabase/migrations" 2>/dev/null | sort)

if [[ "${failed}" -ne 0 ]]; then
  echo
  echo "Supabase drift detected. click-web/supabase is the source of truth for shared"
  echo "migrations and bind-proximity-connection. Sync with:"
  echo "  bash scripts/sync-supabase-from-click-web.sh"
  exit 1
fi

echo "check-supabase-drift: no drift vs ${WEB_ROOT}"
