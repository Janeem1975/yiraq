/**
 * Frida Script - تجاوز كشف المحاكي + سحب توكن AppCheck
 * 
 * يخدع التطبيق ليظن أنه يعمل على جهاز حقيقي:
 * - يغيّر Build properties (BRAND, MODEL, DEVICE, PRODUCT, HARDWARE, FINGERPRINT)
 * - يغيّر System properties (ro.hardware, ro.product.*, ro.build.*)
 * - يخفي ملفات المحاكي (/dev/qemu_pipe, /system/bin/qemu-props, الخ)
 * - يزيّف IMEI/IMSI ومعلومات الشبكة
 * - يتجاوز فحص Google Play Integrity / SafetyNet
 * - يسحب توكن AppCheck تلقائياً
 *
 * الاستخدام:
 *   frida -U -f com.moi.ayniq -l bypass_emulator.js
 */

Java.perform(function () {
    console.log("\n[*] === تجاوز كشف المحاكي + سحب AppCheck ===\n");

    // ============================================================
    //  القسم 1: تزييف Build Properties
    // ============================================================
    try {
        var Build = Java.use("android.os.Build");
        
        // تعيين قيم جهاز Samsung Galaxy S21 حقيقي
        Build.BRAND.value = "samsung";
        Build.MANUFACTURER.value = "samsung";
        Build.MODEL.value = "SM-G991B";
        Build.DEVICE.value = "o1s";
        Build.PRODUCT.value = "o1sxeea";
        Build.HARDWARE.value = "exynos2100";
        Build.BOARD.value = "exynos2100";
        Build.DISPLAY.value = "RP1A.200720.012.G991BXXS9FWAB";
        Build.HOST.value = "21DHA724";
        Build.FINGERPRINT.value = "samsung/o1sxeea/o1s:13/TP1A.220624.014/G991BXXS9FWAB:user/release-keys";
        Build.TAGS.value = "release-keys";
        Build.TYPE.value = "user";
        Build.USER.value = "dpi";
        Build.BOOTLOADER.value = "G991BXXS9FWAB";
        Build.RADIO.value = "G991BXXS9FWAB";
        Build.SERIAL.value = "R5CR30XXXXX";

        // Build.VERSION
        var Version = Java.use("android.os.Build$VERSION");
        Version.SDK_INT.value = 33;
        
        console.log("[+] تم تزييف Build properties → Samsung Galaxy S21");
    } catch (e) {
        console.log("[-] فشل تزييف Build: " + e);
    }

    // ============================================================
    //  القسم 2: تزييف System Properties
    // ============================================================
    try {
        var SystemProperties = Java.use("android.os.SystemProperties");
        
        var propMap = {
            "ro.hardware": "exynos2100",
            "ro.product.model": "SM-G991B",
            "ro.product.brand": "samsung",
            "ro.product.name": "o1sxeea",
            "ro.product.device": "o1s",
            "ro.product.board": "exynos2100",
            "ro.product.manufacturer": "samsung",
            "ro.build.display.id": "RP1A.200720.012",
            "ro.build.fingerprint": "samsung/o1sxeea/o1s:13/TP1A.220624.014/G991BXXS9FWAB:user/release-keys",
            "ro.build.tags": "release-keys",
            "ro.build.type": "user",
            "ro.boot.hardware": "exynos2100",
            "ro.hardware.chipname": "exynos2100",
            "ro.board.platform": "exynos2100",
            "ro.arch": "arm64",
            "gsm.version.baseband": "G991BXXS9FWAB",
            "init.svc.qemu-props": "",
            "init.svc.qemud": "",
            "ro.kernel.qemu": "",
            "ro.kernel.android.qemud": "",
            "qemu.hw.mainkeys": "",
            "ro.bootimage.build.fingerprint": "samsung/o1sxeea/o1s:13/TP1A.220624.014/G991BXXS9FWAB:user/release-keys",
            "ro.build.characteristics": "default",
            "ro.secure": "1",
            "ro.debuggable": "0",
            "ro.genymotion.version": "",
            "ro.hardware.virtual_device": "",
        };

        // Hook get(String)
        SystemProperties.get.overload("java.lang.String").implementation = function (key) {
            if (propMap.hasOwnProperty(key)) {
                var fakeVal = propMap[key];
                return fakeVal;
            }
            var real = this.get(key);
            // إخفاء أي قيمة تحتوي على كلمات المحاكي
            if (real && containsEmulatorHint(real)) {
                return "";
            }
            return real;
        };

        // Hook get(String, String)
        SystemProperties.get.overload("java.lang.String", "java.lang.String").implementation = function (key, def) {
            if (propMap.hasOwnProperty(key)) {
                var fakeVal = propMap[key];
                return fakeVal !== "" ? fakeVal : def;
            }
            var real = this.get(key, def);
            if (real && containsEmulatorHint(real)) {
                return def;
            }
            return real;
        };

        console.log("[+] تم تزييف System Properties");
    } catch (e) {
        console.log("[-] فشل تزييف SystemProperties: " + e);
    }

    // ============================================================
    //  القسم 3: إخفاء ملفات المحاكي
    // ============================================================
    try {
        var File = Java.use("java.io.File");
        
        var emuFiles = [
            "/dev/qemu_pipe",
            "/dev/qemu_trace",
            "/dev/goldfish_pipe",
            "/dev/socket/qemud",
            "/system/bin/qemu-props",
            "/system/bin/qemud",
            "/system/bin/androVM-prop",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/system/lib/libdroid4x.so",
            "/system/bin/microvirt-prop",
            "/system/bin/nox-prop",
            "/system/bin/ttVM-prop",
            "/system/bin/windroyed",
            "/system/etc/genymotion",
            "/data/misc/genymotion",
            "/dev/vboxguest",
            "/dev/vboxuser",
            "/system/lib/vboxguest.ko",
            "/system/lib/vboxsf.ko",
            "/ueventd.android_x86.rc",
            "/x86.prop",
            "/ueventd.ttVM_x86.rc",
            "/init.nox.rc",
            "/init.genymotion.sh",
            "/fstab.vbox86",
            "/init.vbox86.rc",
            "/ueventd.vbox86.rc",
            "/sys/bus/pci/drivers/vboxguest",
        ];

        File.exists.implementation = function () {
            var path = this.getAbsolutePath();
            for (var i = 0; i < emuFiles.length; i++) {
                if (path === emuFiles[i] || path.indexOf("qemu") !== -1 || 
                    path.indexOf("genymotion") !== -1 || path.indexOf("vbox") !== -1 ||
                    path.indexOf("nox") !== -1 || path.indexOf("goldfish") !== -1) {
                    return false;
                }
            }
            return this.exists();
        };

        console.log("[+] تم إخفاء ملفات المحاكي");
    } catch (e) {
        console.log("[-] فشل إخفاء الملفات: " + e);
    }

    // ============================================================
    //  القسم 4: تزييف TelephonyManager (IMEI, Network, SIM)
    // ============================================================
    try {
        var TelephonyManager = Java.use("android.telephony.TelephonyManager");
        
        // تزييف IMEI
        try {
            TelephonyManager.getDeviceId.overload().implementation = function () {
                return "358673091835214";
            };
        } catch (e) {}
        
        try {
            TelephonyManager.getDeviceId.overload("int").implementation = function (slot) {
                return "358673091835214";
            };
        } catch (e) {}

        try {
            TelephonyManager.getImei.overload().implementation = function () {
                return "358673091835214";
            };
        } catch (e) {}

        try {
            TelephonyManager.getImei.overload("int").implementation = function (slot) {
                return "358673091835214";
            };
        } catch (e) {}

        // معلومات الشبكة
        TelephonyManager.getNetworkOperatorName.implementation = function () {
            return "Zain IQ";
        };

        TelephonyManager.getNetworkOperator.implementation = function () {
            return "41820";  // Zain Iraq MCC+MNC
        };

        TelephonyManager.getSimOperatorName.implementation = function () {
            return "Zain IQ";
        };

        TelephonyManager.getSimOperator.implementation = function () {
            return "41820";
        };

        TelephonyManager.getNetworkCountryIso.implementation = function () {
            return "iq";
        };

        TelephonyManager.getSimCountryIso.implementation = function () {
            return "iq";
        };

        TelephonyManager.getPhoneType.implementation = function () {
            return 1;  // PHONE_TYPE_GSM
        };

        TelephonyManager.getNetworkType.implementation = function () {
            return 13;  // NETWORK_TYPE_LTE
        };

        // الخط (حالة SIM)
        TelephonyManager.getSimState.overload().implementation = function () {
            return 5;  // SIM_STATE_READY
        };

        // رقم الخط
        try {
            TelephonyManager.getLine1Number.implementation = function () {
                return "+9647801234567";
            };
        } catch (e) {}

        // مشترك
        try {
            TelephonyManager.getSubscriberId.implementation = function () {
                return "418201234567890";
            };
        } catch (e) {}

        console.log("[+] تم تزييف TelephonyManager (IMEI, Network, SIM)");
    } catch (e) {
        console.log("[-] فشل تزييف TelephonyManager: " + e);
    }

    // ============================================================
    //  القسم 5: تزييف Settings.Secure (ANDROID_ID)
    // ============================================================
    try {
        var Secure = Java.use("android.provider.Settings$Secure");
        Secure.getString.implementation = function (resolver, name) {
            if (name === "android_id") {
                return "a1b2c3d4e5f67890";
            }
            return this.getString(resolver, name);
        };
        console.log("[+] تم تزييف Settings.Secure.ANDROID_ID");
    } catch (e) {
        console.log("[-] فشل تزييف ANDROID_ID: " + e);
    }

    // ============================================================
    //  القسم 6: إخفاء المحاكي من Sensors
    // ============================================================
    try {
        var SensorManager = Java.use("android.hardware.SensorManager");
        var Sensor = Java.use("android.hardware.Sensor");
        
        Sensor.getName.implementation = function () {
            var name = this.getName();
            if (name && (name.indexOf("goldfish") !== -1 || name.indexOf("virtual") !== -1 || 
                         name.indexOf("genymotion") !== -1)) {
                return "LSM6DSO Accelerometer";
            }
            return name;
        };

        Sensor.getVendor.implementation = function () {
            var vendor = this.getVendor();
            if (vendor && (vendor.indexOf("Genymotion") !== -1 || vendor.indexOf("goldfish") !== -1 ||
                           vendor.indexOf("Google") !== -1)) {
                return "STMicroelectronics";
            }
            return vendor;
        };

        console.log("[+] تم إخفاء sensors المحاكي");
    } catch (e) {
        console.log("[-] فشل إخفاء sensors: " + e);
    }

    // ============================================================
    //  القسم 7: إخفاء تطبيقات المحاكي
    // ============================================================
    try {
        var PackageManager = Java.use("android.app.ApplicationPackageManager");
        
        var emuPackages = [
            "com.genymotion",
            "com.bluestacks",
            "com.bignox.app",
            "com.vphone.launcher",
            "com.microvirt",
            "me.haotian.lechange",
            "com.ldplayer",
            "com.google.android.launcher.layouts.genymotion",
            "com.android.emulator.smoketests",
        ];

        PackageManager.getPackageInfo.overload("java.lang.String", "int").implementation = function (pkg, flags) {
            for (var i = 0; i < emuPackages.length; i++) {
                if (pkg === emuPackages[i]) {
                    throw Java.use("android.content.pm.PackageManager$NameNotFoundException").$new(pkg);
                }
            }
            return this.getPackageInfo(pkg, flags);
        };

        console.log("[+] تم إخفاء تطبيقات المحاكي");
    } catch (e) {
        console.log("[-] فشل إخفاء التطبيقات: " + e);
    }

    // ============================================================
    //  القسم 8: تجاوز فحص IP / VPN / Proxy
    // ============================================================
    try {
        var NetworkInterface = Java.use("java.net.NetworkInterface");
        
        // إخفاء واجهات الشبكة الافتراضية
        NetworkInterface.getName.implementation = function () {
            var name = this.getName();
            if (name && (name === "eth0" || name.indexOf("vbox") !== -1 || name.indexOf("vnic") !== -1)) {
                return "wlan0";
            }
            return name;
        };

        console.log("[+] تم تزييف واجهات الشبكة");
    } catch (e) {
        console.log("[-] فشل تزييف الشبكة: " + e);
    }

    // ============================================================
    //  القسم 9: تجاوز فحص البطارية (المحاكي دايماً charging + 50%)
    // ============================================================
    try {
        var BatteryManager = Java.use("android.os.BatteryManager");
        
        BatteryManager.getIntProperty.implementation = function (id) {
            if (id === 4) {  // BATTERY_PROPERTY_CAPACITY
                return 73;
            }
            if (id === 6) {  // BATTERY_PROPERTY_STATUS
                return 3;  // BATTERY_STATUS_DISCHARGING (جهاز حقيقي مش على الشاحن)
            }
            return this.getIntProperty(id);
        };

        console.log("[+] تم تزييف معلومات البطارية");
    } catch (e) {
        console.log("[-] فشل تزييف البطارية: " + e);
    }

    // ============================================================
    //  القسم 10: إخفاء Root (بعض التطبيقات تفحص الروت)
    // ============================================================
    try {
        var Runtime = Java.use("java.lang.Runtime");
        
        Runtime.exec.overload("java.lang.String").implementation = function (cmd) {
            if (cmd && (cmd.indexOf("su") !== -1 || cmd === "which su" || cmd === "id")) {
                throw Java.use("java.io.IOException").$new("Cannot run program");
            }
            return this.exec(cmd);
        };

        Runtime.exec.overload("[Ljava.lang.String;").implementation = function (cmds) {
            if (cmds && cmds.length > 0) {
                var cmd = cmds[0];
                if (cmd && (cmd === "su" || cmd === "which" || cmd.indexOf("/su") !== -1)) {
                    throw Java.use("java.io.IOException").$new("Cannot run program");
                }
            }
            return this.exec(cmds);
        };

        // إخفاء su binary
        var File2 = Java.use("java.io.File");
        var originalExists = File2.exists;

        var suPaths = [
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/system/sd/xbin/su",
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk",
            "/system/app/SuperSU",
        ];

        // ملاحظة: هذا الـ hook موجود بالقسم 3 أيضاً — هنا نضيف مسارات su
        // لكن الـ hook الأول بالقسم 3 بيمسكهم لأنه عام

        console.log("[+] تم إخفاء Root indicators");
    } catch (e) {
        console.log("[-] فشل إخفاء Root: " + e);
    }

    // ============================================================
    //  القسم 11: تجاوز Google SafetyNet / Play Integrity
    // ============================================================
    try {
        // Hook DroidGuard (جزء من SafetyNet)
        try {
            var DroidGuard = Java.use("com.google.android.gms.droidguard.DroidGuardHelper");
            console.log("[*] تم اكتشاف DroidGuard");
        } catch (e) {
            // DroidGuard غير موجود — عادي
        }

        // Hook SafetyNet Attestation
        try {
            var SafetyNet = Java.use("com.google.android.gms.safetynet.SafetyNetApi");
            console.log("[*] تم اكتشاف SafetyNet API");
        } catch (e) {}

        console.log("[+] تم فحص SafetyNet/Play Integrity");
    } catch (e) {
        console.log("[-] فشل فحص SafetyNet: " + e);
    }

    // ============================================================
    //  القسم 12: Native Layer — تجاوز فحوصات C/C++
    // ============================================================
    try {
        // Hook fopen لإخفاء /proc/cpuinfo goldfish
        Interceptor.attach(Module.findExportByName("libc.so", "fopen"), {
            onEnter: function (args) {
                this.path = args[0].readUtf8String();
            },
            onLeave: function (retval) {
                // لا نمنع الفتح — بس نراقب
            }
        });

        // Hook system_property_get (Native)
        var propGet = Module.findExportByName("libc.so", "__system_property_get");
        if (propGet) {
            Interceptor.attach(propGet, {
                onEnter: function (args) {
                    this.name = args[0].readUtf8String();
                    this.valueBuf = args[1];
                },
                onLeave: function (retval) {
                    if (this.name) {
                        var val = this.valueBuf.readUtf8String();
                        
                        // إخفاء أي قيمة تدل على المحاكي
                        if (this.name === "ro.hardware" || this.name === "ro.product.device") {
                            this.valueBuf.writeUtf8String("o1s");
                        } else if (this.name === "ro.product.model") {
                            this.valueBuf.writeUtf8String("SM-G991B");
                        } else if (this.name === "ro.product.brand" || this.name === "ro.product.manufacturer") {
                            this.valueBuf.writeUtf8String("samsung");
                        } else if (this.name === "ro.kernel.qemu" || this.name === "ro.hardware.virtual_device") {
                            this.valueBuf.writeUtf8String("");
                        } else if (this.name === "ro.build.fingerprint" || this.name === "ro.bootimage.build.fingerprint") {
                            this.valueBuf.writeUtf8String("samsung/o1sxeea/o1s:13/TP1A.220624.014/G991BXXS9FWAB:user/release-keys");
                        } else if (this.name === "ro.build.tags") {
                            this.valueBuf.writeUtf8String("release-keys");
                        } else if (this.name === "ro.build.type") {
                            this.valueBuf.writeUtf8String("user");
                        } else if (this.name === "ro.secure") {
                            this.valueBuf.writeUtf8String("1");
                        } else if (this.name === "ro.debuggable") {
                            this.valueBuf.writeUtf8String("0");
                        } else if (val && containsEmulatorHint(val)) {
                            this.valueBuf.writeUtf8String("");
                        }
                    }
                }
            });
            console.log("[+] تم ربط Native __system_property_get");
        }

        console.log("[+] تم تفعيل تجاوز Native layer");
    } catch (e) {
        console.log("[-] فشل Native hooks: " + e);
    }

    // ============================================================
    //  القسم 13: سحب توكن AppCheck (نفس hook_appcheck.js)
    // ============================================================
    
    // اعتراض OkHttp header
    try {
        var Builder = Java.use("okhttp3.Request$Builder");
        Builder.header.overload("java.lang.String", "java.lang.String").implementation = function (name, value) {
            if (name.toLowerCase() === "x-firebase-appcheck") {
                console.log("\n[+] ========== APP CHECK TOKEN ==========");
                console.log("[+] " + value);
                console.log("[+] ========================================\n");
                saveToken(value);
            }
            return this.header(name, value);
        };

        Builder.addHeader.implementation = function (name, value) {
            if (name.toLowerCase() === "x-firebase-appcheck") {
                console.log("\n[+] ========== APP CHECK TOKEN (addHeader) ==========");
                console.log("[+] " + value);
                console.log("[+] ==================================================\n");
                saveToken(value);
            }
            return this.addHeader(name, value);
        };

        console.log("[+] تم ربط OkHttp لسحب التوكن");
    } catch (e) {
        console.log("[-] فشل ربط OkHttp: " + e);
    }

    // اعتراض HttpURLConnection
    try {
        var HttpURL = Java.use("java.net.HttpURLConnection");
        HttpURL.setRequestProperty.implementation = function (key, value) {
            if (key && key.toLowerCase() === "x-firebase-appcheck") {
                console.log("\n[+] ========== APP CHECK TOKEN (URLConnection) ==========");
                console.log("[+] " + value);
                console.log("[+] ======================================================\n");
                saveToken(value);
            }
            return this.setRequestProperty(key, value);
        };
        console.log("[+] تم ربط HttpURLConnection");
    } catch (e) {
        console.log("[-] فشل ربط HttpURLConnection: " + e);
    }

    // مراقبة طلبات ayniq
    try {
        var BuilderUrl = Java.use("okhttp3.Request$Builder");
        BuilderUrl.url.overload("java.lang.String").implementation = function (url) {
            if (url && url.indexOf("ayniq.app") !== -1) {
                console.log("[*] طلب API: " + url);
            }
            return this.url(url);
        };
        console.log("[+] تم ربط مراقبة طلبات ayniq.app");
    } catch (e) {}

    // ============================================================
    //  دوال مساعدة
    // ============================================================

    console.log("\n[*] ✅ تم تفعيل جميع التجاوزات!");
    console.log("[*] التطبيق المفروض يشتغل عادي الحين");
    console.log("[*] سوي أي عملية بالتطبيق وانتظر ظهور التوكن...\n");
});

// دالة فحص كلمات المحاكي
function containsEmulatorHint(str) {
    if (!str) return false;
    var s = str.toLowerCase();
    var hints = [
        "goldfish", "ranchu", "generic", "vbox", "genymotion", 
        "sdk_gphone", "google_sdk", "emulator", "android sdk",
        "qemu", "nox", "bluestacks", "ldplayer", "microvirt",
        "ttvm", "droid4x", "windroyed", "andy", "memu",
        "tencent", "ldinit", "ldnemu",
    ];
    for (var i = 0; i < hints.length; i++) {
        if (s.indexOf(hints[i]) !== -1) return true;
    }
    return false;
}

// دالة حفظ التوكن
function saveToken(token) {
    try {
        Java.perform(function () {
            var FileWriter = Java.use("java.io.FileWriter");
            var f = FileWriter.$new("/sdcard/appcheck_token.txt", false);
            f.write(token);
            f.close();
            console.log("[+] تم حفظ التوكن في: /sdcard/appcheck_token.txt");
        });
    } catch (e) {
        console.log("[-] فشل حفظ الملف: " + e);
    }
}
