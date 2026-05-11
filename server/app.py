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


def validate(encoded, email=None):
    try:
        decoded = database_ops.supabase.auth.get_claims(encoded)
        if email is None:
            return decoded['exp'] >= time.time() and decoded.get("aud") == "authenticated"
        if(decoded.get("email") == email and decoded['exp'] >= time.time() and decoded.get("aud") == "authenticated"):
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

@app.route('/create_account/', methods=['POST'])
def create_account():
        return database_ops.create_user(request.args["name"], request.args["email"], request.args["image_src"])

@app.route("/user/<name>", methods=['GET'])
def user(name):
    if validate(request.headers['Authorization'], request.args.get("email")):
        user_fetched = database_ops.fetch_user(name)
        if(user_fetched == None):
            return "user not found", 404
        return jsonify({user_fetched})
    return "log in", 405

@app.route("/test", methods=['GET'])
def test():
    user_fetched = database_ops.fetch_user_with_email("test_1761762917@example.com")
    return jsonify(user_fetched.id)

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
        context_tag_id = request.args.get("context_tag_id") or request.args.get("context_tag")
        if not (isinstance(lat, float)):
            lat = 0.0
        if not (isinstance(long, float)):
            long = 0.0
        user1 = database_ops.fetch_user_with_id(id1)
        user2 = database_ops.fetch_user_with_id(id2)
        return database_ops.create_connection(user1, user2, (lat, long), context_tag_id=context_tag_id)
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
        message_type = data.get('message_type') or 'text'
        metadata = data.get('metadata')

        if not user_id:
            return jsonify({"error": "user_id is required"}), 400

        if message_type != 'call_log' and not content:
            return jsonify({"error": "content is required"}), 400

        if message_type == 'call_log':
            content = content if isinstance(content, str) else ''

        message = chat_ops.create_message(
            chat_id,
            user_id,
            content,
            message_type=message_type,
            metadata=metadata if isinstance(metadata, dict) else {},
        )
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


