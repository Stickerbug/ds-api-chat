package io.github.stickerbug.dsapichat.api

import io.github.stickerbug.dsapichat.data.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class ApiProtocol { OPENAI, ANTHROPIC }

/** 设置项 protocol 为 auto 时按接口地址与模型名推断。 */
internal fun resolveProtocol(choice: String, baseUrl: String, model: String): ApiProtocol = when (choice) {
    "openai" -> ApiProtocol.OPENAI
    "anthropic" -> ApiProtocol.ANTHROPIC
    else -> when {
        baseUrl.contains("anthropic", true) -> ApiProtocol.ANTHROPIC
        model.startsWith("deepseek-flash") || model.startsWith("deepseek-v4") -> ApiProtocol.ANTHROPIC
        else -> ApiProtocol.OPENAI
    }
}

private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun messagesUrl(baseUrl: String): String {
    val b = baseUrl.trimEnd('/')
    return when {
        b.isEmpty() -> "https://api.deepseek.com/anthropic/v1/messages"
        b.endsWith("/messages") -> b
        b.endsWith("/v1") -> b + "/messages"
        b.contains("/anthropic") -> b + "/messages"
        else -> b + "/anthropic/v1/messages"
    }
}

/**
 * DeepSeek 的 Anthropic 兼容端点：POST /anthropic/v1/messages。
 * 支持 thinking 扩展思考块与 deepseek_web_search / deepseek_read_url 服务端工具。
 */
