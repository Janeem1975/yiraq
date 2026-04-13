#!/usr/bin/env python3
"""
أداة سحب توكن AppCheck من تطبيق عين العراق
============================================

هذا السكربت يوفر 3 طرق لسحب التوكن:
1. Frida - يربط مع التطبيق مباشرة ويسحب التوكن (يحتاج روت)
2. mitmproxy - يعترض الترافيك ويلتقط التوكن
3. ADB مباشر - يسحب التوكن من ملف محفوظ على الجهاز

المتطلبات:
    pip install frida-tools mitmproxy

الاستخدام:
    python extract_token.py
"""

import subprocess
import sys
import os
import time
import json
from pathlib import Path

PACKAGE_NAME = "com.moi.ayniq"
FRIDA_SCRIPT = os.path.join(os.path.dirname(__file__), "hook_appcheck.js")
TOKEN_FILE_ON_DEVICE = "/sdcard/appcheck_token.txt"
LOCAL_TOKEN_FILE = "appcheck_token.txt"

# ألوان الطرفية
R = '\033[91m'
G = '\033[92m'
Y = '\033[93m'
C = '\033[96m'
B = '\033[1m'
RESET = '\033[0m'


def print_banner():
    print(f"""
{C}╔══════════════════════════════════════════════════╗
║   أداة سحب توكن AppCheck - عين العراق            ║
║   Eye of Iraq - AppCheck Token Extractor          ║
╚══════════════════════════════════════════════════╝{RESET}
""")


def check_adb():
    """التحقق من وجود ADB"""
    try:
        result = subprocess.run(["adb", "devices"], capture_output=True, text=True, timeout=10)
        lines = [l.strip() for l in result.stdout.strip().split('\n')[1:] if l.strip() and 'device' in l]
        if lines:
            device = lines[0].split('\t')[0]
            print(f"{G}[+] جهاز متصل: {device}{RESET}")
            return True
        else:
            print(f"{R}[-] لا يوجد جهاز متصل! وصّل المحاكي أو الجهاز بـ ADB{RESET}")
            print(f"{Y}    للمحاكي LDPlayer: تأكد من تفعيل ADB بالإعدادات{RESET}")
            print(f"{Y}    جرب: adb connect 127.0.0.1:5555{RESET}")
            return False
    except FileNotFoundError:
        print(f"{R}[-] ADB غير موجود! نزّله من Android SDK{RESET}")
        return False


def check_app_installed():
    """التحقق من وجود تطبيق عين العراق"""
    try:
        result = subprocess.run(
            ["adb", "shell", "pm", "list", "packages", PACKAGE_NAME],
            capture_output=True, text=True, timeout=10
        )
        if PACKAGE_NAME in result.stdout:
            print(f"{G}[+] تطبيق عين العراق موجود{RESET}")
            return True
        else:
            print(f"{R}[-] تطبيق عين العراق غير منصب على الجهاز{RESET}")
            return False
    except Exception as e:
        print(f"{R}[-] خطأ: {e}{RESET}")
        return False


def method_frida():
    """الطريقة 1: سحب التوكن باستخدام Frida"""
    print(f"\n{B}=== الطريقة 1: Frida (اعتراض مباشر) ==={RESET}\n")

    try:
        import frida
    except ImportError:
        print(f"{R}[-] Frida غير منصب!{RESET}")
        print(f"{Y}    نصّبه: pip install frida-tools{RESET}")
        return False

    if not os.path.exists(FRIDA_SCRIPT):
        print(f"{R}[-] ملف السكربت غير موجود: {FRIDA_SCRIPT}{RESET}")
        return False

    # التحقق من frida-server على الجهاز
    print(f"{C}[*] التحقق من frida-server على الجهاز...{RESET}")
    result = subprocess.run(
        ["adb", "shell", "su", "-c", "ps | grep frida"],
        capture_output=True, text=True, timeout=10
    )

    if "frida" not in result.stdout.lower():
        print(f"{Y}[!] frida-server غير شغال على الجهاز{RESET}")
        print(f"{Y}    الخطوات:{RESET}")
        print(f"{Y}    1. نزّل frida-server من: https://github.com/frida/frida/releases{RESET}")
        print(f"{Y}       (اختر الإصدار المناسب لمعمارية المحاكي - غالباً x86 أو x86_64){RESET}")
        print(f"{Y}    2. ادفعه للجهاز:{RESET}")
        print(f"{Y}       adb push frida-server /data/local/tmp/{RESET}")
        print(f"{Y}       adb shell chmod 755 /data/local/tmp/frida-server{RESET}")
        print(f"{Y}    3. شغّله:{RESET}")
        print(f"{Y}       adb shell su -c '/data/local/tmp/frida-server &'{RESET}")
        print(f"{Y}    4. أعد تشغيل هذا السكربت{RESET}")
        return False

    print(f"{G}[+] frida-server شغال!{RESET}")
    print(f"{C}[*] جاري ربط Frida مع تطبيق عين العراق...{RESET}")
    print(f"{C}[*] افتح التطبيق وسوي أي عملية (مثل OTP أو حجز) عشان يرسل طلب ويظهر التوكن{RESET}")
    print(f"{Y}[*] اضغط Ctrl+C لإيقاف المراقبة{RESET}\n")

    try:
        # تشغيل frida مباشرة
        process = subprocess.Popen(
            ["frida", "-U", PACKAGE_NAME, "-l", FRIDA_SCRIPT],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True
        )

        for line in process.stdout:
            print(line, end="")
            # إذا تم العثور على التوكن
            if "APP CHECK TOKEN" in line:
                print(f"\n{G}[+] تم العثور على التوكن! تحقق من الملف: {LOCAL_TOKEN_FILE}{RESET}")

    except KeyboardInterrupt:
        print(f"\n{Y}[*] تم إيقاف المراقبة{RESET}")
        process.terminate()

    # محاولة سحب الملف من الجهاز
    pull_token_from_device()
    return True


