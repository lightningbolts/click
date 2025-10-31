# Server Setup and Installation

## Prerequisites
- Python 3.8 or higher
- pip (Python package manager)

## Installation Steps

### 1. Navigate to the server directory
```bash
cd server
```

### 2. Install Python dependencies
```bash
pip3 install -r requirements.txt
```

Or install individual packages:
```bash
pip3 install flask==3.0.0
pip3 install supabase==2.3.0
pip3 install python-dotenv==1.0.0
pip3 install pyjwt==2.8.0
pip3 install requests==2.31.0
pip3 install google-auth==2.25.2
pip3 install bcrypt==4.1.2
```

### 3. Set up environment variables

Create a `.env` file in the server directory:
```bash
touch .env
```

Add the following variables to `.env`:
```
GOOGLE_CLIENT_ID=your_google_client_id_here
GOOGLE_CLIENT_SECRET=your_google_client_secret_here
SUPABASE_URL=your_supabase_project_url
SUPABASE_KEY=your_supabase_anon_key
```

### 4. Initialize the refresh token storage

The `refresh.json` file is already created. If it doesn't exist:
```bash
echo "[]" > refresh.json
```

### 5. Set up Supabase database

Your Supabase database should have a `users` table with the following schema:

```sql
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name TEXT,
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  image TEXT,
  "createdAt" REAL,
  "lastSignedIn" REAL,
  connections JSONB DEFAULT '[]'::jsonb
);

-- Add index for email lookups
CREATE INDEX idx_users_email ON users(email);
```

### 6. Run the server

```bash
python3 app.py
```

The server will start on `http://localhost:5000` or `http://127.0.0.1:5000`.

## Testing

### Test the server is running:
```bash
curl http://localhost:5000/
```

Expected response: `Hello World!`

### Test user creation:
```bash
curl -X POST http://localhost:5000/create_account \
  -H "Content-Type: application/json" \
  -d '{"name": "Test User", "email": "test@uw.edu", "password": "testpass123"}'
```

Expected response: JSON with JWT token, refresh token, and user info.

### Test user login:
```bash
curl -X POST http://localhost:5000/login \
  -H "Content-Type: application/json" \
  -d '{"email": "test@uw.edu", "password": "testpass123"}'
```

Expected response: JSON with JWT token, refresh token, and user info.

## Troubleshooting

### Module not found errors
Make sure all dependencies are installed:
```bash
pip3 install -r requirements.txt
```

### Supabase connection errors
- Verify your `.env` file has correct SUPABASE_URL and SUPABASE_KEY
- Check that your Supabase project is active
- Verify the users table exists in your database

### Port already in use
If port 5000 is already in use, you can change it in `app.py`:
```python
if __name__ == '__main__':
    app.run(port=5001)  # Change to any available port
```

## Security Notes

- Never commit the `.env` file to version control
- Keep your Google OAuth credentials secure
- Keep your Supabase keys secure
- Passwords are automatically hashed with bcrypt before storage
- JWT tokens expire after 24 hours
- Refresh tokens are stored server-side and can be invalidated

## Production Deployment

For production deployment:
1. Use a production-grade WSGI server like Gunicorn or uWSGI
2. Enable HTTPS/TLS
3. Set up proper CORS headers
4. Use environment variables or a secrets manager
5. Set up proper logging
6. Implement rate limiting
7. Use a production database (not SQLite)
8. Consider using Redis for refresh token storage instead of JSON file

