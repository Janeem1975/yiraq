# أداة سحب توكن AppCheck - عين العراق

## المتطلبات

```bash
pip install frida-tools mitmproxy
```

وتحتاج ADB (يجي مع Android Studio أو Android SDK Platform Tools).

## الطرق المتاحة

### الطريقة 1: Frida (الأقوى - تحتاج روت)

Frida يربط مباشرة مع تطبيق عين العراق ويعترض التوكن لحظة ما يرسله.

**التجهيز:**

1. نزّل `frida-server` من [هنا](https://github.com/frida/frida/releases) (اختر `frida-server-XX-android-x86` أو `x86_64` للمحاكي)
2. ادفعه للمحاكي:
```bash
adb push frida-server-16-android-x86 /data/local/tmp/frida-server
adb shell chmod 755 /data/local/tmp/frida-server
adb shell su -c '/data/local/tmp/frida-server &'
```
3. شغّل السكربت:
```bash
frida -U com.moi.ayniq -l hook_appcheck.js
```
4. افتح تطبيق عين العراق بالمحاكي واعمل أي عملية (OTP، حجز، الخ)
5. التوكن يظهر بالشاشة ويُحفظ بـ `/sdcard/appcheck_token.txt`

### الطريقة 2: mitmproxy (اعتراض الترافيك)

يعترض كل الترافيك ويلتقط هيدر `x-firebase-appcheck`.

**التجهيز:**

1. شغّل:
```bash
mitmdump -s mitmproxy_capture.py
```
2. بالمحاكي: روح إعدادات WiFi → بروكسي يدوي → Host = IP الكمبيوتر، Port = 8080
3. بالمحاكي: افتح `http://mitm.it` بالمتصفح ونزّل شهادة Android
4. افتح عين العراق → التوكن يظهر ويُحفظ تلقائياً

**تنصيب الشهادة كشهادة نظام (للمحاكي مع روت):**
هذا يخلي mitmproxy يشتغل مع كل التطبيقات بدون مشاكل:
```bash
# أولاً شغّل mitmproxy مرة عشان يولّد الشهادة
mitmdump --set connection_strategy=lazy -q &
sleep 2 && kill %1

# احسب هاش الشهادة
HASH=$(openssl x509 -inform PEM -subject_hash_old -in ~/.mitmproxy/mitmproxy-ca-cert.pem -noout)

# حوّلها وادفعها للمحاكي
openssl x509 -inform PEM -in ~/.mitmproxy/mitmproxy-ca-cert.pem -out /tmp/${HASH}.0 -outform DER
adb push /tmp/${HASH}.0 /sdcard/
adb shell su -c "mount -o remount,rw /system && cp /sdcard/${HASH}.0 /system/etc/security/cacerts/ && chmod 644 /system/etc/security/cacerts/${HASH}.0"
```

### الطريقة 3: السكربت الرئيسي (قائمة تفاعلية)

```bash
python extract_token.py
```

يعطيك قائمة تختار منها الطريقة اللي تناسبك.

## حل مشكلة تطبيق عين العراق ما يشتغل على LDPlayer

إذا التطبيق يعلّق أو يطلع خطأ بالمحاكي، جرّب:

1. **إعدادات LDPlayer:**
   - Other Settings → Root Permission → **ON**
   - وبنفس المكان فعّل **SELinux (Permissive)**

2. **تحديث محاكي الجهاز:**
   - Model → اختر جهاز حقيقي مثل Samsung Galaxy S21
   - IMEI → ولّد رقم جديد

3. **إذا مازال ما يشتغل:** جرب محاكي ثاني مثل [Genymotion](https://www.genymotion.com/) أو Android Studio Emulator اللي عندهم دعم أفضل لـ Google Play Services.
