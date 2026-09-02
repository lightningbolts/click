#!/usr/bin/env bash
# Copy shared supabase artifacts FROM click-web (source of truth) INTO this repo.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WEB_ROOT="${CLICK_WEB_ROOT:-$(cd "$ROOT/../click-web" 2>/dev/null && pwd || true)}"

if [[ -z "${WEB_ROOT}" || ! -d "${WEB_ROOT}/supabase" ]]; then
  echo "click-web not found. Set CLICK_WEB_ROOT to the click-web checkout." >&2
  exit 1
fi

mkdir -p "${ROOT}/supabase/functions/bind-proximity-connection" "${ROOT}/supabase/migrations"

cp "${WEB_ROOT}/supabase/functions/bind-proximity-connection/index.ts" \
  "${ROOT}/supabase/functions/bind-proximity-connection/index.ts"
cp "${WEB_ROOT}/supabase/functions/bind-proximity-connection/bindSupport.ts" \
  "${ROOT}/supabase/functions/bind-proximity-connection/bindSupport.ts"
echo "Synced bind-proximity-connection/index.ts + bindSupport.ts"

# Never rewrite the audited, already-applied historical divergence. It is
# reconciled by a later migration and hash-locked by check-supabase-drift.sh.
HISTORICAL_DIVERGENCE="20260831000000_event_auto_hubs.sql"
MIRROR_REQUIRED_AFTER="20260831000000"

# Sync the existing mirror subset plus every source migration after the
# historical divergence. New shared migration history therefore flows only
# from click-web to click.
synced=0
while IFS= read -r name; do
  version="${name%%_*}"
  destination="${ROOT}/supabase/migrations/${name}"

  if [[ "$name" == "$HISTORICAL_DIVERGENCE" ]]; then
    echo "Skipped hash-locked historical divergence: ${name}"
    continue
  fi
  if [[ -f "$destination" || "$version" > "$MIRROR_REQUIRED_AFTER" ]]; then
    cp "${WEB_ROOT}/supabase/migrations/${name}" "$destination"
    synced=$((synced + 1))
  fi
done < <(ls "${WEB_ROOT}/supabase/migrations" 2>/dev/null | sort)

echo "Synced ${synced} mirrored migration file(s) from ${WEB_ROOT}"
echo "Done. Prefer deploying migrations / shared functions from click-web."
