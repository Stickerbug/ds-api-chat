package io.github.stickerbug.dsapichat

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.stickerbug.dsapichat.api.ApiErrorKind
import io.github.stickerbug.dsapichat.api.ApiException
import io.github.stickerbug.dsapichat.api.ApiMsg
import io.github.stickerbug.dsapichat.api.ApiProtocol
import io.github.stickerbug.dsapichat.api.ChatClient
import io.github.stickerbug.dsapichat.api.FileApi
import io.github.stickerbug.dsapichat.api.StreamEvent
import io.github.stickerbug.dsapichat.api.resolveProtocol
import io.github.stickerbug.dsapichat.data.Attachment
import io.github.stickerbug.dsapichat.data.Citation
import io.github.stickerbug.dsapichat.data.Conversation
import io.github.stickerbug.dsapichat.data.ConversationImport
import io.github.stickerbug.dsapichat.data.ConversationStore
import io.github.stickerbug.dsapichat.data.Msg
import io.github.stickerbug.dsapichat.data.Role
import io.github.stickerbug.dsapichat.data.SearchSource
import io.github.stickerbug.dsapichat.data.Settings
import io.github.stickerbug.dsapichat.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)
    private val store = ConversationStore(app)

    val settings: StateFlow<Settings> = settingsStore.settings

    private val _conversations = MutableStateFlow(store.load())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _current = MutableStateFlow<Conversation?>(null)
    val current: StateFlow<Conversation?> = _current

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments: StateFlow<List<Attachment>> = _attachments

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading

    private var job: Job? = null

    fun clearAttachments() {
        _attachments.value = emptyList()
    }

    fun removeAttachment(fileId: String) {
        _attachments.value = _attachments.value.filterNot { it.fileId == fileId }
    }

    /** 选择文件后立刻上传到 Files API，成功后才出现在待发送附件里。 */
    fun attach(uri: Uri) {
        val app = getApplication<Application>()
        val s = settings.value
        if (s.apiKey.isBlank()) {
            _error.value = "auth"
            return
        }
        if (resolveProtocol(s.protocol, s.baseUrl, s.model, s.search) != ApiProtocol.ANTHROPIC) {
            _error.value = "attach-protocol"
            return
        }
        _uploading.value = true
        viewModelScope.launch {
            try {
                val name = queryName(app, uri) ?: "attachment"
                val dot = name.lastIndexOf('.')
                val ext = if (dot > 0) name.substring(dot + 1).lowercase() else ""
                val mime = app.contentResolver.getType(uri)
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                    ?: "application/octet-stream"
                val bytes = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw ApiException(ApiErrorKind.SERVER, 0, "cannot open file")
                if (bytes.size > FileApi.MAX_BYTES) {
                    _error.value = "attach-size"
                    return@launch
                }
                uploadAndStore(name, mime, bytes)
            } catch (e: ApiException) {
                _error.value = e.kind.name
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = "NETWORK"
            } finally {
                _uploading.value = false
            }
        }
    }


    /** 一次选择多张图片（相册选择器 / 附件面板多选）后按顺序上传。 */
    fun attachAll(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = getApplication<Application>()
        val s = settings.value
        if (s.apiKey.isBlank()) {
            _error.value = "auth"
            return
        }
        if (resolveProtocol(s.protocol, s.baseUrl, s.model, s.search) != ApiProtocol.ANTHROPIC) {
            _error.value = "attach-protocol"
            return
        }
        _uploading.value = true
        viewModelScope.launch {
            try {
                for (uri in uris) {
                    val name = queryName(app, uri) ?: "attachment"
                    val dot = name.lastIndexOf('.')
                    val ext = if (dot > 0) name.substring(dot + 1).lowercase() else ""
                    val mime = app.contentResolver.getType(uri)
                        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                        ?: "application/octet-stream"
                    val bytes = withContext(Dispatchers.IO) {
                        app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: continue
                    if (bytes.size > FileApi.MAX_BYTES) {
                        _error.value = "attach-size"
                        continue
                    }
                    runCatching { uploadAndStore(name, mime, bytes) }
                        .onFailure { e ->
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            if (e is ApiException) _error.value = e.kind.name
                        }
                }
            } finally {
                _uploading.value = false
            }
        }
    }

    /** 上传到 Files API 并把返回的 file_id 记进待发送附件。 */
    private suspend fun uploadAndStore(name: String, mime: String, bytes: ByteArray) {
        val app = getApplication<Application>()
        val s = settings.value
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0) name.substring(dot + 1).lowercase() else ""
        val isImage = mime.startsWith("image/")
        val localPath = if (isImage) withContext(Dispatchers.IO) {
            runCatching {
                val dir = java.io.File(app.cacheDir, "attach").apply { mkdirs() }
                val safeExt = if (ext.isNotEmpty()) "." + ext else ".img"
                val f = java.io.File(dir, java.util.UUID.randomUUID().toString() + safeExt)
                f.writeBytes(bytes)
                f.absolutePath
            }.getOrNull()
        } else null
        val id = FileApi.upload(s.baseUrl, s.apiKey, name, mime, bytes)
        _attachments.value = _attachments.value + Attachment(
            name = name,
            mime = mime,
            fileId = id,
            image = isImage,
            size = bytes.size.toLong(),
            localPath = localPath
        )
    }

    /** 调试：不经文件选择器直接上传一个文本附件，验证 Files API 往返。 */
    fun debugAttach() {
        viewModelScope.launch {
            _uploading.value = true
            try {
                uploadAndStore("笔记.txt", "text/plain", "mock attachment".toByteArray())
            } catch (e: ApiException) {
                _error.value = e.kind.name
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = "NETWORK"
            } finally {
                _uploading.value = false
            }
        }
    }

    private fun queryName(app: Application, uri: Uri): String? = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    fun saveSettings(s: Settings) = settingsStore.save(s)

    fun seedDemo(answer: String? = null) {
        val now = System.currentTimeMillis()
        val user = Msg(id = "demo-u", role = Role.USER, text = "screen有哪些意思", ts = now - 6000)
        val bot = Msg(
            id = "demo-a",
            role = Role.ASSISTANT,
            reasoning = "我们需要回答中文。用户问screen有哪些意思。需要解释screen多义。要全面但简洁。可以分名词动词，常见义项，搭配。要中文。可包括屏幕、屏风、筛子、掩饰、电影银幕。",
            thinkMs = 3000,
            text = answer ?: "“Screen” 是个多义词，核心意象可以理解为两类：“屏障/遮挡物”和“筛选/筛查”。具体意思要看语境。\n\n## 名词常见义项\n\n1. **屏幕**：电视、电脑、手机、投影屏幕\n\n   - computer screen 电脑屏幕；phone screen 手机屏幕\n\n2. **银幕；电影**：the big screen 大银幕/电影\n\n   - on screen 在屏幕上/在电影中",
            ts = now
        )
        val conv = Conversation(
            id = "demo-conv",
            title = "防晒霜名称区别",
            updatedAt = now,
            messages = mutableListOf(user, bot)
        )
        _conversations.value = listOf(conv) + _conversations.value.filterNot { it.id == "demo-conv" }
        _current.value = conv
    }

    /** 调试：一条纯文本长回答，用于验证朗读分句与云端合成的多段播放。 */
    fun seedLongAnswer() {
        seedDemo(
            answer = "朗读分块测试。系统会把一段较长的回答按句号切成若干段，再逐段合成语音，" +
                "这样长回答不会因为一次请求太长而失败。第一段用来确认第一个请求正常返回。" +
                "第二段用来确认播放结束后会继续请求下一段。第三段用来确认各段之间的衔接没有明显停顿。" +
                "第四段用来确认全部播放结束后朗读按钮会复位。第五段用来确认中途点击停止能够立即取消后续请求。" +
                "测试到此结束，如果听到完整连贯的语音说明分块逻辑工作正常。"
        )
    }

    /** 演示：用户消息带图片 + 文档附件，用于核对附件 chip 的排版。 */
    fun seedAttach() {
        val app = getApplication<Application>()
        val img = java.io.File(java.io.File(app.cacheDir, "attach").apply { mkdirs() }, "demo_shot.png")
        if (!img.exists()) {
            runCatching {
                val bmp = android.graphics.Bitmap.createBitmap(240, 160, android.graphics.Bitmap.Config.ARGB_8888)
                val cv = android.graphics.Canvas(bmp)
                cv.drawColor(android.graphics.Color.rgb(236, 242, 254))
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(77, 107, 254)
                    textSize = 56f
                    isAntiAlias = true
                }
                cv.drawText("DS", 52f, 104f, p)
                java.io.FileOutputStream(img).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
            }
        }
        val now = System.currentTimeMillis()
        val user = Msg(
            id = "att-u",
            role = Role.USER,
            text = "这张截图里的公式是什么？",
            ts = now - 4000,
            attachments = listOf(
                Attachment(
                    name = "截图 2026-11-20 09.41.18.png",
                    mime = "image/png",
                    fileId = "file_demo_image",
                    image = true,
                    size = 240L * 160L,
                    localPath = img.absolutePath
                ),
                Attachment(
                    name = "梯度下降推导.pdf",
                    mime = "application/pdf",
                    fileId = "file_demo_pdf",
                    image = false,
                    size = 1_204_000L
                )
            )
        )
        _attachments.value = user.attachments
        val conv = Conversation(
            id = "demo-attach",
            title = "这张截图里的公式是什么？",
            updatedAt = now,
            messages = mutableListOf(user)
        )
        _conversations.value = listOf(conv) + _conversations.value.filterNot { it.id == "demo-attach" }
        _current.value = conv
    }

