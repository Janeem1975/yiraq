package com.example.ayniqbooking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class AyniqApi(private val settingsProvider: () -> AppSettings) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun requestOtp(
        phone: String,
        accountOverride: AccountNetworkOverride? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = resolveSettings(accountOverride)
            validateProxyConfig(settings.proxy)?.let { throw IOException(it) }
            validateAppCheck(settings.appCheck, required = true)?.let { throw IOException(it) }
            val body = FormBody.Builder().add("phone_number", phone).build()
            val request = Request.Builder()
                .url("$BASE_URL/v3/api/auth/otp")
                .post(body)
                .header("User-Agent", USER_AGENT)
                .applyAppCheck(settings.appCheck)
                .build()

            val (code, responseBody) = try {
                execute(buildClient(settings), request)
            } catch (e: Exception) {
                throw IOException("OTP connection failed عبر ${describeConnectionMode(settings)}: ${e.message.orEmpty()}", e)
            }
            if (code in 200..299) {
                "تم إرسال OTP"
            } else {
                throw IOException("OTP failed: HTTP $code - $responseBody")
            }
        }
    }

    suspend fun login(
        phone: String,
        otp: String,
        accountOverride: AccountNetworkOverride? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = resolveSettings(accountOverride)
            validateProxyConfig(settings.proxy)?.let { throw IOException(it) }
            validateAppCheck(settings.appCheck, required = true)?.let { throw IOException(it) }
            val body = FormBody.Builder()
                .add("phone_number", phone)
                .add("otp", otp)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/v3/api/auth/login")
                .post(body)
                .header("User-Agent", USER_AGENT)
                .applyAppCheck(settings.appCheck)
                .build()

            val (code, responseBody) = try {
                execute(buildClient(settings), request)
            } catch (e: Exception) {
                throw IOException("Login connection failed عبر ${describeConnectionMode(settings)}: ${e.message.orEmpty()}", e)
            }
            if (code !in 200..299) {
                throw IOException("Login failed: HTTP $code - $responseBody")
            }

            val root = JSONObject(responseBody)
            val token = root.optJSONObject("data")?.optString("token").orEmpty()
            if (token.isBlank()) {
                throw IOException("Login response has no token")
            }
            token
        }
    }

    suspend fun submitBooking(
        account: AccountSession,
        targetDate: String,
        slot: String
    ): BookingResult = withContext(Dispatchers.IO) {
        if (!isValidUuid(account.officeUuid)) {
            return@withContext BookingResult(
                success = false,
                statusCode = -3,
                body = "الدائرة ${account.officeName} غير مدعومة حاليًا لأن office_uuid المحفوظ غير صالح (${account.officeUuid}). " +
                    "حدّث تعريف الدائرة أو استخدم دائرة أخرى."
            )
        }
        val settings = resolveSettings(account.networkOverride)
        validateProxyConfig(settings.proxy)?.let {
            return@withContext BookingResult(success = false, statusCode = -2, body = it)
        }
        validateAppCheck(settings.appCheck, required = true)?.let {
            return@withContext BookingResult(success = false, statusCode = -2, body = it)
        }
        val payloadJson = JSONObject(json.encodeToString(account.payload)).apply {
            put("booking_date", "$targetDate $slot")
            put("office_uuid", account.officeUuid)
        }

        val request = Request.Builder()
            .url("$BASE_URL/booking/api/booking")
            .post(payloadJson.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${account.token}")
            .applyAppCheck(settings.appCheck)
            .build()

        return@withContext try {
            val (status, body) = execute(buildClient(settings), request)
            BookingResult(success = status == 200, statusCode = status, body = body)
        } catch (e: Exception) {
            BookingResult(
                success = false,
                statusCode = -1,
                body = "endpoint=/booking/api/booking | slot=$slot | ${e::class.simpleName}: ${e.message.orEmpty()} | network=${describeConnectionMode(settings)}"
            )
        }
    }

    private fun resolveSettings(accountOverride: AccountNetworkOverride?): AppSettings {
        val global = settingsProvider()
        return if (accountOverride == null) global else mergeEffectiveSettings(global, accountOverride)
    }

    private fun buildClient(settings: AppSettings): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        val proxyHost = normalizeProxyHost(settings.proxy.host)
        if (settings.proxy.enabled && proxyHost.isNotBlank() && settings.proxy.port > 0) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, settings.proxy.port)))
            if (settings.proxy.username.isNotBlank()) {
                builder.proxyAuthenticator { _, response ->
                    val credential = Credentials.basic(settings.proxy.username, settings.proxy.password)
                    response.request.newBuilder().header("Proxy-Authorization", credential).build()
                }
            }
        }

        return builder.build()
    }

    private fun Request.Builder.applyAppCheck(appCheck: String): Request.Builder {
        if (appCheck.isNotBlank()) {
            header("x-firebase-appcheck", appCheck)
        }
        return this
    }

    private fun execute(client: OkHttpClient, request: Request): Pair<Int, String> {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return response.code to body
        }
    }
}
