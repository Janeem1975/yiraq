#!/usr/bin/env python3
"""
إعداد Genymotion Cloud + سحب توكن AppCheck
============================================

هذا السكربت يساعدك خطوة بخطوة:
1. إعداد Genymotion Cloud (gmsaas)
2. تشغيل جهاز سحابي مروّت
3. تنصيب تطبيق عين العراق
4. تنصيب Frida Server
5. سحب التوكن تلقائياً

المتطلبات:
    pip install gmsaas frida-tools mitmproxy

الاستخدام:
    python cloud_setup.py
"""

import subprocess
import sys
import os
import time
import json
import platform
import shutil
import tempfile
from pathlib import Path

# ألوان الطرفية
R = '\033[91m'
G = '\033[92m'
Y = '\033[93m'
C = '\033[96m'
B = '\033[1m'
RESET = '\033[0m'

PACKAGE_NAME = "com.moi.ayniq"
FRIDA_SCRIPT = os.path.join(os.path.dirname(__file__), "hook_appcheck.js")
BYPASS_SCRIPT = os.path.join(os.path.dirname(__file__), "bypass_emulator.js")
TOKEN_FILE_ON_DEVICE = "/sdcard/appcheck_token.txt"
LOCAL_TOKEN_FILE = os.path.join(os.path.dirname(__file__), "appcheck_token.txt")

# إصدار Frida المطلوب (عدّله حسب إصدار frida-tools عندك)
FRIDA_VERSION = "17.3.2"


def print_banner():
    print(f"""
{C}╔══════════════════════════════════════════════════════╗
║   سحب توكن AppCheck - Genymotion Cloud              ║
║   AppCheck Extractor - Cloud Edition                 ║
╚══════════════════════════════════════════════════════╝{RESET}
""")


# متغيرات عامة
DEVICE_SERIAL = None
INSTANCE_NAME = None  # UUID الجهاز السحابي لإعادة الاتصال


def run_cmd(cmd, capture=True, timeout=30):
    """تشغيل أمر مع التقاط المخرجات"""
    try:
        result = subprocess.run(
            cmd, capture_output=capture, text=True, timeout=timeout
        )
        return result
    except FileNotFoundError:
        return None
    except subprocess.TimeoutExpired:
        return None


def adb_cmd(*args, timeout=30):
    """تشغيل أمر ADB مع تحديد الجهاز تلقائياً بـ -s"""
    cmd = ["adb"]
    if DEVICE_SERIAL:
        cmd.extend(["-s", DEVICE_SERIAL])
    cmd.extend(args)
    return run_cmd(cmd, timeout=timeout)


def _find_serial_from_adb_devices():
    """البحث عن سريال جهاز Genymotion من قائمة adb devices"""
    global DEVICE_SERIAL
    result = run_cmd(["adb", "devices"])
    if not result or not result.stdout:
        return False
    print(f"{C}    الأجهزة المتصلة: {result.stdout.strip()}{RESET}")
    for line in result.stdout.strip().split('\n'):
        line = line.strip()
        if 'localhost:' in line and 'device' in line:
            new_serial = line.split()[0]
            if new_serial != DEVICE_SERIAL:
                print(f"{Y}    السريال تغيّر: {DEVICE_SERIAL} → {new_serial}{RESET}")
            DEVICE_SERIAL = new_serial
            return True
    return False


def _test_adb_alive(retries=3):
    """فحص هل ADB يستجيب مع عدة محاولات"""
    for i in range(retries):
        test = adb_cmd("shell", "echo", "ok", timeout=10)
        if test and test.returncode == 0 and "ok" in test.stdout:
            return True
        if i < retries - 1:
            time.sleep(3)
    return False


def ensure_adb_connected():
    """التحقق من اتصال ADB وإعادة الاتصال إذا انقطع"""
    global DEVICE_SERIAL

    if not DEVICE_SERIAL:
        return True

    # فحص سريع: هل الجهاز متصل ويستجيب؟
    if _test_adb_alive(retries=1):
        return True

    print(f"{Y}[!] اتصال ADB انقطع — جاري إعادة الاتصال...{RESET}")

    # المحاولة 1: gmsaas instances adbconnect
    if INSTANCE_NAME:
        print(f"{C}[*] محاولة 1: gmsaas instances adbconnect {INSTANCE_NAME}{RESET}")
        result = run_cmd(["gmsaas", "instances", "adbconnect", INSTANCE_NAME], timeout=60)
        if result:
            print(f"{C}    stdout: {result.stdout.strip()}{RESET}")
            if result.stderr.strip():
                print(f"{Y}    stderr: {result.stderr.strip()}{RESET}")
        if result and result.returncode == 0:
            # السريال ممكن يتغيّر — نأخذه من adb devices
            time.sleep(3)
            _find_serial_from_adb_devices()
            if _test_adb_alive(retries=3):
                print(f"{G}[+] تم إعادة الاتصال! الجهاز: {DEVICE_SERIAL}{RESET}")
                return True
            else:
                print(f"{Y}[!] gmsaas نجح لكن ADB ما يستجيب بعد{RESET}")

    # المحاولة 2: adb connect مباشر
    if DEVICE_SERIAL and 'localhost:' in DEVICE_SERIAL:
        print(f"{C}[*] محاولة 2: adb connect {DEVICE_SERIAL}{RESET}")
        result = run_cmd(["adb", "connect", DEVICE_SERIAL], timeout=10)
        if result:
            print(f"{C}    {result.stdout.strip()}{RESET}")
        time.sleep(3)
        if _test_adb_alive(retries=3):
            print(f"{G}[+] تم إعادة الاتصال بـ {DEVICE_SERIAL}{RESET}")
            return True

    # المحاولة 3: adb devices لإيجاد أي جهاز localhost جديد
    print(f"{C}[*] محاولة 3: البحث عن الجهاز بـ adb devices...{RESET}")
    if _find_serial_from_adb_devices() and _test_adb_alive(retries=2):
        print(f"{G}[+] لقينا الجهاز: {DEVICE_SERIAL}{RESET}")
        return True

    # كل المحاولات فشلت — طلب يدوي
    print(f"\n{R}[-] فشلت كل محاولات إعادة الاتصال{RESET}")
    print(f"{Y}    شغّل هالأمر بنافذة PowerShell ثانية:{RESET}")
    print(f"{C}    gmsaas instances adbconnect {INSTANCE_NAME or '<UUID>'}{RESET}")
    print(f"{Y}    بعدها ارجع هنا واضغط Enter{RESET}")
    retry = input(f"{Y}اضغط Enter بعد إعادة الاتصال يدوياً (أو 'q' للإلغاء): {RESET}").strip()
    if retry.lower() == 'q':
        return False
    # فحص بعد المحاولة اليدوية
    _find_serial_from_adb_devices()
    if _test_adb_alive(retries=3):
        print(f"{G}[+] الجهاز متصل: {DEVICE_SERIAL}{RESET}")
        return True
    print(f"{R}[-] الجهاز مازال غير متصل{RESET}")
    return False


