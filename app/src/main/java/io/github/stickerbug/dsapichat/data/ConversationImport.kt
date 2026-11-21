package io.github.stickerbug.dsapichat.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * 读取 DeepSeek 官方「导出所有历史对话」生成的文件，以及本应用自己导出的 JSON。
 *
 * 官方目前有两种导出格式：
 *
 * 1. 旧格式（export_format_version 1/2）：zip 里一个 `conversations.json`，形如
 *    `[{ "id": ..., "current_node": "4", "mapping": { ... } }]`，
 *    分叉靠 current_node 回溯，只导入当前分支。
 *
 * 2. 新格式（export_format_version 3，deepseek_data-*.zip）：
 *    `chat_sessions/<uuid>.json` 每个会话一个文件，另有 `chat_session_index.json`、
 *    `manifest.json`、`user.json` 和 `chat_sessions/<uuid>_files/` 附件目录。
 *    会话结构：
 *    `{ id, title, pinned, current_message_id, inserted_at, updated_at,
 *       messages: [{ id, parent_id, role: "USER"/"ASSISTANT", inserted_at,
 *                    fragments: [ {type:"REQUEST"|"RESPONSE"|"THINK"|"FILE"|"TOOL_SEARCH", ...} ] }] }`
 *    分叉同样靠 current_message_id 沿 parent_id 回溯当前分支；附件在 FILE 片段里，
 *    带 export_path 指向 zip 里的图片文件。
 *
 * 图片附件会通过 [MediaSaver] 落盘，Msg.attachments[i].localPath 指向本地文件；
 * 未导出/非图片附件只保留文件名。
 */
object ConversationImport {

    data class Report(
        val conversations: List<Conversation>,
        val warnings: List<String> = emptyList()
    )

    /** 把官方导出里的图片附件写到应用目录，返回可用于显示的本地绝对路径；失败返回 null。 */
    fun interface MediaSaver {
        fun save(name: String, bytes: ByteArray): String?
    }

    private const val MAX_BYTES = 64L * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 16L * 1024 * 1024

