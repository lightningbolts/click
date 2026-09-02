#!/usr/bin/env bash
# Fail if mirrored supabase paths diverge from click-web (single source of truth).
#
# Layout expectation (local / CI checkout-of-sibling):
#   <parent>/click
#   <parent>/click-web
#
# Shared paths that must stay identical:
#   supabase/functions/bind-proximity-connection/index.ts
#   mirrored migration filenames under supabase/migrations/
#
# One historical migration is intentionally different because the two already
# merged copies were reconciled forward rather than rewritten. Its exact hashes
# are asserted below; every newer migration must be present and byte-identical
# in both repositories.
#
# Mobile-only Edge Functions (send-push-notification, expire-*, verify-hub-proximity)
# are intentionally owned by this repo and are not compared.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WEB_ROOT="${CLICK_WEB_ROOT:-$(cd "$ROOT/../click-web" 2>/dev/null && pwd || true)}"

if [[ -z "${WEB_ROOT}" || ! -d "${WEB_ROOT}/supabase" ]]; then
  echo "check-supabase-drift: sibling click-web not found at ${ROOT}/../click-web"
  echo "  Set CLICK_WEB_ROOT to a checkout of click-web (the Supabase source of truth)."
  echo "  This check never skips: migration/function drift is a release blocker."
  exit 1
fi

failed=0

sha256() {
  shasum -a 256 "$1" | awk '{print $1}'
}

is_acknowledged_historical_divergence() {
  local rel="$1"
  local mobile_file="$2"
  local web_file="$3"

  case "$rel" in
    supabase/migrations/20260831000000_event_auto_hubs.sql)
      # Audited 2026-09-01: the web source shipped map_beacons.hub_id in this
      # migration, while mobile shipped it in the later reconciliation
      # migration. Do not normalize this applied history; fail if either copy
      # changes, and require parity for all subsequent migrations.
      [[ "$(sha256 "$mobile_file")" == "9bbcdd3c72fa2d116a6eab2d490bb625987d49a76c0fd97cd688363c6baa3270" ]] \
        && [[ "$(sha256 "$web_file")" == "066fe55640560aace04edd73fe94b9b77a786828f61b683d39cbef3a0086ffa0" ]]
      ;;
    *)
      return 1
      ;;
  esac
}

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
    if is_acknowledged_historical_divergence "$rel" "$a" "$b"; then
      echo "ACKNOWLEDGED HISTORICAL DIVERGENCE (hash-locked): ${rel}"
      return
    fi
    echo "DRIFT: ${rel}"
    diff -u "$b" "$a" | head -80 || true
    failed=1
  else
    echo "OK: ${rel}"
  fi
}

compare_file "supabase/functions/bind-proximity-connection/index.ts"
compare_file "supabase/functions/bind-proximity-connection/bindSupport.ts"

# The 20260831000000 historical exception is hash-locked above. Every migration
# after it must exist and match in both repositories; older mobile-only mirrors
# remain an intentional subset of the web source of truth.
HISTORICAL_DIVERGENCE_VERSION="20260831000000"

while IFS= read -r name; do
  version="${name%%_*}"
  if [[ "$version" > "$HISTORICAL_DIVERGENCE_VERSION" ]]; then
    compare_file "supabase/migrations/${name}"
  elif [[ -f "${WEB_ROOT}/supabase/migrations/${name}" ]]; then
    compare_file "supabase/migrations/${name}"
  fi
done < <(ls "${ROOT}/supabase/migrations" 2>/dev/null | sort)

while IFS= read -r name; do
  version="${name%%_*}"
  if [[ "$version" > "$HISTORICAL_DIVERGENCE_VERSION" && ! -f "${ROOT}/supabase/migrations/${name}" ]]; then
    compare_file "supabase/migrations/${name}"
  fi
done < <(ls "${WEB_ROOT}/supabase/migrations" 2>/dev/null | sort)

if [[ "${failed}" -ne 0 ]]; then
  echo
  echo "Supabase drift detected. click-web/supabase is the source of truth for shared"
  echo "migrations and bind-proximity-connection. Sync with:"
  echo "  bash scripts/sync-supabase-from-click-web.sh"
  exit 1
fi

echo "check-supabase-drift: no drift vs ${WEB_ROOT}"
