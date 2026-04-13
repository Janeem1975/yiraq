package com.example.ayniqbooking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GateLoginResult(
    val token: String,
    val email: String
)

class GateApi {
    companion object {
        const val BLOCKED_CODE = "ACCOUNT_BLOCKED"
        const val DEVICE_BLOCKED_CODE = "DEVICE_BLOCKED"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun login(
        email: String,
        password: String,
        deviceId: String
    ): Result<GateLoginResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("email", email.trim().lowercase())
                .put("password", password)
                .put("device_id", deviceId)

            val request = Request.Builder()
                .url("$GATE_SERVER_URL/auth/login")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val (status, body) = execute(request)
            if (status !in 200..299) {
                val detail = extractDetail(body)
                if (status == 403 && detail.contains("inactive", ignoreCase = true)) {
                    throw IOException(BLOCKED_CODE)
                }
                if (status == 403 && detail.contains("device blocked", ignoreCase = true)) {
                    throw IOException(DEVICE_BLOCKED_CODE)
                }
                throw IOException("Login rejected: HTTP $status - $detail")
            }

            val root = JSONObject(body)
            val token = root.optString("token")
            if (token.isBlank()) throw IOException("Missing token")
            GateLoginResult(token = token, email = email.trim().lowercase())
        }
    }

    suspend fun validate(token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$GATE_SERVER_URL/auth/me")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val (status, body) = execute(request)
            if (status !in 200..299) {
                val detail = extractDetail(body)
                if (status == 403 && detail.contains("inactive", ignoreCase = true)) {
                    throw IOException(BLOCKED_CODE)
                }
                if (status == 403 && detail.contains("device blocked", ignoreCase = true)) {
                    throw IOException(DEVICE_BLOCKED_CODE)
                }
                throw IOException("Token invalid: HTTP $status")
            }

            val root = JSONObject(body)
            val active = root.optBoolean("active", false)
            val email = root.optString("email")
            if (!active) throw IOException(BLOCKED_CODE)
            email
        }
    }

    private fun execute(request: Request): Pair<Int, String> {
        client.newCall(request).execute().use { response ->
            return response.code to response.body?.string().orEmpty()
        }
    }

    private fun extractDetail(body: String): String {
        return runCatching { JSONObject(body).optString("detail").ifBlank { body } }
            .getOrElse { body }
    }
}
