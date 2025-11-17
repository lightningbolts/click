import os
import random
import time
from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv

import database_ops
from database_ops import fetch_connection, update_connection_with_id
from chat_ops import ChatOperations
from supabase import create_client, Client

# Load environment variables from .env file
load_dotenv()

GOOGLE_CLIENT_ID = os.environ.get("GOOGLE_CLIENT_ID")
GOOGLE_CLIENT_SECRET = os.environ.get("GOOGLE_CLIENT_SECRET")

# Initialize Supabase client for chat functionality
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")
supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY) if SUPABASE_URL and SUPABASE_KEY else None
chat_ops = ChatOperations(supabase) if supabase else None


def validate(encoded, email):
    try:
        decoded = database_ops.supabase.auth.get_claims(encoded)
        if(decoded["email"] == email and decoded['exp'] >= time.time() and decoded["aud"] == "authenticated"):
            return True
        else:
            return False
    except:
        return False

app = Flask(__name__)

# Enable CORS for iOS/Android/Web clients
CORS(app, resources={
    r"/*": {
        "origins": "*",  # In production, restrict this to your domains
        "methods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
        "allow_headers": ["Content-Type", "Authorization"]
    }
})


@app.route('/')
def hello_world():  # put application's code here
    return 'Hello World!'

@app.route('/create_account', methods=['POST'])
def create_account():
    if validate(request.headers['Authorization']):
        user_data = request.json
        return database_ops.create_user(request.args["name"], request.args["email"], request.args["image_src"])
    else:
        return "log in!"

@app.route("/user/<name>", methods=['GET'])
def user(name):
    if validate(request.headers['Authorization'], request.args.get("email")):
        user_fetched = database_ops.fetch_user(name)
        if(user_fetched == None):
            return "user not found", 404
        return jsonify({user_fetched})
    return "log in", 405

@app.route("/user_with_email/<email>", methods=['GET'])
def user_with_email(email):
    if validate(request.headers['Authorization'], email):
        user_fetched = database_ops.fetch_user_with_email(email)
        if(user_fetched == None):
            return "user not found", 404
        return jsonify(user_fetched)
    return "log in", 405

@app.route("/connections/", methods=['GET'])
def connections():
    if validate(request.headers['Authorization'], request.args.get("email")):
        list = request.json["connections"]
        return jsonify([database_ops.fetch_connection(id) for id in list if not database_ops.delete_connection_if_expired(id)])
    return "log in", 405

@app.route("/connection/<id>", methods=['GET'])
def connection(id):
    if validate(request.headers['Authorization'], request.args.get("email")):
        if database_ops.delete_connection_if_expired(id):
            return "connection expired", 404
        return jsonify(database_ops.fetch_connection(id))
    return "log in", 405

@app.route("/connection/new/", methods=['POST'])
def new_connection():
    if validate(request.headers['Authorization'], request.args.get("email")):
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
    return "log in", 405


@app.route("/messages/restore", methods=['GET'])
def message_restore():
    if validate(request.headers['Authorization'], request.args.get("email")):
        conn = fetch_connection(request.args.get("id"))
        return jsonify(conn.chat)
    return "log in", 405

#may need route for several messages
@app.route("/message/new", methods=['POST'])
def message_new():
    if validate(request.headers['Authorization'], request.args.get("email")):
        conn = fetch_connection(request.args.get("connid"))
        if not conn.has_begun:
            return 500
        return jsonify(database_ops.create_message(conn.id, request.args.get("userid"), request.args.get("content")))
    return "log in", 405

@app.route("/pollpairs", methods=['POST'])
def pollpairs():
    if validate(request.headers['Authorization'], request.args.get("email")):
        user_id = request.args.get("id")
        user = database_ops.fetch_user_with_id(user_id)
        right_now = time.time()
        user.lastPolled = right_now
        #todo: test this conditional
        if user.last_paired < right_now - 86400:
            counter = 0
            index = len(user.connections) - counter
            proposed_connection = database_ops.fetch_connection(user.connections[index])
            other_user_id = [item for item in proposed_connection.user_ids if not item == user_id][0]
            other_user = database_ops.fetch_user_with_id(other_user_id)
            while user.connections[index] not in user.paired_with and other_user.last_paired < time.time() - 86400:
                index = random.randint(0, len(user.connections))
                if index < 0:
                    return  500
                proposed_connection = database_ops.fetch_connection(user.connections[index])
                other_user_id = [item for item in proposed_connection.user_ids if not item == user_id][0]
                other_user = database_ops.fetch_user_with_id(other_user_id)
            user.paired_with.append(user.connections[index])
            other_user.paired_with.append(user.connections[index])
            user.connection_today = user.connections[index]
            other_user.connection_today = user.connections[index]
            user.last_paired = right_now
            other_user.last_paired = right_now
            if database_ops.update_user_with_id(user_id, user) and database_ops.update_user_with_id(other_user_id, other_user):
                return proposed_connection
            return 500
        else:
            connection = fetch_connection(user.paired_with)
            other_user_id = [item for item in connection.user_ids if not item == user_id][0]
            other_user = database_ops.fetch_user_with_id(other_user_id)
            if other_user.lastPolled + 300 > right_now:
                connection.has_begun = True
            if update_connection_with_id(user.paired_with, connection):
                return connection
            return 500
    return "log in", 405

# ============== CHAT API ENDPOINTS ==============

