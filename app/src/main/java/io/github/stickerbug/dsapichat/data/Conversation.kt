package io.github.stickerbug.dsapichat.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class Role {
    USER, ASSISTANT;

    val apiName: String
        get() = if (this == USER) "user" else "assistant"
}

@Serializable
data class SearchSource(
    val title: String = "",
    val url: String = "",
    val siteName: String? = null,
    /** web_search_result.site_icon，站点 favicon URL */
    val siteIcon: String? = null,
    val snippet: String? = null,
    /** web_search_result.page_age，形如 2025/03/21 */
    val pageAge: String? = null
)

@Serializable
data class Citation(
    /** 被引用的原文（后端 citations_delta.cited_text），用于在正文里定位插入角标 */
    val text: String = "",
    val url: String = "",
    /** 与搜索结果列表一致的序号，从 1 开始 */
    val index: Int = 1
)

@Serializable
data class Attachment(
    val name: String = "",
    val mime: String = "",
    /** Files API 返回的 file_id；为空表示尚未上传成功 */
    val fileId: String = "",
    val image: Boolean = false,
    val size: Long = 0L,
    /** 本地缓存副本路径（仅图片），用于气泡与输入框里显示缩略图 */
    val localPath: String? = null
)

@Serializable
data class Msg(
    val id: String,
    val role: Role,
    val text: String = "",
    val reasoning: String? = null,
    val thinkMs: Long = 0L,
    val err: String? = null,
    val ts: Long = 0L,
    val searchQuery: String? = null,
    val sources: List<SearchSource> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val citations: List<Citation> = emptyList(),
    /** 正在阅读的页面（deepseek_read_url 的中间状态） */
    val reading: String? = null,
    /** 思考块签名，续写时回传 */
    val signature: String? = null
)

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: MutableList<Msg> = mutableListOf(),
    val pinned: Boolean = false
)

class ConversationStore(context: Context) {
    private val file = File(context.filesDir, "conversations.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): List<Conversation> = try {
        if (!file.exists()) emptyList()
        else json.decodeFromString<List<Conversation>>(file.readText())
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<Conversation>) {
        try {
            file.writeText(json.encodeToString(list))
        } catch (e: Exception) {
            // ignore disk errors
        }
    }

    /** 导出用：把对话列表序列化成与本机存储一致的 JSON。 */
    fun encode(list: List<Conversation>): String = json.encodeToString(list)
}
