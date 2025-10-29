import json

from supabase import create_client, Client
import os

from schema import *

url: str = os.environ.get("SUPABASE_URL")
key: str = os.environ.get("SUPABASE_KEY")

supabase: Client = create_client(url, key)

def fetch_user(name:str) -> User:
    return User( setup_dict = [x for x in json.loads(supabase.table("users")["data"]) if x["name"] == name][0])

def fetch_connection(id:str) -> Connection:
    return Connection(setup_dict=[x for x in json.loads(supabase.table("connections")["data"]) if x["id"] == id][0])

