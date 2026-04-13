package com.example.ayniqbooking

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

const val BASE_URL = "https://api.ayniq.app"
const val USER_AGENT = "com.moi.ayniq 1.10.11"
const val DELAY_MS = 3500L
const val GATE_SERVER_URL = "https://omran22-meayniq-control-serverlicense.hf.space"

@Serializable
data class Office(val id: String, val uuid: String, val name: String)

@Serializable
data class Relation(val id: String, val name: String, val value: Int)

@Serializable
data class PersonPayload(
    val citizen_name: String,
    val citizen_name_2: String,
    val citizen_name_3: String,
    val citizen_surname: String,
    val citizen_mother_name: String,
    val citizen_gender: Int,
    val citizen_dob: String,
    val relationship: Int? = null
)

@Serializable
data class BookingPayload(
    val citizen_name: String,
    val citizen_name_2: String,
    val citizen_name_3: String,
    val citizen_surname: String,
    val citizen_mother_name: String,
    val citizen_gender: Int,
    val citizen_dob: String,
    val citizen_phone_number: String,
    val citizen_family: Int,
    val family_members: List<PersonPayload>
)

@Serializable
data class AccountSession(
    val phone: String,
    val token: String,
    val officeUuid: String,
    val officeName: String,
    val payload: BookingPayload,
    val networkOverride: AccountNetworkOverride = AccountNetworkOverride()
)

@Serializable
data class ProxyConfig(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = ""
)

@Serializable
data class AccountNetworkOverride(
    val useCustomAppCheck: Boolean = false,
    val appCheck: String = "",
    val useCustomProxy: Boolean = false,
    val proxy: ProxyConfig = ProxyConfig()
)

@Serializable
data class AppSettings(
    val appCheck: String = "",
    val proxy: ProxyConfig = ProxyConfig()
)

enum class AccountRunState {
    RUNNING,
    PAUSED,
    STOPPED
}

@Serializable
data class AppState(
    val sessions: Map<String, AccountSession> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val access: AccessControl = AccessControl()
)

@Serializable
data class AccessControl(
    val serverUrl: String = GATE_SERVER_URL,
    val email: String = "",
    val token: String = "",
    val approved: Boolean = false
)

data class DayOption(val label: String, val date: String)

data class BookingResult(val success: Boolean, val statusCode: Int, val body: String)

fun normalizeProxyHost(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    val noScheme = trimmed
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("socks5://")
        .removePrefix("HTTP://")
        .removePrefix("HTTPS://")
        .removePrefix("SOCKS5://")
    return noScheme.substringBefore("/").substringBefore("?").trim()
}

fun validateAppCheck(value: String, required: Boolean): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) {
        return if (required) {
            "حقل AppCheck مطلوب. ألصق x-firebase-appcheck الحقيقي هنا قبل OTP أو الإرسال."
        } else {
            null
        }
    }

    val lower = trimmed.lowercase()
    if (
        lower.contains("proxy.soax") ||
        lower.contains("sessionid") ||
        lower.contains("sessionlength") ||
        trimmed.startsWith("package-", ignoreCase = true)
    ) {
        return "القيمة الحالية تبدو بيانات بروكسي وليست AppCheck. ضع Username/Password في إعدادات البروكسي فقط."
    }

    if (!trimmed.startsWith("eyJ") || trimmed.count { it == '.' } != 2) {
        return "قيمة AppCheck غير صحيحة. غالبًا يجب أن تكون JWT طويلة تبدأ بـ eyJ ومكوّنة من 3 أجزاء."
    }

    return null
}

fun validateProxyConfig(proxy: ProxyConfig): String? {
    if (!proxy.enabled) return null
    val host = normalizeProxyHost(proxy.host)
    if (host.isBlank()) return "أدخل Proxy Host أو أغلق تفعيل البروكسي اليدوي."
    if (host.contains(" ")) return "Proxy Host غير صحيح."
    if (proxy.port !in 1..65535) return "أدخل Proxy Port صحيح."
    if (proxy.username.isBlank() && proxy.password.isNotBlank()) {
        return "أدخل Proxy Username أو امسح كلمة المرور."
    }
    if (proxy.username.isNotBlank() && proxy.password.isBlank()) {
        return "أدخل Proxy Password أو امسح اسم المستخدم."
    }
    return null
}

fun describeConnectionMode(settings: AppSettings): String {
    val proxy = settings.proxy
    val host = normalizeProxyHost(proxy.host)
    return if (proxy.enabled && host.isNotBlank() && proxy.port in 1..65535) {
        "بروكسي يدوي ${host}:${proxy.port}"
    } else {
        "اتصال مباشر / بروكسي الجهاز"
    }
}

fun isUnauthorizedClientBody(body: String): Boolean {
    return body.contains("Unauthorized Client", ignoreCase = true)
}

