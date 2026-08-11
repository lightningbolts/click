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
echo "Synced bind-proximity-connection/index.ts"

# Sync every migration that already exists in both trees (mobile mirror subset).
synced=0
while IFS= read -r name; do
  if [[ -f "${WEB_ROOT}/supabase/migrations/${name}" ]]; then
    cp "${WEB_ROOT}/supabase/migrations/${name}" "${ROOT}/supabase/migrations/${name}"
    synced=$((synced + 1))
  fi
done < <(ls "${ROOT}/supabase/migrations" 2>/dev/null | sort)

echo "Synced ${synced} overlapping migration file(s) from ${WEB_ROOT}"
echo "Done. Prefer deploying migrations / shared functions from click-web."
