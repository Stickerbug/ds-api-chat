package io.github.stickerbug.dsapichat.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * 读取 DeepSeek 官方「导出所有历史对话」生成的文件，以及本应用自己导出的 JSON。
 *
 * 官方导出通常是一个 zip，里面有一个 conversations.json，结构为：
 * [
 *   {
 *     "id": "...", "title": "...", "inserted_at": "...", "updated_at": "...",
 *     "current_node": "4",
 *     "mapping": {
 *       "root": { "id": "root", "parent": null, "children": ["1"], "message": null },
 *       "1": { "id": "1", "parent": "root", "children": ["2", "3"],
 *              "message": { "inserted_at": "...", "fragments": [
 *                 { "type": "REQUEST", "content": "..." } ] } },
 *       "3": { ... "fragments": [ { "type": "THINK", "content": "..." },
 *                                 { "type": "RESPONSE", "content": "..." } ] }
 *     }
 *   }
 * ]
 *
 * 重新生成/编辑过的对话在 mapping 里会分叉，这里沿 current_node 回溯到 root，
 * 只导入当前分支，历史分支不导入（与官方行为一致）。
 */
object ConversationImport {

    data class Report(
        val conversations: List<Conversation>,
        val warnings: List<String> = emptyList()
    )

    private const val MAX_BYTES = 64 * 1024 * 1024

    /** 解析失败时抛出 IllegalArgumentException，message 可直接展示给用户。 */
    fun parse(bytes: ByteArray): Report {
        require(bytes.isNotEmpty()) { "文件是空的" }
        require(bytes.size <= MAX_BYTES) { "文件太大（超过 64 MB）" }

        val text = if (isZip(bytes)) readZip(bytes) else bytes.toString(Charsets.UTF_8).trim()
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("不是有效的 JSON 文件")
        }

