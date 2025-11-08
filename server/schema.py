import random
import time
import uuid
from utils import get_semantic_location

class User:
    connections = []
    def __init__(self, name:str=None, email:str=None, image:str=None, setup_dict=None):
        if(setup_dict is not None):
            #careful!
            for key, value in setup_dict.items():
                setattr(self, key, value)
            return
        self.id = str(uuid.uuid4())
        self.name = name
        self.email = email
        self.image = image
        self.createdAt = time.time()
        self.lastPolled = self.createdAt
        self.connections = []
        self.paired_with = []
        self.connection_today = -1
        self.last_paired = self.createdAt

class Message:
    def __init__(self, userid:str, content:str, timeEdited:float, setup_dict=None):
        if setup_dict is not None:
            # careful!
            for key, value in setup_dict.items():
                setattr(self, key, value)
            return
        self.id = uuid.uuid4()
        self.user_id = userid
        self.content = content
        self.timeCreated = time.time()
        self.timeEdited = timeEdited

class Chat:
    messages:list[Message] = []
    def add_message(self, userid:str, content:str):
        self.messages.append(Message(userid, content))

class Connection:
    should_continue = (False, False)
    has_begun = False
    def __init__(self, user1: User, user2: User, geo_location:tuple[float, float], setup_dict=None):
        if setup_dict is not None:
            # careful!
            for key, value in setup_dict.items():
                setattr(self, key, value)
            return
        self.id = uuid.uuid4()
        self.created = time.time()
        self.expiry = time.time() + 30 * 24 * 3600
        #location is a lat + long coordinate pair. But is this right? may want to change to a semantic location ie "Red Square"
        self.geo_location = geo_location
        self.full_location = get_semantic_location(geo_location)
        self.semantic_location = get_semantic_location(geo_location).get("display_name")
        self.user_ids = (user1.id, user2.id)
        #todo: is this right?
        self.chat = Chat()
        user1.connections.append(self.id)
        user2.connections.append(self.id)

    def check_for_expiry(self) -> bool:
        return time.time() > self.expiry and self.should_continue.__contains__(False)


