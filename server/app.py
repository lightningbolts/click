import json
import os
import time
import uuid
import jwt
import requests
from google.oauth2 import id_token
from flask import Flask, request, jsonify
import os

GOOGLE_CLIENT_ID = os.environ.get("GOOGLE_CLIENT_ID")
GOOGLE_CLIENT_SECRET = os.environ.get("GOOGLE_CLIENT_SECRET")


def generate_refresh_token() -> str:
    key = str(uuid.uuid4()).replace('-', '')[:32]
    print(key)
    keys = []
    with open("refresh.json", "r") as f:
        keys = json.loads(f.read())
    keys.append(key)
    with open("refresh.json", "w") as f:
        f.write(json.dumps(keys))
    return key

def refresh_jwt_key(refresh: str) -> str:
    with open("refresh.json", "r") as fp:
        f = json.load(fp)
        if(refresh in f):
            encoded_jwt = jwt.encode({'org':"uw.edu", 'cid': GOOGLE_CLIENT_ID, 'exp': time.time() + 86400},
                                     GOOGLE_CLIENT_SECRET, algorithm="HS256")
            return encoded_jwt
        return "not allowed"


def validate(encoded):
    try:
        decoded = jwt.decode(encoded, GOOGLE_CLIENT_SECRET, algorithms=["HS256"])
        if(decoded['org'] == "uw.edu" and decoded['exp'] >= time.time() and decoded['cid'] == GOOGLE_CLIENT_ID):
            return True
        else:
            return False
    except:
        return False

app = Flask(__name__)


@app.route('/')
def hello_world():  # put application's code here
    return 'Hello World!'

@app.route('/create_account', methods=['POST'])
def create_account():
    if(validate(request.headers['Authorization'])):
        pass
    else:
        return "log in!"

@app.route('/google', methods=['POST'])
def google():
    CONF_URL = 'https://accounts.google.com/.well-known/openid-configuration'
    try:
        # Specify the WEB_CLIENT_ID of the app that accesses the backend:
        idinfo = id_token.verify_oauth2_token(request.args['token'], requests.Request(), GOOGLE_CLIENT_ID)
        if (idinfo['aud'] == GOOGLE_CLIENT_ID and 'accounts.google.com' in idinfo['iss'] and idinfo['hd'] == "uw.edu" and idinfo['exp'] >= time.time()):
            #plus one day
            encoded_jwt = jwt.encode({'org': idinfo['hd'], 'cid': idinfo['aud'], 'exp': time.time() + 86400}, GOOGLE_CLIENT_SECRET, algorithm="HS256")
            refresh = generate_refresh_token()
            return jsonify({"jwt": encoded_jwt, "refresh" : refresh}), 200
        else:
            return "not allowed", 403
    except:
        return "not allowed", 403

@app.route("/refresh", methods=['POST'])
def refresh():
    res = refresh_jwt_key(request.headers['Authorization'])
    if(res == "now allowed"):
        return res, 403
    return res, 200


@app.route("/logout", methods=['POST'])
def logout():
    try:
        refresh = request.headers['Authorization']
        f = []
        with open("refresh.json", "r") as fp:
            f = json.load(fp)
        if (refresh in f):
            f.remove(refresh)
        with open("refresh.json", "w") as fp:
            json.dump(fp = fp, obj= f)
        return "logged out"
    except:
        return "server error", 500



if __name__ == '__main__':
    app.run()
