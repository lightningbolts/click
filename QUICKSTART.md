# Quick Start Guide - Supabase Integration

This guide will help you quickly get started with Supabase in your Click project.

## 🚀 Quick Setup (5 minutes)

### 1. Install Python Dependencies

```bash
cd server
pip install -r requirements.txt
```

### 2. Configure Your Credentials

#### Option A: Use the .env file (Recommended for local development)

Edit `.env` in the root directory:

```env
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_KEY=your-anon-key-here
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
```

#### Option B: Set environment variables

**macOS/Linux:**
```bash
export SUPABASE_URL="https://your-project.supabase.co"
export SUPABASE_KEY="your-anon-key-here"
```

**Windows (PowerShell):**
```powershell
$env:SUPABASE_URL="https://your-project.supabase.co"
$env:SUPABASE_KEY="your-anon-key-here"
```

### 3. Update Kotlin Configuration

Edit `composeApp/src/commonMain/kotlin/compose/project/click/click/data/SupabaseConfig.kt`:

```kotlin
private const val SUPABASE_URL = "https://your-project.supabase.co"
private const val SUPABASE_ANON_KEY = "your-anon-key-here"
```

### 4. Set Up Database Tables

Go to your Supabase project → SQL Editor and run:

```sql
-- Create users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    email TEXT UNIQUE NOT NULL,
    image TEXT,
    share_key BIGINT DEFAULT 0,
    connections TEXT[] DEFAULT '{}',
    created_at BIGINT NOT NULL
);

-- Create connections table
CREATE TABLE connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user1_id UUID REFERENCES users(id) ON DELETE CASCADE,
    user2_id UUID REFERENCES users(id) ON DELETE CASCADE,
    location_lat DOUBLE PRECISION NOT NULL,
    location_lng DOUBLE PRECISION NOT NULL,
    created BIGINT NOT NULL,
    expiry BIGINT NOT NULL,
    should_continue_user1 BOOLEAN DEFAULT FALSE,
    should_continue_user2 BOOLEAN DEFAULT FALSE
);

-- Create messages table
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    connection_id UUID REFERENCES connections(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    time_created BIGINT NOT NULL,
    time_edited BIGINT NOT NULL
);
```

## 📝 Usage Examples

### Python Server

```python
from database_ops import fetch_user, supabase
from schema import User

# Fetch a user
user = fetch_user("John Doe")

# Insert a new user
new_user_data = {
    "name": "Jane Smith",
    "email": "jane@example.com",
    "image": "https://example.com/avatar.jpg",
    "created_at": int(time.time())
}
result = supabase.table("users").insert(new_user_data).execute()
```

### Kotlin App

```kotlin
import compose.project.click.click.data.repository.SupabaseRepository
import kotlinx.coroutines.runBlocking

val repository = SupabaseRepository()

// Fetch user
runBlocking {
    val user = repository.fetchUserByName("John Doe")
    println(user)
}

// Create new user
val newUser = User(
    id = UUID.randomUUID().toString(),
    name = "Jane Smith",
    email = "jane@example.com",
    image = "https://example.com/avatar.jpg",
    createdAt = System.currentTimeMillis()
)

runBlocking {
    repository.createUser(newUser)
}
```

### In a Composable (Kotlin)

```kotlin
@Composable
fun UserProfile() {
    val viewModel = remember { ExampleSupabaseViewModel() }
    val user by viewModel.currentUser.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.fetchUser("John Doe")
    }
    
    when {
        isLoading -> CircularProgressIndicator()
        user != null -> Text("Welcome, ${user?.name}!")
        else -> Text("User not found")
    }
}
```

## 🔧 Testing the Connection

### Python Test Script

Create `server/test_connection.py`:

```python
from dotenv import load_dotenv
import os

load_dotenv()

from database_ops import supabase

try:
    result = supabase.table("users").select("*").limit(1).execute()
    print("✅ Connected to Supabase successfully!")
    print(f"Result: {result}")
except Exception as e:
    print(f"❌ Connection failed: {e}")
```

Run it:
```bash
cd server
python test_connection.py
```

## 📚 Files Created

### Python Server
- `server/requirements.txt` - Python dependencies
- `server/app.py` - Updated to use .env
- `server/database_ops.py` - Updated to use .env

### Kotlin App
- `composeApp/src/commonMain/kotlin/compose/project/click/click/data/SupabaseConfig.kt` - Supabase client configuration
- `composeApp/src/commonMain/kotlin/compose/project/click/click/data/models/Models.kt` - Data models
- `composeApp/src/commonMain/kotlin/compose/project/click/click/data/repository/SupabaseRepository.kt` - Database operations
- `composeApp/src/commonMain/kotlin/compose/project/click/click/data/viewmodel/ExampleSupabaseViewModel.kt` - Example ViewModel

### Documentation
- `.env` - Environment variables
- `SUPABASE_SETUP.md` - Detailed setup guide
- `QUICKSTART.md` - This file

## 🎯 Next Steps

1. **Get your Supabase credentials**: https://app.supabase.com → Your Project → Settings → API
2. **Update the .env file** with your actual credentials
3. **Update SupabaseConfig.kt** with your actual credentials
4. **Run the SQL schema** in Supabase SQL Editor
5. **Test the connection** using the test scripts above
6. **Start building!** Use the example ViewModel as a reference

## 🆘 Need Help?

- Check `SUPABASE_SETUP.md` for detailed setup instructions
- Supabase Docs: https://supabase.com/docs
- Python Client: https://github.com/supabase-community/supabase-py
- Kotlin Client: https://github.com/supabase-community/supabase-kt

## 🔐 Security Note

- Never commit your `.env` file to version control
- Use environment variables or secrets management in production
- The anon key is safe for client-side use, but enable Row Level Security (RLS) on your tables

