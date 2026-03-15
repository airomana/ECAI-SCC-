package com.eldercare.ai.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SmsGatewayClient {

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun sendCode(
        url: String,
        token: String?,
        phone: String,
        code: String,
        expireMinutes: Int
    ): Result<Unit> {
        return runCatching {
            val bodyJson = JSONObject()
                .put("phone", phone)
                .put("code", code)
                .put("expireMinutes", expireMinutes)
                .toString()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody(jsonMediaType))

            val t = token?.trim().orEmpty()
            if (t.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $t")
            }

            client.newCall(requestBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IllegalStateException("短信网关返回错误：${resp.code} ${err.take(200)}")
                }
            }
        }
    }
}
