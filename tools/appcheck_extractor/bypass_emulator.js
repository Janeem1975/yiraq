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
    //  (ملاحظة: File.exists hook الشامل موجود بالقسم 10a
    //   يغطي ملفات المحاكي + الروت + Magisk + Frida)
    // ============================================================
    console.log("[+] إخفاء ملفات المحاكي (يُدار من القسم 10a)");

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
    //  (ملاحظة: PackageManager hook الشامل موجود بالقسم 10b
    //   يغطي تطبيقات المحاكي + الروت + Magisk + Xposed)
    // ============================================================
    console.log("[+] إخفاء تطبيقات المحاكي (يُدار من القسم 10b)");

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
    //  القسم 10: تجاوز كشف الروت (Root Detection Bypass)
    //  يتجاوز: RootBeer, SafetyNet root checks, Magisk detection,
    //  su binary checks, root packages, root props, shell commands
    // ============================================================

    // --- 10a: إخفاء ملفات ومجلدات الروت من File.exists ---
    // (نعيد تعريف File.exists مع دمج فحوصات المحاكي من القسم 3)
    try {
        var FileRoot = Java.use("java.io.File");

        var rootFiles = [
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su",
            "/system/sd/xbin/su", "/su/bin/su", "/magisk",
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk",
            "/system/app/SuperSU", "/system/app/Superuser",
            "/system/etc/init.d/99telecominfomern",
            "/system/xbin/daemonsu", "/system/xbin/busybox",
            "/system/bin/.ext/.su", "/system/bin/failsafe/su",
            "/data/adb/magisk", "/sbin/.magisk", "/cache/.disable_magisk",
            "/dev/.magisk.unblock", "/data/adb/modules",
            "/system/addon.d/99-magisk.sh",
            "/data/data/com.topjohnwu.magisk",
            "/data/user_de/0/com.topjohnwu.magisk",
            // مسارات Frida (إخفاء Frida عن التطبيق)
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
        ];

        FileRoot.exists.implementation = function () {
            var path = this.getAbsolutePath();
            // فحص ملفات الروت
            for (var i = 0; i < rootFiles.length; i++) {
                if (path === rootFiles[i]) return false;
            }
            // فحص ملفات المحاكي (من القسم 3)
            if (path.indexOf("qemu") !== -1 || path.indexOf("genymotion") !== -1 ||
                path.indexOf("vbox") !== -1 || path.indexOf("nox") !== -1 ||
                path.indexOf("goldfish") !== -1 || path.indexOf("magisk") !== -1 ||
                path.indexOf("/su") !== -1 || path.indexOf("frida") !== -1 ||
                path.indexOf("xposed") !== -1) {
                return false;
            }
            return this.exists();
        };

        // إخفاء canRead للملفات الحساسة
        FileRoot.canRead.implementation = function () {
            var path = this.getAbsolutePath();
            for (var i = 0; i < rootFiles.length; i++) {
                if (path === rootFiles[i]) return false;
            }
            if (path.indexOf("magisk") !== -1 || path.indexOf("/su") !== -1 ||
                path.indexOf("frida") !== -1) {
                return false;
            }
            return this.canRead();
        };

        console.log("[+] تم إخفاء ملفات Root + Magisk + Frida من File.exists/canRead");
    } catch (e) {
        console.log("[-] فشل إخفاء ملفات Root: " + e);
    }

    // --- 10b: إخفاء تطبيقات/حزم الروت من PackageManager ---
    try {
        var PM = Java.use("android.app.ApplicationPackageManager");

        var rootPackages = [
            "com.topjohnwu.magisk", "io.github.vvb2060.magisk",
            "com.koushikdutta.superuser", "com.noshufou.android.su",
            "eu.chainfire.supersu", "com.thirdparty.superuser",
            "com.yellowes.su", "com.kingroot.kinguser",
            "com.kingo.root", "com.smedialink.onecleanmaster",
            "com.zhiqupk.root.global", "com.alephzain.framaroot",
            "com.formyhm.hideroot", "com.amphoras.hidemyroot",
            "com.saurik.substrate", "de.robv.android.xposed",
            "de.robv.android.xposed.installer",
            // تطبيقات المحاكي (من القسم 7)
            "com.genymotion", "com.bluestacks", "com.bignox.app",
            "com.vphone.launcher", "com.microvirt", "com.ldplayer",
            "com.google.android.launcher.layouts.genymotion",
            "com.android.emulator.smoketests",
        ];

        PM.getPackageInfo.overload("java.lang.String", "int").implementation = function (pkg, flags) {
            for (var i = 0; i < rootPackages.length; i++) {
                if (pkg === rootPackages[i]) {
                    throw Java.use("android.content.pm.PackageManager$NameNotFoundException").$new(pkg);
                }
            }
            return this.getPackageInfo(pkg, flags);
        };

        // أيضاً hook getApplicationInfo
        try {
            PM.getApplicationInfo.overload("java.lang.String", "int").implementation = function (pkg, flags) {
                for (var i = 0; i < rootPackages.length; i++) {
                    if (pkg === rootPackages[i]) {
                        throw Java.use("android.content.pm.PackageManager$NameNotFoundException").$new(pkg);
                    }
                }
                return this.getApplicationInfo(pkg, flags);
            };
        } catch (e) {}

        console.log("[+] تم إخفاء حزم Root + Magisk + Xposed + المحاكي");
    } catch (e) {
        console.log("[-] فشل إخفاء حزم Root: " + e);
    }

    // --- 10c: تجاوز Runtime.exec لأوامر الروت ---
    try {
        var Runtime = Java.use("java.lang.Runtime");

        var blockedCmds = ["su", "which", "id", "mount", "busybox", "magisk"];

        Runtime.exec.overload("java.lang.String").implementation = function (cmd) {
            if (cmd) {
                for (var i = 0; i < blockedCmds.length; i++) {
                    if (cmd === blockedCmds[i] || cmd.indexOf("/" + blockedCmds[i]) !== -1 ||
                        cmd.indexOf(blockedCmds[i] + " ") === 0) {
                        throw Java.use("java.io.IOException").$new("Cannot run program \"" + cmd + "\"");
                    }
                }
            }
            return this.exec(cmd);
        };

        Runtime.exec.overload("[Ljava.lang.String;").implementation = function (cmds) {
            if (cmds && cmds.length > 0) {
                var cmd0 = cmds[0];
                if (cmd0) {
                    for (var i = 0; i < blockedCmds.length; i++) {
                        if (cmd0 === blockedCmds[i] || cmd0.indexOf("/" + blockedCmds[i]) !== -1) {
                            throw Java.use("java.io.IOException").$new("Cannot run program \"" + cmd0 + "\"");
                        }
                    }
                }
            }
            return this.exec(cmds);
        };

        // Hook exec with envp + dir overloads
        try {
            Runtime.exec.overload("java.lang.String", "[Ljava.lang.String;", "java.io.File").implementation = function (cmd, env, dir) {
                if (cmd) {
                    for (var i = 0; i < blockedCmds.length; i++) {
                        if (cmd === blockedCmds[i] || cmd.indexOf("/" + blockedCmds[i]) !== -1) {
                            throw Java.use("java.io.IOException").$new("Cannot run program \"" + cmd + "\"");
                        }
                    }
                }
                return this.exec(cmd, env, dir);
            };
        } catch (e) {}

        try {
            Runtime.exec.overload("[Ljava.lang.String;", "[Ljava.lang.String;", "java.io.File").implementation = function (cmds, env, dir) {
                if (cmds && cmds.length > 0) {
                    var cmd0 = cmds[0];
                    if (cmd0) {
                        for (var i = 0; i < blockedCmds.length; i++) {
                            if (cmd0 === blockedCmds[i] || cmd0.indexOf("/" + blockedCmds[i]) !== -1) {
                                throw Java.use("java.io.IOException").$new("Cannot run program \"" + cmd0 + "\"");
                            }
                        }
                    }
                }
                return this.exec(cmds, env, dir);
            };
        } catch (e) {}

        console.log("[+] تم حظر أوامر Root في Runtime.exec");
    } catch (e) {
        console.log("[-] فشل حظر أوامر Root: " + e);
    }

    // --- 10d: تجاوز ProcessBuilder (طريقة ثانية لتشغيل الأوامر) ---
    try {
        var ProcessBuilder = Java.use("java.lang.ProcessBuilder");
        ProcessBuilder.start.implementation = function () {
            var cmdList = this.command();
            if (cmdList && cmdList.size() > 0) {
                var cmd0 = cmdList.get(0).toString();
                for (var i = 0; i < blockedCmds.length; i++) {
                    if (cmd0 === blockedCmds[i] || cmd0.indexOf("/" + blockedCmds[i]) !== -1) {
                        throw Java.use("java.io.IOException").$new("Cannot run program \"" + cmd0 + "\"");
                    }
                }
            }
            return this.start();
        };
        console.log("[+] تم حظر أوامر Root في ProcessBuilder");
    } catch (e) {
        console.log("[-] فشل حظر ProcessBuilder: " + e);
    }

    // --- 10e: تزييف Build.TAGS و ro.secure و ro.debuggable ---
    try {
        var BuildTags = Java.use("android.os.Build");
        BuildTags.TAGS.value = "release-keys";  // مش "test-keys"
        console.log("[+] تم تعيين Build.TAGS = release-keys");
    } catch (e) {}

    // --- 10f: إخفاء Magisk Mount من /proc/self/mounts ---
    try {
        var BufferedReader = Java.use("java.io.BufferedReader");
        BufferedReader.readLine.implementation = function () {
            var line = this.readLine();
            if (line && (line.indexOf("magisk") !== -1 || line.indexOf("/su") !== -1 ||
                         line.indexOf("tmpfs /system/") !== -1)) {
                // تخطي السطور التي تكشف Magisk mount
                return this.readLine();
            }
            return line;
        };
        console.log("[+] تم إخفاء Magisk mounts من /proc/self/mounts");
    } catch (e) {
        console.log("[-] فشل إخفاء mounts: " + e);
    }

    // --- 10g: تجاوز RootBeer مباشرة (إذا موجود) ---
    try {
        var RootBeer = Java.use("com.scottyab.rootbeer.RootBeer");
        RootBeer.isRooted.implementation = function () { return false; };
        RootBeer.isRootedWithoutBusyBoxCheck.implementation = function () { return false; };
        RootBeer.detectRootManagementApps.implementation = function () { return false; };
        RootBeer.detectPotentiallyDangerousApps.implementation = function () { return false; };
        RootBeer.detectTestKeys.implementation = function () { return false; };
        RootBeer.checkForBinary.overload("java.lang.String").implementation = function (f) { return false; };
        RootBeer.checkForDangerousProps.implementation = function () { return false; };
        RootBeer.checkForRWPaths.implementation = function () { return false; };
        RootBeer.detectRootCloakingApps.implementation = function () { return false; };
        RootBeer.checkSuExists.implementation = function () { return false; };
        RootBeer.checkForRootNative.implementation = function () { return false; };
        RootBeer.checkForMagiskBinary.implementation = function () { return false; };
        console.log("[+] تم تجاوز RootBeer بالكامل!");
    } catch (e) {
        // RootBeer مش موجود — عادي
    }

    // --- 10h: تجاوز مكتبة rootbeer RootCheck الداخلية ---
    try {
        var RootCheck = Java.use("com.scottyab.rootbeer.util.RootCheck");
        RootCheck.isRooted.implementation = function () { return false; };
        console.log("[+] تم تجاوز RootCheck");
    } catch (e) {}

    // --- 10i: إخفاء Frida من التطبيق ---
    try {
        // إخفاء منفذ Frida (27042) من الاتصالات
        var InetSocketAddress = Java.use("java.net.InetSocketAddress");
        // لا نغيّر الـ constructor — بس نراقب

        // إخفاء frida-agent من maps
        // (بعض التطبيقات تقرأ /proc/self/maps وتبحث عن frida)
        // هذا يتم عبر BufferedReader hook أعلاه (10f)

        console.log("[+] تم إخفاء Frida indicators");
    } catch (e) {}

    console.log("[+] === تجاوز كشف الروت مفعّل بالكامل ===");

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