def check_tool(name, install_cmd=None):
    """التحقق من وجود أداة"""
    if shutil.which(name):
        print(f"{G}[+] {name} موجود{RESET}")
        return True
    else:
        print(f"{R}[-] {name} غير موجود!{RESET}")
        if install_cmd:
            print(f"{Y}    نصّبه: {install_cmd}{RESET}")
        return False


def step_0_check_requirements():
    """الخطوة 0: التحقق من المتطلبات"""
    print(f"\n{B}{'=' * 55}{RESET}")
    print(f"{B}  الخطوة 0: التحقق من المتطلبات{RESET}")
    print(f"{B}{'=' * 55}{RESET}\n")

    all_ok = True

    # Python
    print(f"{C}[*] Python: {sys.version.split()[0]}{RESET}")

    # pip
    if not check_tool("pip3", "تأكد من تنصيب Python بشكل صحيح"):
        if not check_tool("pip"):
            all_ok = False

    # gmsaas
    if not check_tool("gmsaas", "pip install gmsaas"):
        all_ok = False

    # adb
    if not check_tool("adb", "نزّل Android SDK Platform Tools"):
        all_ok = False

    # frida
    try:
        import frida
        print(f"{G}[+] frida موجود (إصدار: {frida.__version__}){RESET}")
        global FRIDA_VERSION
        FRIDA_VERSION = frida.__version__
    except ImportError:
        print(f"{R}[-] frida غير موجود!{RESET}")
        print(f"{Y}    نصّبه: pip install frida-tools{RESET}")
        all_ok = False

    if not all_ok:
        print(f"\n{R}[-] بعض المتطلبات ناقصة! نصّبها وأعد التشغيل{RESET}")
        print(f"\n{Y}أمر التنصيب الشامل:{RESET}")
        print(f"{C}    pip install gmsaas frida-tools mitmproxy{RESET}")
        return False

    print(f"\n{G}[+] كل المتطلبات موجودة!{RESET}")
    return True


def step_1_setup_gmsaas():
    """الخطوة 1: إعداد gmsaas"""
    print(f"\n{B}{'=' * 55}{RESET}")
    print(f"{B}  الخطوة 1: إعداد Genymotion Cloud{RESET}")
    print(f"{B}{'=' * 55}{RESET}\n")

    # التحقق هل مسجّل دخول
    result = run_cmd(["gmsaas", "auth", "whoami"])
    if result and result.returncode == 0 and result.stdout.strip():
        print(f"{G}[+] أنت مسجّل دخول: {result.stdout.strip()}{RESET}")
        return True

    print(f"{C}[*] لازم تسجّل دخول بـ Genymotion Cloud{RESET}")
    print(f"""
{Y}الخطوات:{RESET}
{C}  1. روح على: https://cloud.geny.io/signin{RESET}
{C}  2. سجّل حساب جديد أو سجّل دخول{RESET}
{C}  3. روح على API section بالداشبورد{RESET}
{C}  4. اضغط Create عشان تولّد API Token{RESET}
{C}  5. انسخ التوكن (مهم! ما بتقدر تشوفه مرة ثانية){RESET}
""")

    token = input(f"{Y}الصق API Token هنا: {RESET}").strip()
    if not token:
        print(f"{R}[-] ما أدخلت توكن!{RESET}")
        return False

    result = run_cmd(["gmsaas", "auth", "token", token])
    if result and result.returncode == 0:
        print(f"{G}[+] تم تسجيل الدخول بنجاح!{RESET}")
        return True
    else:
        print(f"{R}[-] فشل تسجيل الدخول! تأكد من التوكن{RESET}")
        if result:
            print(f"{R}    {result.stderr}{RESET}")
        return False


