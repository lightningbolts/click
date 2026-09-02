# Mobile Release Validation

The canonical cross-repository release, migration, review, auto-merge, and
no-go procedure lives in the [Click Web production release runbook](https://github.com/lightningbolts/click-web/blob/main/docs/production-release-runbook.md).
`click-web` owns shared Supabase migrations; this repository is a mirror.

Before approving a mobile change, run the Android and iOS gates in that runbook
with a real `click-web` checkout and `REQUIRE_CLICK_WEB=1`. Do not count a
missing sibling checkout, skipped drift check, Kotlin-only iOS build, or green
compiler without Android/iOS device evidence as release approval.

Pull-request workflows require the matching `click-web` branch so migration
parity is validated before either PR merges. Main-branch runs use `click-web/main`.
The signed Android release bundle, Debug and Release iOS simulator apps,
unsigned Release iOS device compile, and both Maestro Cloud platforms are
required pull-request checks rather than post-merge-only evidence.
`scripts/check-production-security-guards.sh` also blocks an audible handshake
carrier, ungated simulator fixtures, a public hub-media bucket, or raw push-token
logging patterns.
