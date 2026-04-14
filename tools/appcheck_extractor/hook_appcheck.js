/**
 * Frida Script - سحب توكن Firebase AppCheck من تطبيق عين العراق
 *
 * الاستخدام:
 *   frida -U -f com.moi.ayniq -l hook_appcheck.js --no-pause
 *
 * أو إذا التطبيق شغال:
 *   frida -U com.moi.ayniq -l hook_appcheck.js
 */

Java.perform(function () {
    console.log("\n[*] === سحب توكن AppCheck من عين العراق ===\n");

    // === الطريقة 1: اعتراض هيدر x-firebase-appcheck من OkHttp ===
    try {
        var Builder = Java.use("okhttp3.Request$Builder");
        Builder.header.overload("java.lang.String", "java.lang.String").implementation = function (name, value) {
            if (name.toLowerCase() === "x-firebase-appcheck") {
                console.log("\n[+] ========== APP CHECK TOKEN ==========");
                console.log("[+] " + value);
                console.log("[+] ========================================\n");

                // حفظ التوكن بملف على الجهاز
                try {
                    var File = Java.use("java.io.File");
                    var FileWriter = Java.use("java.io.FileWriter");
                    var f = FileWriter.$new("/sdcard/appcheck_token.txt", false);
                    f.write(value);
                    f.close();
                    console.log("[+] تم حفظ التوكن في: /sdcard/appcheck_token.txt");
                } catch (e) {
                    console.log("[-] فشل حفظ الملف: " + e);
                }
            }
            return this.header(name, value);
        };
        console.log("[+] تم ربط OkHttp Request.Builder.header()");
    } catch (e) {
        console.log("[-] فشل ربط OkHttp: " + e);
    }

    // === الطريقة 2: اعتراض HttpURLConnection ===
    try {
        var URL = Java.use("java.net.HttpURLConnection");
        URL.setRequestProperty.implementation = function (key, value) {
            if (key && key.toLowerCase() === "x-firebase-appcheck") {
                console.log("\n[+] ========== APP CHECK TOKEN (HttpURLConnection) ==========");
                console.log("[+] " + value);
                console.log("[+] ==========================================================\n");
            }
            return this.setRequestProperty(key, value);
        };
        console.log("[+] تم ربط HttpURLConnection.setRequestProperty()");
    } catch (e) {
        console.log("[-] فشل ربط HttpURLConnection: " + e);
    }

    // === الطريقة 3: اعتراض Firebase AppCheck SDK مباشرة ===
    try {
        // محاولة الربط مع عدة أسماء كلاسات محتملة للـ SDK
        var classNames = [
            "com.google.firebase.appcheck.internal.DefaultFirebaseAppCheck",
            "com.google.firebase.appcheck.FirebaseAppCheck",
            "com.google.firebase.appcheck.internal.DefaultTokenRefresher",
            "com.google.firebase.appcheck.playintegrity.internal.PlayIntegrityAppCheckProvider"
        ];

        classNames.forEach(function (className) {
            try {
                var cls = Java.use(className);
                var methods = cls.class.getDeclaredMethods();
                methods.forEach(function (method) {
                    var methodName = method.getName();
                    if (methodName.toLowerCase().indexOf("token") !== -1 ||
                        methodName.toLowerCase().indexOf("gettoken") !== -1 ||
                        methodName.toLowerCase().indexOf("appcheck") !== -1) {
                        console.log("[*] Firebase class found: " + className + "." + methodName + "()");
                    }
                });
            } catch (e) {
                // الكلاس غير موجود
            }
        });
    } catch (e) {
        console.log("[-] فشل البحث عن Firebase classes: " + e);
    }

    // === الطريقة 4: اعتراض كل الطلبات لـ api.ayniq.app ===
    try {
        var BuilderUrl = Java.use("okhttp3.Request$Builder");
        BuilderUrl.url.overload("java.lang.String").implementation = function (url) {
            if (url && url.indexOf("ayniq.app") !== -1) {
                console.log("[*] طلب API: " + url);
            }
            return this.url(url);
        };
        console.log("[+] تم ربط مراقبة الطلبات لـ ayniq.app");
    } catch (e) {
        console.log("[-] فشل ربط URL: " + e);
    }

    // === الطريقة 5: اعتراض addHeader أيضاً ===
    try {
        var Builder2 = Java.use("okhttp3.Request$Builder");
        Builder2.addHeader.implementation = function (name, value) {
            if (name.toLowerCase() === "x-firebase-appcheck") {
                console.log("\n[+] ========== APP CHECK TOKEN (addHeader) ==========");
                console.log("[+] " + value);
                console.log("[+] ==================================================\n");
            }
            return this.addHeader(name, value);
        };
        console.log("[+] تم ربط OkHttp Request.Builder.addHeader()");
    } catch (e) {
        console.log("[-] فشل ربط addHeader: " + e);
    }

    console.log("\n[*] الأكواد جاهزة! افتح تطبيق عين العراق وانتظر ظهور التوكن...\n");
    console.log("[*] التوكن سيظهر هنا وسيُحفظ في /sdcard/appcheck_token.txt\n");
});