def step_1b_configure_sdk():
    """تهيئة مسار Android SDK لـ gmsaas"""
    # محاولة الكشف التلقائي عن مسار SDK
    sdk_path = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")

    if not sdk_path:
        # أماكن شائعة
        if sys.platform == "win32":
            candidates = [
                os.path.expanduser(r"~\AppData\Local\Android\Sdk"),
                r"C:\Android\Sdk",
                r"C:\Users\Public\Android\Sdk",
            ]
        elif sys.platform == "darwin":
            candidates = [
                os.path.expanduser("~/Library/Android/sdk"),
            ]
        else:
            candidates = [
                os.path.expanduser("~/Android/Sdk"),
                "/usr/lib/android-sdk",
            ]

        for c in candidates:
            if os.path.exists(c):
                sdk_path = c
                break

    if sdk_path and os.path.exists(sdk_path):
        print(f"{G}[+] Android SDK: {sdk_path}{RESET}")
        run_cmd(["gmsaas", "config", "set", "android-sdk-path", sdk_path])
        return True
    else:
        print(f"{Y}[!] ما قدرت أكتشف مسار Android SDK تلقائياً{RESET}")
        sdk_input = input(f"{Y}أدخل مسار Android SDK (أو اضغط Enter لتخطي): {RESET}").strip()
        if sdk_input and os.path.exists(sdk_input):
            run_cmd(["gmsaas", "config", "set", "android-sdk-path", sdk_input])
            print(f"{G}[+] تم تعيين مسار SDK{RESET}")
            return True
        elif sdk_input:
            print(f"{R}[-] المسار غير موجود: {sdk_input}{RESET}")
            return False
        else:
            print(f"{Y}[!] تم التخطي — قد تحتاج تعيينه لاحقاً{RESET}")
            return True


def step_2_start_device():
    """الخطوة 2: تشغيل جهاز سحابي"""
    print(f"\n{B}{'=' * 55}{RESET}")
    print(f"{B}  الخطوة 2: تشغيل جهاز سحابي مروّت{RESET}")
    print(f"{B}{'=' * 55}{RESET}\n")

    # التحقق هل في جهاز شغال
    result = run_cmd(["gmsaas", "instances", "list"], timeout=30)
    if result and result.returncode == 0 and result.stdout.strip():
        lines = result.stdout.strip().split('\n')
        if len(lines) > 1:  # في هيدر + أجهزة
            print(f"{G}[+] في جهاز/أجهزة شغالة:{RESET}")
            print(result.stdout)
            use_existing = input(f"{Y}هل تريد استخدام جهاز موجود؟ (y/n): {RESET}").strip().lower()
            if use_existing == 'y':
                instance_name = input(f"{Y}أدخل اسم الجهاز: {RESET}").strip()
                if instance_name:
                    return instance_name

    # عرض الوصفات المتاحة
    print(f"{C}[*] جاري جلب الأجهزة المتاحة...{RESET}")
    result = run_cmd(["gmsaas", "recipes", "list"], timeout=30)
    if result and result.returncode == 0:
        print(f"\n{C}الأجهزة المتاحة:{RESET}")
        print(result.stdout[:3000])  # أول 3000 حرف
    else:
        print(f"{Y}[!] ما قدرت أجلب القائمة. بنستخدم وصفة افتراضية{RESET}")

    print(f"""
{C}نصيحة: اختر جهاز Android 11 أو 12 (x86_64) — أفضل توافق مع Frida{RESET}
{C}مثال: Google Pixel 3 - Android 11{RESET}
""")

    recipe_uuid = input(f"{Y}أدخل UUID الوصفة (أو اضغط Enter للافتراضي): {RESET}").strip()

    instance_name = f"ayniq-extractor-{int(time.time())}"

    print(f"{C}[*] جاري تشغيل الجهاز ({instance_name})...{RESET}")
    print(f"{Y}    هذا ممكن ياخذ 1-3 دقائق...{RESET}")

    cmd = ["gmsaas", "instances", "start"]
    if recipe_uuid:
        cmd.append(recipe_uuid)
    else:
        # محاولة استخدام وصفة شائعة
        cmd.append("google-pixel-3-11.0")
    cmd.append(instance_name)

    result = run_cmd(cmd, timeout=300)
    if result and result.returncode == 0:
        print(f"{G}[+] تم تشغيل الجهاز: {instance_name}{RESET}")
        return instance_name
    else:
        print(f"{R}[-] فشل تشغيل الجهاز{RESET}")
        if result:
            print(f"{R}    {result.stderr}{RESET}")
        print(f"{Y}    جرب تشغيله يدوياً:{RESET}")
        print(f"{C}    gmsaas recipes list{RESET}")
        print(f"{C}    gmsaas instances start <RECIPE_UUID> my-device{RESET}")
        manual_name = input(f"{Y}أدخل اسم الجهاز اللي شغلته يدوياً (أو Enter للإلغاء): {RESET}").strip()
        return manual_name if manual_name else None


