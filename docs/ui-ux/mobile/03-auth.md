# Click Mobile — Auth (Login & Sign Up)

**Product:** Click — Anti-doomscrolling · Stop scrolling, start living.  
**Scope:** Kotlin Multiplatform mobile (`click/`) — Android + iOS Compose UI only.  
**Out of scope:** Web companion reset-password UI (`/reset-password` is an external browser handoff only).  
**Source of truth:** `LoginScreen.kt`, `SignUpScreen.kt`, `AuthViewModel.kt`, `App.kt` auth gate.  
**Date:** 2026-07-16  

---

## Gate overview

Unauthenticated users see `LoginScreen` by default. Tapping **Sign Up** swaps to `SignUpScreen` in-place (`showSignUp` in `App.kt`). While `authViewModel.authState is AuthState.Loading` or `authShimmerVisible`, `AppShimmerScreen` masks the auth surface. On `AuthState.Success`, `App.kt` transitions to the authenticated graph (profile gate → onboarding → home).

```
Cold boot / signed out
        │
        ▼
  AppShimmer (auth loading)
        │
        ▼
   LoginScreen ◄──────────────┐
        │ Sign Up             │ Back to Login
        ▼                     │
   SignUpScreen ───────────────┘
        │
        │ email / OAuth success
        ▼
  Authenticated app graph
```

---

## LoginScreen

**Source:** `ui/screens/LoginScreen.kt`  
**Wiring:** `App.kt` → `authViewModel.signInWithEmail`, `signInWithGoogle`, `signInWithApple`; `isLoading` / `errorMessage` from `AuthViewModel.authState`.

### Layout / Container

| Layer | Element | Notes |
|-------|---------|-------|
| Root | `Box` full-screen | Vertical gradient: `background` → `primaryContainer` @ 20% alpha |
| Scroll | `Column` + `verticalScroll` | `imePadding`, `navigationBarsPadding`, horizontal 24dp |
| Header | `Icon` TouchApp 80dp | Primary tint |
| Header | Title + subtitle | Center-aligned |
| Form | Email `OutlinedTextField` | 64dp height, 12dp corner radius |
| Form | Password `OutlinedTextField` | Trailing visibility toggle |
| Error | Inline `Text` | Below password, start-aligned |
| Action | Forgot Password `TextButton` | End-aligned row |
| Primary | Sign In `Button` | Full width, 56dp height |
| OAuth | Divider + provider buttons | Only if callbacks non-null |
| Footer | Sign Up link row | Center |

Top inset: `statusBars` + 24dp. Tap outside fields clears focus (`detectTapGestures`).

### Interactive Elements

| Element | Action | Result |
|---------|--------|--------|
| Email field | Type | Updates local state; enables Sign In when non-blank |
| Password field | Type / IME Next → Done | Done submits if both fields non-blank |
| Visibility toggle | Tap | Toggles `passwordVisible` |
| Forgot Password? | Tap | Opens system browser: `{CLICK_WEB_BASE_URL}/reset-password` via `LocalUriHandler` |
| Sign In | Tap | `onEmailSignIn(email, password)` if fields non-blank |
| Continue with Google | Tap | `onGoogleSignIn()` |
| Continue with Apple | Tap | `onAppleSignIn()` |
| Don't have an account? Sign Up | Tap | `onSignUpClick()` |
| Background tap | Tap | Clears focus |

Keyboard: Email → Next; Password → Done (submits).

### States

| State | Visual / behavior |
|-------|-------------------|
| **Default** | Empty or partial fields; Sign In disabled until email + password non-blank |
| **Focus** | Field border `primary`; unfocused border `onSurfaceVariant` @ 50% |
| **Disabled** | All inputs and actions disabled while `isLoading` |
| **Loading** | Sign In shows `AdaptiveCircularProgressIndicator` (24dp, onPrimary, 2dp stroke) instead of label |
| **Error** | `errorMessage` rendered in `error` color, `bodySmall`, below password field |
| **Password hidden** | `PasswordVisualTransformation`; toggle CD "Show password" |
| **Password visible** | Plain text; toggle CD "Hide password" |

OAuth buttons: outlined, 52dp height, disabled while loading.

### Micro-copy

| Context | String |
|---------|--------|
| Logo CD | `"Click Logo"` |
| Title | `"Click"` |
| Subtitle | `"Connect with your world"` |
| Email label | `"Email"` |
| Email icon CD | `"Email"` |
| Password label | `"Password"` |
| Password icon CD | `"Password"` |
| Show/hide password | `"Show password"` / `"Hide password"` |
| Forgot password | `"Forgot Password?"` |
| Primary CTA | `"Sign In"` |
| OAuth divider | `"or"` |
| Google | `"Continue with Google"` |
| Apple | `"Continue with Apple"` |
| Footer prompt | `"Don't have an account?"` |
| Footer link | `"Sign Up"` |

### Flow Sequence

**Happy path — email**
1. User enters email + password → taps Sign In.
2. `AuthViewModel.signInWithEmail` → `AuthState.Loading`.
3. Success → `AuthState.Success` → `isAuthenticated = true` → `AppDataManager.resetAndReload()` → authenticated graph.