def method_mitmproxy():
    """الطريقة 2: سحب التوكن باستخدام mitmproxy"""
    print(f"\n{B}=== الطريقة 2: mitmproxy (اعتراض الترافيك) ==={RESET}\n")

    try:
        import mitmproxy  # noqa: F401
    except ImportError:
        print(f"{R}[-] mitmproxy غير منصب!{RESET}")
        print(f"{Y}    نصّبه: pip install mitmproxy{RESET}")
        return False

    addon_file = os.path.join(os.path.dirname(__file__), "mitmproxy_capture.py")
    if not os.path.exists(addon_file):
        print(f"{R}[-] ملف الأدون غير موجود: {addon_file}{RESET}")
        return False

    # الحصول على IP الكمبيوتر
    try:
        import socket
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
    except Exception:
        local_ip = "YOUR_PC_IP"

    print(f"{C}الخطوات:{RESET}")
    print(f"{C}1. شغّل mitmproxy (سيشتغل تلقائياً الآن){RESET}")
    print(f"{C}2. بالمحاكي LDPlayer:{RESET}")
    print(f"{C}   - روح على الإعدادات -> WiFi -> عدّل الشبكة{RESET}")
    print(f"{C}   - فعّل البروكسي اليدوي{RESET}")
    print(f"{C}   - Host: {local_ip}{RESET}")
    print(f"{C}   - Port: 8080{RESET}")
    print(f"{C}3. بالمحاكي، افتح المتصفح وروح على: http://mitm.it{RESET}")
    print(f"{C}   - نزّل شهادة Android واقبلها{RESET}")
    print(f"{C}4. افتح تطبيق عين العراق وسوي أي عملية{RESET}")
    print(f"{C}5. التوكن سيظهر هنا ويُحفظ تلقائياً{RESET}")
    print(f"\n{Y}[*] اضغط Ctrl+C لإيقاف mitmproxy{RESET}\n")

    # تنصيب الشهادة كشهادة نظام (للمحاكي مع روت)
    print(f"{C}[*] هل تريد تنصيب شهادة mitmproxy كشهادة نظام بالمحاكي (يحتاج روت)؟{RESET}")
    install_cert = input(f"{Y}    (y/n): {RESET}").strip().lower()

    if install_cert == 'y':
        install_system_cert()

    try:
        subprocess.run(["mitmdump", "-s", addon_file], check=True)
    except KeyboardInterrupt:
        print(f"\n{Y}[*] تم إيقاف mitmproxy{RESET}")
    except FileNotFoundError:
        print(f"{R}[-] mitmdump غير موجود! تأكد من تنصيب mitmproxy{RESET}")

    return True


def install_system_cert():
    """تنصيب شهادة mitmproxy كشهادة نظام (للمحاكي مع روت)"""
    cert_path = os.path.expanduser("~/.mitmproxy/mitmproxy-ca-cert.pem")
    if not os.path.exists(cert_path):
        print(f"{Y}[!] شغّل mitmproxy مرة أولاً عشان يولّد الشهادة{RESET}")
        print(f"{Y}    شغّل: mitmdump --quit{RESET}")
        return

    print(f"{C}[*] جاري تنصيب الشهادة كشهادة نظام...{RESET}")
    try:
        # حساب هاش الشهادة
        result = subprocess.run(
            ["openssl", "x509", "-inform", "PEM", "-subject_hash_old", "-in", cert_path, "-noout"],
            capture_output=True, text=True
        )
        cert_hash = result.stdout.strip()
        cert_name = f"{cert_hash}.0"

        # تحويل الشهادة
        subprocess.run([
            "openssl", "x509", "-inform", "PEM",
            "-in", cert_path,
            "-out", f"/tmp/{cert_name}",
            "-outform", "DER"
        ], check=True)

        # دفعها للجهاز
        subprocess.run(["adb", "push", f"/tmp/{cert_name}", f"/sdcard/{cert_name}"], check=True)
        subprocess.run(["adb", "shell", "su", "-c",
                        f"mount -o remount,rw /system && "
                        f"cp /sdcard/{cert_name} /system/etc/security/cacerts/{cert_name} && "
                        f"chmod 644 /system/etc/security/cacerts/{cert_name} && "
                        f"mount -o remount,ro /system"], check=True)

        print(f"{G}[+] تم تنصيب الشهادة! أعد تشغيل المحاكي{RESET}")
    except Exception as e:
        print(f"{R}[-] فشل تنصيب الشهادة: {e}{RESET}")