        val items: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> when {
                root["conversations"] is JsonArray -> root["conversations"] as JsonArray
                root.containsKey("mapping") || root.containsKey("messages") -> JsonArray(listOf(root))
                else -> throw IllegalArgumentException("无法识别的 JSON 结构")
            }
            else -> throw IllegalArgumentException("无法识别的 JSON 结构")
        }

        val warnings = mutableListOf<String>()
        val out = mutableListOf<Conversation>()
        var skipped = 0
        for (item in items) {
            val obj = item as? JsonObject
            if (obj == null) {
                skipped++
                continue
            }
            val conv = try {
                parseConversation(obj)
            } catch (e: Exception) {
                null
            }
            if (conv != null && conv.messages.isNotEmpty()) out += conv else skipped++
        }
        if (skipped > 0) warnings += "跳过 $skipped 个空对话或无法解析的条目"
        if (out.isEmpty()) throw IllegalArgumentException("没有找到可导入的对话")
        return Report(out, warnings)
    }

    // ------------------------------------------------------------------ zip

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun readZip(bytes: ByteArray): String {
        var fallback: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.substringAfterLast('/').lowercase()
                    val data = readEntry(zip)
                    if (name == "conversations.json" || name.endsWith("conversations.json")) {
                        return data.toString(Charsets.UTF_8)
                    }
                    if (fallback == null && entry.name.lowercase().endsWith(".json")) {
                        fallback = data.toString(Charsets.UTF_8)
                    }
                }
                entry = zip.nextEntry
            }
        }
        return fallback ?: throw IllegalArgumentException("压缩包里没有 conversations.json")
    }

    private fun readEntry(zip: ZipInputStream): ByteArray {
        val buf = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = zip.read(chunk)
            if (n <= 0) break
            total += n
            if (total > MAX_BYTES) throw IllegalArgumentException("压缩包内的 JSON 太大")
            buf.write(chunk, 0, n)
        }
        return buf.toByteArray()
    }

    // ----------------------------------------------------------- conversation

    private fun parseConversation(obj: JsonObject): Conversation? {
        val mapping = obj["mapping"] as? JsonObject
        val messages = if (mapping != null) parseMapping(obj, mapping) else parseMessageArray(obj)
        if (messages.isEmpty()) return null

        val id = obj.str("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val title = obj.str("title")?.trim()?.takeIf { it.isNotBlank() }
            ?: messages.firstOrNull { it.role == Role.USER }?.text?.replace('\n', ' ')?.take(30)
            ?: "未命名对话"
        val updated = obj.time("updated_at")
            ?: messages.lastOrNull { it.ts > 0 }?.ts
            ?: System.currentTimeMillis()
        return Conversation(
            id = id,
            title = title.take(80),
            updatedAt = updated,
            messages = messages.toMutableList()
        )
    }

    private fun parseMapping(conv: JsonObject, mapping: JsonObject): List<Msg> {
        data class Node(
            val id: String,
            val parent: String?,
            val children: List<String>,
            val message: JsonObject?
        )

        val nodes = LinkedHashMap<String, Node>()
        for ((key, value) in mapping) {
            val o = value as? JsonObject ?: continue
            val id = o.str("id")?.takeIf { it.isNotBlank() } ?: key
            val children = (o["children"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
            nodes[id] = Node(
                id = id,
                parent = o.str("parent")?.takeIf { it.isNotBlank() && it != "null" },
                children = children,
                message = o["message"] as? JsonObject
            )
        }
        if (nodes.isEmpty()) return emptyList()

        // 当前分支末端：优先 current_node，其次取 inserted_at 最新的消息节点
        val currentId = conv.str("current_node")?.takeIf { nodes.containsKey(it) }
        var cursor: Node? = currentId?.let { nodes[it] }
            ?: nodes.values.filter { it.message != null }.maxByOrNull { nodeTime(it.message) }

        val path = mutableListOf<Node>()
        val seen = mutableSetOf<String>()
        while (cursor != null && seen.add(cursor.id)) {
            path += cursor
            cursor = cursor.parent?.let { nodes[it] }
        }
        path.reverse()

        val result = mutableListOf<Msg>()
        for (node in path) {
            val message = node.message ?: continue
            result += parseNodeMessage(message)
        }
        if (result.isNotEmpty()) return result

        // mapping 结构异常时降级：导入全部消息，按时间排序
        return nodes.values.mapNotNull { it.message }
            .sortedBy { nodeTime(it) }
            .flatMap { parseNodeMessage(it) }
    }

    private fun nodeTime(message: JsonObject?): Long =
        message?.time("inserted_at") ?: 0L

    private fun parseNodeMessage(message: JsonObject): List<Msg> {
        val fragments = message["fragments"] as? JsonArray
        val ts = nodeTime(message)
        val request = StringBuilder()
        val response = StringBuilder()
        val think = StringBuilder()
        val sources = mutableListOf<SearchSource>()

        if (fragments != null) {
            for (fragment in fragments) {
                val f = fragment as? JsonObject ?: continue
                when (f.str("type")?.uppercase()) {
                    "REQUEST" -> appendText(request, flatten(f["content"]))
                    "RESPONSE" -> appendText(response, flatten(f["content"]))
                    "THINK" -> appendText(think, flatten(f["content"]))
                    "SEARCH" -> sources += parseSearchResults(f)
                    else -> Unit
                }
            }
        }

        // 兼容 role/content 结构
        if (request.isEmpty() && response.isEmpty() && think.isEmpty()) {
            val role = message.str("role")?.lowercase()
            val content = flatten(message["content"] ?: message["text"])
            when {
                role == "user" || role == "human" -> appendText(request, content)
                role == "assistant" || role == "ai" || role == "model" -> appendText(response, content)
                else -> {
                    val type = message.str("type")?.uppercase()
                    if (type == "REQUEST") appendText(request, content)
                    else appendText(response, content)
                }
            }
        }

        val out = mutableListOf<Msg>()
        if (request.isNotBlank()) {
            out += Msg(id = UUID.randomUUID().toString(), role = Role.USER, text = request.toString().trim(), ts = ts)
        }
        if (response.isNotBlank() || think.isNotBlank()) {
            out += Msg(
                id = UUID.randomUUID().toString(),
                role = Role.ASSISTANT,
                text = response.toString().trim(),
                reasoning = think.toString().trim().takeIf { it.isNotBlank() },
                ts = ts,
                sources = sources
            )
        }
        return out
    }

    private fun parseSearchResults(fragment: JsonObject): List<SearchSource> {
        val results = fragment["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val url = o.str("url") ?: return@mapNotNull null
            SearchSource(
                title = o.str("title") ?: "",
                url = url,
                siteName = o.str("site_name"),
                siteIcon = o.str("site_icon"),
                snippet = o.str("snippet"),
                pageAge = o.str("published_at")
            )
        }
    }

    /** 本应用自己导出的 JSON：conversations 数组或单个对象，messages 内含 role/text。 */
    private fun parseMessageArray(obj: JsonObject): List<Msg> {
        val arr = obj["messages"] as? JsonArray ?: return emptyList()
        val convTs = obj.time("updated_at") ?: 0L
        return arr.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val roleName = o.str("role")?.uppercase()
            val role = when (roleName) {
                "USER", "HUMAN" -> Role.USER
                "ASSISTANT", "AI", "MODEL" -> Role.ASSISTANT
                else -> return@mapNotNull null
            }
            val text = o.str("text") ?: flatten(o["content"])
            val reasoning = o.str("reasoning")
            if (text.isBlank() && reasoning.isNullOrBlank()) return@mapNotNull null
            Msg(
                id = o.str("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                role = role,
                text = text,
                reasoning = reasoning?.takeIf { it.isNotBlank() },
                thinkMs = o["thinkMs"]?.let { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() } ?: 0L,
                err = o.str("err"),
                ts = o.time("ts") ?: convTs,
                searchQuery = o.str("searchQuery"),
                sources = parseNativeSources(o["sources"]),
                citations = emptyList(),
                attachments = emptyList()
            )
        }
    }

    private fun parseNativeSources(el: JsonElement?): List<SearchSource> {
        val arr = el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            SearchSource(
                title = o.str("title") ?: "",
                url = o.str("url") ?: "",
                siteName = o.str("siteName"),
                siteIcon = o.str("siteIcon"),
                snippet = o.str("snippet"),
                pageAge = o.str("pageAge")
            )
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun appendText(builder: StringBuilder, text: String) {
        if (text.isBlank()) return
        if (builder.isNotEmpty()) builder.append('\n')
        builder.append(text.trim())
    }

    private fun flatten(element: JsonElement?): String = when (element) {
        null, is JsonNull -> ""
        is JsonPrimitive -> element.contentOrNull ?: element.toString()
        is JsonArray -> element.map { flatten(it) }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        is JsonObject -> {
            val keys = listOf("text", "content", "parts", "value", "title", "name")
            keys.firstNotNullOfOrNull { key ->
                flatten(element[key]).takeIf { it.isNotBlank() }
            } ?: ""
        }
        else -> ""
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.time(key: String): Long? =
        str(key)?.let { parseTime(it) }

    private fun parseTime(raw: String): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        value.toLongOrNull()?.let { n ->
            return if (n in 1..9_999_999_999L) n * 1000 else n
        }
        val normalized = if (value.length >= 19 && value[10] == ' ') value.replaceFirst(' ', 'T') else value
        return try {
            Instant.parse(normalized).toEpochMilli()
        } catch (e: Exception) {
            try {
                OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                null
            }
        }
    }
}
