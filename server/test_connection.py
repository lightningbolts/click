#!/usr/bin/env python3
"""
Test script to verify Supabase connection
Run this script to check if your Supabase configuration is working correctly.
"""

from dotenv import load_dotenv
import os, sys
from urllib.request import Request, urlopen

load_dotenv()
URL = os.getenv("SUPABASE_URL")
KEY = os.getenv("SUPABASE_KEY")
TABLE = "users"

if not URL or not KEY:
    print("Missing SUPABASE_URL or SUPABASE_KEY")
    sys.exit(1)

print("SUPABASE_URL:", URL)
print("SUPABASE_KEY:", KEY[:12] + "..." + KEY[-8:])

if KEY.startswith("sb_publishable_"):
    ep = f"{URL.rstrip('/')}/rest/v1/{TABLE}?select=id&limit=1"
    req = Request(ep, headers={"apikey": KEY, "Authorization": f"Bearer {KEY}"}, method="GET")
    try:
        with urlopen(req, timeout=8) as r:
            s = r.getcode(); b = r.read(1024).decode(errors="ignore")
            print(s, b[:400])
            sys.exit(0 if s == 200 else 2)
    except Exception as e:
        print("HTTP request failed:", e)
        sys.exit(3)

try:
    from supabase import create_client
    sup = create_client(URL, KEY)
    res = sup.table(TABLE).select("id").limit(1).execute()
    data = getattr(res, "data", None) or getattr(res, "json", None) or res
    print("SDK result:", str(data)[:400])
    sys.exit(0)
except Exception as e:
    print("SDK error:", e)
    sys.exit(4)