def step_3_connect_adb(instance_name):
    """الخطوة 3: ربط ADB بالجهاز السحابي"""
    global DEVICE_SERIAL, INSTANCE_NAME
    INSTANCE_NAME = instance_name

    print(f"\n{B}{'=' * 55}{RESET}")
    print(f"{B}  الخطوة 3: ربط ADB بالجهاز السحابي{RESET}")
    print(f"{B}{'=' * 55}{RESET}\n")

    print(f"{C}[*] جاري ربط ADB بالجهاز...{RESET}")

    result = run_cmd(["gmsaas", "instances", "adbconnect", instance_name], timeout=60)
    if result and result.returncode == 0:
        # استخراج سريال الجهاز من مخرجات gmsaas
        output = result.stdout.strip()
        print(f"{G}[+] تم ربط ADB!{RESET}")
        print(f"{C}    {output}{RESET}")

        # gmsaas عادة يطبع السريال مثل "localhost:61178"
        for line in output.split('\n'):
            line = line.strip()
            if line.startswith('localhost:') or ':' in line:
                # استخراج السريال (مثل localhost:61178)
                parts = line.split()
                for part in parts:
                    if 'localhost:' in part or (':' in part and part[0].isdigit()):
                        DEVICE_SERIAL = part
                        break
                if DEVICE_SERIAL:
                    break
    else:
        print(f"{R}[-] فشل ربط ADB{RESET}")
        if result:
            print(f"{R}    {result.stderr}{RESET}")
        print(f"{Y}    جرب يدوياً: gmsaas instances adbconnect {instance_name}{RESET}")
        return False

    # التحقق من الاتصال
    time.sleep(2)
    result = run_cmd(["adb", "devices"])
    if result:
        print(f"\n{C}الأجهزة المتصلة:{RESET}")
        print(result.stdout)

        # إذا ما قدرنا نستخرج السريال من gmsaas، نبحث عنه بقائمة الأجهزة
        if not DEVICE_SERIAL:
            for line in result.stdout.strip().split('\n'):
                line = line.strip()
                if 'localhost:' in line and 'device' in line:
                    DEVICE_SERIAL = line.split()[0]
                    break

    if DEVICE_SERIAL:
        print(f"{G}[+] سريال الجهاز المستهدف: {DEVICE_SERIAL}{RESET}")
        print(f"{C}    كل أوامر ADB ستستهدف هذا الجهاز تحديداً{RESET}")
    else:
        print(f"{Y}[!] ما قدرت أحدد سريال الجهاز السحابي تلقائياً{RESET}")
        serial_input = input(f"{Y}أدخل سريال الجهاز (مثل localhost:61178): {RESET}").strip()
        if serial_input:
            DEVICE_SERIAL = serial_input
        else:
            print(f"{Y}[!] سيتم استخدام ADB بدون تحديد جهاز — قد يستهدف جهاز خطأ{RESET}")

    return True


def step_4_install_app():
    """الخطوة 4: تنصيب تطبيق عين العراق"""
    global PACKAGE_NAME

    print(f"\n{B}{'=' * 55}{RESET}")
    print(f"{B}  الخطوة 4: تنصيب تطبيق عين العراق{RESET}")
    print(f"{B}{'=' * 55}{RESET}\n")

    # عرض التطبيقات المنصبة عشان المستخدم يلاقي اسم الحزمة
    print(f"{C}[*] جاري جلب قائمة التطبيقات المنصبة...{RESET}")
    result = adb_cmd("shell", "pm", "list", "packages", "-3")  # -3 = third party only
    installed_packages = []
    if result and result.returncode == 0 and result.stdout.strip():
        lines = result.stdout.strip().split('\n')
        installed_packages = [l.replace('package:', '').strip() for l in lines if l.strip()]
        print(f"{G}[+] التطبيقات المنصبة ({len(installed_packages)}):{RESET}")
        for pkg in installed_packages:
            marker = " ← عين العراق" if "ayniq" in pkg.lower() or "moi" in pkg.lower() else ""
            print(f"{C}    • {pkg}{G}{marker}{RESET}")
    else:
        print(f"{Y}[!] ما قدرت أجلب قائمة التطبيقات{RESET}")

    # التحقق هل التطبيق موجود
    result = adb_cmd("shell", "pm", "list", "packages", PACKAGE_NAME)
    if result and PACKAGE_NAME in result.stdout:
        print(f"\n{G}[+] تطبيق عين العراق ({PACKAGE_NAME}) موجود!{RESET}")
        return True

    # التطبيق مش موجود — يمكن باسم ثاني؟
    ayniq_matches = [p for p in installed_packages if "ayniq" in p.lower() or "moi" in p.lower()]
    if ayniq_matches:
        print(f"\n{Y}[!] التطبيق مش موجود بالاسم {PACKAGE_NAME}{RESET}")
        print(f"{G}[+] بس لقيت تطبيقات مشابهة:{RESET}")
        for i, pkg in enumerate(ayniq_matches, 1):
            print(f"{C}    {i}. {pkg}{RESET}")
        choice = input(f"{Y}اختر رقم التطبيق (أو Enter لتخطي): {RESET}").strip()
        if choice.isdigit() and 1 <= int(choice) <= len(ayniq_matches):
            PACKAGE_NAME = ayniq_matches[int(choice) - 1]
            print(f"{G}[+] تم تغيير اسم الحزمة إلى: {PACKAGE_NAME}{RESET}")
            return True

    print(f"\n{Y}[!] التطبيق ({PACKAGE_NAME}) مش منصب{RESET}")
    print(f"""
{Y}الخيارات:{RESET}
{C}  1. نزّل التطبيق من Google Play مباشرة على الجهاز السحابي{RESET}
{C}     (إذا الجهاز يدعم Google Play){RESET}
{C}  2. إذا عندك ملف APK:{RESET}
{C}     adb {'-s ' + DEVICE_SERIAL + ' ' if DEVICE_SERIAL else ''}install path/to/ayniq.apk{RESET}
{C}  3. نزّل الـ APK من:{RESET}
{C}     - https://apkpure.com (ابحث عن عين العراق){RESET}
{C}     - https://apkmirror.com{RESET}
{C}  4. إذا التطبيق باسم حزمة ثاني — أدخله يدوياً{RESET}
""")

    user_input = input(f"{Y}أدخل مسار APK / اسم حزمة / Enter لتخطي: {RESET}").strip()
    if user_input:
        user_input = user_input.strip('"').strip("'")
        # هل هو اسم حزمة؟
        if '.' in user_input and not os.path.exists(user_input) and '/' not in user_input and '\\' not in user_input:
            PACKAGE_NAME = user_input
            print(f"{G}[+] تم تغيير اسم الحزمة إلى: {PACKAGE_NAME}{RESET}")
            # تحقق
            result = adb_cmd("shell", "pm", "list", "packages", PACKAGE_NAME)
            if result and PACKAGE_NAME in result.stdout:
                print(f"{G}[+] التطبيق موجود!{RESET}")
                return True
            else:
                print(f"{Y}[!] الحزمة غير موجودة — بنكمل على أمل إنك تنصبها{RESET}")
                return True  # نكمل عشان يقدر ينصب من Google Play
        # هل هو ملف APK؟
        elif os.path.exists(user_input):
            print(f"{C}[*] جاري تنصيب التطبيق...{RESET}")
            ensure_adb_connected()
            result = adb_cmd("install", user_input, timeout=120)
            if result and result.returncode == 0:
                print(f"{G}[+] تم تنصيب التطبيق بنجاح!{RESET}")
                return True
            else:
                print(f"{R}[-] فشل التنصيب{RESET}")
                if result:
                    print(f"{R}    {result.stderr}{RESET}")
                return False
        else:
            print(f"{R}[-] الملف غير موجود: {user_input}{RESET}")
            return False
    else:
        # تخطي — التحقق مرة أخيرة
        result = adb_cmd("shell", "pm", "list", "packages", PACKAGE_NAME)
        if result and PACKAGE_NAME in result.stdout:
            print(f"{G}[+] تطبيق عين العراق موجود الآن!{RESET}")
            return True

        print(f"{Y}[!] التطبيق مش منصب بعد{RESET}")
        print(f"{Y}    نصبه من Google Play أو بملف APK ثم اضغط Enter{RESET}")
        input(f"{Y}اضغط Enter بعد التنصيب... {RESET}")

        result = adb_cmd("shell", "pm", "list", "packages", PACKAGE_NAME)
        if result and PACKAGE_NAME in result.stdout:
            print(f"{G}[+] تطبيق عين العراق موجود!{RESET}")
            return True
        print(f"{R}[-] التطبيق مازال غير موجود{RESET}")
        return False