internal fun streamAnthropic(
    baseUrl: String,
    apiKey: String,
    model: String,
    messages: List<ApiMsg>,
    thinking: Boolean,
    search: Boolean
): Flow<StreamEvent> = flow {
    fun buildPayload(withTools: Boolean): String = buildJsonObject {
        put("model", model)
        put("max_tokens", 8192)
        put("stream", true)
        put("system", "You are DeepSeek, a helpful assistant.")
        put("thinking", buildJsonObject { put("type", if (thinking) "enabled" else "disabled") })
        put("messages", buildJsonArray {
            messages.forEach { m ->
                add(buildJsonObject {
                    put("role", m.role)
                    put("content", buildJsonArray {
                        m.attachments.forEach { a ->
                            if (a.fileId.isBlank()) return@forEach
                            add(buildJsonObject {
                                put("type", if (a.image) "image" else "document")
                                put("source", buildJsonObject {
                                    put("type", "file")
                                    put("file_id", a.fileId)
                                })
                            })
                        }
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", m.text)
                        })
                    })
                })
            }
        })
        if (search && withTools) {
            put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("type", "deepseek_web_search")
                    put("name", "deepseek_web_search")
                })
                add(buildJsonObject {
                    put("type", "deepseek_read_url")
                    put("name", "deepseek_read_url")
                })
            })
        }
    }.toString()

    // 带附件的请求需要 files-api beta 头，否则服务端不认识 file_id 引用。
    val hasFiles = messages.any { m -> m.attachments.any { it.fileId.isNotBlank() } }

    fun makeRequest(body: String): Request = Request.Builder()
        .url(messagesUrl(baseUrl))
        .addHeader("x-api-key", apiKey)
        .addHeader("Authorization", "Bearer " + apiKey)
        .addHeader("Accept", "text/event-stream")
        .addHeader("Content-Type", "application/json")
        .apply { if (hasFiles) addHeader("anthropic-beta", FileApi.BETA) }
        .post(body.toRequestBody("application/json".toMediaType()))
        .build()

    fun exec(body: String): okhttp3.Response = try {
        Net.http.newCall(makeRequest(body)).execute()
    } catch (e: Exception) {
        throw ApiException(ApiErrorKind.NETWORK, 0, e.message ?: "")
    }

    var response = exec(buildPayload(true))
    if (!response.isSuccessful) {
        val code = response.code
        val body = try { response.body?.string() ?: "" } catch (e: Exception) { "" }
        response.close()
        // 上下文超限（服务端工具注入的检索结果也算 token）时去掉 tools 重发一次。
        val tooLong = code == 400 && (body.contains("too long") || body.contains("exceeds max"))
        if (!tooLong) throw ApiException(kindOf(code, body), code, body)
        response = exec(buildPayload(false))
        if (!response.isSuccessful) {
            val code2 = response.code
            val body2 = try { response.body?.string() ?: "" } catch (e: Exception) { "" }
            response.close()
            throw ApiException(kindOf(code2, body2), code2, body2)
        }
    }

    val source = response.body?.source()
        ?: throw ApiException(ApiErrorKind.SERVER, response.code, "empty body")

    var stopReason: String? = null
    var finished = false
    // 服务端工具的参数是流式分片下发的（input_json_delta），要攒起来再解析。
    val toolName = HashMap<Int, String>()
    val toolInput = HashMap<Int, StringBuilder>()

    suspend fun announceTool(name: String?, input: JsonObject?) {
        if (name == null || input == null) return
        if (name == "deepseek_read_url") {
            input["url"].str()?.takeIf { it.isNotBlank() }?.let { emit(StreamEvent.Reading(it)) }
        } else {
            val q = input["query"].str()
                ?: (input["queries"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.content }
            if (!q.isNullOrBlank()) emit(StreamEvent.Searching(q))
        }
    }

    try {
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data.isEmpty() || data == "[DONE]") continue
            val obj = try { Net.json.parseToJsonElement(data).jsonObject } catch (e: Exception) { continue }
            when (obj["type"].str()) {
                "content_block_start" -> {
                    val block = obj["content_block"]?.jsonObject ?: continue
                    when (block["type"].str()) {
                        "server_tool_use" -> {
                            val idx = (obj["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                            toolName[idx] = block["name"].str() ?: ""
                            toolInput[idx] = StringBuilder()
                            announceTool(block["name"].str(), block["input"]?.jsonObject)
                        }
                        "web_search_tool_result" -> {
                            val items = searchResults(block)
                            if (items.isNotEmpty()) emit(StreamEvent.Sources(items))
                        }
                        "web_fetch_tool_result" -> {
                            val content = block["content"] as? JsonObject
                            if (content != null && content["type"].str() == "web_fetch_result") {
                                val url = content["url"].str()
                                if (!url.isNullOrBlank()) emit(StreamEvent.Reading(url))
                            }
                        }
                    }
                }
                "content_block_delta" -> {
                    val d = obj["delta"]?.jsonObject ?: continue
                    when (d["type"].str()) {
                        "text_delta" -> d["text"].str()?.let { emit(StreamEvent.Delta(it, null)) }
                        "thinking_delta" -> d["thinking"].str()?.let { emit(StreamEvent.Delta(null, it)) }
                        "signature_delta" -> d["signature"].str()?.let { if (it.isNotEmpty()) emit(StreamEvent.Signature(it)) }
                        "input_json_delta" -> {
                            val idx = (obj["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                            d["partial_json"].str()?.let { piece -> toolInput[idx]?.append(piece) }
                        }
                        "citations_delta" -> {
                            val cit = d["citation"]?.jsonObject
                            if (cit != null && cit["type"].str() == "web_search_result_location") {
                                val url = cit["url"].str() ?: ""
                                if (url.isNotEmpty()) {
                                    emit(StreamEvent.Citation(
                                        text = cit["cited_text"].str() ?: "",
                                        url = url,
                                        title = cit["title"].str()
                                    ))
                                    emit(StreamEvent.Sources(listOf(
                                        SearchSource(
                                            title = cit["title"].str() ?: url,
                                            url = url,
                                            snippet = cit["cited_text"].str()
                                        )
                                    )))
                                }
                            }
                        }
                    }
                }
                "content_block_stop" -> {
                    val idx = (obj["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                    val name = toolName.remove(idx)
                    val buf = toolInput.remove(idx)
                    if (name != null && buf != null && buf.isNotEmpty()) {
                        val input = runCatching {
                            Net.json.parseToJsonElement(buf.toString()).jsonObject
                        }.getOrNull()
                        announceTool(name, input)
                    }
                }
                "message_delta" -> {
                    stopReason = obj["delta"]?.jsonObject?.get("stop_reason").str() ?: stopReason
                }
                "message_stop" -> {
                    finished = true
                    emit(StreamEvent.Done(stopReason))
                    break
                }
                "error" -> throw ApiException(ApiErrorKind.SERVER, 0, data)
            }
        }
        if (!finished) emit(StreamEvent.Done(stopReason))
    } finally {
        try { response.close() } catch (e: Exception) { }
    }
}.flowOn(Dispatchers.IO)

private fun searchResults(block: JsonObject): List<SearchSource> {
    val content = block["content"] as? JsonArray ?: return emptyList()
    return content.mapNotNull { it as? JsonObject }
        .filter { it["type"].str() == "web_search_result" }
        .map {
            SearchSource(
                title = it["title"].str() ?: "",
                url = it["url"].str() ?: "",
                siteName = it["site_name"].str(),
                siteIcon = it["site_icon"].str(),
                snippet = it["snippet"].str(),
                pageAge = it["page_age"].str()
            )
        }
        .filter { it.url.isNotEmpty() }
}