**Happy path — OAuth**
1. User taps Google or Apple.
2. `AuthState.Loading` during IdP flow.
3. Google: immediate success if user returned; else awaits deep-link `SessionStatus.Authenticated`.
4. Apple: browser/deep-link completion via `observeOAuthCompletion`.

**Exceptions**
- Sign-in failure → `AuthState.Error` message shown inline (see AuthViewModel errors).
- Forgot Password → external browser to web `/reset-password` (no in-app UI).
- Switch to Sign Up → `showSignUp = true`, `resetAuthState()`.

### A11y & Responsive

| Topic | Detail |
|-------|--------|
| Logo | `contentDescription = "Click Logo"` |
| Password toggle | Explicit Show/Hide CD |
| Scroll | `verticalScroll` + `imePadding` for keyboard |
| Safe areas | `statusBars`, `navigationBars` |
| Button corners | **iOS 14dp** / **Android 12dp** (`LocalPlatformStyle.isIOS`) |
| Button elevation | iOS flat (0dp); Android default Material elevation |
| OAuth buttons | Same corner delta as primary |

---

## SignUpScreen

**Source:** `ui/screens/SignUpScreen.kt`  
**Wiring:** `App.kt` → `authViewModel.signUpWithEmail(...)`; back → `showSignUp = false`, `resetAuthState()`.

### Layout / Container

| Layer | Element | Notes |
|-------|---------|-------|
| Root | `Box` full-screen | Same gradient as Login |
| Chrome | Back `IconButton` | Top-start, 48dp, circular surface + shadow, `zIndex(2)` |
| Scroll | `Column` + `verticalScroll` | `safeDrawing` top, `imePadding`, horizontal 24dp, top 60dp |
| Header | TouchApp icon + titles | Center-aligned |
| Avatar | Optional photo block | 112dp circle, bordered; `AddAPhoto` placeholder |
| Avatar | Remove photo `TextButton` | Shown when preview set |
| Form | First / Last name row | Side-by-side `OutlinedTextField`, weight 1f each |
| Form | Birthday field | Placeholder `YYYY-MM-DD`, calendar trailing icon |
| Form | Email, Password, Confirm Password | Stacked 64dp fields |
| Primary | Create Account `Button` | Full width, 56dp |
| Footer | Sign In link row | Center |

Date picker: `DatePickerDialog` with glass/OLED theming (`GlassSheetTokens`, `PrimaryBlue`).

### Interactive Elements

| Element | Action | Result |
|---------|--------|--------|
| Back (arrow) | Tap | `onLoginClick()` — returns to Login |
| Avatar circle | Tap | `mediaPickers.openPhotoLibrary()` |
| Remove photo | Tap | Clears `pendingAvatarBytes` / mime |
| First / Last name | Type | Required for submit |
| Birthday | Type or calendar | Normalizes `/` → `-`; opens `DatePickerDialog` |
| Date picker OK | Tap | Sets `birthdayIso` from selected millis |
| Date picker Cancel | Tap | Dismisses dialog |
| Email / passwords | Type | Validation gates Create Account |
| Password visibility | Tap | Independent toggles per field |
| Confirm Password IME Done | Submit | Signs up if `canSignUp` |
| Create Account | Tap | `onEmailSignUp(firstName, lastName, birthdayIso, email, password, avatarBytes, avatarMime)` |
| Already have an account? Sign In | Tap | `onLoginClick()` |

### States

| State | Visual / behavior |
|-------|-------------------|
| **Default** | Create Account disabled until all validation passes |
| **Loading** | Fields disabled; button shows spinner (same spec as Login) |
| **Error (server)** | `errorMessage` below confirm password, start-aligned |
| **Error (avatar local)** | `localAvatarError` from media picker, centered below avatar |
| **Birthday empty** | Supporting text: required hint |
| **Birthday invalid** | `isError` + helper when non-blank and invalid |
| **Birthday under 13** | Helper: age requirement |
| **Birthday valid** | No supporting text |
| **Password mismatch** | Confirm field `isError` when non-empty and mismatch |
| **canSignUp** | Requires: non-blank names, valid birthday (≥13), email, password ≥6 chars, passwords match |

Validation constants: `MinSignupAgeYears = 13`, `password.length >= 6`.

### Micro-copy

| Context | String |
|---------|--------|
| Back CD | `"Back to Login"` |
| Logo CD | `"Click Logo"` |
| Title | `"Create Account"` |
| Subtitle | `"Join Click today and start connecting"` |
| Avatar section | `"Profile photo (optional)"` |
| Avatar placeholder CD | `"Choose profile photo"` |
| Avatar preview CD | `"Profile photo preview"` |
| Remove photo | `"Remove photo"` |
| First name | `"First name"` |
| Last name | `"Last name"` |
| Birthday label | `"Birthday"` |
| Birthday placeholder | `"YYYY-MM-DD"` |
| Calendar CD | `"Open birthday calendar"` |
| Birthday helper (empty) | `"Required — type YYYY-MM-DD or use calendar"` |
| Birthday helper (invalid) | `"Enter a valid date (YYYY-MM-DD)"` |
| Birthday helper (under 13) | `"You must be at least 13 years old"` |
| Email | `"Email"` |
| Password | `"Password"` |
| Confirm Password | `"Confirm Password"` |
| Date picker OK | `"OK"` |
| Date picker Cancel | `"Cancel"` |
| Primary CTA | `"Create Account"` |
| Footer prompt | `"Already have an account?"` |
| Footer link | `"Sign In"` |

