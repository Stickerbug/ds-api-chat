package io.github.stickerbug.dsapichat.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * DeepSeek Files API（Anthropic 兼容）：POST {base}/anthropic/v1/files。
 * multipart 字段 file，可选 purpose；请求头带 anthropic-beta: files-api-2025-04-14。
 */
internal object FileApi {

    const val BETA = "files-api-2025-04-14"
    const val MAX_BYTES = 64L * 1024 * 1024
    private val LAYOUT_EXTS = setOf("pdf", "doc", "docx", "ppt", "pptx")

    /** 图片不传 purpose；PDF/Word/PPT 需要服务端做版面解析；其余按 user_data 上传。 */
    fun purposeFor(name: String, image: Boolean): String? {
        if (image) return null
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0) name.substring(dot + 1).lowercase() else ""
        return if (LAYOUT_EXTS.contains(ext)) "rich_layout_recognition" else "user_data"
    }

    fun filesUrl(baseUrl: String): String = messagesUrl(baseUrl).removeSuffix("/messages") + "/files"

    suspend fun upload(
        baseUrl: String,
        apiKey: String,
        name: String,
        mime: String,
        bytes: ByteArray
    ): String = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) throw ApiException(ApiErrorKind.SERVER, 400, "empty file")
        if (bytes.size > MAX_BYTES) throw ApiException(ApiErrorKind.SERVER, 413, "file too large")
        val safeMime = if (mime.isBlank()) "application/octet-stream" else mime
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", name, bytes.toRequestBody(safeMime.toMediaTypeOrNull()))
        val purpose = purposeFor(name, safeMime.startsWith("image/"))
        if (purpose != null) builder.addFormDataPart("purpose", purpose)
        val request = Request.Builder()
            .url(filesUrl(baseUrl))
            .addHeader("x-api-key", apiKey)
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("anthropic-beta", BETA)
            .post(builder.build())
            .build()
        val response = try {
            Net.http.newCall(request).execute()
        } catch (e: Exception) {
            throw ApiException(ApiErrorKind.NETWORK, 0, e.message ?: "")
        }
        response.use {
            val text = try { it.body?.string() ?: "" } catch (e: Exception) { "" }
            if (!it.isSuccessful) throw ApiException(kindOf(it.code, text), it.code, text)
            val id = runCatching {
                Net.json.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.content
            }.getOrNull()
            id ?: throw ApiException(ApiErrorKind.SERVER, it.code, text)
        }
    }
}
