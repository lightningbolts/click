import json
import os
import time
import uuid
import jwt
import requests
from google.oauth2 import id_token
from flask import Flask, request, jsonify
from dotenv import load_dotenv

import database_ops
from database_ops import fetch_connection

# Load environment variables from .env file
load_dotenv()

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
    if validate(request.headers['Authorization']):
        user_data = request.json
        return database_ops.create_user(user_data["name"], user_data["email"], user_data["image_src"])
    else:
        return "log in!"

@app.route('/google', methods=['POST'])
def google():
    CONF_URL = 'https://accounts.google.com/.well-known/openid-configuration'
    try:
        # Specify the WEB_CLIENT_ID of the app that accesses the backend:
        idinfo = id_token.verify_oauth2_token(request.args['token'], requests.Request(), GOOGLE_CLIENT_ID)
        if (idinfo['aud'] == GOOGLE_CLIENT_ID and 'accounts.google.com' in idinfo['iss'] and idinfo['exp'] >= time.time()):
            #plus one day
            encoded_jwt = jwt.encode({'org': idinfo['hd'], 'cid': idinfo['aud'], 'exp': time.time() + 86400}, GOOGLE_CLIENT_SECRET, algorithm="HS256")
            refresh = generate_refresh_token()
            return jsonify({"jwt": encoded_jwt, "refresh" : refresh, "user": database_ops.fetch_user_with_email(request.args["email"])}), 200
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


@app.route("/user/<name>", methods=['GET'])
def user(name):
    user_fetched = database_ops.fetch_user(name)
    if(user_fetched == None):
        return "user not found", 404
    return jsonify({user_fetched})

@app.route("/user_with_email/<email>", methods=['GET'])
def user_with_email(email):
    user_fetched = database_ops.fetch_user_with_email(email)
    if(user_fetched == None):
        return "user not found", 404
    return jsonify(user_fetched)

@app.route("/connections/", methods=['GET'])
def connections():
    list = request.json["connections"]
    return jsonify([database_ops.fetch_connection(id) for id in list if not database_ops.delete_connection_if_expired(id)])

@app.route("/connection/<id>", methods=['GET'])
def connection(id):
    if database_ops.delete_connection_if_expired(id):
        return "connection expired", 404
    return jsonify(database_ops.fetch_connection(id))

@app.route("/connection/new/", methods=['POST'])
def new_connection():
    id1 = request.args.get("id1")
    id2 = request.args.get("id2")
    lat = request.args.get("lat")
    long = request.args.get("long")
    if not (isinstance(lat, float)):
        lat = 0.0
    if not (isinstance(long, float)):
        long = 0.0
    user1 = database_ops.fetch_user_with_id(id1)
    user2 = database_ops.fetch_user_with_id(id2)
    return database_ops.create_connection(user1, user2, (lat, long))


@app.route("/messages/restore", methods=['GET'])
def message_restore():
    conn = fetch_connection(request.args.get("id"))
    return jsonify(conn.chat)

@app.route("/message/new", methods=['POST'])
def message_new():
    return 200

@app.route("/pollpairs", methods=['POST'])
def pollpairs():
    return 200








if __name__ == '__main__':
    app.run()
