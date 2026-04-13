package com.example.ayniqbooking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches the AppCheck token from the control server,
 * mirroring the Python script's get_global_appcheck() function.
 */
class AppCheckFetcher(
    private val serverUrl: String = GATE_SERVER_URL,
    private val adminKey: String = ""
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches the current AppCheck token from the control server.
     *
     * Endpoint: GET {serverUrl}/admin/booking/settings
     * Header:   x-admin-key: {adminKey}
     * Response: { "app_check": "eyJ..." }
     */
    suspend fun fetchAppCheckToken(
        customServerUrl: String? = null,
        customAdminKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = (customServerUrl ?: serverUrl).trimEnd('/')
            val key = customAdminKey ?: adminKey

            if (key.isBlank()) {
                throw IOException("مفتاح الأدمن (Admin Key) مطلوب لسحب التوكن من السيرفر")
            }

            val request = Request.Builder()
                .url("$url/admin/booking/settings")
                .header("x-admin-key", key)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IOException("فشل الاتصال بالسيرفر: HTTP ${response.code} - $body")
            }

            val json = JSONObject(body)
            val token = json.optString("app_check", "")

            if (token.isBlank()) {
                throw IOException("لم يتم العثور على توكن AppCheck في السيرفر. تأكد من إعداده في لوحة التحكم.")
            }

            val validationError = validateAppCheck(token, required = true)
            if (validationError != null) {
                throw IOException("التوكن المُستلم غير صالح: $validationError")
            }

            token
        }
    }
}
