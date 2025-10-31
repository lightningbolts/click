# Authentication Implementation with Python Backend

## Overview

The authentication system has been **completely refactored** to use the Python Flask server instead of Supabase. This gives you full control over the authentication logic and allows you to use your existing Python infrastructure.

## Architecture

### Backend (Python Flask)

**Location**: `/server/app.py`

The Flask server provides the following authentication endpoints:

1. **`POST /login`** - Email/password login
   - Request body: `{ "email": "user@uw.edu", "password": "password" }`
   - Response: `{ "jwt": "...", "refresh": "...", "user": { "email": "..." } }`

2. **`POST /create_account`** - Email/password signup
   - Request body: `{ "email": "user@uw.edu", "password": "password", "name": "User Name" }`
   - Response: `{ "jwt": "...", "refresh": "...", "user": { "email": "...", "name": "..." } }`

3. **`POST /google?token=...`** - Google OAuth authentication
   - Query param: `token` (Google OAuth token)
   - Response: `{ "jwt": "...", "refresh": "..." }`

4. **`POST /refresh`** - Refresh JWT token
   - Header: `Authorization: <refresh_token>`
   - Response: New JWT token (plain text)

5. **`POST /logout`** - Logout and invalidate refresh token
   - Header: `Authorization: <refresh_token>`
   - Response: Success message

### Frontend (Kotlin Multiplatform)

**Key Components**:

#### 1. API Client (`data/api/ApiClient.kt`)
- Ktor-based HTTP client for communicating with the Flask server
- Handles all API requests and responses
- Returns `Result<T>` for error handling

#### 2. Data Models (`data/models/AuthModels.kt`)
- `LoginRequest`, `SignUpRequest`, `GoogleAuthRequest` - Request models
- `AuthResponse`, `UserInfo`, `ErrorResponse` - Response models
- All models are serializable with kotlinx.serialization

#### 3. Token Storage (`data/storage/TokenStorage.kt`)
- **Interface**: Common interface for storing JWT and refresh tokens
- **Android implementation**: Uses encrypted SharedPreferences for security
- **iOS implementation**: Uses NSUserDefaults (KeyChain integration recommended)
- Platform-specific implementations via expect/actual pattern

#### 4. AuthViewModel (`viewmodel/AuthViewModel.kt`)
- Manages authentication state
- Handles login, signup, Google OAuth, token refresh, and logout
- Stores tokens securely using TokenStorage
- Exposes `AuthState` for UI updates

## Security Features

### Backend
- **JWT tokens** with 24-hour expiration
- **Refresh tokens** stored in `refresh.json`
- **Domain validation** - Only @uw.edu emails allowed
- **Google OAuth verification** using Google's id_token library

### Frontend
- **Encrypted storage** on Android using androidx.security:security-crypto
- **Secure token storage** on iOS using NSUserDefaults (can be upgraded to KeyChain)
- **Automatic token refresh** when needed
- **Tokens cleared on logout** both locally and on server

## Configuration

### Python Server

1. Create a `.env` file in `/server/` with:
```env
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
```

2. Install dependencies:
```bash
cd server
pip install -r requirements.txt
```

3. Run the server:
```bash
python app.py
```

The server will run on `http://localhost:5000` by default.

### Kotlin App

The API client is configured to connect to `http://localhost:5000` by default. To change this:

Edit `/composeApp/src/commonMain/kotlin/compose/project/click/click/data/api/ApiClient.kt`:
```kotlin
class ApiClient(private val baseUrl: String = "http://YOUR_SERVER_URL:5000")
```

For production, you'll want to:
1. Deploy the Flask server (e.g., on AWS, Google Cloud, Heroku)
2. Update the `baseUrl` to your production URL
3. Ensure HTTPS is enabled

## Authentication Flow

### Email/Password Login
1. User enters email and password
2. App sends request to `/login`
3. Server validates credentials (currently accepts any @uw.edu email)
4. Server returns JWT + refresh token
5. App stores tokens securely
6. App updates authentication state

### Email/Password Signup
1. User enters name, email, and password
2. App sends request to `/create_account`
3. Server validates email domain (@uw.edu)
4. Server creates account (TODO: store in database)
5. Server returns JWT + refresh token
6. App stores tokens and authenticates user

