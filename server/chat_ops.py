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

    def _normalize_message_for_api(self, message: Dict[str, Any]) -> Dict[str, Any]:
        """
        Normalize DB message fields to API shape expected by mobile clients.
        DB schema uses time_created/time_edited; API contracts use created_at/updated_at.
        """
        return {
            "id": message.get("id"),
            "chat_id": message.get("chat_id"),
            "user_id": message.get("user_id"),
            "content": message.get("content", ""),
            "created_at": message.get("time_created") or message.get("created_at") or 0,
            "updated_at": message.get("time_edited") or message.get("updated_at"),
            "is_read": bool(message.get("is_read", False)),
            "message_type": message.get("message_type") or "text",
            "metadata": message.get("metadata") if message.get("metadata") is not None else {},
        }

    def _normalize_user_for_api(self, user: Dict[str, Any]) -> Dict[str, Any]:
        """
        Normalize user payloads so clients always receive a stable display `name`.
        Prefer full_name, then legacy name, then email prefix.
        """
        email = user.get("email")
        email_prefix = email.split("@")[0] if isinstance(email, str) and "@" in email else None

        def normalize_candidate(value: Any) -> str:
            if not isinstance(value, str):
                return ""
            normalized = value.strip()
            if not normalized or normalized.lower() == "connection":
                return ""
            return normalized

        display_name = (
            normalize_candidate(user.get("full_name"))
            or normalize_candidate(user.get("name"))
            or normalize_candidate(email_prefix)
            or "Connection"
        )

        return {
            "id": user.get("id"),
            "name": display_name,
            "full_name": user.get("full_name"),
            "email": email,
            "image": user.get("image"),
        }

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
            # Get all connections for the user (user_ids is a uuid[] column)
            connections_response = self.supabase.table("connections").select("*").contains(
                "user_ids", [user_id]
            ).execute()

            if not connections_response.data:
                return []

            chats = []
            for connection in connections_response.data:
                # Get or create chat row for this connection
                chat_response = self.supabase.table("chats").select("*").eq(
                    "connection_id", connection["id"]
                ).limit(1).execute()

                if chat_response.data:
                    chat = chat_response.data[0]
                else:
                    created_chat = self.create_chat(connection["id"])
                    if not created_chat:
                        continue
                    chat = created_chat

                chat["connection"] = connection

                # Get other user from user_ids array
                user_ids = connection.get("user_ids", [])
                other_user_id = next((uid for uid in user_ids if uid != user_id), None)
                if other_user_id:
                    user_response = self.supabase.table("users").select("id,name,full_name,email,image").eq(
                        "id", other_user_id
                    ).limit(1).execute()
                    if user_response.data:
                        chat["other_user"] = self._normalize_user_for_api(user_response.data[0])

                # Get last message
                last_msg_response = self.supabase.table("messages").select("*").eq(
                    "chat_id", chat["id"]
                ).order("time_created", desc=True).limit(1).execute()

                chat["last_message"] = (
                    self._normalize_message_for_api(last_msg_response.data[0])
                    if last_msg_response.data else None
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
        content: str,
        message_type: str = "text",
        metadata: Optional[Dict[str, Any]] = None,
    ) -> Optional[Dict[str, Any]]:
        """Create a new message in a chat"""
        try:
            now = int(time.time() * 1000)
            message_data = {
                "chat_id": chat_id,
                "user_id": user_id,
                "content": content,
                "time_created": now,
                "is_read": False,
                "message_type": message_type,
                "metadata": metadata if isinstance(metadata, dict) else {},
            }
            response = self.supabase.table("messages").insert(message_data).execute()

            if response.data:
                # Update chat timestamp
                self.update_chat_timestamp(chat_id)
                return self._normalize_message_for_api(response.data[0])
            return None
        except Exception as e:
            print(f"Error creating message: {e}")
            return None

    def fetch_messages_for_chat(self, chat_id: str) -> List[Dict[str, Any]]:
        """Fetch all messages for a chat"""
        try:
            response = self.supabase.table("messages").select("*").eq(
                "chat_id", chat_id
            ).order("time_created", desc=False).execute()

            messages = [self._normalize_message_for_api(m) for m in (response.data if response.data else [])]

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
                "time_edited": now
            }).eq("id", message_id).execute()

            return self._normalize_message_for_api(response.data[0]) if response.data else None
        except Exception as e:
            print(f"Error updating message: {e}")
            return None

    # Connection-Chat operations
    def fetch_chat_for_connection(self, connection_id: str) -> Optional[Dict[str, Any]]:
        """Get the chat associated with a connection"""
        try:
            response = self.supabase.table("chats").select("*").eq(
                "connection_id", connection_id
            ).limit(1).execute()
            return response.data[0] if response.data else None
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

            user_ids = connection.get("user_ids", [])
            if not user_ids:
                return []

            users_response = self.supabase.table("users").select("id,name,full_name,email,image").in_(
                "id", user_ids
            ).execute()

            return [self._normalize_user_for_api(u) for u in users_response.data] if users_response.data else []
        except Exception as e:
            print(f"Error fetching chat participants: {e}")
            return []

    def add_reaction(self, message_id: str, user_id: str, reaction_type: str) -> Optional[Dict[str, Any]]:
        """Add a reaction to a message"""
        try:
            now = int(time.time() * 1000)
            reaction = {
                "message_id": message_id,
                "user_id": user_id,
                "reaction_type": reaction_type,
                "created_at": now
            }
            response = self.supabase.table("message_reactions").insert(reaction).execute()
            return response.data[0] if response.data else None
        except Exception as e:
            print(f"Error adding reaction: {e}")
            return None

    def remove_reaction(self, message_id: str, user_id: str, reaction_type: str) -> bool:
        """Remove a user's reaction from a message"""
        try:
            self.supabase.table("message_reactions").delete().eq("message_id", message_id).eq("user_id", user_id).eq("reaction_type", reaction_type).execute()
            return True
        except Exception as e:
            print(f"Error removing reaction: {e}")
            return False

    def fetch_reactions(self, message_id: str) -> List[Dict[str, Any]]:
        """Fetch all reactions for a message"""
        try:
            response = self.supabase.table("message_reactions").select("*").eq("message_id", message_id).execute()
            return response.data if response.data else []
        except Exception as e:
            print(f"Error fetching reactions: {e}")
            return []

    def set_typing(self, chat_id: str, user_id: str) -> bool:
        """Upsert typing indicator for a user in a chat"""
        try:
            now = int(time.time() * 1000)
            existing = self.supabase.table("typing_events").select("id").eq("chat_id", chat_id).eq("user_id", user_id).execute()
            if existing.data:
                self.supabase.table("typing_events").update({"updated_at": now}).eq("id", existing.data[0]["id"]).execute()
            else:
                self.supabase.table("typing_events").insert({"chat_id": chat_id, "user_id": user_id, "updated_at": now}).execute()
            return True
        except Exception as e:
            print(f"Error setting typing: {e}")
            return False

    def fetch_typing_users(self, chat_id: str) -> List[str]:
        """Fetch users currently typing in a chat (within last 3s)"""
        try:
            cutoff = int(time.time() * 1000) - 3000
            response = self.supabase.table("typing_events").select("user_id, updated_at").eq("chat_id", chat_id).gt("updated_at", cutoff).execute()
            return [row["user_id"] for row in response.data] if response.data else []
        except Exception as e:
            print(f"Error fetching typing users: {e}")
            return []

    def search_messages(self, chat_id: str, query: str) -> List[Dict[str, Any]]:
        """Search messages in a chat by substring"""
        try:
            response = self.supabase.table("messages").select("*").eq("chat_id", chat_id).ilike("content", f"%{query}%").order("created_at", desc=False).execute()
            return response.data if response.data else []
        except Exception as e:
            print(f"Error searching messages: {e}")
            return []

    def forward_message(self, source_message_id: str, target_chat_id: str, forwarding_user_id: str) -> Optional[Dict[str, Any]]:
        """Forward an existing message to another chat"""
        try:
            src = self.supabase.table("messages").select("*").eq("id", source_message_id).execute()
            if not src.data:
                return None
            original = src.data[0]
            now = int(time.time() * 1000)
            new_msg = {
                "chat_id": target_chat_id,
                "user_id": forwarding_user_id,
                "content": original.get("content", ""),
                "created_at": now,
                "is_read": False,
                "status": "sent"
            }
            response = self.supabase.table("messages").insert(new_msg).execute()
            if response.data:
                self.update_chat_timestamp(target_chat_id)
                return response.data[0]
            return None
        except Exception as e:
            print(f"Error forwarding message: {e}")
            return None

    def update_message_status(self, message_id: str, status: str) -> bool:
        """Update status of a message"""
        try:
            self.supabase.table("messages").update({"status": status}).eq("id", message_id).execute()
            return True
        except Exception as e:
            print(f"Error updating message status: {e}")
            return False
