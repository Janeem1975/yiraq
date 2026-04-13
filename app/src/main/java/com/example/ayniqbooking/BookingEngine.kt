package com.example.ayniqbooking

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class BookingEngine(private val api: AyniqApi) {

    suspend fun runForAccounts(
        accounts: List<AccountSession>,
        targetDates: List<String>,
        slots: List<String>,
        accountStateProvider: (String) -> AccountRunState = { AccountRunState.RUNNING },
        onLog: (String) -> Unit
    ): Boolean {
        val pending = accounts.toMutableList()
        val rateLimitBackoff = RateLimitBackoff()
        var pauseReported = false
        while (currentCoroutineContext().isActive && pending.isNotEmpty()) {
            var attemptedAnyRequestInCycle = false
            for (date in targetDates) {
                for (slot in slots) {
                    val iterator = pending.iterator()
                    while (iterator.hasNext()) {
                        if (!currentCoroutineContext().isActive) return false

                        val account = iterator.next()
                        when (accountStateProvider(account.phone)) {
                            AccountRunState.STOPPED -> {
                                iterator.remove()
                                val t = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                                onLog("[$t] ${account.phone} -> تم إيقاف الحساب يدويًا")
                                if (pending.isEmpty()) return false
                                continue
                            }
                            AccountRunState.PAUSED -> {
                                continue
                            }
                            AccountRunState.RUNNING -> Unit
                        }
                        attemptedAnyRequestInCycle = true
                        val result = api.submitBooking(account, date, slot)
                        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        val rateLimited = isRateLimited(result)
                        val fatalSetup = result.statusCode == -2 || isUnauthorizedClientBody(result.body)
                        val line = when {
                            result.success -> "[$time] ${account.phone} | $date $slot -> ✅ نجاح | endpoint=/booking/api/booking"
                            result.statusCode == -3 -> "[$time] ${account.phone} | $date $slot -> 🏢 تعريف دائرة غير صالح | endpoint=/booking/api/booking | السبب=${shortReason(result.body)}"
                            fatalSetup -> "[$time] ${account.phone} | $date $slot -> 🛑 إعدادات مرفوضة | endpoint=/booking/api/booking | السبب=${shortReason(result.body)}"
                            rateLimited -> "[$time] ${account.phone} | $date $slot -> ⏳ RateLimit | endpoint=/booking/api/booking | السبب=${shortReason(result.body)}"
                            result.statusCode == 401 || result.statusCode == 403 -> "[$time] ${account.phone} | $date $slot -> 🔑 توكن منتهي/مرفوض | endpoint=/booking/api/booking | السبب=${shortReason(result.body)}"
                            result.statusCode == -1 -> "[$time] ${account.phone} | $date $slot -> 🌐 خطأ اتصال | ${shortReason(result.body)}"
                            result.body.contains("full", ignoreCase = true) || result.body.contains("ممتلئ") -> "[$time] ${account.phone} | $date $slot -> ❌ الموعد ممتلئ | endpoint=/booking/api/booking | السبب=${shortReason(result.body)}"
                            else -> "[$time] ${account.phone} | $date $slot -> ⛔ مرفوض HTTP ${result.statusCode} | endpoint=/booking/api/booking | السبب=${shortReason(result.body)}"
                        }
                        onLog(line)

                        if (fatalSetup) {
                            onLog("[$time] تم إيقاف التشغيل لأن AppCheck أو إعدادات البروكسي غير صحيحة. صحح الإعدادات ثم أعد المحاولة.")
                            return false
                        }

                        if (result.success) {
                            iterator.remove()
                            onLog("[$time] ${account.phone} -> تم تأكيد الحجز، متبقي ${pending.size} حساب")
                            if (pending.isEmpty()) return true
                        }

                        if (result.statusCode == 401 || result.statusCode == 403) {
                            iterator.remove()
                            onLog("[$time] ${account.phone} -> تم استبعاد الحساب من الدورة الحالية لأن التوكن مرفوض أو منتهي")
                            if (pending.isEmpty()) return false
                        }

                        val waitMs = if (rateLimited) {
                            val next = rateLimitBackoff.nextDelayMs()
                            onLog("[$time] ${account.phone} -> تبريد تلقائي بسبب RateLimit: ${next / 1000} ثانية قبل المحاولة التالية")
                            next
                        } else {
                            rateLimitBackoff.reset()
                            DELAY_MS
                        }
                        delay(waitMs)
                    }
                }
            }
            if (!attemptedAnyRequestInCycle) {
                if (!pauseReported) {
                    val t = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    onLog("[$t] كل الحسابات المحددة الآن في وضع Pause. سيتم الاستئناف فور ضغط تشغيل.")
                    pauseReported = true
                }
                delay(800)
                continue
            }
            pauseReported = false
            val t = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            onLog("[$t] انتهت دورة البحث الحالية، إعادة المحاولة تلقائيًا...")
        }

        return pending.isEmpty()
    }

    suspend fun runUntilSuccess(
        account: AccountSession,
        targetDate: String,
        slots: List<String>,
        onLog: (String) -> Unit
    ): Boolean {
        val rateLimitBackoff = RateLimitBackoff()
        while (currentCoroutineContext().isActive) {
            for (slot in slots) {
                if (!currentCoroutineContext().isActive) return false

                val result = api.submitBooking(account, targetDate, slot)
                val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                val rateLimited = isRateLimited(result)
                val fatalSetup = result.statusCode == -2 || isUnauthorizedClientBody(result.body)
                val line = when {
                    result.success -> "[$time] $slot -> ✅ نجاح | endpoint=/booking/api/booking"
                    result.statusCode == -3 -> "[$time] $slot -> 🏢 تعريف دائرة غير صالح | endpoint=/booking/api/booking | السبب=${shortReason(result.body)}"
                    fatalSetup -> "[$time] $slot -> 🛑 إعدادات مرفوضة | endpoint=/booking/api/booking | السبب=${shortReason(result.body)}"
                    rateLimited -> "[$time] $slot -> ⏳ RateLimit | endpoint=/booking/api/booking | السبب=${shortReason(result.body)}"
                    result.statusCode == 401 || result.statusCode == 403 -> "[$time] $slot -> 🔑 توكن منتهي/مرفوض | endpoint=/booking/api/booking | السبب=${shortReason(result.body)}"
                    result.statusCode == -1 -> "[$time] $slot -> 🌐 خطأ اتصال | ${shortReason(result.body)}"
                    result.body.contains("full", ignoreCase = true) || result.body.contains("ممتلئ") -> "[$time] $slot -> ❌ الموعد ممتلئ | endpoint=/booking/api/booking | السبب=${shortReason(result.body)}"
                    else -> "[$time] $slot -> ⛔ مرفوض HTTP ${result.statusCode} | endpoint=/booking/api/booking | السبب=${shortReason(result.body)}"
                }
                onLog(line)

                if (fatalSetup) {
                    onLog("[$time] تم إيقاف التشغيل لأن AppCheck أو إعدادات البروكسي غير صحيحة. صحح الإعدادات ثم أعد المحاولة.")
                    return false
                }
                if (result.success) return true
                if (result.statusCode == 401 || result.statusCode == 403) {
                    onLog("[$time] تم إيقاف المحاولة لهذا الحساب لأن التوكن مرفوض أو منتهي")
                    return false
                }
                val waitMs = if (rateLimited) {
                    val next = rateLimitBackoff.nextDelayMs()
                    onLog("[$time] تبريد تلقائي بسبب RateLimit: ${next / 1000} ثانية قبل المحاولة التالية")
                    next
                } else {
                    rateLimitBackoff.reset()
                    DELAY_MS
                }
                delay(waitMs)
            }
        }
        return false
    }

    private fun isRateLimited(result: BookingResult): Boolean {
        if (result.statusCode == 429) return true
        val body = result.body.lowercase()
        return body.contains("1015") ||
            body.contains("ratelimit") ||
            body.contains("rate limit") ||
            body.contains("too many requests")
    }

    private class RateLimitBackoff {
        private var streak = 0

        fun nextDelayMs(): Long {
            streak += 1
            val exponent = (streak - 1).coerceIn(0, 5)
            val base = (8_000L * (1L shl exponent)).coerceAtMost(120_000L)
            val jitter = (base * 0.20).toLong()
            return base + Random.nextLong(0L, jitter + 1L)
        }

        fun reset() {
            streak = 0
        }
    }

    private fun shortReason(body: String): String {
        return body.replace(Regex("\\s+"), " ").trim().take(180).ifBlank { "لا يوجد وصف من السيرفر" }
    }
}
