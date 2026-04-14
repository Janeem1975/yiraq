"""
mitmproxy Addon - اعتراض توكن AppCheck من ترافيك تطبيق عين العراق

الاستخدام:
    mitmproxy -s mitmproxy_capture.py
    أو
    mitmdump -s mitmproxy_capture.py

بعدها وجّه بروكسي المحاكي (LDPlayer) على IP الكمبيوتر والبورت 8080
"""

import os
import json
from datetime import datetime
from mitmproxy import http

TOKEN_FILE = "appcheck_token.txt"
LOG_FILE = "captured_tokens.json"


class AppCheckCapture:
    def __init__(self):
        self.tokens = []
        print("\n" + "=" * 60)
        print("  مراقب توكن AppCheck - عين العراق")
        print("  يراقب كل الطلبات لـ api.ayniq.app")
        print("=" * 60 + "\n")

    def request(self, flow: http.HTTPFlow) -> None:
        # مراقبة كل الطلبات لـ ayniq
        if "ayniq.app" not in (flow.request.host or ""):
            return

        # البحث عن هيدر AppCheck
        appcheck = flow.request.headers.get("x-firebase-appcheck", "")

        print(f"\n[*] طلب: {flow.request.method} {flow.request.url}")

        if appcheck:
            print("\n" + "=" * 60)
            print("[+] تم العثور على توكن AppCheck!")
            print("=" * 60)
            print(f"[+] التوكن: {appcheck[:80]}...")
            print("=" * 60 + "\n")

            # حفظ التوكن بملف نصي
            with open(TOKEN_FILE, "w", encoding="utf-8") as f:
                f.write(appcheck)
            print(f"[+] تم حفظ التوكن في: {TOKEN_FILE}")

            # حفظ بسجل JSON مع الوقت
            entry = {
                "timestamp": datetime.now().isoformat(),
                "url": flow.request.url,
                "token": appcheck,
            }
            self.tokens.append(entry)

            try:
                existing = []
                if os.path.exists(LOG_FILE):
                    with open(LOG_FILE, "r", encoding="utf-8") as f:
                        existing = json.load(f)
                existing.append(entry)
                with open(LOG_FILE, "w", encoding="utf-8") as f:
                    json.dump(existing, f, indent=2, ensure_ascii=False)
                print(f"[+] تم إضافة السجل إلى: {LOG_FILE}")
            except Exception as e:
                print(f"[-] خطأ بحفظ السجل: {e}")
        else:
            print("[-] بدون هيدر AppCheck")

        # طباعة كل الهيدرات المهمة
        auth = flow.request.headers.get("Authorization", "")
        if auth:
            print(f"[*] Authorization: {auth[:50]}...")


addons = [AppCheckCapture()]
