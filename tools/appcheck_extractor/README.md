# أداة سحب توكن AppCheck - عين العراق

## الطريقة المُوصى بها: Genymotion Cloud (سحابي)

أسهل وأسرع طريقة — جهاز مروّت بالسحابة، بدون تعقيدات.

```bash
pip install gmsaas frida-tools mitmproxy
python cloud_setup.py
```

السكربت يمشّيك خطوة بخطوة:
1. تسجيل دخول بـ Genymotion Cloud
2. تشغيل جهاز سحابي مروّت
3. ربط ADB تلقائياً
4. تنصيب تطبيق عين العراق
5. تنزيل وتشغيل Frida Server تلقائياً
6. سحب التوكن وحفظه بملف

**التكلفة:** ~$0.05/دقيقة | **الوقت:** 10-15 دقيقة

### خطوات التسجيل بـ Genymotion Cloud

1. روح على https://cloud.geny.io/signin
2. سجّل حساب جديد (أو بـ Google/GitHub)
3. اختر خطة Pay-As-You-Go ($0.05/دقيقة)
4. روح على قسم API بالداشبورد
5. اضغط Create لتوليد API Token
6. انسخ التوكن وخزّنه (ما بتقدر تشوفه مرة ثانية!)

---

## الطرق المحلية (LDPlayer / محاكي محلي)

### المتطلبات

```bash
pip install frida-tools mitmproxy
```

وتحتاج ADB (يجي مع Android Studio أو Android SDK Platform Tools).

### السكربت التفاعلي

```bash
python extract_token.py
```

يعطيك قائمة تختار منها الطريقة اللي تناسبك (Frida, mitmproxy, ADB, Logcat).

---

### الطريقة 1: Frida (الأقوى - تحتاج روت)

Frida يربط مباشرة مع تطبيق عين العراق ويعترض التوكن لحظة ما يرسله.

**التجهيز:**

1. نزّل `frida-server` من [هنا](https://github.com/frida/frida/releases) (اختر `frida-server-XX-android-x86` أو `x86_64` للمحاكي)
2. ادفعه للمحاكي:
```bash
adb push frida-server /data/local/tmp/frida-server
adb shell chmod 755 /data/local/tmp/frida-server
adb shell su -c '/data/local/tmp/frida-server &'
```
3. شغّل السكربت (يفتح التطبيق تلقائياً):
```bash
frida -U -f com.moi.ayniq -l hook_appcheck.js
```
4. سوي أي عملية بالتطبيق (OTP، حجز، الخ)
5. التوكن يظهر بالشاشة ويُحفظ بـ `/sdcard/appcheck_token.txt`

### الطريقة 2: mitmproxy (اعتراض الترافيك)

يعترض كل الترافيك ويلتقط هيدر `x-firebase-appcheck`.

**التجهيز:**

1. شغّل:
```bash
mitmdump -s mitmproxy_capture.py
```
2. بالمحاكي: روح إعدادات WiFi → بروكسي يدوي → Host = IP الكمبيوتر، Port = 8080 (أو البورت اللي يطلع بالشاشة)
3. بالمحاكي: افتح `http://mitm.it` بالمتصفح ونزّل شهادة Android
4. افتح عين العراق → التوكن يظهر ويُحفظ تلقائياً

**تنصيب الشهادة كشهادة نظام (للمحاكي مع روت):**
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

---

## حل مشكلة تطبيق عين العراق ما يشتغل على LDPlayer

1. **إعدادات LDPlayer:**
   - Other Settings → Root Permission → **ON**
   - فعّل **SELinux (Permissive)**

2. **تحديث محاكي الجهاز:**
   - Model → اختر جهاز حقيقي مثل Samsung Galaxy S21
   - IMEI → ولّد رقم جديد

3. **إذا مازال ما يشتغل:** استخدم Genymotion Cloud (الطريقة المُوصى بها أعلاه)

---

## الملفات

| الملف | الوصف |
|-------|-------|
| `cloud_setup.py` | سكربت الإعداد السحابي (Genymotion Cloud) — الأسهل |
| `extract_token.py` | سكربت تفاعلي مع قائمة (للمحاكي المحلي) |
| `hook_appcheck.js` | سكربت Frida لاعتراض التوكن |
| `mitmproxy_capture.py` | إضافة mitmproxy لالتقاط التوكن من الترافيك |