### Flow Sequence

**Happy path**
1. User fills required fields (optional avatar).
2. Create Account → `AuthViewModel.signUpWithEmail`.
3. Account created → if avatar bytes present, `uploadProfilePicture` runs.
4. Success → `AuthState.Success` → authenticated graph.
5. Avatar upload failure → account still succeeds; transient snackbar via `AppDataManager.postTransientUserMessage`.

**Exceptions**
- Sign-up API failure → inline `AuthState.Error`.
- Avatar pick blocked → inline `localAvatarError` (platform media-picker strings).
- Back to Login → clears error state via `resetAuthState()`.

### A11y & Responsive

| Topic | Detail |
|-------|--------|
| Back button | `contentDescription = "Back to Login"` |
| Password toggles | Show/Hide CD per field |
| Birthday field | `testTag = "signup-birthday-field"` |
| Scroll / IME | Same pattern as Login |
| Button corners | **iOS 14dp** / **Android 12dp** |
| Text field corners | 12dp (both platforms) |

---

## AuthViewModel — user-visible errors

**Source:** `viewmodel/AuthViewModel.kt`  
**Surfacing:** `AuthState.Error.message` → `errorMessage` prop on Login/SignUp; avatar upload → `AppDataManager.postTransientUserMessage` (snackbar in onboarding shell or main app).

### Sign in

| Trigger | Default message |
|---------|-----------------|
| `signInWithEmail` failure | Repository error message, else `"Failed to sign in"` |
| `signInWithEmail` exception | Exception message, else `"An error occurred during sign in"` |
| `signInWithGoogle` failure / exception | Error message, else `"Could not start Google sign-in right now."` |
| `signInWithApple` failure / exception | Error message, else `"Could not start Apple sign-in right now."` |

### Sign up

| Trigger | Default message |
|---------|-----------------|
| `signUpWithEmail` failure | Repository error message, else `"Failed to create account"` |
| `signUpWithEmail` exception | Exception message, else `"An error occurred during sign up"` |
| Avatar upload failure (post-account) | Upload error first line (max 200 chars), else `"Could not upload profile photo. You can add one later in Settings."` |

### Avatar / media picker errors (Sign Up inline)

Sign Up surfaces `onMediaAccessBlocked` messages inline below the avatar. Representative strings from platform pickers:

| Platform | String |
|----------|--------|
| Android (gallery) | `"Couldn't read that photo. If access was denied, enable Photos & videos permission for Click in Settings."` |
| Android (camera denied) | `"Camera permission is off. To take photos in chat, enable Camera for Click in Settings."` |
| iOS (iCloud) | `"Cannot load image from iCloud. Please try a local photo."` |
| iOS (read fail) | `"Couldn't read that photo. Enable Photos access for Click in Settings."` |
| iOS (no camera) | `"Camera is not available on this device."` |
| iOS (camera denied) | `"Camera permission is off. To take photos in chat, enable Camera for Click in Settings."` |

> Sign Up avatar picker uses photo library only (`openPhotoLibrary`); camera strings apply to other screens sharing the same picker infrastructure.

### External handoff — Forgot Password

| Action | Destination |
|--------|-------------|
| Forgot Password? tap | System browser → `{ApiConfig.CLICK_WEB_BASE_URL}/reset-password` |

No web UI spec in this document.

---

## Auth flow sequence (end-to-end)

```
App launch
    │
    ├─ authState Loading / shimmer ──► AppShimmerScreen
    │
    ├─ !isAuthenticated
    │       ├─ LoginScreen (default)
    │       │     ├─ Sign In (email) ──► Success ──► authenticated
    │       │     ├─ Google / Apple ──► Success ──► authenticated
    │       │     ├─ Forgot Password ──► external browser /reset-password
    │       │     └─ Sign Up ──► SignUpScreen
    │       └─ SignUpScreen
    │             ├─ Create Account ──► Success (+ optional avatar upload snackbar)
    │             └─ Back / Sign In link ──► LoginScreen
    │
    └─ isAuthenticated ──► Profile gate → Onboarding → Home (see 04-onboarding-gates.md)
```

---

## Platform deltas (auth buttons)

| Control | iOS | Android |
|---------|-----|---------|
| Sign In / Create Account corner radius | 14dp | 12dp |
| OAuth outlined buttons | 14dp | 12dp |
| Primary button elevation | 0dp (flat) | Material default |
| Text field corners | 12dp | 12dp |