def method_adb_pull():
    """الطريقة 3: سحب التوكن من ملف محفوظ على الجهاز (بعد استخدام Frida)"""
    print(f"\n{B}=== الطريقة 3: سحب التوكن المحفوظ من الجهاز ==={RESET}\n")
    return pull_token_from_device()


def pull_token_from_device():
    """سحب ملف التوكن من الجهاز"""
    try:
        result = subprocess.run(
            ["adb", "pull", TOKEN_FILE_ON_DEVICE, LOCAL_TOKEN_FILE],
            capture_output=True, text=True, timeout=10
        )
        if result.returncode == 0:
            with open(LOCAL_TOKEN_FILE, "r") as f:
                token = f.read().strip()
            if token:
                print(f"\n{G}{'=' * 60}{RESET}")
                print(f"{G}[+] تم سحب التوكن بنجاح!{RESET}")
                print(f"{G}{'=' * 60}{RESET}")
                print(f"\n{B}التوكن:{RESET}")
                print(f"{token[:100]}...")
                print(f"\n{C}التوكن الكامل محفوظ في: {LOCAL_TOKEN_FILE}{RESET}")
                print(f"{G}{'=' * 60}{RESET}\n")

                # نسخ للحافظة إذا ممكن
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
            else:
                print(f"{Y}[-] الملف فارغ{RESET}")
                return False
        else:
            print(f"{Y}[-] لم يتم العثور على ملف التوكن على الجهاز{RESET}")
            print(f"{Y}    شغّل Frida أولاً وافتح التطبيق{RESET}")
            return False
    except Exception as e:
        print(f"{R}[-] خطأ: {e}{RESET}")
        return False


def method_logcat():
    """الطريقة 4: مراقبة logcat للبحث عن التوكن"""
    print(f"\n{B}=== الطريقة 4: مراقبة Logcat ==={RESET}\n")
    print(f"{C}[*] جاري مراقبة logcat... افتح تطبيق عين العراق{RESET}")
    print(f"{Y}[*] اضغط Ctrl+C لإيقاف المراقبة{RESET}\n")

    try:
        # مسح logcat القديم
        subprocess.run(["adb", "logcat", "-c"], timeout=5)

        process = subprocess.Popen(
            ["adb", "logcat", "-s", "OkHttp:*", "Retrofit:*", "firebase:*", "AppCheck:*"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True
        )

        for line in process.stdout:
            lower = line.lower()
            if "appcheck" in lower or "firebase" in lower or "x-firebase" in lower:
                if "eyj" in lower:
                    print(f"{G}{line}{RESET}", end="")
                else:
                    print(f"{C}{line}{RESET}", end="")

    except KeyboardInterrupt:
        print(f"\n{Y}[*] تم إيقاف المراقبة{RESET}")
        process.terminate()


def main():
    print_banner()

    if not check_adb():
        print(f"\n{Y}هل تريد محاولة الاتصال بمحاكي LDPlayer؟{RESET}")
        retry = input(f"{Y}(y/n): {RESET}").strip().lower()
        if retry == 'y':
            ports = [5555, 5556, 5557, 21503, 21513]
            for port in ports:
                print(f"{C}[*] جاري المحاولة على المنفذ {port}...{RESET}")
                subprocess.run(["adb", "connect", f"127.0.0.1:{port}"],
                               capture_output=True, timeout=5)
            time.sleep(1)
            if not check_adb():
                print(f"{R}[-] فشل الاتصال. تأكد من تشغيل المحاكي{RESET}")
                return
        else:
            return

    check_app_installed()

    while True:
        print(f"\n{B}اختر طريقة السحب:{RESET}")
        print(f"  {C}1{RESET} - Frida (ربط مباشر - الأقوى)")
        print(f"  {C}2{RESET} - mitmproxy (اعتراض الترافيك)")
        print(f"  {C}3{RESET} - سحب التوكن المحفوظ من الجهاز")
        print(f"  {C}4{RESET} - مراقبة Logcat")
        print(f"  {C}0{RESET} - خروج")

        choice = input(f"\n{Y}اختيارك: {RESET}").strip()

        if choice == "1":
            method_frida()
        elif choice == "2":
            method_mitmproxy()
        elif choice == "3":
            method_adb_pull()
        elif choice == "4":
            method_logcat()
        elif choice == "0":
            print(f"{C}مع السلامة!{RESET}")
            break
        else:
            print(f"{R}اختيار غير صحيح{RESET}")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print(f"\n{Y}تم الإيقاف{RESET}")
