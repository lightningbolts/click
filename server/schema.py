import random
import time


class User:
    connections = []
    share_key = 0
    def __init__(self, name:str, email:str):
        share_key = time.time() * random.getrandbits(32)
        self.name = name
        self.email = email

class Message:
    def __init__(self, user:User, content:str):
        self.user = user
        self.content = content

class Chat:
    messages:list[Message] = []

    def add_message(self, user:User, content:str):
        self.messages.append(Message(user, content))

class Connection:
    should_continue = (False, False)
    def __init__(self, user1: User, user2: User, location:tuple[float, float]):
        self.created = time.time()
        self.expiry = time.time() + 30 * 24 * 3600
        #location is a long + lat coordinate pair. But is this right? may want to change to a semantic location ie "Red Square"
        self.location = location
        self.users = (user1, user2)
        self.chat = Chat()
        user1.connections.append(self)
        user2.connections.append(self)

    def check_and_delete(self):
        if time.time() > self.expiry and self.should_continue.__contains__(False):
            del self