def step_5_install_frida_server():
    """الخطوة 5: تنصيب Frida Server على الجهاز"""
    print(f"\n{B}{'=' * 55}{RESET}")
    print(f"{B}  الخطوة 5: تنصيب Frida Server على الجهاز{RESET}")
    print(f"{B}{'=' * 55}{RESET}\n")

    # التحقق هل Frida Server شغال
    result = adb_cmd("shell", "su", "-c", "ps | grep frida-server")
    if result and "frida-server" in result.stdout:
        print(f"{G}[+] Frida Server شغال مسبقاً!{RESET}")
        return True

    # معرفة معمارية الجهاز
    result = adb_cmd("shell", "getprop", "ro.product.cpu.abi")
    arch = result.stdout.strip() if result else ""

    # إذا فشل الاستعلام الأول، جرب طرق بديلة
    if not arch:
        result = adb_cmd("shell", "getprop", "ro.product.cpu.abilist")
        if result and result.stdout.strip():
            # يرجع قائمة مثل "arm64-v8a,armeabi-v7a,armeabi" — ناخذ الأول
            arch = result.stdout.strip().split(',')[0]

    if not arch:
        result = adb_cmd("shell", "uname", "-m")
        if result and result.stdout.strip():
            uname_arch = result.stdout.strip()
            uname_map = {
                "aarch64": "arm64-v8a",
                "armv7l": "armeabi-v7a",
                "x86_64": "x86_64",
                "i686": "x86",
                "i386": "x86",
            }
            arch = uname_map.get(uname_arch, uname_arch)

    if not arch:
        print(f"{Y}[!] ما قدرت أكتشف المعمارية تلقائياً{RESET}")
        print(f"{Y}    اختر المعمارية:{RESET}")
        print(f"{C}    1. arm64  (معظم أجهزة Genymotion الحديثة){RESET}")
        print(f"{C}    2. x86_64{RESET}")
        print(f"{C}    3. x86{RESET}")
        print(f"{C}    4. arm{RESET}")
        arch_choice = input(f"{Y}اختيارك (1-4): {RESET}").strip()
        arch_fallback = {"1": "arm64-v8a", "2": "x86_64", "3": "x86", "4": "armeabi-v7a"}
        arch = arch_fallback.get(arch_choice, "arm64-v8a")

    print(f"{C}[*] معمارية الجهاز: {arch}{RESET}")

    # تحديد اسم الملف
    arch_map = {
        "x86_64": "x86_64",
        "x86": "x86",
        "arm64-v8a": "arm64",
        "armeabi-v7a": "arm",
    }
    frida_arch = arch_map.get(arch, arch)

    # التحقق هل الملف موجود محلياً
    frida_filename = f"frida-server-{FRIDA_VERSION}-android-{frida_arch}"
    frida_xz = f"{frida_filename}.xz"
    frida_local = os.path.join(tempfile.gettempdir(), frida_filename)

    if not os.path.exists(frida_local):
        # تنزيل frida-server
        download_url = f"https://github.com/frida/frida/releases/download/{FRIDA_VERSION}/{frida_xz}"
        print(f"{C}[*] جاري تنزيل Frida Server...{RESET}")
        print(f"{C}    {download_url}{RESET}")

        try:
            import urllib.request
            xz_path = os.path.join(tempfile.gettempdir(), frida_xz)
            urllib.request.urlretrieve(download_url, xz_path)
            print(f"{G}[+] تم التنزيل!{RESET}")

            # فك الضغط
            print(f"{C}[*] جاري فك الضغط...{RESET}")
            if sys.platform == "win32":
                # على Windows نستخدم 7z أو Python lzma
                import lzma
                with lzma.open(xz_path) as f_in:
                    with open(frida_local, 'wb') as f_out:
                        f_out.write(f_in.read())
            else:
                subprocess.run(["unxz", "-k", xz_path], check=True)
                # الملف المفكوك يكون بدون .xz
                unxz_path = xz_path.replace(".xz", "")
                if os.path.exists(unxz_path) and unxz_path != frida_local:
                    shutil.move(unxz_path, frida_local)

            print(f"{G}[+] تم فك الضغط!{RESET}")
        except Exception as e:
            print(f"{R}[-] فشل التنزيل: {e}{RESET}")
            print(f"")
            print(f"{Y}    === تعليمات التنزيل اليدوي ==={RESET}")
            print(f"{C}    1. افتح هذا الرابط بالمتصفح:{RESET}")
            print(f"{B}       https://github.com/frida/frida/releases/download/{FRIDA_VERSION}/{frida_xz}{RESET}")
            print(f"{C}    2. بعد التنزيل، فك الضغط بـ 7-Zip:{RESET}")
            print(f"{C}       كلك يمين على الملف → 7-Zip → Extract Here{RESET}")
            print(f"{C}    3. المفروض يطلع ملف اسمه: {frida_filename}{RESET}")
            print(f"{C}       (بدون .xz بالآخر){RESET}")
            print(f"{C}    4. الصق مسار الملف المفكوك هنا{RESET}")
            print(f"")
            print(f"{Y}    ملاحظة: الملف لازم يكون المفكوك (بدون .xz){RESET}")
            print(f"{Y}    إذا أعطيت ملف .xz بنفكه تلقائياً{RESET}")
            manual = input(f"{Y}أدخل مسار الملف (أو Enter للإلغاء): {RESET}").strip()
            if manual:
                manual = manual.strip('"').strip("'")
                if not os.path.exists(manual):
                    print(f"{R}[-] الملف غير موجود: {manual}{RESET}")
                    return False
                # إذا الملف بصيغة .xz، نفكه
                if manual.endswith('.xz'):
                    print(f"{C}[*] جاري فك الضغط...{RESET}")
                    try:
                        import lzma
                        with lzma.open(manual) as f_in:
                            with open(frida_local, 'wb') as f_out:
                                f_out.write(f_in.read())
                        print(f"{G}[+] تم فك الضغط!{RESET}")
                    except Exception as ex:
                        print(f"{R}[-] فشل فك الضغط: {ex}{RESET}")
                        return False
                else:
                    frida_local = manual
            else:
                return False

    # التحقق من حجم الملف (frida-server عادة > 10MB)
    file_size = os.path.getsize(frida_local)
    if file_size < 1_000_000:  # أقل من 1MB — غالباً ملف خطأ
        print(f"{R}[-] حجم الملف صغير جداً ({file_size} bytes) — غالباً مش ملف frida-server الصحيح{RESET}")
        print(f"{Y}    الملف الصحيح حجمه أكبر من 10MB بعد فك الضغط{RESET}")
        return False

    print(f"{G}[+] حجم الملف: {file_size / 1024 / 1024:.1f} MB — يبدو صحيح{RESET}")

    # إيقاف أي frida-server قديم
    adb_cmd("shell", "su", "-c", "pkill -f frida-server 2>/dev/null")
    time.sleep(1)

    # إعادة اتصال ADB (قد ينقطع أثناء التنزيل اليدوي)
    if not ensure_adb_connected():
        print(f"{R}[-] ADB غير متصل — ما نقدر ندفع Frida Server{RESET}")
        return False

    # دفع Frida Server للجهاز
    print(f"{C}[*] جاري دفع Frida Server للجهاز ({DEVICE_SERIAL})...{RESET}")
    result = adb_cmd("push", frida_local, "/data/local/tmp/frida-server", timeout=120)
    if not result or result.returncode != 0:
        err_msg = ""
        if result:
            err_msg = (result.stderr or "").strip() or (result.stdout or "").strip()
        print(f"{R}[-] فشل دفع الملف: {err_msg}{RESET}")
        # محاولة إعادة اتصال ثم إعادة المحاولة
        print(f"{Y}[!] جاري إعادة اتصال ADB ومحاولة ثانية...{RESET}")
        if ensure_adb_connected():
            result = adb_cmd("push", frida_local, "/data/local/tmp/frida-server", timeout=120)
            if result and result.returncode == 0:
                print(f"{G}[+] تم دفع الملف بعد إعادة الاتصال!{RESET}")
            else:
                print(f"{R}[-] فشل مرة ثانية{RESET}")
                return False
        else:
            return False

    print(f"{G}[+] تم دفع الملف!{RESET}")

    # تعيين الصلاحيات
    adb_cmd("shell", "su", "-c", "chmod 755 /data/local/tmp/frida-server")

    # التحقق من وجود الملف على الجهاز
    result = adb_cmd("shell", "su", "-c", "ls -la /data/local/tmp/frida-server")
    if result:
        print(f"{C}[*] {result.stdout.strip()}{RESET}")

    # إعداد SELinux (مهم لـ Genymotion Cloud)
    print(f"{C}[*] جاري تعطيل SELinux مؤقتاً (مطلوب لـ Frida)...{RESET}")
    adb_cmd("shell", "su", "-c", "setenforce 0 2>/dev/null")

    # تشغيل Frida Server
    print(f"{C}[*] جاري تشغيل Frida Server...{RESET}")
    adb_popen_cmd = ["adb"]
    if DEVICE_SERIAL:
        adb_popen_cmd.extend(["-s", DEVICE_SERIAL])
    adb_popen_cmd.extend(["shell", "su", "-c", "/data/local/tmp/frida-server -D &"])
    subprocess.Popen(
        adb_popen_cmd,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL
    )
    time.sleep(4)

    # التحقق — محاولات متعددة
    for attempt in range(3):
        result = adb_cmd("shell", "su", "-c", "ps | grep frida-server")
        if result and "frida-server" in result.stdout:
            print(f"{G}[+] Frida Server شغال!{RESET}")

            # اختبار اتصال Frida
            print(f"{C}[*] جاري اختبار اتصال Frida...{RESET}")
            frida_test_cmd = ["frida-ps"]
            if DEVICE_SERIAL:
                frida_test_cmd.extend(["-D", DEVICE_SERIAL])
            else:
                frida_test_cmd.append("-U")
            test_result = run_cmd(frida_test_cmd, timeout=10)
            if test_result and test_result.returncode == 0:
                print(f"{G}[+] Frida متصل بالجهاز بنجاح!{RESET}")
                return True
            else:
                print(f"{Y}[!] Frida Server شغال لكن الاتصال فشل{RESET}")
                if test_result:
                    print(f"{R}    {test_result.stderr}{RESET}")
                # ننتظر ونجرب مرة ثانية
                time.sleep(2)
                continue

        if attempt < 2:
            print(f"{Y}[!] محاولة {attempt + 1}/3 — ننتظر...{RESET}")
            time.sleep(3)

    # فشل — نعطي تعليمات يدوية
    serial_flag = f"-s {DEVICE_SERIAL} " if DEVICE_SERIAL else ""
    print(f"{R}[-] Frida Server لم يشتغل!{RESET}")
    print(f"{Y}    جرب يدوياً:{RESET}")
    print(f"{C}    1. adb {serial_flag}shell su -c 'setenforce 0'{RESET}")
    print(f"{C}    2. adb {serial_flag}shell su -c '/data/local/tmp/frida-server -D &'{RESET}")
    print(f"{C}    3. frida-ps {'-D ' + DEVICE_SERIAL if DEVICE_SERIAL else '-U'}{RESET}")
    print(f"{Y}    إذا الأمر الثالث طبع قائمة عمليات، يعني شغال{RESET}")

    retry = input(f"{Y}هل شغّلته يدوياً وتريد الاستمرار؟ (y/n): {RESET}").strip().lower()
    if retry == 'y':
        # تحقق أخير
        frida_test_cmd = ["frida-ps"]
        if DEVICE_SERIAL:
            frida_test_cmd.extend(["-D", DEVICE_SERIAL])
        else:
            frida_test_cmd.append("-U")
        test_result = run_cmd(frida_test_cmd, timeout=10)
        if test_result and test_result.returncode == 0:
            print(f"{G}[+] Frida متصل بنجاح!{RESET}")
            return True
        else:
            print(f"{R}[-] مازال غير متصل{RESET}")
            return False
    return False


