# Click Regression Checklist

**This file is a stub.** The canonical regression docs live here:

→ **[docs/regression-testing/00-INDEX.md](docs/regression-testing/00-INDEX.md)**

| Doc | Use |
|-----|-----|
| [Full checklist](docs/regression-testing/01-full-checklist.md) | ~200 items, 25 sections — both platforms |
| [10-min smoke](docs/regression-testing/02-smoke-10min.md) | Pre-merge sanity |
| [Known issues audit](docs/regression-testing/03-known-issues-audit.md) | Issue sheet #1–11 + code evidence |
| [Android focus](docs/regression-testing/04-android-focus.md) | Calls, voice, BLE, map on Android |

Do not treat the old Copilot-era bullets below as complete coverage; they are preserved only for historical reference and are superseded by the docs above.

<details>
<summary>Legacy stub (superseded)</summary>

## Data Layer
- [ ] ConnectionInsert fields unchanged (user_id_1, user_id_2, location_id, context_tag, initiated_by, expires_at)
- [ ] No Map&lt;String, Any&gt; or untyped collections in Repository files
- [ ] All new @Serializable classes have @Serializable annotation

## Security
- [ ] redeem_qr_token RPC call unchanged
- [ ] Proximity score calculation unchanged
- [ ] QR token expiry (90s) unchanged

## iOS Specific
- [ ] No Any types in iosMain code
- [ ] NFC read path still functional
- [ ] NFC write path still functional (after implementation)

## Core Flow
- [ ] Tap → ConnectionInsert creation still fires
- [ ] 30-minute Vibe Check expiry still set
- [ ] Keep/expire mutual opt-in logic unchanged

</details>