### Google OAuth (TODO)
1. User clicks "Sign in with Google"
2. Platform-specific Google Sign-In flow obtains token
3. App sends token to `/google`
4. Server verifies token with Google
5. Server returns JWT + refresh token
6. App stores tokens and authenticates user

### Token Refresh
1. App detects expired JWT
2. App sends refresh token to `/refresh`
3. Server validates refresh token
4. Server returns new JWT
5. App updates stored JWT

### Logout
1. User clicks logout
2. App sends refresh token to `/logout`
3. Server removes refresh token from valid list
4. App clears local tokens
5. App returns to login screen

## TODO / Next Steps

### Backend Improvements

1. **Database Integration**
   - Store user accounts in database (currently no persistence)
   - Hash passwords with bcrypt/argon2
   - Store refresh tokens in database instead of JSON file

2. **Password Validation**
   - Currently accepts any password for @uw.edu emails
   - Implement proper password verification

3. **Email Verification**
   - Send verification emails on signup
   - Require email verification before login

4. **Rate Limiting**
   - Prevent brute force attacks
   - Use Flask-Limiter or similar

5. **CORS Configuration**
   - Configure proper CORS headers for production
   - Restrict allowed origins

### Frontend Improvements

1. **Google OAuth Integration**
   - Implement platform-specific Google Sign-In
   - Android: Use Google Sign-In SDK
   - iOS: Use Sign in with Apple or Google Sign-In SDK

2. **Token Expiration Handling**
   - Parse JWT to check expiration
   - Auto-refresh before expiration
   - Handle edge cases (offline mode, etc.)

3. **iOS KeyChain Integration**
   - Upgrade from NSUserDefaults to KeyChain for better security

4. **Biometric Authentication**
   - Add fingerprint/Face ID support
   - Store tokens behind biometric lock

5. **Error Handling**
   - Better error messages for network failures
   - Retry logic for failed requests
   - Offline mode handling

## File Structure

```
composeApp/src/
├── commonMain/kotlin/compose/project/click/click/
│   ├── App.kt (updated to use TokenStorage)
│   ├── data/
│   │   ├── api/
│   │   │   └── ApiClient.kt (NEW - HTTP client)
│   │   ├── models/
│   │   │   └── AuthModels.kt (NEW - data models)
│   │   └── storage/
│   │       └── TokenStorage.kt (NEW - storage interface)
│   └── viewmodel/
│       └── AuthViewModel.kt (UPDATED - uses Python API)
├── androidMain/kotlin/compose/project/click/click/
│   └── data/storage/
│       └── TokenStorage.android.kt (NEW - Android implementation)
└── iosMain/kotlin/compose/project/click/click/
    └── data/storage/
        └── TokenStorage.ios.kt (NEW - iOS implementation)

server/
├── app.py (UPDATED - added email/password endpoints)
├── refresh.json (NEW - stores refresh tokens)
└── requirements.txt
```

## Migration from Supabase

The following changes were made:

1. **Removed Supabase dependencies** from AuthViewModel
2. **Created API client** using Ktor (already in dependencies)
3. **Added token storage** with platform-specific implementations
4. **Updated Python server** with email/password endpoints
5. **Updated App.kt** to initialize TokenStorage

### What's No Longer Used

- `SupabaseConfig.kt` - No longer needed for auth
- Supabase Auth SDK - Can be removed if not used elsewhere
- Google OAuth provider from Supabase - Now handled by Python server

### What Can Be Removed (Optional)

If you're not using Supabase for anything else:

```kotlin
// In build.gradle.kts, these can be removed:
implementation("io.github.jan-tennert.supabase:auth-kt")
```

## Testing

### Test Email/Password Login
1. Start Python server: `cd server && python app.py`
2. Run the app
3. Enter any email ending in `@uw.edu` and any password
4. Should successfully authenticate

### Test Logout
1. After logging in, click logout button
2. Should return to login screen
3. Tokens should be cleared from storage

### Test Token Persistence
1. Log in successfully
2. Close and reopen app
3. Should remain authenticated (tokens loaded from storage)

## Support

For issues or questions about authentication:
1. Check Python server logs for backend errors
2. Check Logcat (Android) or Console (iOS) for frontend errors
3. Verify server is running and accessible
4. Check network connectivity

## License

Same as the main project.

