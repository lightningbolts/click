# Phase 4 — Remaining `name` / `full_name` references (cleanup checklist)

After running `migrate_user_names_and_birthday.sql`, new signups populate `first_name`, `last_name`, and `birthday`. The following files still mention legacy single-field names for display, APIs, or tests. Update them to prefer `"${firstName} ${lastName}".trim()` (or `displayNameFromUserMetadata` on web) where a human-readable label is needed.

## Kotlin Multiplatform (`click/`)

| Area | File |
|------|------|
| App shell / auth user stub | `composeApp/.../App.kt` — `User(id, name = state.name \|\| email)` |
| Settings UI still labels “Full name” | `composeApp/.../SettingsScreen.kt` |
| Chat / connections (display uses `User.name`) | `ConnectionsScreen.kt`, `ChatViewModel.kt`, `NfcScreen.kt`, `SupabaseChatRepository.kt`, `ConnectionViewModel.kt`, `MyQRCodeScreen.kt` |
| Tests | `composeApp/src/androidUnitTest/.../ChatViewModelTest.kt` |
| REST `UserInfo` model (Python backend contract) | `composeApp/.../AuthModels.kt` — `UserInfo.name` kept for JSON compatibility |
| iOS CallKit | `iosApp/iosApp/ClickCallKitManager.swift` — `normalizedUserInfo` |

## Next.js (`click-web/`)

| Area | File |
|------|------|
| Auth context / session typing | `lib/AuthContext.tsx` (if it exposes `full_name` only) |
| Business / other routes | Grep: `full_name`, `user_metadata?.name` |
| Tests / mocks | Any test still setting only `full_name` without `first_name` / `last_name` |

## Backend / SQL (repo)

| Area | File |
|------|------|
| Python API `create_account` (if deployed) | Expect `first_name`, `last_name`, `birthday` in JSON body to match `SignUpRequest` |
| Duplicate RPC definition | `add_display_name_rpc.sql` — kept in sync with migration; prefer running `migrate_user_names_and_birthday.sql` on existing projects |

Use ripgrep to refresh this list:

```bash
rg "full_name|\\.name\\b|user_metadata\\?\\.full" click click-web --glob '!**/build/**' --glob '!**/.venv/**'
```
