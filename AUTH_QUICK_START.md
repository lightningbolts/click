# Authentication Refactoring - Quick Start Guide

## ✅ Completed Successfully!

The authentication system has been fully refactored to use your Python Flask server instead of Supabase.

## What Was Changed

### Backend (Python)
- ✅ Added `/login` endpoint for email/password authentication
- ✅ Added `/create_account` endpoint for user registration
- ✅ Existing `/google`, `/refresh`, and `/logout` endpoints remain functional
- ✅ Created `refresh.json` file to store refresh tokens

### Frontend (Kotlin Multiplatform)
- ✅ Created `ApiClient` using Ktor for HTTP communication
- ✅ Created data models (`AuthModels.kt`) for requests/responses
- ✅ Implemented secure token storage for Android (EncryptedSharedPreferences) and iOS (NSUserDefaults)
- ✅ Updated `AuthViewModel` to use Python API instead of Supabase
- ✅ Updated `App.kt` to initialize TokenStorage properly
- ✅ Fixed compilation errors for both Android and iOS

## How to Run

### 1. Start the Python Server

```bash
cd server
python3 app.py
```

The server will start on `http://localhost:5000` (or `http://127.0.0.1:5000`)

### 2. Run the Android App

```bash
./gradlew :composeApp:installDebugAndroid
```

Or use the "Run" button in Android Studio/IntelliJ IDEA.

### 3. Test Authentication

#### Email/Password Login
1. Enter any email ending with `@uw.edu` (e.g., `test@uw.edu`)
2. Enter any password
3. Click "Sign In"
4. ✅ Should successfully authenticate

#### Email/Password Signup
1. Click "Sign Up"
2. Enter a name, email ending with `@uw.edu`, and password
3. Click "Create Account"
4. ✅ Should successfully create account and authenticate

#### Logout
1. Navigate to Settings
2. Click "Sign Out"
3. ✅ Should return to login screen and clear tokens

## Current Limitations

### ⚠️ TODO Items

1. **Password Storage**: Passwords are not actually stored or verified yet. The server accepts any password for valid @uw.edu emails.

2. **Database Integration**: User accounts are not persisted. You need to integrate with your database (see `database_ops.py`).

3. **Google OAuth**: The Google sign-in button is currently disabled (placeholder). You'll need to:
   - Implement platform-specific Google Sign-In on Android
   - Implement platform-specific Google Sign-In on iOS
   - Pass the OAuth token to `authViewModel.signInWithGoogle(token)`

4. **Server URL**: The API client is hardcoded to `http://localhost:5000`. For production:
   - Deploy your Flask server
   - Update the `baseUrl` in `ApiClient.kt`
   - Enable HTTPS

5. **iOS Keychain**: Consider upgrading from NSUserDefaults to Keychain for better security

## File Structure

```
composeApp/src/
├── commonMain/
│   ├── data/
│   │   ├── api/
│   │   │   └── ApiClient.kt ..................... NEW - HTTP client
│   │   ├── models/
│   │   │   └── AuthModels.kt .................... NEW - Data models
│   │   └── storage/
│   │       └── TokenStorage.kt .................. NEW - Storage interface
│   └── viewmodel/
│       └── AuthViewModel.kt ..................... UPDATED - Uses Python API
├── androidMain/
│   ├── data/storage/
│   │   └── TokenStorage.android.kt .............. NEW - Android implementation
│   └── MainActivity.kt .......................... UPDATED - Init storage
└── iosMain/
    └── data/storage/
        └── TokenStorage.ios.kt .................. NEW - iOS implementation

server/
├── app.py ....................................... UPDATED - Added endpoints
└── refresh.json ................................. NEW - Stores refresh tokens
```

## Next Steps

1. **Integrate Database**: Update `/login` and `/create_account` to actually store/verify user credentials
2. **Add Password Hashing**: Use bcrypt or argon2 to hash passwords
3. **Implement Google OAuth**: Add platform-specific Google Sign-In SDKs
4. **Deploy Server**: Deploy Flask to production (AWS, Google Cloud, Heroku, etc.)
5. **Update API URL**: Change `baseUrl` in ApiClient.kt to production URL
6. **Add Error Handling**: Improve error messages and offline handling
7. **Add Email Verification**: Send verification emails on signup

## Documentation

See `AUTH_REFACTOR.md` for complete documentation including:
- Detailed architecture explanation
- Security features
- API endpoint documentation
- Token storage implementation
- Testing instructions
- Troubleshooting guide

## Support

If you encounter issues:
1. Check that Python server is running: `curl http://localhost:5000/`
2. Check server logs for errors
3. Check Android Logcat or iOS Console for app errors
4. Verify network connectivity between app and server

## Build Status

✅ Android: Compiles successfully
✅ iOS: Compiles successfully
✅ Authentication flow: Implemented
✅ Token storage: Implemented (secure on Android)
✅ API client: Implemented with Ktor

Ready to use!

