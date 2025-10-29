import random
import time
import uuid

class User:
    connections = []
    share_key = 0
    def __init__(self, name:str, email:str, image:str, setup_dict=None):
        if(setup_dict is not None):
            #careful!
            for key, value in setup_dict.items():
                setattr(self, key, value)
            return
        self.id = uuid.uuid4()
        share_key = time.time() * random.getrandbits(64)
        self.name = name
        self.email = email
        self.image = image
        self.createdAt = time.time()



class Message:
    def __init__(self, user:User, content:str, timeEdited:float, setup_dict=None):
        if (setup_dict is not None):
            # careful!
            for key, value in setup_dict.items():
                setattr(self, key, value)
            return
        self.id = uuid.uuid4()
        self.user_id = user.id
        self.content = content
        self.timeCreated = time.time()
        self.timeEdited = timeEdited
class Chat:
    messages:list[Message] = []

    def add_message(self, user:User, content:str):
        self.messages.append(Message(user, content))

class Connection:
    should_continue = (False, False)
    def __init__(self, user1: User, user2: User, location:tuple[float, float], setup_dict=None):
        if (setup_dict is not None):
            # careful!
            for key, value in setup_dict.items():
                setattr(self, key, value)
            return
        self.id = uuid.uuid4()
        self.created = time.time()
        self.expiry = time.time() + 30 * 24 * 3600
        #location is a lat + long coordinate pair. But is this right? may want to change to a semantic location ie "Red Square"
        self.location = location
        self.user_ids = (user1.id, user2.id)
        self.chat = Chat()
        user1.connections.append(self.id)
        user2.connections.append(self.id)

    def check_and_delete(self):
        if time.time() > self.expiry and self.should_continue.__contains__(False):
            del self

