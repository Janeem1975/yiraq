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
    print(f"\n{B}{'=' * 55}{RESET}")
    print(f"{B}  الخطوة 3: ربط ADB بالجهاز السحابي{RESET}")
    print(f"{B}{'=' * 55}{RESET}\n")

    print(f"{C}[*] جاري ربط ADB بالجهاز...{RESET}")

    result = run_cmd(["gmsaas", "instances", "adbconnect", instance_name], timeout=60)
    if result and result.returncode == 0:
        print(f"{G}[+] تم ربط ADB!{RESET}")
        print(f"{C}    {result.stdout.strip()}{RESET}")
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

    return True


def step_4_install_app():
    """الخطوة 4: تنصيب تطبيق عين العراق"""
    print(f"\n{B}{'=' * 55}{RESET}")
    print(f"{B}  الخطوة 4: تنصيب تطبيق عين العراق{RESET}")
    print(f"{B}{'=' * 55}{RESET}\n")

    # التحقق هل التطبيق موجود
    result = run_cmd(["adb", "shell", "pm", "list", "packages", PACKAGE_NAME])
    if result and PACKAGE_NAME in result.stdout:
        print(f"{G}[+] تطبيق عين العراق موجود مسبقاً!{RESET}")
        return True

    print(f"{C}[*] التطبيق مش منصب — لازم تنصبه{RESET}")
    print(f"""
{Y}الخيارات:{RESET}
{C}  1. إذا عندك ملف APK:{RESET}
{C}     adb install path/to/ayniq.apk{RESET}
{C}  2. نزّل الـ APK من:{RESET}
{C}     - https://apkpure.com (ابحث عن عين العراق){RESET}
{C}     - https://apkmirror.com{RESET}
""")

    apk_path = input(f"{Y}أدخل مسار ملف APK (أو Enter لتخطي): {RESET}").strip()
    if apk_path:
        # إزالة علامات التنصيص إذا موجودة
        apk_path = apk_path.strip('"').strip("'")
        if os.path.exists(apk_path):
            print(f"{C}[*] جاري تنصيب التطبيق...{RESET}")
            result = run_cmd(["adb", "install", apk_path], timeout=120)
            if result and result.returncode == 0:
                print(f"{G}[+] تم تنصيب التطبيق بنجاح!{RESET}")
                return True
            else:
                print(f"{R}[-] فشل التنصيب{RESET}")
                if result:
                    print(f"{R}    {result.stderr}{RESET}")
                return False
        else:
            print(f"{R}[-] الملف غير موجود: {apk_path}{RESET}")
            return False
    else:
        print(f"{Y}[!] تم التخطي — نصّب التطبيق يدوياً وأعد التشغيل{RESET}")
        return False


def step_5_install_frida_server():
    """الخطوة 5: تنصيب Frida Server على الجهاز"""
    print(f"\n{B}{'=' * 55}{RESET}")
    print(f"{B}  الخطوة 5: تنصيب Frida Server على الجهاز{RESET}")
    print(f"{B}{'=' * 55}{RESET}\n")

    # التحقق هل Frida Server شغال
    result = run_cmd(["adb", "shell", "su", "-c", "ps | grep frida-server"])
    if result and "frida-server" in result.stdout:
        print(f"{G}[+] Frida Server شغال مسبقاً!{RESET}")
        return True

    # معرفة معمارية الجهاز
    result = run_cmd(["adb", "shell", "getprop", "ro.product.cpu.abi"])
    if not result:
        print(f"{R}[-] ما قدرت أعرف معمارية الجهاز{RESET}")
        return False

    arch = result.stdout.strip()
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
            print(f"{Y}    نزّله يدوياً من:{RESET}")
            print(f"{C}    https://github.com/frida/frida/releases/tag/{FRIDA_VERSION}{RESET}")
            print(f"{C}    اختر: {frida_xz}{RESET}")
            manual = input(f"{Y}أدخل مسار الملف بعد التنزيل (أو Enter للإلغاء): {RESET}").strip()
            if manual and os.path.exists(manual):
                frida_local = manual
            else:
                return False

    # دفع Frida Server للجهاز
    print(f"{C}[*] جاري دفع Frida Server للجهاز...{RESET}")
    result = run_cmd(["adb", "push", frida_local, "/data/local/tmp/frida-server"], timeout=120)
    if not result or result.returncode != 0:
        print(f"{R}[-] فشل دفع الملف{RESET}")
        return False

    # تعيين الصلاحيات
    run_cmd(["adb", "shell", "su", "-c", "chmod 755 /data/local/tmp/frida-server"])

    # تشغيل Frida Server
    print(f"{C}[*] جاري تشغيل Frida Server...{RESET}")
    subprocess.Popen(
        ["adb", "shell", "su", "-c", "/data/local/tmp/frida-server -D &"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL
    )
    time.sleep(3)

    # التحقق
    result = run_cmd(["adb", "shell", "su", "-c", "ps | grep frida-server"])
    if result and "frida-server" in result.stdout:
        print(f"{G}[+] Frida Server شغال!{RESET}")
        return True
    else:
        print(f"{Y}[!] ما قدرت أتحقق — جرب يدوياً:{RESET}")
        print(f"{C}    adb shell su -c '/data/local/tmp/frida-server &'{RESET}")
        return True  # نكمل على أمل إنه شغال


def step_6_extract_token():
    """الخطوة 6: سحب التوكن"""
    print(f"\n{B}{'=' * 55}{RESET}")
    print(f"{B}  الخطوة 6: سحب التوكن من تطبيق عين العراق{RESET}")
    print(f"{B}{'=' * 55}{RESET}\n")

    if not os.path.exists(FRIDA_SCRIPT):
        print(f"{R}[-] ملف hook_appcheck.js غير موجود!{RESET}")
        return False

    # التحقق هل التطبيق شغال
    result = run_cmd(["adb", "shell", "pidof", PACKAGE_NAME])
    app_running = result and bool(result.stdout.strip())

    if app_running:
        print(f"{G}[+] التطبيق شغال — جاري الربط...{RESET}")
        frida_cmd = ["frida", "-U", PACKAGE_NAME, "-l", FRIDA_SCRIPT]
    else:
        print(f"{C}[*] جاري تشغيل التطبيق مع Frida...{RESET}")
        frida_cmd = ["frida", "-U", "-f", PACKAGE_NAME, "-l", FRIDA_SCRIPT]

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
    result = run_cmd(["adb", "pull", TOKEN_FILE_ON_DEVICE, LOCAL_TOKEN_FILE])
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
    step_5_install_frida_server()

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