fun seedRich() {
        val now = System.currentTimeMillis()
        val D = '$'.toString()
        val code = """
def gd(f, grad, x0, lr=0.1, steps=100):
    x = x0
    for _ in range(steps):
        x -= lr * grad(x)
    return x
""".trim()
        val tex = """\theta_{t+1} = \theta_t - \eta \nabla_\theta J(\theta_t)"""
        val text = listOf(
            "## 先说结论",
            "",
            "梯度下降每一步都沿负梯度方向走一小段：",
            "",
            D + D + tex + D + D,
            "",
            "其中 " + D + "\\eta" + D + " 是学习率，决定每步走多远。",
            "",
            "## 最小实现",
            "",
            "```python",
            code,
            "```",
            "",
            "调用 `gd(f, grad, 3.0)` 就能逼近极小值点。"
        ).joinToString("\n")
        val user = Msg(id = "rich-u", role = Role.USER, text = "梯度下降的公式和最小实现", ts = now - 5000)
        val bot = Msg(id = "rich-a", role = Role.ASSISTANT, text = text, ts = now)
        val conv = Conversation(
            id = "demo-rich",
            title = "梯度下降公式",
            updatedAt = now,
            messages = mutableListOf(user, bot)
        )
        _conversations.value = listOf(conv) + _conversations.value.filterNot { it.id == "demo-rich" }
        _current.value = conv
    }

    fun seedSearch() {
        val now = System.currentTimeMillis()
        val user = Msg(id = "demo-su", role = Role.USER, text = "帮我搜一下 2026 年值得关注的国产大模型", ts = now - 9000)
        val bot = Msg(
            id = "demo-sa",
            role = Role.ASSISTANT,
            reasoning = "用户想了解国产大模型的最新进展。先搜索最近的报道，再挑几个有代表性的整理成要点，给出模型名称、发布方和特点，最后提示信息可能滞后。",
            thinkMs = 4200,
            searchQuery = "2026 国产大模型 最新进展",
            sources = listOf(
                SearchSource(
                    title = "2026 年国产大模型全景盘点：从开源到落地",
                    url = "https://www.jiqizhixin.com/articles/2026-01-08-3",
                    siteName = "机器之心",
                    snippet = "过去一年国产大模型在多模态与长上下文上进展明显。",
                    pageAge = "2026/01/08"
                ),
                SearchSource(
                    title = "国产大模型 2026 开年观察",
                    url = "https://36kr.com/p/3000000000000",
                    siteName = "36氪",
                    snippet = "推理成本继续下降，端侧部署成为新战场。",
                    pageAge = "2026/01/05"
                ),
                SearchSource(
                    title = "如何评价 2026 年的国产大模型",
                    url = "https://www.zhihu.com/question/000000000",
                    siteName = "知乎",
                    snippet = "讨论集中在代码能力与工具调用稳定性。",
                    pageAge = null
                )
            ),
            citations = listOf(
                Citation("长上下文和工具调用是今年重点", "https://www.jiqizhixin.com/articles/2026-01-08-3", 1),
                Citation("社区微调生态活跃", "https://36kr.com/p/3000000000000", 2),
                Citation("多模态：图文、语音、视频理解", "https://www.zhihu.com/question/000000000", 3)
            ),
            text = "我查了几篇近期报道，2026 年值得关注的国产大模型大致有这几类：\n\n1. **通用对话与推理**：以 DeepSeek、通义千问、豆包为代表，长上下文和工具调用是今年重点。\n2. **开源权重**：DeepSeek、Qwen 系继续开源，社区微调生态活跃。\n3. **多模态**：图文、语音、视频理解逐步成为标配。\n\n以上信息来自公开报道，具体能力与价格请以官方发布为准。",
            ts = now
        )
        val conv = Conversation(
            id = "demo-search",
            title = "2026 国产大模型",
            updatedAt = now,
            messages = mutableListOf(user, bot)
        )
        _conversations.value = listOf(conv) + _conversations.value.filterNot { it.id == "demo-search" }
        _current.value = conv
    }

    fun newChat() {
        job?.cancel()
        _streaming.value = false
        _current.value = null
        _attachments.value = emptyList()
    }

    fun openChat(id: String) {
        val c = _conversations.value.firstOrNull { it.id == id } ?: return
        job?.cancel()
        _streaming.value = false
        _current.value = c
    }

    fun deleteChat(id: String) {
        _conversations.value = _conversations.value.filterNot { it.id == id }
        if (_current.value?.id == id) _current.value = null
        persist()
    }

    fun renameChat(id: String, title: String) {
        val list = _conversations.value.map { if (it.id == id) it.copy(title = title) else it }
        _conversations.value = list
        if (_current.value?.id == id) _current.value = _current.value?.copy(title = title)
        persist()
    }

    fun togglePin(id: String) {
        _conversations.value = _conversations.value.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }
        persist()
    }

    fun clearAllConversations() {
        job?.cancel()
        _streaming.value = false
        _conversations.value = emptyList()
        _current.value = null
        persist()
    }

    /** 导入对话：支持本应用 JSON 备份与 DeepSeek 官方导出 zip。 */
    fun importConversations(uri: Uri, onResult: (String) -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                onResult("导入失败：无法读取文件")
                return@launch
            }
            val parsed = withContext(Dispatchers.Default) {
                runCatching {
                    ConversationImport.parse(bytes) { name, data ->
                        val dir = File(app.filesDir, "imported-media").apply { mkdirs() }
                        val ext = name.substringAfterLast('.', "").lowercase()
                            .filter { it.isLetterOrDigit() }
                            .take(8)
                        val file = File(dir, UUID.randomUUID().toString() + if (ext.isEmpty()) "" else "." + ext)
                        file.outputStream().use { it.write(data) }
                        file.absolutePath
                    }
                }
            }
            val report = parsed.getOrNull()
            if (report == null) {
                val reason = parsed.exceptionOrNull()?.message?.takeIf { it.isNotBlank() } ?: "文件格式不支持"
                onResult("导入失败：$reason")
                return@launch
            }
            val merged = _conversations.value.toMutableList()
            val ids = merged.mapTo(HashSet()) { it.id }
            val keys = merged.mapTo(HashSet()) { conversationKey(it) }
            var added = 0
            var skipped = 0
            for (conv in report.conversations.sortedByDescending { it.updatedAt }) {
                val key = conversationKey(conv)
                if (conv.id in ids || key in keys) {
                    skipped++
                    continue
                }
                ids += conv.id
                keys += key
                merged += conv
                added++
            }
            if (added > 0) {
                _conversations.value = merged.sortedWith(
                    compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt }
                )
                withContext(Dispatchers.IO) { store.save(_conversations.value) }
            }
            val msg = buildString {
                append(if (added > 0) "已导入 $added 个对话" else "没有导入新对话")
                if (skipped > 0) append("，跳过 $skipped 个重复对话")
            }
            onResult(msg)
        }
    }

    /** 导出全部对话为 JSON 备份。 */
    fun exportConversations(uri: Uri, onResult: (String) -> Unit) {
        val list = _conversations.value
        if (list.isEmpty()) {
            onResult("没有可导出的对话")
            return
        }
        val app = getApplication<Application>()
        viewModelScope.launch {
            val text = store.encode(list)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("无法打开输出文件")
                }.isSuccess
            }
            val count = list.sumOf { it.messages.size }
            onResult(if (ok) "已导出 " + list.size + " 个对话（" + count + " 条消息）" else "导出失败：无法写入文件")
        }
    }

    private fun conversationKey(c: Conversation): String {
        val first = c.messages.firstOrNull { it.role == Role.USER }?.text?.trim()?.take(80) ?: ""
        return c.title.trim() + "|" + first
    }

    fun clearError() { _error.value = null }

    private fun persist() {
        store.save(_conversations.value)
    }

    fun send(text: String) {
        val body = text.trim()
        val files = _attachments.value
        if (body.isEmpty() && files.isEmpty()) return
        val s = settings.value
        if (s.apiKey.isBlank()) {
            _error.value = "auth"
            return
        }
        var conv = _current.value
        if (conv == null) {
            conv = Conversation(
                id = UUID.randomUUID().toString(),
                title = (body.ifBlank { files.firstOrNull()?.name ?: "" }).take(20),
                updatedAt = System.currentTimeMillis(),
                messages = mutableListOf()
            )
            _conversations.value = listOf(conv) + _conversations.value
        }
        _attachments.value = emptyList()
        val userMsg = Msg(
            id = UUID.randomUUID().toString(),
            role = Role.USER,
            text = body,
            ts = System.currentTimeMillis(),
            attachments = files
        )
        val botMsg = Msg(id = UUID.randomUUID().toString(), role = Role.ASSISTANT, text = "", ts = System.currentTimeMillis())
        val updated = conv.copy(
            messages = (conv.messages + userMsg + botMsg).toMutableList(),
            updatedAt = System.currentTimeMillis()
        )
        setCurrent(updated)
        stream(updated.id, botMsg.id, s)
    }

    fun regenerate() {
        val conv = _current.value ?: return
        val lastUser = conv.messages.lastOrNull { it.role == Role.USER } ?: return
        val trimmed = conv.messages.filterNot { it.role == Role.ASSISTANT && it.ts >= lastUser.ts }.toMutableList()
        val botMsg = Msg(id = UUID.randomUUID().toString(), role = Role.ASSISTANT, text = "", ts = System.currentTimeMillis())
        val updated = conv.copy(messages = (trimmed + botMsg).toMutableList())
        setCurrent(updated)
        stream(updated.id, botMsg.id, settings.value)
    }

    fun stop() {
        job?.cancel()
        job = null
        _streaming.value = false
        persist()
    }

    private fun setCurrent(c: Conversation) {
        _current.value = c
        _conversations.value = _conversations.value.map { if (it.id == c.id) c else it }
    }

    private fun updateMsg(convId: String, msgId: String, block: (Msg) -> Msg) {
        val conv = _current.value ?: return
        if (conv.id != convId) return
        val msgs = conv.messages.map { if (it.id == msgId) block(it) else it }
        setCurrent(conv.copy(messages = msgs.toMutableList()))
    }

    private fun stream(convId: String, msgId: String, s: Settings) {
        job?.cancel()
        _streaming.value = true
        val client = ChatClient(s.baseUrl, s.apiKey, resolveProtocol(s.protocol, s.baseUrl, s.model, s.search))
        val history = (_current.value?.messages ?: emptyList())
            .filter { it.id != msgId && it.err == null && (it.text.isNotBlank() || it.attachments.isNotEmpty()) }
            .map { ApiMsg(it.role.apiName, it.text, it.attachments.filter { a -> a.fileId.isNotBlank() }) }
        job = viewModelScope.launch {
            val started = System.currentTimeMillis()
            var firstTokenAt = 0L
            try {
                client.stream(
                    model = s.model,
                    messages = history,
                    thinking = s.thinking,
                    search = s.search
                ).collect { ev ->
                    when (ev) {
                        is StreamEvent.Delta -> {
                            if (ev.reasoning != null && firstTokenAt == 0L) firstTokenAt = System.currentTimeMillis()
                            if (ev.content != null && firstTokenAt == 0L) firstTokenAt = System.currentTimeMillis()
                            updateMsg(convId, msgId) { m ->
                                m.copy(
                                    text = m.text + (ev.content ?: ""),
                                    reasoning = if (ev.reasoning != null) (m.reasoning ?: "") + ev.reasoning else m.reasoning,
                                    thinkMs = if (m.thinkMs == 0L && ev.content != null && firstTokenAt > 0) System.currentTimeMillis() - started else m.thinkMs
                                )
                            }
                        }

                        is StreamEvent.Searching -> updateMsg(convId, msgId) { m ->
                            m.copy(searchQuery = ev.query)
                        }

                        is StreamEvent.Sources -> updateMsg(convId, msgId) { m ->
                            m.copy(sources = (m.sources + ev.items).distinctBy { it.url })
                        }

                        is StreamEvent.Citation -> updateMsg(convId, msgId) { m ->
                            val idx = m.sources.indexOfFirst { it.url == ev.url }
                            val source = if (idx >= 0) m.sources[idx] else null
                            val index = if (idx >= 0) idx + 1 else m.sources.size + 1
                            val list = if (source == null) {
                                m.sources + SearchSource(
                                    title = ev.title ?: ev.url,
                                    url = ev.url
                                )
                            } else m.sources
                            val dup = m.citations.any { it.url == ev.url && it.text == ev.text }
                            if (dup || ev.text.isBlank()) m.copy(sources = list)
                            else m.copy(
                                sources = list,
                                citations = m.citations + Citation(
                                    text = ev.text,
                                    url = ev.url,
                                    index = index,
                                    // 记录收到 citation 时正文已流出的长度，用于把角标插回到引用句末
                                    offset = m.text.length
                                )
                            )
                        }

                        is StreamEvent.Reading -> updateMsg(convId, msgId) { m ->
                            m.copy(reading = ev.url)
                        }

                        is StreamEvent.Signature -> updateMsg(convId, msgId) { m ->
                            m.copy(signature = (m.signature ?: "") + ev.sig)
                        }

                        is StreamEvent.Done -> updateMsg(convId, msgId) { m -> m.copy(reading = null) }
                    }
                }
            } catch (e: ApiException) {
                updateMsg(convId, msgId) { it.copy(err = e.kind.name) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                updateMsg(convId, msgId) { it.copy(err = "network") }
            } finally {
                _streaming.value = false
                job = null
                persist()
            }
        }
    }
}
