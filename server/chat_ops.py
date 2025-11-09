"""
Chat-specific database operations for Click app
Handles messages, chats, and real-time messaging functionality
"""
import time
import uuid
from typing import List, Optional, Dict, Any
from supabase import Client
from schema import User


class ChatOperations:
    """Database operations for chat functionality"""

    def __init__(self, supabase_client: Client):
        self.supabase = supabase_client

    # Chat operations
    def create_chat(self, connection_id: str) -> Optional[Dict[str, Any]]:
        """Create a new chat for a connection"""
        try:
            now = int(time.time() * 1000)
            chat_data = {
                "connection_id": connection_id,
                "created_at": now,
                "updated_at": now
            }
            response = self.supabase.table("chats").insert(chat_data).execute()
            return response.data[0] if response.data else None
        except Exception as e:
            print(f"Error creating chat: {e}")
            return None

    def fetch_chat_by_id(self, chat_id: str) -> Optional[Dict[str, Any]]:
        """Fetch a chat by its ID"""
        try:
            response = self.supabase.table("chats").select("*").eq("id", chat_id).execute()
            return response.data[0] if response.data else None
        except Exception as e:
            print(f"Error fetching chat: {e}")
            return None

    def fetch_chats_for_user(self, user_id: str) -> List[Dict[str, Any]]:
        """Fetch all chats for a user with connection details"""
        try:
            # Get all connections for the user
            connections_response = self.supabase.table("connections").select("*").or_(
                f"user1_id.eq.{user_id},user2_id.eq.{user_id}"
            ).execute()

            if not connections_response.data:
                return []

            chats = []
            for connection in connections_response.data:
                if connection.get("chat_id"):
                    # Get chat details
                    chat_response = self.supabase.table("chats").select("*").eq(
                        "id", connection["chat_id"]
                    ).execute()

                    if chat_response.data:
                        chat = chat_response.data[0]
                        chat["connection"] = connection

                        # Get other user
                        other_user_id = (
                            connection["user2_id"]
                            if connection["user1_id"] == user_id
                            else connection["user1_id"]
                        )
                        user_response = self.supabase.table("users").select("*").eq(
                            "id", other_user_id
                        ).execute()

                        if user_response.data:
                            chat["other_user"] = user_response.data[0]

                        # Get last message
                        last_msg_response = self.supabase.table("messages").select("*").eq(
                            "chat_id", chat["id"]
                        ).order("created_at", desc=True).limit(1).execute()

                        chat["last_message"] = (
                            last_msg_response.data[0] if last_msg_response.data else None
                        )

                        # Count unread messages
                        unread_response = self.supabase.table("messages").select(
                            "*", count="exact"
                        ).eq("chat_id", chat["id"]).eq("is_read", False).neq(
                            "user_id", user_id
                        ).execute()

                        chat["unread_count"] = unread_response.count if unread_response.count else 0

                        chats.append(chat)

            # Sort by last activity
            chats.sort(key=lambda x: x.get("updated_at", 0), reverse=True)
            return chats

        except Exception as e:
            print(f"Error fetching chats for user: {e}")
            return []

    def update_chat_timestamp(self, chat_id: str) -> bool:
        """Update the chat's last activity timestamp"""
        try:
            now = int(time.time() * 1000)
            self.supabase.table("chats").update({
                "updated_at": now
            }).eq("id", chat_id).execute()
            return True
        except Exception as e:
            print(f"Error updating chat timestamp: {e}")
            return False

    # Message operations
    def create_message(
        self,
        chat_id: str,
        user_id: str,
        content: str
    ) -> Optional[Dict[str, Any]]:
        """Create a new message in a chat"""
        try:
            now = int(time.time() * 1000)
            message_data = {
                "chat_id": chat_id,
                "user_id": user_id,
                "content": content,
                "created_at": now,
                "is_read": False
            }
            response = self.supabase.table("messages").insert(message_data).execute()

            if response.data:
                # Update chat timestamp
                self.update_chat_timestamp(chat_id)
                return response.data[0]
            return None
        except Exception as e:
            print(f"Error creating message: {e}")
            return None

    def fetch_messages_for_chat(self, chat_id: str) -> List[Dict[str, Any]]:
        """Fetch all messages for a chat"""
        try:
            response = self.supabase.table("messages").select("*").eq(
                "chat_id", chat_id
            ).order("created_at", desc=False).execute()

            messages = response.data if response.data else []

            # Enrich messages with user data
            for message in messages:
                user_response = self.supabase.table("users").select("*").eq(
                    "id", message["user_id"]
                ).execute()
                if user_response.data:
                    message["user"] = user_response.data[0]

            return messages
        except Exception as e:
            print(f"Error fetching messages: {e}")
            return []

    def mark_messages_as_read(self, chat_id: str, user_id: str) -> bool:
        """Mark all messages in a chat as read for a user (except their own)"""
        try:
            self.supabase.table("messages").update({
                "is_read": True
            }).eq("chat_id", chat_id).neq("user_id", user_id).eq("is_read", False).execute()
            return True
        except Exception as e:
            print(f"Error marking messages as read: {e}")
            return False

    def delete_message(self, message_id: str, user_id: str) -> bool:
        """Delete a message (only by the sender)"""
        try:
            # Verify the user owns the message
            message = self.supabase.table("messages").select("*").eq(
                "id", message_id
            ).eq("user_id", user_id).execute()

            if not message.data:
                return False

            self.supabase.table("messages").delete().eq("id", message_id).execute()
            return True
        except Exception as e:
            print(f"Error deleting message: {e}")
            return False

    def update_message(
        self,
        message_id: str,
        user_id: str,
        new_content: str
    ) -> Optional[Dict[str, Any]]:
        """Update a message's content (only by the sender)"""
        try:
            # Verify the user owns the message
            message = self.supabase.table("messages").select("*").eq(
                "id", message_id
            ).eq("user_id", user_id).execute()

            if not message.data:
                return None

            now = int(time.time() * 1000)
            response = self.supabase.table("messages").update({
                "content": new_content,
                "updated_at": now
            }).eq("id", message_id).execute()

            return response.data[0] if response.data else None
        except Exception as e:
            print(f"Error updating message: {e}")
            return None

    # Connection-Chat operations
    def fetch_chat_for_connection(self, connection_id: str) -> Optional[Dict[str, Any]]:
        """Get the chat associated with a connection"""
        try:
            # Get the connection
            conn_response = self.supabase.table("connections").select("*").eq(
                "id", connection_id
            ).execute()

            if not conn_response.data:
                return None

            connection = conn_response.data[0]
            chat_id = connection.get("chat_id")

            if not chat_id:
                return None

            return self.fetch_chat_by_id(chat_id)
        except Exception as e:
            print(f"Error fetching chat for connection: {e}")
            return None

    def get_chat_participants(self, chat_id: str) -> List[Dict[str, Any]]:
        """Get the users participating in a chat"""
        try:
            # Get the chat's connection
            chat = self.fetch_chat_by_id(chat_id)
            if not chat:
                return []

            conn_response = self.supabase.table("connections").select("*").eq(
                "id", chat["connection_id"]
            ).execute()

            if not conn_response.data:
                return []

            connection = conn_response.data[0]

            # Fetch both users
            user1_response = self.supabase.table("users").select("*").eq(
                "id", connection["user1_id"]
            ).execute()
            user2_response = self.supabase.table("users").select("*").eq(
                "id", connection["user2_id"]
            ).execute()

            participants = []
            if user1_response.data:
                participants.append(user1_response.data[0])
            if user2_response.data:
                participants.append(user2_response.data[0])

            return participants
        except Exception as e:
            print(f"Error fetching chat participants: {e}")
            return []

