# Mobile Release Validation

The canonical cross-repository release, migration, review, auto-merge, and
no-go procedure lives in the [Click Web production release runbook](https://github.com/lightningbolts/click-web/blob/main/docs/production-release-runbook.md).
`click-web` owns shared Supabase migrations; this repository is a mirror.

Before approving a mobile change, run the Android and iOS gates in that runbook
with a real `click-web` checkout and `REQUIRE_CLICK_WEB=1`. Do not count a
missing sibling checkout, skipped drift check, Kotlin-only iOS build, or green
compiler without Android/iOS device evidence as release approval.

Pull-request workflows check out a same-named `click-web` branch when it exists
so paired PRs stay in lockstep; otherwise they validate against `click-web/main`.
Main-branch runs always use `click-web/main`. Missing matching web branches must
not skip the drift or migration-contract checks.
The signed Android release bundle and iOS PR gate remain pre-merge evidence.
Post-merge iOS release validation resolves the Release Xcode scheme/build
settings and runs the iOS Maestro simulator smoke. The PR gate already compiles
the Kotlin iOS targets and performs the full Debug Xcode integration build.
Xcode Cloud remains the canonical full Release-app integration/archive evidence.
GitHub Actions must not duplicate the full Release native link/archive path,
because that adds tens of minutes without distinct release evidence.
`scripts/check-production-security-guards.sh` also blocks an audible handshake
carrier, ungated simulator fixtures, a public hub-media bucket, or raw push-token
logging patterns.
