import asyncio
import aiohttp
import json
import os
import random
from datetime import datetime, timedelta

# ==========================================
# ⚙️ إعدادات سيرفر التحكم (Control Server)
# ==========================================
# ضع رابط سيرفر التحكم الخاص بك هنا (بدون / في النهاية)
CONTROL_SERVER_URL = "https://omran22-meayniq-control-serverlicense.hf.space"
# ضع مفتاح الأدمن الذي وضعته في إعدادات Hugging Face
ADMIN_KEY = "mypassword123"

# ==========================================
# ⚙️ إعدادات التطبيق الأساسية
# ==========================================
BASE_URL = "https://api.ayniq.app"
SESSIONS_PATH = "sessions.json"
DELAY_TIME = 3.5

# الألوان لتوضيح حالة السكربت
R = '\033[91m'  # أحمر
G = '\033[92m'  # أخضر
Y = '\033[93m'  # أصفر
C = '\033[96m'  # سماوي
RESET = '\033[0m'  # إعادة اللون

OFFICES = {
    "1": {"uuid": "9117dadb-6563-48f1-aca1-f6cd7896d4a7", "name": "الموصل الايمن (صباحي)"},
    "2": {"uuid": "0b3c251b-e28a-4b3d-9859-909dc6b21b97", "name": "الموصل الايسر (مسائي)"},
    "3": {"uuid": "b6920038-049c-4079-bed9-5989f4507014", "name": "المعلومات المدنية بعشيقة"},
    "4": {"uuid": "5e7e9c43-a16d-4927-a852-36ee94cb6cc9", "name": "المعلومات المدنية وانه"},
    "5": {"uuid": "8295b95a-971f-4300-8c38-835cd9df0ca2", "name": "المعلومات المدنية حمام العليل"},
    "6": {"uuid": "1b854974-73f9-48da-b798-9def2948b141", "name": "المعلومات المدنية الشورة"},
    "7": {"uuid": "add4c2be-9949-4d50-ae2b-d7c06aac5616", "name": "المعلومات المدنية اربيل"},
    "8": {"uuid": "1004-adhamiya", "name": "قسم المعلومات المدنية الأعظمية 1004"}
}

MORNING_SLOTS = [
    "07:00 AM", "07:30 AM", "08:00 AM", "08:30 AM", "09:00 AM", "09:30 AM", "10:00 AM", "10:30 AM",
    "11:00 AM", "11:30 AM", "12:00 PM", "12:30 PM", "01:00 PM", "01:30 PM", "02:00 PM", "02:30 PM"
]

EVENING_SLOTS = [
    "03:00 PM", "03:30 PM", "04:00 PM", "04:30 PM", "05:00 PM", "05:30 PM", "06:00 PM", "06:30 PM",
    "07:00 PM", "07:30 PM", "08:00 PM"
]


