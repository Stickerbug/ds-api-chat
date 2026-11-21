package io.github.stickerbug.dsapichat.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 云端语音识别（OpenAI 兼容 /audio/transcriptions）。
 * 官方 App 的语音提问走 DeepSeek 自家云端 ASR；复刻版无法复用该服务，
 * 因此在用户配置了接口地址时改用标准 OpenAI 兼容端点（whisper-1 等）。
 */
internal object CloudAsr {

    fun endpoint(base: String): String {
        val b = base.trim().trimEnd('/')
        return if (b.endsWith("/audio/transcriptions")) b else b + "/audio/transcriptions"
    }

    suspend fun transcribe(
        base: String,
        apiKey: String,
        model: String,
        language: String = "zh",
        bytes: ByteArray,
        fileName: String,
        mime: String
    ): String = withContext(Dispatchers.IO) {
        if (base.isBlank()) throw IllegalStateException("还没有配置语音识别接口")
        if (bytes.isEmpty()) throw IllegalStateException("录音为空")
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaTypeOrNull()))
            .addFormDataPart("model", model.ifBlank { "whisper-1" })
        if (language.isNotBlank() && language != "auto") form.addFormDataPart("language", language)
        val body = form.addFormDataPart("response_format", "json").build()
        val builder = Request.Builder().url(endpoint(base)).post(body)
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer " + apiKey.trim())
        val client = Net.http.newBuilder()
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP " + resp.code + " " + text.take(120))
            extract(text)
        }
    }

    /** 兼容常见返回体：{"text"}、{"result"}、{"data":{"text"}}、{"choices":[{"message":{"content"}}]}。 */
    private fun extract(raw: String): String {
        val el = runCatching { Net.json.parseToJsonElement(raw) }.getOrNull() ?: return raw.trim()
        if (el is JsonObject) {
            for (k in listOf("text", "result", "transcript")) {
                val v = el[k]
                if (v is JsonPrimitive && v.isString && v.content.isNotBlank()) return v.content.trim()
            }
            (el["data"] as? JsonObject)?.get("text")?.let {
                if (it is JsonPrimitive && it.isString) return it.content.trim()
            }
            (el["choices"] as? JsonArray)?.firstOrNull()?.let { c ->
                val co = c as? JsonObject ?: return@let
                ((co["message"] as? JsonObject)?.get("content") as? JsonPrimitive)?.let {
                    if (it.isString) return it.content.trim()
                }
                (co["text"] as? JsonPrimitive)?.let { if (it.isString) return it.content.trim() }
            }
        }
        return raw.trim()
    }
}
