package io.github.stickerbug.dsapichat.api

import io.github.stickerbug.dsapichat.data.Attachment
import io.github.stickerbug.dsapichat.data.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

enum class ApiErrorKind { AUTH, BALANCE, RATE, SERVER, NETWORK }

class ApiException(val kind: ApiErrorKind, val code: Int = 0, message: String = "") : Exception(message)

/** 一条待发送的消息：文本 + 已上传附件的引用（Files API 的 file_id）。 */
data class ApiMsg(
    val role: String,
    val text: String,
    val attachments: List<Attachment> = emptyList()
)

sealed interface StreamEvent {
    data class Delta(val content: String?, val reasoning: String?) : StreamEvent
    data class Searching(val query: String) : StreamEvent
    data class Sources(val items: List<SearchSource>) : StreamEvent
    data class Citation(val text: String, val url: String, val title: String? = null) : StreamEvent
    data class Reading(val url: String) : StreamEvent
    data class Signature(val sig: String) : StreamEvent
    data class Done(val finishReason: String?) : StreamEvent
}

internal object Net {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}

/** 按协议分发到 OpenAI 兼容 /chat/completions 或 Anthropic 兼容 /anthropic/v1/messages。 */
class ChatClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val protocol: ApiProtocol = ApiProtocol.OPENAI
) {
    fun stream(
        model: String,
        messages: List<ApiMsg>,
        thinking: Boolean,
        search: Boolean
    ): Flow<StreamEvent> = when (protocol) {
        ApiProtocol.ANTHROPIC -> streamAnthropic(baseUrl, apiKey, model, messages, thinking, search)
        ApiProtocol.OPENAI -> streamOpenAi(baseUrl, apiKey, model, messages)
    }
}

private fun streamOpenAi(
    baseUrl: String,
    apiKey: String,
    model: String,
    messages: List<ApiMsg>
): Flow<StreamEvent> = flow {
    val payload = buildJsonObject {
        put("model", model)
        put("stream", true)
        put("messages", buildJsonArray {
            messages.forEach { m ->
                add(buildJsonObject {
                    put("role", m.role)
                    put("content", m.text)
                })
            }
        })
    }

    val url = baseUrl.trimEnd('/') + "/chat/completions"
    val request = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer " + apiKey)
        .addHeader("Accept", "text/event-stream")
        .addHeader("Content-Type", "application/json")
        .post(payload.toString().toRequestBody("application/json".toMediaType()))
        .build()

    val response = try {
        Net.http.newCall(request).execute()
    } catch (e: Exception) {
        throw ApiException(ApiErrorKind.NETWORK, 0, e.message ?: "")
    }

    if (!response.isSuccessful) {
        val body = try { response.body?.string() ?: "" } catch (e: Exception) { "" }
        response.close()
        throw ApiException(kindOf(response.code, body), response.code, body)
    }

    val source = response.body?.source()
        ?: throw ApiException(ApiErrorKind.SERVER, response.code, "empty body")

    try {
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data.isEmpty()) continue
            if (data == "[DONE]") {
                emit(StreamEvent.Done(null))
                break
            }
            val obj = try { Net.json.parseToJsonElement(data).jsonObject } catch (e: Exception) { continue }
            val choices = obj["choices"] as? JsonArray ?: continue
            val first = choices.firstOrNull()?.jsonObject ?: continue
            val delta = first["delta"]?.jsonObject
            val content = (delta?.get("content") as? JsonPrimitive)?.contentOrNullSafe()
            val reasoning = (delta?.get("reasoning_content") as? JsonPrimitive)?.contentOrNullSafe()
            val finish = (first["finish_reason"] as? JsonPrimitive)?.contentOrNullSafe()
            if (content != null || reasoning != null) {
                emit(StreamEvent.Delta(content, reasoning))
            }
            if (finish != null) {
                emit(StreamEvent.Done(finish))
                break
            }
        }
    } finally {
        try { response.close() } catch (e: Exception) { }
    }
}.flowOn(Dispatchers.IO)

internal fun kindOf(code: Int, body: String): ApiErrorKind = when {
    code == 401 || code == 403 -> ApiErrorKind.AUTH
    code == 402 -> ApiErrorKind.BALANCE
    code == 429 -> ApiErrorKind.RATE
    code in 500..599 -> ApiErrorKind.SERVER
    body.contains("insufficient", true) -> ApiErrorKind.BALANCE
    else -> ApiErrorKind.SERVER
}

private fun JsonPrimitive.contentOrNullSafe(): String? =
    if (isString) content else null
