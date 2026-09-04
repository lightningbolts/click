#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_ROOT="${CLICK_WEB_ROOT:-$ROOT/../click-web}"
cd "$ROOT"

fail() {
  echo "production security guard failed: $1" >&2
  exit 1
}

grep -Fq 'const val HANDSHAKE_CARRIER_HZ: Double = 18_500.0' \
  composeApp/src/commonMain/kotlin/compose/project/click/click/proximity/UltrasonicTokenCodec.kt \
  || fail "ultrasonic carrier must remain 18.5 kHz"

if grep -R -n -E 'AUDIBLE TEST|440[[:space:]]*Hz' composeApp/src --include='*.kt'; then
  fail "audible test carrier marker found in production mobile source"
fi

for edge_function in \
  supabase/functions/bind-proximity-connection/index.ts \
  "$WEB_ROOT/supabase/functions/bind-proximity-connection/index.ts"; do
  grep -Fq "Deno.env.get('CLICK_ENABLE_SIMULATOR_MOCK') === 'true'" "$edge_function" \
    || fail "simulator fixture lacks explicit environment gate in $edge_function"
  grep -Fq "Deno.env.get('CLICK_APP_ENV') !== 'production'" "$edge_function" \
    || fail "simulator fixture lacks production denial in $edge_function"
done

grep -Fq "VALUES ('hub-media', 'hub-media', false)" \
  "$WEB_ROOT/supabase/migrations/20260901300000_event_hub_security_reconciliation.sql" \
  || fail "hub-media bucket is not explicitly private"

if grep -R -n -E 'console\.(log|warn|error)\([^)]*(token\.token|push[_-]?token)' \
  supabase/functions "$WEB_ROOT/supabase/functions" --include='*.ts'; then
  fail "raw push-token logging pattern found"
fi

echo "production security guards: passed"