    /** 解析失败时抛出 IllegalArgumentException，message 可直接展示给用户。 */
    fun parse(bytes: ByteArray, media: MediaSaver? = null): Report {
        require(bytes.isNotEmpty()) { "文件是空的" }
        require(bytes.size <= MAX_BYTES) { "文件太大（超过 64 MB）" }
        if (isZip(bytes)) return parseZip(bytes, media)
        val root = try {
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8).trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("不是有效的 JSON 文件")
        }
        return parseRoot(root)
    }

    // ------------------------------------------------------------------ zip

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private class PendingSession(
        val conversation: Conversation,
        val images: MutableList<PendingImage> = mutableListOf()
    )

    private class PendingImage(
        val msgIndex: Int,
        val attIndex: Int,
        val exportPath: String
    )

    private fun parseZip(bytes: ByteArray, media: MediaSaver?): Report {
        val warnings = mutableListOf<String>()
        val sessions = mutableListOf<PendingSession>()
        var oldRoot: JsonElement? = null
        var fallbackRoot: JsonElement? = null
        var sawSessionFiles = false
        var skipped = 0

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/')
                    val lower = name.lowercase()
                    if (lower.endsWith(".json")) {
                        val data = readEntry(zip, MAX_BYTES)
                        val fileName = name.substringAfterLast('/').lowercase()
                        when {
                            fileName == "conversations.json" ->
                                oldRoot = parseJsonOrNull(data)
                            fileName == "chat_session_index.json" || fileName == "manifest.json" || fileName == "user.json" ->
                                Unit
                            name.startsWith("chat_sessions/") && !name.contains("_files/") -> {
                                sawSessionFiles = true
                                val obj = parseJsonOrNull(data) as? JsonObject
                                val pending = try {
                                    obj?.let { parseSessionObject(it) }
                                } catch (e: Exception) {
                                    null
                                }
                                if (pending != null) sessions += pending else skipped++
                            }
                            fallbackRoot == null -> fallbackRoot = parseJsonOrNull(data)
                        }
                    }
                }
                entry = zip.nextEntry
            }
        }

        if (sessions.isNotEmpty()) {
            val saved = saveImages(bytes, sessions, media)
            val out = sessions.map { pending ->
                if (pending.images.isEmpty()) {
                    pending.conversation
                } else {
                    val msgs = pending.conversation.messages.toMutableList()
                    for (ref in pending.images) {
                        val localPath = saved[ref.exportPath] ?: continue
                        val msg = msgs.getOrNull(ref.msgIndex) ?: continue
                        if (ref.attIndex !in msg.attachments.indices) continue
                        val attachments = msg.attachments.toMutableList()
                        attachments[ref.attIndex] = attachments[ref.attIndex].copy(localPath = localPath)
                        msgs[ref.msgIndex] = msg.copy(attachments = attachments)
                    }
                    pending.conversation.copy(messages = msgs)
                }
            }
            if (skipped > 0) warnings += "跳过 $skipped 个空对话或无法解析的条目"
            return Report(out, warnings)
        }

        val root = oldRoot ?: fallbackRoot
            ?: throw IllegalArgumentException("压缩包里没有找到对话数据")
        val report = parseRoot(root)
        return if (skipped > 0) {
            Report(report.conversations, report.warnings + "跳过 $skipped 个无法解析的条目")
        } else {
            report
        }
    }

    private fun saveImages(
        bytes: ByteArray,
        sessions: List<PendingSession>,
        media: MediaSaver?
    ): Map<String, String> {
        if (media == null) return emptyMap()
        val needed = LinkedHashSet<String>()
        sessions.forEach { pending -> pending.images.forEach { needed += it.exportPath } }
        if (needed.isEmpty()) return emptyMap()

        val saved = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/')
                    if (name in needed && name !in saved) {
                        val data = try {
                            readEntry(zip, MAX_IMAGE_BYTES)
                        } catch (e: Exception) {
                            null
                        }
                        if (data != null) {
                            val path = try {
                                media.save(name.substringAfterLast('/'), data)
                            } catch (e: Exception) {
                                null
                            }
                            if (path != null) saved[name] = path
                        }
                    }
                }
                entry = zip.nextEntry
            }
        }
        return saved
    }

    private fun parseJsonOrNull(data: ByteArray): JsonElement? = try {
        Json.parseToJsonElement(data.toString(Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }

    private fun readEntry(zip: ZipInputStream, maxBytes: Long): ByteArray {
        val buf = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = zip.read(chunk)
            if (n <= 0) break
            total += n
            if (total > maxBytes) throw IllegalArgumentException("压缩包内的文件太大")
            buf.write(chunk, 0, n)
        }
        return buf.toByteArray()
    }

    // ------------------------------------------------------------ 通用 JSON

    private fun parseRoot(root: JsonElement): Report {
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

    private fun parseConversation(obj: JsonObject): Conversation? {
        val mapping = obj["mapping"] as? JsonObject
        val messages: List<Msg> = when {
            mapping != null -> parseMapping(obj, mapping)
            obj["messages"] is JsonArray -> {
                val arr = obj["messages"] as JsonArray
                val official = arr.any { item ->
                    ((item as? JsonObject)?.get("fragments") as? JsonArray)?.isNotEmpty() == true
                }
                if (official) parseSessionObject(obj)?.conversation?.messages ?: emptyList()
                else parseMessageArray(obj)
            }
            else -> emptyList()
        }
        if (messages.isEmpty()) return null

        val id = obj.str("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val title = obj.str("title")?.trim()?.takeIf { it.isNotBlank() }
            ?: messages.firstOrNull { it.role == Role.USER }?.text?.replace('\n', ' ')?.take(30)
            ?: "未命名对话"
        val updated = obj.time("updated_at")
            ?: messages.lastOrNull { it.ts > 0 }?.ts
            ?: System.currentTimeMillis()
        val pinned = (obj["pinned"] as? JsonPrimitive)?.booleanOrNull == true
        return Conversation(
            id = id,
            title = title.take(80),
            updatedAt = updated,
            messages = messages.toMutableList(),
            pinned = pinned
        )
    }

    // ------------------------------------------------- 官方新格式（chat_sessions）

    private fun parseSessionObject(obj: JsonObject): PendingSession? {
        val arr = obj["messages"] as? JsonArray ?: return null
        val byId = LinkedHashMap<Long, JsonObject>()
        val orderedIds = mutableListOf<Long>()
        for (item in arr) {
            val message = item as? JsonObject ?: continue
            val id = message.long("id") ?: continue
            byId[id] = message
            orderedIds += id
        }
        if (byId.isEmpty()) return null

        val branch = currentBranch(obj, byId, orderedIds)
        if (branch.isEmpty()) return null

        val msgs = mutableListOf<Msg>()
        val pending = PendingSession(
            Conversation(
                id = obj.str("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                title = "",
                updatedAt = obj.time("updated_at") ?: 0L
            )
        )
        for (message in branch) {
            val parsed = parseOfficialMessage(message) ?: continue
            val msgIndex = msgs.size
            msgs += parsed.msg
            parsed.imageRefs.forEach { (attIndex, exportPath) ->
                pending.images += PendingImage(msgIndex, attIndex, exportPath)
            }
        }
        if (msgs.isEmpty()) return null

        val title = obj.str("title")?.trim()?.takeIf { it.isNotBlank() }
            ?: msgs.firstOrNull { it.role == Role.USER }?.text?.replace('\n', ' ')?.take(30)
            ?: "未命名对话"
        val updated = obj.time("updated_at") ?: msgs.lastOrNull { it.ts > 0 }?.ts ?: System.currentTimeMillis()
        val pinned = (obj["pinned"] as? JsonPrimitive)?.booleanOrNull == true
        val conv = pending.conversation.copy(
            title = title.take(80),
            updatedAt = updated,
            messages = msgs.toMutableList(),
            pinned = pinned
        )
        return PendingSession(conv, pending.images)
    }

    /** 沿 current_message_id → parent_id 回溯当前分支；数据异常时退回全部消息。 */
    private fun currentBranch(
        obj: JsonObject,
        byId: Map<Long, JsonObject>,
        orderedIds: List<Long>
    ): List<JsonObject> {
        var cursorId = obj.long("current_message_id")
        if (cursorId != null && byId.containsKey(cursorId)) {
            val path = mutableListOf<JsonObject>()
            val seen = HashSet<Long>()
            while (cursorId != null && byId.containsKey(cursorId)) {
                val id: Long = cursorId
                if (!seen.add(id)) break
                val message = byId[id] ?: break
                path += message
                cursorId = message.long("parent_id")
            }
            path.reverse()
            if (path.isNotEmpty()) return path
        }
        // 兜底：没有 current_message_id 时按 id 顺序导入全部消息
        return orderedIds.mapNotNull { byId[it] }
    }

    private class ParsedMessage(
        val msg: Msg,
        /** 附件下标 → zip 内路径 */
        val imageRefs: List<Pair<Int, String>> = emptyList()
    )

    private fun parseOfficialMessage(o: JsonObject): ParsedMessage? {
        val roleName = o.str("role")?.uppercase() ?: return null
        val role = when (roleName) {
            "USER", "HUMAN" -> Role.USER
            "ASSISTANT", "AI", "MODEL" -> Role.ASSISTANT
            else -> return null
        }
        val ts = o.time("inserted_at") ?: 0L
        val request = StringBuilder()
        val response = StringBuilder()
        val think = StringBuilder()
        val sources = mutableListOf<SearchSource>()
        val attachments = mutableListOf<Attachment>()
        val imageRefs = mutableListOf<Pair<Int, String>>()
        var searchQuery: String? = null

        val fragments = o["fragments"] as? JsonArray
        if (fragments != null) {
            for (fragment in fragments) {
                val f = fragment as? JsonObject ?: continue
                when (f.str("type")?.uppercase()) {
                    "REQUEST" -> appendText(request, flatten(f["content"]))
                    "RESPONSE" -> appendText(response, flatten(f["content"]))
                    "THINK" -> appendText(think, flatten(f["content"]))
                    "SEARCH", "TOOL_SEARCH" -> {
                        if (searchQuery == null) {
                            searchQuery = (f["queries"] as? JsonArray)
                                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                                ?.firstOrNull { it.isNotBlank() }
                        }
                        sources += parseSearchResults(f)
                    }
                    "FILE" -> parseFileFragment(f, attachments, imageRefs)
                    else -> Unit
                }
            }
        }

        val text = if (role == Role.USER) request.toString().trim() else response.toString().trim()
        val reasoning = think.toString().trim().takeIf { it.isNotBlank() }
        // 只读到一半（只有思考、没有正文）的助手消息不导入，避免出现空回答气泡
        if (role == Role.ASSISTANT && text.isBlank()) return null
        if (text.isBlank() && attachments.isEmpty()) return null

        return ParsedMessage(
            Msg(
                id = UUID.randomUUID().toString(),
                role = role,
                text = text,
                reasoning = if (role == Role.ASSISTANT) reasoning else null,
                ts = ts,
                searchQuery = searchQuery,
                sources = sources,
                attachments = attachments
            ),
            imageRefs
        )
    }

    private fun parseFileFragment(
        fragment: JsonObject,
        attachments: MutableList<Attachment>,
        imageRefs: MutableList<Pair<Int, String>>
    ) {
        val files = fragment["files"] as? JsonArray ?: return
        for (item in files) {
            val f = item as? JsonObject ?: continue
            val name = f.str("file_name")?.takeIf { it.isNotBlank() } ?: "附件"
            val isImage = (f["is_image"] as? JsonPrimitive)?.booleanOrNull == true
            val size = f.long("file_size") ?: 0L
            val exportPath = f.str("export_path")?.takeIf { it.isNotBlank() && it != "null" }
            val attIndex = attachments.size
            attachments += Attachment(
                name = name,
                mime = mimeOf(name, isImage),
                fileId = "",
                image = isImage,
                size = size,
                localPath = null
            )
            if (isImage && exportPath != null) imageRefs += attIndex to exportPath
        }
    }

    private fun mimeOf(name: String, isImage: Boolean): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "webp" -> "image/webp"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt", "md", "log" -> "text/plain"
            "csv" -> "text/csv"
            else -> if (isImage) "image/*" else ""
        }
    }

    // ------------------------------------------------------------ mapping 格式

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
                    "SEARCH", "TOOL_SEARCH" -> sources += parseSearchResults(f)
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

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

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