def load_data():
    if os.path.exists(SESSIONS_PATH):
        try:
            with open(SESSIONS_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
        except:
            return {}
    return {}


def save_data(data):
    with open(SESSIONS_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def strict_input(prompt):
    while True:
        val = input(prompt).strip()
        if val: return val
        print("هذا الحقل مطلوب")


def normalize_target_date(raw_value):
    tomorrow = (datetime.now() + timedelta(days=1)).date()
    try:
        parsed = datetime.strptime(raw_value.strip(), "%Y-%m-%d").date()
    except ValueError:
        return tomorrow.isoformat(), True
    if parsed < tomorrow: return tomorrow.isoformat(), True
    return parsed.isoformat(), False


# ==========================================
# 🔄 محرك الأتمتة - التواصل مع سيرفر التحكم
# ==========================================
async def get_global_appcheck():
    """جلب مفتاح AppCheck من السيرفر"""
    headers = {"x-admin-key": ADMIN_KEY}
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(f"{CONTROL_SERVER_URL}/admin/booking/settings", headers=headers) as resp:
                if resp.status == 200:
                    data = await resp.json()
                    return data.get("app_check", "")
    except:
        pass
    return ""


async def sync_accounts_from_server():
    """سحب جميع حسابات الحجز من سيرفر التحكم وتخزينها"""
    headers = {"x-admin-key": ADMIN_KEY}
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(f"{CONTROL_SERVER_URL}/admin/booking/accounts", headers=headers) as resp:
                if resp.status == 200:
                    data = await resp.json()
                    accounts_list = data.get("accounts", [])
                    sessions = {}
                    for acc in accounts_list:
                        sessions[acc["phone"]] = {
                            "token": acc["token"],
                            "office_uuid": acc["office_uuid"],
                            "office_name": acc["office_name"],
                            "payload": acc["payload"]
                        }
                    # حفظ نسخة محلية احتياطية
                    save_data(sessions)
                    return sessions
    except Exception as e:
        print(f"{R}❌ فشل الاتصال بسيرفر التحكم: {e}{RESET}")
    return None


async def wait_for_new_token_from_server(phone, old_token):
    """الخطوة 1: يطلب OTP عبر السيرفر. الخطوة 2: ينتظر وصول التوكن الجديد"""
    headers = {"x-admin-key": ADMIN_KEY, "Content-Type": "application/json"}

    print(f"\n{Y}⚠️  الحساب {phone} فقد التوكن. جاري طلب OTP جديد عبر السيرفر...{RESET}")
    try:
        async with aiohttp.ClientSession() as session:
            async with session.post(f"{CONTROL_SERVER_URL}/admin/booking/auth/request-otp", json={"phone": phone},
                                    headers=headers) as resp:
                if resp.status == 200:
                    print(f"{C}✅ تم طلب الـ OTP بنجاح! السكربت الآن ينتظر وصول الكود...{RESET}")
                elif resp.status == 429:
                    print(
                        f"{R}⏳ السيرفر الأصلي يفرض حظراً مؤقتاً (Rate Limit) على طلب الـ OTP. سننتظر ونحاول لاحقاً.{RESET}")
    except Exception as e:
        print(f"{R}❌ فشل الاتصال بالسيرفر لطلب OTP: {e}{RESET}")

    print(f"{Y}⏳ جاري البحث عن التوكن الجديد في السيرفر كل 5 ثوانٍ...{RESET}")
    api_url = f"{CONTROL_SERVER_URL}/admin/booking/accounts"

    async with aiohttp.ClientSession() as session:
        while True:
            try:
                async with session.get(api_url, headers=headers, timeout=10) as response:
                    if response.status == 200:
                        data = await response.json()
                        for acc in data.get("accounts", []):
                            if acc.get("phone") == phone:
                                server_token = acc.get("token", "")
                                if server_token and server_token != old_token and server_token != "will-be-auto-filled":
                                    print(f"\n{G}🚀 تم استلام التوكن الجديد بنجاح! جاري استئناف الهجوم...{RESET}\n")
                                    return server_token
            except Exception:
                pass
            await asyncio.sleep(5)


# ==========================================
# 🎯 محرك الهجوم
# ==========================================
async def start_loop(phone, account, target_date, slots):
    current_token = account['token']
    appcheck_token = await get_global_appcheck()

    async with aiohttp.ClientSession() as session:
        while True:
            headers = {
                "User-Agent": "com.moi.ayniq 1.10.11",
                "x-firebase-appcheck": appcheck_token,
                "Content-Type": "application/json",
                "Authorization": f"Bearer {current_token}",
            }

            for slot in slots:
                payload = dict(account["payload"])
                payload["booking_date"] = f"{target_date} {slot}"
                payload["office_uuid"] = account["office_uuid"]

                try:
                    async with session.post(f"{BASE_URL}/booking/api/booking", json=payload, headers=headers,
                                            timeout=10) as response:
                        body = await response.text()

                    if response.status == 200:
                        print(f"{G}{phone} | {account['office_name']} | {slot} -> SUCCESS ✅{RESET}")
                        return True

                    if response.status == 429:
                        print(f"{Y}{phone} | {slot} -> RATE LIMIT ⏳{RESET}")

                    elif response.status in (401, 403):
                        print(f"{R}{phone} | {slot} -> TOKEN EXPIRED 🔑{RESET}")

                        new_token = await wait_for_new_token_from_server(phone, current_token)

                        current_token = new_token
                        account['token'] = new_token
                        sessions = load_data()
                        if phone in sessions:
                            sessions[phone]['token'] = new_token
                            save_data(sessions)

                        appcheck_token = await get_global_appcheck()
                        break

                    elif "full" in body.lower() or "ممتلئ" in body:
                        print(f"{phone} | {slot} -> FULL ❌")
                    else:
                        print(f"{phone} | {slot} -> REJECTED ({response.status}) ⛔")
                except aiohttp.ClientError as e:
                    print(f"{R}{phone} | {slot} -> CONNECTION ERROR: {e}{RESET}")

                await asyncio.sleep(DELAY_TIME)


async def main():
    print(f"{C}🔄 جاري الاتصال بسيرفر التحكم لجلب حسابات الحجز...{RESET}")
    sessions = await sync_accounts_from_server()

    # إذا فشل الاتصال بالسيرفر لسبب ما، نعتمد على الملف المحلي كخطة بديلة
    if not sessions:
        sessions = load_data()

    # التحقق من وجود حسابات حجز فعلياً
    if not sessions:
        print(f"\n{Y}⚠️ لم يتم العثور على (حسابات حجز) جاهزة!{RESET}")
        print(f"{C}تأكد من الذهاب إلى لوحة التحكم (قسم إضافة / تحديث حسابات الحجز) وتعبئة:{RESET}")
        print(f"{C}1. رقم الهاتف\n2. تحديد الدائرة\n3. لصق الـ Payload JSON\nثم اضغط (حفظ حساب الحجز).{RESET}")
        return

    print(f"{G}✅ تم جلب {len(sessions)} حساب حجز بنجاح!{RESET}\n")

    target_date_raw = strict_input("التاريخ YYYY-MM-DD: ")
    target_date, adjusted = normalize_target_date(target_date_raw)
    if adjusted: print(f"تم تعديل التاريخ تلقائيًا إلى {target_date}")
    pack = strict_input("الحزمة (1 صباح / 2 مساء): ")
    slots = MORNING_SLOTS if pack == "1" else EVENING_SLOTS

    tasks = [
        asyncio.create_task(start_loop(phone, account, target_date, slots))
        for phone, account in sessions.items()
    ]
    if not tasks: return
    await asyncio.gather(*tasks)


if __name__ == "__main__":
    try:
        os.system('clear' if os.name == 'posix' else 'cls')
        print(f"{C}🚀 جاري تشغيل نظام الهجوم الذكي المرتبط بسيرفر التحكم...{RESET}\n")
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nStopped by user")