fun isValidUuid(value: String): Boolean {
    return runCatching {
        UUID.fromString(value)
        true
    }.getOrDefault(false)
}

fun isValidIsoDate(value: String): Boolean {
    return runCatching {
        LocalDate.parse(value, DateTimeFormatter.ISO_DATE)
        true
    }.getOrDefault(false)
}

fun normalizeToTomorrowIfPast(value: String, now: LocalDate = LocalDate.now()): String {
    val tomorrow = now.plusDays(1)
    val parsed = runCatching { LocalDate.parse(value, DateTimeFormatter.ISO_DATE) }.getOrNull()
    val effective = when {
        parsed == null -> tomorrow
        parsed.isBefore(tomorrow) -> tomorrow
        else -> parsed
    }
    return effective.format(DateTimeFormatter.ISO_DATE)
}

fun mergeEffectiveSettings(global: AppSettings, accountOverride: AccountNetworkOverride): AppSettings {
    val normalizedGlobalProxy = global.proxy.copy(host = normalizeProxyHost(global.proxy.host))
    val normalizedCustomProxy = accountOverride.proxy.copy(host = normalizeProxyHost(accountOverride.proxy.host))
    return AppSettings(
        appCheck = if (accountOverride.useCustomAppCheck) accountOverride.appCheck.trim() else global.appCheck.trim(),
        proxy = if (accountOverride.useCustomProxy) normalizedCustomProxy else normalizedGlobalProxy
    )
}

val offices = listOf(
    Office("1", "9117dadb-6563-48f1-aca1-f6cd7896d4a7", "الموصل الايمن (صباحي)"),
    Office("2", "0b3c251b-e28a-4b3d-9859-909dc6b21b97", "الموصل الايسر (مسائي)"),
    Office("3", "b6920038-049c-4079-bed9-5989f4507014", "المعلومات المدنية بعشيقة"),
    Office("4", "5e7e9c43-a16d-4927-a852-36ee94cb6cc9", "المعلومات المدنية وانه"),
    Office("5", "8295b95a-971f-4300-8c38-835cd9df0ca2", "المعلومات المدنية حمام العليل"),
    Office("6", "1b854974-73f9-48da-b798-9def2948b141", "المعلومات المدنية الشورة"),
    Office("7", "add4c2be-9949-4d50-ae2b-d7c06aac5616", "المعلومات المدنية اربيل"),
    Office("8", "1004-adhamiya", "قسم المعلومات المدنية الأعظمية 1004")
)

val relations = listOf(
    Relation("1", "زوج", 10),
    Relation("2", "زوجة", 10),
    Relation("3", "ابن", 20),
    Relation("4", "ابنة", 30),
    Relation("5", "اخ", 40),
    Relation("6", "اخت", 50),
    Relation("7", "اب", 60),
    Relation("8", "ام", 70)
)

val morningSlots = listOf(
    "07:00 AM", "07:30 AM", "08:00 AM", "08:30 AM", "09:00 AM", "09:30 AM", "10:00 AM", "10:30 AM",
    "11:00 AM", "11:30 AM", "12:00 PM", "12:30 PM", "01:00 PM", "01:30 PM", "02:00 PM", "02:30 PM"
)

val eveningSlots = listOf(
    "03:00 PM", "03:30 PM", "04:00 PM", "04:30 PM", "05:00 PM", "05:30 PM", "06:00 PM", "06:30 PM",
    "07:00 PM", "07:30 PM", "08:00 PM"
)

fun randomDob(isHead: Boolean): String {
    val range = if (isHead) 7300..18000 else 365..18000
    val daysBack = Random.nextInt(range.first, range.last)
    return LocalDate.now().minusDays(daysBack.toLong()).format(DateTimeFormatter.ISO_DATE)
}

fun getUpcomingWeek(now: LocalDateTime = LocalDateTime.now()): List<DayOption> {
    val today = now.toLocalDate()
    val dayOfWeek = today.dayOfWeek

    var daysToSunday = ((DayOfWeek.SUNDAY.value - dayOfWeek.value) + 7) % 7
    if (daysToSunday == 0 && now.hour >= 14) {
        daysToSunday = 7
    }

    val nextSunday = today.plusDays(daysToSunday.toLong())
    val labels = mapOf(
        DayOfWeek.MONDAY to "الاثنين",
        DayOfWeek.TUESDAY to "الثلاثاء",
        DayOfWeek.WEDNESDAY to "الأربعاء",
        DayOfWeek.THURSDAY to "الخميس",
        DayOfWeek.FRIDAY to "الجمعة",
        DayOfWeek.SATURDAY to "السبت",
        DayOfWeek.SUNDAY to "الأحد"
    )

    return (0..6).map { offset ->
        val day = nextSunday.plusDays(offset.toLong())
        val dateText = day.format(DateTimeFormatter.ISO_DATE)
        DayOption(label = "${labels[day.dayOfWeek]} $dateText", date = dateText)
    }
}