@app.route('/api/chats/user/<user_id>', methods=['GET'])
def get_user_chats(user_id):
    """Get all chats for a user with details"""
    if not validate(request.headers.get('Authorization'), request.args.get("email")):
        return jsonify({"error": "Unauthorized"}), 401

    if not chat_ops:
        return jsonify({"error": "Chat service not configured"}), 500

    try:
        chats = chat_ops.fetch_chats_for_user(user_id)
        return jsonify({"chats": chats}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/chats/<chat_id>', methods=['GET'])
def get_chat(chat_id):
    """Get a specific chat by ID"""
    if not validate(request.headers.get('Authorization'), request.args.get("email")):
        return jsonify({"error": "Unauthorized"}), 401

    if not chat_ops:
        return jsonify({"error": "Chat service not configured"}), 500

    try:
        chat = chat_ops.fetch_chat_by_id(chat_id)
        if not chat:
            return jsonify({"error": "Chat not found"}), 404
        return jsonify({"chat": chat}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/chats/<chat_id>/messages', methods=['GET'])
def get_chat_messages(chat_id):
    """Get all messages for a specific chat"""
    if not validate(request.headers.get('Authorization'), request.args.get("email")):
        return jsonify({"error": "Unauthorized"}), 401

    if not chat_ops:
        return jsonify({"error": "Chat service not configured"}), 500

    try:
        messages = chat_ops.fetch_messages_for_chat(chat_id)
        return jsonify({"messages": messages}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/chats/<chat_id>/messages', methods=['POST'])
def send_message(chat_id):
    """Send a new message in a chat"""
    if not validate(request.headers.get('Authorization'), request.args.get("email")):
        return jsonify({"error": "Unauthorized"}), 401

    if not chat_ops:
        return jsonify({"error": "Chat service not configured"}), 500

    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No data provided"}), 400

        user_id = data.get('user_id')
        content = data.get('content')

        if not user_id or not content:
            return jsonify({"error": "user_id and content are required"}), 400

        message = chat_ops.create_message(chat_id, user_id, content)
        if not message:
            return jsonify({"error": "Failed to create message"}), 500

        return jsonify({"message": message}), 201
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/chats/<chat_id>/mark_read', methods=['POST'])
def mark_messages_read(chat_id):
    """Mark messages as read for a user"""
    if not validate(request.headers.get('Authorization'), request.args.get("email")):
        return jsonify({"error": "Unauthorized"}), 401

    if not chat_ops:
        return jsonify({"error": "Chat service not configured"}), 500

    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No data provided"}), 400

        user_id = data.get('user_id')
        if not user_id:
            return jsonify({"error": "user_id is required"}), 400

        success = chat_ops.mark_messages_as_read(chat_id, user_id)
        if not success:
            return jsonify({"error": "Failed to mark messages as read"}), 500

        return jsonify({"success": True}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/chats/<chat_id>/messages/<message_id>', methods=['PUT'])
def update_message(chat_id, message_id):
    """Update a message's content"""
    if not validate(request.headers.get('Authorization'), request.args.get("email")):
        return jsonify({"error": "Unauthorized"}), 401

    if not chat_ops:
        return jsonify({"error": "Chat service not configured"}), 500

    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No data provided"}), 400

        user_id = data.get('user_id')
        content = data.get('content')

        if not user_id or not content:
            return jsonify({"error": "user_id and content are required"}), 400

        message = chat_ops.update_message(message_id, user_id, content)
        if not message:
            return jsonify({"error": "Failed to update message or unauthorized"}), 403

        return jsonify({"message": message}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/chats/<chat_id>/messages/<message_id>', methods=['DELETE'])
def delete_message(chat_id, message_id):
    """Delete a message"""
    if not validate(request.headers.get('Authorization'), request.args.get("email")):
        return jsonify({"error": "Unauthorized"}), 401

    if not chat_ops:
        return jsonify({"error": "Chat service not configured"}), 500

    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No data provided"}), 400

        user_id = data.get('user_id')
        if not user_id:
            return jsonify({"error": "user_id is required"}), 400

        success = chat_ops.delete_message(message_id, user_id)
        if not success:
            return jsonify({"error": "Failed to delete message or unauthorized"}), 403

        return jsonify({"success": True}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/chats/connection/<connection_id>', methods=['GET'])
def get_chat_for_connection(connection_id):
    """Get the chat associated with a connection"""
    if not validate(request.headers.get('Authorization'), request.args.get("email")):
        return jsonify({"error": "Unauthorized"}), 401

    if not chat_ops:
        return jsonify({"error": "Chat service not configured"}), 500

    try:
        chat = chat_ops.fetch_chat_for_connection(connection_id)
        if not chat:
            return jsonify({"error": "Chat not found for this connection"}), 404
        return jsonify({"chat": chat}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/chats/<chat_id>/participants', methods=['GET'])
def get_chat_participants(chat_id):
    """Get the participants in a chat"""
    if not validate(request.headers.get('Authorization'), request.args.get("email")):
        return jsonify({"error": "Unauthorized"}), 401

    if not chat_ops:
        return jsonify({"error": "Chat service not configured"}), 500

    try:
        participants = chat_ops.get_chat_participants(chat_id)
        return jsonify({"participants": participants}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


# ============== END CHAT API ENDPOINTS ==============

if __name__ == '__main__':
    # Run on all network interfaces (0.0.0.0) so iOS simulator can connect
    # Port 5000 is the default
    print("🚀 Starting Flask server...")
    print("📡 Server will be accessible at:")
    print(f"   - Local: http://localhost:5000")
    print(f"   - Network: http://<your-ip>:5000")
    print("💡 iOS Simulator: Use your Mac's local IP address in ApiConfig.kt")
    print()
    app.run(host='0.0.0.0', port=5000, debug=True)