def step_6_extract_token():
    """الخطوة 6: سحب التوكن"""
    print(f"\n{B}{'=' * 55}{RESET}")
    print(f"{B}  الخطوة 6: سحب التوكن من تطبيق عين العراق{RESET}")
    print(f"{B}{'=' * 55}{RESET}\n")

    # اختيار السكربت — تجاوز كشف المحاكي تلقائياً على الأجهزة السحابية
    use_bypass = os.path.exists(BYPASS_SCRIPT)
    script_to_use = BYPASS_SCRIPT if use_bypass else FRIDA_SCRIPT

    if not os.path.exists(script_to_use):
        print(f"{R}[-] ملف السكربت غير موجود!{RESET}")
        return False

    if use_bypass:
        print(f"{G}[+] سيتم استخدام سكربت تجاوز كشف المحاكي تلقائياً{RESET}")
        print(f"{C}[*] هذا يمنع التطبيق من كشف الجهاز السحابي كمحاكي{RESET}")
    else:
        print(f"{Y}[!] سكربت تجاوز المحاكي غير موجود — سيتم استخدام السكربت العادي{RESET}")

    # التحقق من اتصال ADB
    ensure_adb_connected()

    # إيقاف التطبيق أولاً لضمان تحميل التجاوز من البداية
    print(f"{C}[*] جاري إيقاف التطبيق (إذا شغال)...{RESET}")
    adb_cmd("shell", "am", "force-stop", PACKAGE_NAME)
    time.sleep(1)

    print(f"{C}[*] جاري تشغيل التطبيق مع Frida...{RESET}")
    # استخدام -D بدل -U لتحديد الجهاز بالسريال
    if DEVICE_SERIAL:
        frida_cmd = ["frida", "-D", DEVICE_SERIAL, "-f", PACKAGE_NAME, "-l", script_to_use]
        print(f"{C}[*] Frida يستهدف الجهاز: {DEVICE_SERIAL}{RESET}")
    else:
        frida_cmd = ["frida", "-U", "-f", PACKAGE_NAME, "-l", script_to_use]

    print(f"{C}[*] سوي أي عملية بالتطبيق عشان يرسل طلب ويظهر التوكن{RESET}")
    print(f"{Y}[*] اضغط Ctrl+C لإيقاف المراقبة وسحب التوكن{RESET}\n")

    token_found = False
    try:
        process = subprocess.Popen(
            frida_cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True
        )

        for line in process.stdout:
            print(line, end="")
            if "APP CHECK TOKEN" in line or "eyJ" in line:
                token_found = True
                print(f"\n{G}[+] تم رصد التوكن!{RESET}")

    except KeyboardInterrupt:
        print(f"\n{Y}[*] تم إيقاف المراقبة{RESET}")
        try:
            process.terminate()
        except Exception:
            pass

    # محاولة سحب الملف
    time.sleep(1)
    result = adb_cmd("pull", TOKEN_FILE_ON_DEVICE, LOCAL_TOKEN_FILE)
    if result and result.returncode == 0 and os.path.exists(LOCAL_TOKEN_FILE):
        with open(LOCAL_TOKEN_FILE, "r") as f:
            token = f.read().strip()
        if token:
            print(f"\n{G}{'=' * 60}{RESET}")
            print(f"{G}  تم سحب التوكن بنجاح!{RESET}")
            print(f"{G}{'=' * 60}{RESET}")
            print(f"\n{B}التوكن (أول 100 حرف):{RESET}")
            print(f"{token[:100]}...")
            print(f"\n{C}التوكن الكامل محفوظ في:{RESET}")
            print(f"{C}  {LOCAL_TOKEN_FILE}{RESET}")
            print(f"{G}{'=' * 60}{RESET}")

            # نسخ للحافظة
            try:
                if sys.platform == "win32":
                    subprocess.run(["clip"], input=token.encode(), check=True)
                    print(f"{G}[+] تم نسخ التوكن للحافظة!{RESET}")
                elif sys.platform == "darwin":
                    subprocess.run(["pbcopy"], input=token.encode(), check=True)
                    print(f"{G}[+] تم نسخ التوكن للحافظة!{RESET}")
            except Exception:
                pass

            return True

    if not token_found:
        print(f"\n{Y}[-] ما تم العثور على التوكن{RESET}")
        print(f"{Y}    تأكد من:{RESET}")
        print(f"{Y}    1. التطبيق فتح بشكل طبيعي (بدون crash){RESET}")
        print(f"{Y}    2. سويت عملية تتطلب اتصال بالسيرفر{RESET}")
        print(f"{Y}    3. Frida Server شغال بصلاحيات root{RESET}")

    return False


