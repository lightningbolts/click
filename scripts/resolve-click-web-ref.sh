#!/usr/bin/env bash
# Print the lightningbolts/click-web git ref CI should check out.
#
# Paired mobile/web PRs share a branch name. Mobile-only branches have no
# matching web ref; those must validate against click-web main (the Supabase
# source of truth) instead of failing checkout on a missing branch.
set -euo pipefail

CANDIDATE="${1:-}"
FALLBACK="${2:-main}"
TOKEN="${CLICK_WEB_READ_TOKEN:-${GITHUB_TOKEN:-}}"
REMOTE="https://github.com/lightningbolts/click-web.git"

if [[ -z "$CANDIDATE" || "$CANDIDATE" == "HEAD" ]]; then
  printf '%s\n' "$FALLBACK"
  exit 0
fi

if [[ "$CANDIDATE" == *['*'?'[']* ]]; then
  echo "resolve-click-web-ref: refusing glob-like candidate '${CANDIDATE}'" >&2
  exit 1
fi

ls_remote() {
  if [[ -n "$TOKEN" ]]; then
    git -c "http.extraheader=AUTHORIZATION: bearer ${TOKEN}" \
      ls-remote --heads "$REMOTE" "refs/heads/${CANDIDATE}"
  else
    git ls-remote --heads "$REMOTE" "refs/heads/${CANDIDATE}"
  fi
}

heads="$(ls_remote)"
if [[ -n "$heads" ]]; then
  printf '%s\n' "$CANDIDATE"
else
  echo "click-web has no branch '${CANDIDATE}'; falling back to '${FALLBACK}'" >&2
  printf '%s\n' "$FALLBACK"
fi
