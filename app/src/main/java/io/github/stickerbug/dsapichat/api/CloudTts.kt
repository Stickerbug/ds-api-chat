package io.github.stickerbug.dsapichat.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 云端朗读合成（OpenAI 兼容 /audio/speech）。
 * 官方朗读是云端神经语音；复刻版默认用系统 TTS，配置了接口后改用云端合成，音色更自然，
 * 且在没有系统 TTS 引擎的设备上也能朗读。
 */
internal object CloudTts {

    fun endpoint(base: String): String {
        val b = base.trim().trimEnd('/')
        return if (b.endsWith("/audio/speech")) b else b + "/audio/speech"
    }

    suspend fun synth(
        base: String,
        apiKey: String,
        model: String,
        voice: String,
        text: String
    ): ByteArray = withContext(Dispatchers.IO) {
        if (base.isBlank()) throw IllegalStateException("还没有配置朗读接口")
        val payload = buildJsonObject {
            put("model", model.ifBlank { "tts-1" })
            put("input", text)
            put("voice", voice.ifBlank { "alloy" })
            put("response_format", "mp3")
        }
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val builder = Request.Builder().url(endpoint(base)).post(body)
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer " + apiKey.trim())
        val client = Net.http.newBuilder()
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        client.newCall(builder.build()).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP " + resp.code + " " + String(bytes).take(120))
            }
            if (bytes.isEmpty()) throw IllegalStateException("返回的音频为空")
            bytes
        }
    }
}
