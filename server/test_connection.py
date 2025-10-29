#!/usr/bin/env python3
"""
Test script to verify Supabase connection
Run this script to check if your Supabase configuration is working correctly.
"""

from dotenv import load_dotenv
import os, sys, json, time
from urllib.request import Request, urlopen, HTTPError

load_dotenv()
URL = os.getenv("SUPABASE_URL")
KEY = os.getenv("SUPABASE_KEY")
SERVICE_KEY = os.getenv("SUPABASE_SERVICE_KEY")
TABLE = os.getenv("SUPABASE_TEST_TABLE", "users")
if not URL or not KEY:
    print("Missing SUPABASE_URL or SUPABASE_KEY")
    sys.exit(1)
print("URL:", URL)
print("KEY:", KEY[:12] + "..." + KEY[-8:])

email = f"test_{int(time.time())}@example.com"
row = {"name": "Test User", "email": email}

def rest_key(method, path, data=None, key=None):
    k = key or KEY
    ep = f"{URL.rstrip('/')}/rest/v1/{path}"
    hdrs = {"apikey": k, "Authorization": f"Bearer {k}", "Content-Type": "application/json", "Prefer": "return=representation"}
    req = Request(ep, data=(json.dumps(data).encode() if data else None), headers=hdrs, method=method)
    with urlopen(req, timeout=8) as r:
        return r.getcode(), r.read(4096).decode(errors="ignore")

try:
    status, body = rest_key("GET", f"{TABLE}?select=id&limit=1")
    print("READ", status, body[:400])
except Exception as e:
    print("READ failed:", e)

if KEY.startswith("sb_publishable_"):
    try:
        st, bd = rest_key("POST", TABLE, row, key=KEY)
        print("CREATE (REST)", st, bd[:400])
        sys.exit(0 if st in (200,201) else 2)
    except HTTPError as e:
        print("CREATE (REST) failed:", e)
        if e.code == 401 and SERVICE_KEY:
            print("Publishable key unauthorized for POST; retrying with SUPABASE_SERVICE_KEY (service_role)...")
            try:
                st, bd = rest_key("POST", TABLE, row, key=SERVICE_KEY)
                print("CREATE (SERVICE)", st, bd[:400])
                sys.exit(0 if st in (200,201) else 2)
            except Exception as e2:
                print("CREATE with service key failed:", e2)
                sys.exit(5)
        sys.exit(3)
    except Exception as e:
        print("CREATE (REST) failed:", e)
        sys.exit(3)
else:
    try:
        from supabase import create_client
        sup = create_client(URL, KEY)
        res = sup.table(TABLE).insert(row).select("id,email").execute()
        data = getattr(res, "data", None) or getattr(res, "json", None) or res
        print("CREATE (SDK)", str(data)[:400])
        sys.exit(0)
    except Exception as e:
        print("CREATE (SDK) failed:", e)
        sys.exit(4)
