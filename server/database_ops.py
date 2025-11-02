import json

from supabase import create_client, Client
import os

from schema import *

url: str = os.environ.get("SUPABASE_URL")
key: str = os.environ.get("SUPABASE_KEY")

supabase: Client = create_client(url, key)


def fetch_user(name: str) -> User:
    return User(
        setup_dict=[
            x for x in supabase.table("users").execute()["data"] if x["name"] == name
        ][0]
    )


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
    return Message(setup_dict=[x for x in json.loads(supabase.table("messages")["data"]) if x["id"] == id][0])

def create_user(name:str) -> User:
    supabase.table("users").insert({"name":name}).execute()
    return User(setup_dict=[x for x in json.loads(supabase.table("users")["data"]) if x["name"] == name][0])

def create_connection(user1:User, user2:User) -> Connection:
    supabase.table("connections").insert({"user1_id":user1.id, "user2_id":user2.id}).execute()
    return Connection(setup_dict=[x for x in json.loads(supabase.table("connections")["data"]) if x["user1_id"] == user1.id and x["user2_id"] == user2.id][0])

def create_message(user:User, content:str) -> Message:
    supabase.table("messages").insert({"user_id":user.id, "content":content}).execute()
    return Message(setup_dict=[x for x in json.loads(supabase.table("messages")["data"]) if x["user_id"] == user.id][0])
