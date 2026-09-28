package com.zalodich

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Gọi backend AI do người dùng cấu hình. API key tuyệt đối không nằm trong APK. */
object AiContextEngine {
    const val PREFS = "zalo_dich_settings"
    const val KEY_ENDPOINT = "ai_proxy_url"
    const val KEY_TOKEN = "ai_proxy_token"

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val acceptedCodes = setOf("vi", "en", "ja", "ko", "zh", "und")

    fun endpoint(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENDPOINT, "")?.trim().orEmpty()

    fun token(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, "")?.trim().orEmpty()

    fun isConfigured(context: Context): Boolean =
        endpoint(context).isNotBlank() && token(context).isNotBlank()

    fun saveConfig(context: Context, endpoint: String, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENDPOINT, endpoint.trim().trimEnd('/'))
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    /**
     * Backend nhận toàn bộ đoạn chat để suy luận ngôn ngữ theo ngữ cảnh.
     * cb(null) khi chưa cấu hình, lỗi mạng hoặc phản hồi không hợp lệ.
     */
    fun detectLanguages(
        context: Context,
        messages: List<ChatLine>,
        localHints: List<String>,
        cb: (List<LanguageDecision>?) -> Unit
    ) {
        val baseUrl = endpoint(context)
        val accessToken = token(context)
        if (baseUrl.isBlank() || accessToken.isBlank() || messages.isEmpty()) {
            cb(null)
            return
        }

        worker.execute {
            val result = runCatching {
                val url = URL("$baseUrl/detect-languages")
                require(url.protocol == "https") {
                    "AI endpoint phải dùng HTTPS"
                }
                val payload = JSONObject().apply {
                    put("messages", JSONArray().apply {
                        messages.forEachIndexed { index, message ->
                            put(JSONObject().apply {
                                put("index", index)
                                put("sender", message.sender.take(80))
                                put("direction", if (message.outgoing) "outgoing" else "incoming")
                                put("text", message.text.take(1000))
                                put("local_hint", localHints.getOrElse(index) { "und" })
                            })
                        }
                    })
                }

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12_000
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Zalo-Dich-Token", accessToken)
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    connection.errorStream?.close()
                    connection.disconnect()
                    error("AI HTTP $responseCode")
                }
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                connection.disconnect()
                parseLanguages(body, messages.size)
            }.getOrNull()
            main.post { cb(result) }
        }
    }

    private fun parseLanguages(body: String, expectedSize: Int): List<LanguageDecision>? {
        val rows = JSONObject(body).optJSONArray("languages") ?: return null
        if (rows.length() != expectedSize) return null
        val result = MutableList(expectedSize) { LanguageDecision("und") }
        val seen = BooleanArray(expectedSize)
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: return null
            val index = row.optInt("index", -1)
            val code = row.optString("code", "und").lowercase()
            if (index !in result.indices || code !in acceptedCodes || seen[index]) return null
            result[index] = LanguageDecision(
                code = code,
                translationVi = row.optString("translation_vi").trim().ifEmpty { null },
                contextNote = row.optString("meaning_vi").trim().ifEmpty { null }
            )
            seen[index] = true
        }
        return result.takeIf { seen.all { it } }
    }
}