@app.route('/api/users/display-names', methods=['POST'])
def get_display_names():
    """Resolve display names for a batch of user IDs in an RLS-safe way."""
    if not validate(request.headers.get('Authorization')):
        return jsonify({"error": "Unauthorized"}), 401

    if not supabase:
        return jsonify({"error": "Chat service not configured"}), 500

    try:
        payload = request.get_json(silent=True) or {}
        user_ids = payload.get("user_ids") or []
        user_ids = [str(uid) for uid in user_ids if uid][:100]

        if not user_ids:
            return jsonify({"names": {}}), 200

        names = {}

        def normalize_display_name(full_name, name, email):
            email_prefix = email.split("@")[0] if isinstance(email, str) and "@" in email else None
            def normalize_candidate(value):
                if not isinstance(value, str):
                    return ""
                normalized = value.strip()
                if not normalized or normalized.lower() == "connection":
                    return ""
                return normalized

            return (
                normalize_candidate(full_name)
                or normalize_candidate(name)
                or normalize_candidate(email_prefix)
                or "Connection"
            )

        try:
            users_response = supabase.table("users").select("id,name,full_name,email").in_("id", user_ids).execute()
            users = users_response.data or []
        except Exception:
            users_response = supabase.table("users").select("id,name,email").in_("id", user_ids).execute()
            users = users_response.data or []

        for user in users:
            user_id = user.get("id")
            if not user_id:
                continue
            names[user_id] = normalize_display_name(
                user.get("full_name"),
                user.get("name"),
                user.get("email")
            )

        unresolved_ids = [user_id for user_id in user_ids if names.get(user_id) == "Connection"]
        if unresolved_ids:
            for user_id in unresolved_ids:
                try:
                    auth_response = supabase.auth.admin.get_user_by_id(user_id)
                    auth_user = getattr(auth_response, "user", None) or auth_response
                    if isinstance(auth_user, dict):
                        user_metadata = auth_user.get("user_metadata") or {}
                        email = auth_user.get("email")
                    else:
                        user_metadata = getattr(auth_user, "user_metadata", None) or {}
                        email = getattr(auth_user, "email", None)
                    resolved = normalize_display_name(
                        user_metadata.get("full_name"),
                        user_metadata.get("name"),
                        email
                    )
                    if resolved != "Connection":
                        names[user_id] = resolved
                except Exception:
                    continue

        return jsonify({"names": names}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


# ============== THIN BFF HELPERS (mobile Ktor / widgets / hubs) ==============


@app.route("/public-profile", methods=["GET"])
def public_profile():
    """Unsigned teaser payload for App Clips / Instant entry / QR landing."""
    user_id = (request.args.get("user_id") or request.args.get("user") or "").strip()
    if not user_id:
        return jsonify({"error": "user_id required"}), 400
    if not supabase:
        return jsonify({
            "user_id": user_id,
            "name": "Click user",
            "aura_hex": "#4A6CF7",
            "tagline": "Tap in to connect privately.",
        }), 200
    try:
        row = supabase.table("users").select("id,name,full_name,first_name,last_name").eq("id", user_id).limit(1).execute()
        rows = row.data or []
        data = rows[0] if rows else None
        if not data:
            return jsonify({"error": "not_found"}), 404
        name = (
            (data.get("full_name") or "").strip()
            or (data.get("name") or "").strip()
            or " ".join(
                x for x in [data.get("first_name") or "", data.get("last_name") or ""]
                if isinstance(x, str) and x.strip()
            ).strip()
            or "Click user"
        )
        return jsonify({
            "user_id": user_id,
            "name": name,
            "aura_hex": "#4A6CF7",
            "tagline": "Accept the connection in Click — your aura travels with you.",
        }), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/connections/encounter-context", methods=["GET"])
def encounter_context():
    """Whether the signed-in viewer is starting a net-new edge vs logging another encounter."""
    if not validate(request.headers.get("Authorization")):
        return jsonify({"error": "Unauthorized"}), 401
    peer = (request.args.get("peer_user_id") or "").strip()
    if not peer or not supabase:
        return jsonify({"is_new_connection": True}), 200
    try:
        uid = None
        try:
            user = supabase.auth.get_user(request.headers.get("Authorization", "").replace("Bearer ", "").strip())
            uid = getattr(user, "user", None)
            if uid is not None:
                uid = getattr(uid, "id", None) or (uid.get("id") if isinstance(uid, dict) else None)
        except Exception:
            uid = None
        if not uid:
            return jsonify({"is_new_connection": True}), 200
        r = supabase.table("connections").select("id,user_ids").contains("user_ids", [uid]).execute()
        conns = r.data or []
        exists = any(
            isinstance(c.get("user_ids"), list) and peer in c["user_ids"] and uid in c["user_ids"]
            for c in conns
        )
        return jsonify({"is_new_connection": not exists}), 200
    except Exception:
        return jsonify({"is_new_connection": True}), 200


@app.route("/api/connections/encounter", methods=["POST"])
def log_encounter():
    if not validate(request.headers.get("Authorization")):
        return jsonify({"error": "Unauthorized"}), 401
    body = request.get_json(silent=True) or {}
    peer_user_id = (body.get("peer_user_id") or "").strip()
    if not peer_user_id:
        return jsonify({"error": "peer_user_id required"}), 400
    # Thin acknowledgement — full encounter fan-out remains owned by Supabase / edge in production.
    return jsonify({"ok": True, "accepted": True, "peer_user_id": peer_user_id}), 200


@app.route("/api/hub/create", methods=["POST"])
def hub_create():
    if not validate(request.headers.get("Authorization")):
        return jsonify({"error": "Unauthorized"}), 401
    body = request.get_json(silent=True) or {}
    name = (body.get("name") or "Community hub").strip() or "Community hub"
    category = (body.get("category") or "general").strip() or "general"
    lat = body.get("latitude")
    lon = body.get("longitude")
    hid = f"hub_{int(time.time() * 1000)}_{random.randint(1000, 9999)}"
    return jsonify({
        "hub_id": hid,
        "name": name,
        "category": category,
        "channel": f"hub:{hid}",
        "latitude": lat,
        "longitude": lon,
    }), 200


@app.route("/api/insights/widget-vibe", methods=["GET"])
def widget_vibe():
    if not validate(request.headers.get("Authorization")):
        return jsonify({"error": "Unauthorized"}), 401
    hues = ["#6C5CE7", "#00CEC9", "#FD79A8", "#FDCB6E", "#74B9FF"]
    labels = ["Electric calm", "Social lift", "Deep focus", "Golden hour", "Ocean air"]
    i = random.randint(0, len(hues) - 1)
    return jsonify({"hex_color": hues[i], "label": labels[i]}), 200


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
