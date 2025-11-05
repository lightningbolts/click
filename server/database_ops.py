import json

from supabase import create_client, Client
import os

from schema import *

url: str = os.environ.get("SUPABASE_URL")
key: str = os.environ.get("SUPABASE_KEY")

supabase: Client = create_client(url, key)


def fetch_user(name: str) -> User:
    user =  User(
        setup_dict=[
            x for x in supabase.table("users").execute()["data"] if x["name"] == name
        ][0]
    )
    for id in user.connections:
        if(delete_connection_if_expired(id)):
            user.connections.remove(id)
    return user

def fetch_user_with_id(id: str) -> User:
    user =  User(
        setup_dict=[
            x for x in supabase.table("users").execute()["data"] if x["id"] == id
        ][0]
    )
    for id in user.connections:
        if(delete_connection_if_expired(id)):
            user.connections.remove(id)
    return user

def fetch_user_with_email(email: str) -> User:
    user =  User(
        setup_dict=[
            x for x in supabase.table("users").execute()["data"] if x["email"] == email
        ][0]
    )
    for id in user.connections:
        if(delete_connection_if_expired(id)):
            user.connections.remove(id)
    return user

def delete_connection_if_expired(id: str) -> bool:
    connection = fetch_connection(id)
    if connection.check_for_expiry():
        user1 = fetch_user_with_id(connection.user_ids[0])
        user2 = fetch_user_with_id(connection.user_ids[1])
        user1.connections.remove(id)
        user2.connections.remove(id)
        supabase.table("connections").delete().eq("id", id).execute()
        supabase.table("users").update(vars(user1)).eq("id", user1.id).execute()
        supabase.table("users").update(vars(user2)).eq("id", user2.id).execute()
        return True
    return False




def fetch_connection(id: str) -> Connection:
    return Connection(
        setup_dict=[
            x
            for x in supabase.table("connections").execute()["data"]
            if x["id"] == id
        ][0]
    )

def create_user(name:str, email:str, image:str) -> User:
    user = User(name=name, email=email, image=image)
    supabase.table("users").insert(vars(user)).execute()
    return user


def create_connection(
    user1: User, user2: User, location: tuple[float, float]
) -> Connection:
    connection = Connection(
        user1=user1,
        user2=user2,
        location=location,
    )
    supabase.table("connections").insert(vars(connection)).execute()
    supabase.table("users").update(vars(user1)).eq("id", user1.id).execute()
    supabase.table("users").update(vars(user2)).eq("id", user2.id).execute()
    return connection

def fetch_message(id:str) -> Message:
    return Message(setup_dict=[x for x in supabase.table("messages").execute()["data"] if x["id"] == id][0])

def create_message(user:User, content:str) -> Message:
    supabase.table("messages").insert({"user_id":user.id, "content":content}).execute()
    return Message(setup_dict=[x for x in json.loads(supabase.table("messages")["data"]) if x["user_id"] == user.id][0])