def step_7_cleanup(instance_name):
    """الخطوة 7: إيقاف الجهاز (اختياري)"""
    print(f"\n{B}{'=' * 55}{RESET}")
    print(f"{B}  الخطوة 7: تنظيف{RESET}")
    print(f"{B}{'=' * 55}{RESET}\n")

    stop = input(f"{Y}هل تريد إيقاف الجهاز السحابي (لتوفير التكلفة)؟ (y/n): {RESET}").strip().lower()
    if stop == 'y' and instance_name:
        print(f"{C}[*] جاري إيقاف الجهاز...{RESET}")
        # نحتاج UUID مش الاسم
        result = run_cmd(["gmsaas", "instances", "list"])
        if result:
            print(f"{Y}[!] أوقف الجهاز يدوياً من الداشبورد:{RESET}")
            print(f"{C}    https://cloud.geny.io{RESET}")
            print(f"{C}    أو: gmsaas instances stop <INSTANCE_UUID>{RESET}")
    else:
        print(f"{Y}[!] الجهاز مازال شغال — أوقفه من الداشبورد لتوفير التكلفة{RESET}")
        print(f"{C}    https://cloud.geny.io{RESET}")


def main():
    print_banner()

    print(f"{C}هذا السكربت يساعدك تسحب توكن AppCheck من تطبيق عين العراق{RESET}")
    print(f"{C}باستخدام جهاز أندرويد سحابي مروّت من Genymotion Cloud{RESET}")
    print(f"\n{Y}التكلفة: ~$0.05/دقيقة (Pay-as-you-go){RESET}")
    print(f"{Y}الوقت المتوقع: 10-15 دقيقة{RESET}\n")

    choice = input(f"{Y}هل تريد البدء؟ (y/n): {RESET}").strip().lower()
    if choice != 'y':
        print(f"{C}مع السلامة!{RESET}")
        return

    # الخطوة 0: المتطلبات
    if not step_0_check_requirements():
        return

    # الخطوة 1: إعداد gmsaas
    if not step_1_setup_gmsaas():
        return

    # تهيئة SDK
    step_1b_configure_sdk()

    # الخطوة 2: تشغيل جهاز
    instance_name = step_2_start_device()
    if not instance_name:
        return

    # الخطوة 3: ربط ADB
    if not step_3_connect_adb(instance_name):
        return

    # الخطوة 4: تنصيب التطبيق
    step_4_install_app()

    # الخطوة 5: تنصيب Frida
    if not step_5_install_frida_server():
        print(f"\n{R}[-] فشل تنصيب/تشغيل Frida Server — ما نقدر نكمل{RESET}")
        print(f"{Y}    لازم Frida Server يكون شغال على الجهاز عشان نسحب التوكن{RESET}")
        step_7_cleanup(instance_name)
        return

    # الخطوة 6: سحب التوكن
    step_6_extract_token()

    # الخطوة 7: تنظيف
    step_7_cleanup(instance_name)

    print(f"\n{C}انتهينا! إذا واجهت مشاكل راجع README.md{RESET}")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print(f"\n{Y}تم الإيقاف{RESET}")
