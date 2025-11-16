#!/usr/bin/env python3
"""
Test Data Creation Script for Chat Testing

This script creates test users and connections for easy chat testing.
Run this before testing the iOS app to have ready-to-use test data.
"""

import os
import sys
import time
import requests
from datetime import datetime
from supabase import create_client, Client

# Load environment variables
from dotenv import load_dotenv
load_dotenv()

SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")

if not SUPABASE_URL or not SUPABASE_KEY:
    print("❌ Error: SUPABASE_URL and SUPABASE_KEY must be set in .env file")
    sys.exit(1)

# Initialize Supabase client
supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)

def create_test_user(email, name, image=None):
    """Create a test user in the database"""
    now = datetime.utcnow().isoformat()

    # Check if user already exists
    try:
        existing = supabase.table("users").select("*").eq("email", email).execute()
        if existing.data:
            print(f"✅ User already exists: {name} ({email})")
            return existing.data[0]
    except Exception as e:
        print(f"Checking existing user failed: {e}")

    # Create new user - only include essential fields
    user_data = {
        "name": name,
        "email": email,
        "created_at": now
    }

    # Add optional fields if provided
    if image:
        user_data["image"] = image

    try:
        result = supabase.table("users").insert(user_data).execute()
        print(f"✅ Created user: {name} ({email})")
        return result.data[0]
    except Exception as e:
        print(f"❌ Failed to create user: {e}")
        return None

def create_connection(user1_id, user2_id, location=None):
    """Create a connection between two users"""
    now = datetime.utcnow().isoformat()
    expiry = datetime.utcnow()
    expiry = expiry.replace(day=expiry.day + 7).isoformat()  # 7 days from now

    # Check if connection already exists
    try:
        existing = supabase.table("connections").select("*").or_(
            f"and(user1_id.eq.{user1_id},user2_id.eq.{user2_id}),"
            f"and(user1_id.eq.{user2_id},user2_id.eq.{user1_id})"
        ).execute()

        if existing.data:
            print(f"✅ Connection already exists between users")
            return existing.data[0]
    except Exception as e:
        print(f"Checking existing connection failed: {e}")

    # Create connection
    connection_data = {
        "user1_id": user1_id,
        "user2_id": user2_id,
        "location": location or "Test Location",
        "created": now,
        "expiry": expiry,
        "should_continue": True
    }

    try:
        result = supabase.table("connections").insert(connection_data).execute()
        connection = result.data[0]
        print(f"✅ Created connection between users")

        # Connection trigger should auto-create chat, but let's verify
        time.sleep(1)  # Give trigger time to execute

        # Check if chat was created
        if connection.get("chat_id"):
            print(f"✅ Chat auto-created: {connection['chat_id']}")
        else:
            # Manually create chat if trigger didn't work
            chat_data = {
                "connection_id": connection["id"],
                "created_at": now,
                "updated_at": now
            }
            chat_result = supabase.table("chats").insert(chat_data).execute()
            chat_id = chat_result.data[0]["id"]

            # Update connection with chat_id
            supabase.table("connections").update({
                "chat_id": chat_id
            }).eq("id", connection["id"]).execute()

            print(f"✅ Manually created chat: {chat_id}")
            connection["chat_id"] = chat_id

        return connection
    except Exception as e:
        print(f"❌ Failed to create connection: {e}")
        return None

def create_test_message(chat_id, user_id, content):
    """Create a test message in a chat"""
    now = datetime.utcnow().isoformat()

    message_data = {
        "chat_id": chat_id,
        "user_id": user_id,
        "content": content,
        "created_at": now,
        "is_read": False
    }

    try:
        result = supabase.table("messages").insert(message_data).execute()
        print(f"✅ Created message: {content[:50]}...")
        return result.data[0]
    except Exception as e:
        print(f"❌ Failed to create message: {e}")
        return None

def main():
    print("🚀 Creating test data for iOS chat testing")
    print("=" * 60)
    print()

    # Create test users
    print("👥 Creating test users...")
    user1 = create_test_user(
        email="testuser1@example.com",
        name="Test User 1"
    )

    user2 = create_test_user(
        email="testuser2@example.com",
        name="Test User 2"
    )

    if not user1 or not user2:
        print()
        print("❌ Failed to create users. Please check your database schema.")
        print("   The users table may need to be created or have different columns.")
        sys.exit(1)

    print()
    print(f"User 1 ID: {user1['id']}")
    print(f"User 2 ID: {user2['id']}")
    print()

    # Create connection between users
    print("🔗 Creating connection...")
    connection = create_connection(
        user1_id=user1["id"],
        user2_id=user2["id"],
        location="Test Lab, Seattle"
    )

    if not connection:
        print()
        print("❌ Failed to create connection.")
        sys.exit(1)

    print()
    print(f"Connection ID: {connection['id']}")
    print(f"Chat ID: {connection.get('chat_id', 'Not created yet')}")
    print()

    # Create some test messages
    if connection.get("chat_id"):
        print("💬 Creating test messages...")
        create_test_message(
            chat_id=connection["chat_id"],
            user_id=user1["id"],
            content="Hey! This is a test message from User 1"
        )

        create_test_message(
            chat_id=connection["chat_id"],
            user_id=user2["id"],
            content="Hi! This is a reply from User 2. The chat is working!"
        )

        create_test_message(
            chat_id=connection["chat_id"],
            user_id=user1["id"],
            content="Great! Let's test the real-time functionality 🚀"
        )
    else:
        print("⚠️  No chat ID available, skipping message creation")

    print()
    print("=" * 60)
    print("✅ Test data created successfully!")
    print()
    print("📱 You can now test the iOS app with these credentials:")
    print()
    print("User 1:")
    print(f"  Email: {user1['email']}")
    print(f"  Name: {user1['name']}")
    print(f"  ID: {user1['id']}")
    print()
    print("User 2:")
    print(f"  Email: {user2['email']}")
    print(f"  Name: {user2['name']}")
    print(f"  ID: {user2['id']}")
    print()
    print("💡 Tip: Save these IDs for testing!")
    print()
    print("Next steps:")
    print("1. Start Flask server: python app.py")
    print("2. Open iOS simulator")
    print("3. Login as Test User 1 or Test User 2")
    print("4. Go to Connections screen")
    print("5. Open the chat and start messaging!")
    print()

if __name__ == "__main__":
    main()

