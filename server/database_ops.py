from supabase import create_client, Client
import os
from functools import lru_cache

from schema import *

# Try to load a local .env automatically if python-dotenv is installed.
try:
    from dotenv import load_dotenv

    load_dotenv()
except Exception:
    # If dotenv isn't installed, that's fine — we'll rely on environment variables.
    pass


@lru_cache(maxsize=1)
def get_supabase_client() -> Client:
    """Lazily create and cache the Supabase client.

    This avoids raising an exception at import time if environment variables
    aren't set. If the required variables are missing, raise a clear RuntimeError
    with instructions.
    """
    url = os.environ.get("SUPABASE_URL")
    key = os.environ.get("SUPABASE_KEY")

    if not url or not key:
        raise RuntimeError(
            "SUPABASE_URL and SUPABASE_KEY must be set in the environment. "
            "You can export them in your shell or create a .env file and install "
            "python-dotenv (pip install python-dotenv). Example .env:\n"
            "SUPABASE_URL=https://your-project.supabase.co\n"
            "SUPABASE_KEY=your-service-role-or-anon-key"
        )

    return create_client(url, key)


def fetch_user(name: str) -> User:
    client = get_supabase_client()
    user = User(
        setup_dict=[
            x for x in client.table("users").select("*").execute().data if x["name"] == name
        ][0]
    )
    # Iterate over a copy to avoid mutating the list while looping
    for conn_id in list(user.connections):
        if delete_connection_if_expired(conn_id):
            user.connections.remove(conn_id)
    return user


def fetch_user_with_id(id: str) -> User:
    client = get_supabase_client()
    user = User(
        setup_dict=[
            x for x in client.table("users").select("*").execute()["data"].data  if x["id"] == id
        ][0]
    )
    # Iterate over a copy to avoid mutating the list while looping
    for conn_id in list(user.connections):
        if delete_connection_if_expired(conn_id):
            user.connections.remove(conn_id)
    return user


def fetch_user_with_email(email: str) -> User:
    client = get_supabase_client()
    thing = [
            x for x in client.table("users").select("*").execute().data if x["email"] == email
        ]
    print(thing)
    user = User(
        setup_dict=thing[0]
    )
    # Iterate over a copy to avoid mutating the list while looping
    for conn_id in list(user.connections):
        if delete_connection_if_expired(conn_id):
            user.connections.remove(conn_id)
    return user


def delete_connection_if_expired(id: str) -> bool:
    connection = fetch_connection(id)
    if connection.check_for_expiry():
        user1 = fetch_user_with_id(connection.user_ids[0])
        user2 = fetch_user_with_id(connection.user_ids[1])
        user1.connections.remove(id)
        user2.connections.remove(id)
        client = get_supabase_client()
        client.table("connections").delete().eq("id", id).execute()
        client.table("users").update(vars(user1)).eq("id", user1.id).execute()
        client.table("users").update(vars(user2)).eq("id", user2.id).execute()
        return True
    return False


def update_user_with_id(id: str, user: User) -> bool:
    if fetch_user_with_id(id) is None:
        return False
    client = get_supabase_client()
    client.table("users").update(vars(user)).eq("id", id).execute()
    return True


def update_connection_with_id(id: str, connection: Connection) -> bool:
    if fetch_user_with_id(id) is None:
        return False
    client = get_supabase_client()
    client.table("connections").update(vars(connection)).eq("id", id).execute()
    return True


def fetch_connection(id: str) -> Connection:
    client = get_supabase_client()
    return Connection(
        setup_dict=[
            x for x in client.table("connections").select("*").execute()["data"].data  if x["id"] == id
        ][0]
    )


def create_user(name: str, email: str, image: str) -> User:
    user = User(name=name, email=email, image=image)
    client = get_supabase_client()
    client.table("users").insert(vars(user)).execute()
    return user


def create_connection(
    user1: User, user2: User, location: tuple[float, float]
) -> Connection:
    # Pass geo_location to match schema.Connection signature
    connection = Connection(
        user1=user1,
        user2=user2,
        geo_location=location,
    )
    client = get_supabase_client()
    client.table("connections").insert(vars(connection)).execute()
    client.table("users").update(vars(user1)).eq("id", user1.id).execute()
    client.table("users").update(vars(user2)).eq("id", user2.id).execute()
    return connection


def create_message(connid: str, userid: str, content: str) -> Chat:
    connection = fetch_connection(connid)
    connection.chat.add_message(userid, content)
    client = get_supabase_client()
    client.table("connections").update(vars(connection)).eq("id", connid).execute()
    return connection.chat


def generate_pair(userid: str) -> tuple[User, bool]:
    user = fetch_user_with_id(userid)

    return user, False
