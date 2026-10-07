#!/usr/bin/env bash
# Print the lightningbolts/click-web git ref CI should check out.
#
# Precedence:
#   1. CLICK_WEB_REF (manual override: branch, tag or SHA), used verbatim.
#   2. A paired branch passed as $1, if click-web has a branch with that name.
#   3. The pinned SHA in .click-web-ref (D9). CI never follows click-web main
#      implicitly, so a new web migration cannot turn Android red until the
#      weekly drift report bumps the pin (see AGENTS.md).
#
# Uses the GitHub API rather than `git ls-remote` so GitHub Actions can
# authenticate with GH_TOKEN. Hosted runners have no TTY, so an HTTPS
# username prompt fails with "could not read Username".
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PIN_FILE="${ROOT}/.click-web-ref"
OVERRIDE="${CLICK_WEB_REF:-}"
CANDIDATE="${1:-}"
TOKEN="${CLICK_WEB_READ_TOKEN:-${GH_TOKEN:-${GITHUB_TOKEN:-}}}"
REPO="lightningbolts/click-web"

refuse_glob() {
  if [[ "$1" == *['*'?'[']* ]]; then
    echo "resolve-click-web-ref: refusing glob-like ref '$1'" >&2
    exit 1
  fi
}

if [[ -n "$OVERRIDE" ]]; then
  refuse_glob "$OVERRIDE"
  echo "click-web ref: manual override '${OVERRIDE}'" >&2
  printf '%s\n' "$OVERRIDE"
  exit 0
fi

PIN="$(tr -d '[:space:]' < "$PIN_FILE" 2>/dev/null || true)"
if [[ ! "$PIN" =~ ^[0-9a-f]{40}$ ]]; then
  echo "resolve-click-web-ref: ${PIN_FILE} must contain one full 40-char click-web SHA" >&2
  exit 1
fi

if [[ -z "$CANDIDATE" || "$CANDIDATE" == "HEAD" ]]; then
  printf '%s\n' "$PIN"
  exit 0
fi

refuse_glob "$CANDIDATE"

encoded="$(python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=""))' "$CANDIDATE")"
if [[ -n "$TOKEN" ]]; then
  export GH_TOKEN="$TOKEN"
fi

set +e
api_err="$(gh api --silent "repos/${REPO}/branches/${encoded}" 2>&1)"
api_status=$?
set -e

if [[ "$api_status" -eq 0 ]]; then
  printf '%s\n' "$CANDIDATE"
  exit 0
fi

if [[ "$api_err" == *"HTTP 404"* || "$api_err" == *"Not Found"* ]]; then
  echo "click-web has no branch '${CANDIDATE}'; using pinned ${PIN}" >&2
  printf '%s\n' "$PIN"
  exit 0
fi

echo "resolve-click-web-ref: failed to inspect ${REPO} branch '${CANDIDATE}'" >&2
printf '%s\n' "$api_err" >&2
exit 1
