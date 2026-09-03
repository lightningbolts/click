#!/usr/bin/env bash
# Print the lightningbolts/click-web git ref CI should check out.
#
# Paired mobile/web PRs share a branch name. Mobile-only branches have no
# matching web ref; those must validate against click-web main (the Supabase
# source of truth) instead of failing checkout on a missing branch.
#
# Uses the GitHub API rather than `git ls-remote` so GitHub Actions can
# authenticate with GH_TOKEN. Hosted runners have no TTY, so an HTTPS
# username prompt fails with "could not read Username".
set -euo pipefail

CANDIDATE="${1:-}"
FALLBACK="${2:-main}"
TOKEN="${CLICK_WEB_READ_TOKEN:-${GH_TOKEN:-${GITHUB_TOKEN:-}}}"
REPO="lightningbolts/click-web"

if [[ -z "$CANDIDATE" || "$CANDIDATE" == "HEAD" ]]; then
  printf '%s\n' "$FALLBACK"
  exit 0
fi

if [[ "$CANDIDATE" == *['*'?'[']* ]]; then
  echo "resolve-click-web-ref: refusing glob-like candidate '${CANDIDATE}'" >&2
  exit 1
fi

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
  echo "click-web has no branch '${CANDIDATE}'; falling back to '${FALLBACK}'" >&2
  printf '%s\n' "$FALLBACK"
  exit 0
fi

echo "resolve-click-web-ref: failed to inspect ${REPO} branch '${CANDIDATE}'" >&2
printf '%s\n' "$api_err" >&2
exit 1
