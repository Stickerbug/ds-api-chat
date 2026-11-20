package io.github.stickerbug.dsapichat.ui.chat

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 官方「朗读」是云端 TTS（音色在「设置 - 朗读音色」里选），复刻版用系统 TextToSpeech 近似：
 *  - TextToSpeech 的初始化是异步的，第一次点朗读时引擎可能还没绑定成功，先把请求排队到 onInit；
 *  - 整段代码块不朗读，只播报一句「代码块」，公式念「公式」，链接念「链接」；
 *  - 按句切分后排队朗读（长回答一次 speak 容易被系统截断，也不利于「停止」及时生效）；
 *  - 最后一句读完才把 speakingId 清空，动作行图标才会变回喇叭。
 */
object TtsController {
    private var engine: TextToSpeech? = null
    private var inited = false
    private var ready = false
    private var appContext: Context? = null

    /** 引擎未就绪时暂存的朗读请求。 */
    private var queued: Pair<String, String>? = null

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 本次朗读的根 id（utterance id 形如 "<msgId>#3"）。 */
    private var rootId: String? = null
    private var pending = 0

    /** 正在朗读的消息 id，null 表示空闲。 */
    var speakingId: String? by mutableStateOf(null)
        private set

    fun ensure(context: Context) {
        if (inited) return
        inited = true
        appContext = context.applicationContext
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val e = engine
                if (e == null) {
                    // 极少数机型回调早于赋值完成，下次点朗读时重试。
                    inited = false
                    return@TextToSpeech
                }
                val lang = e.setLanguage(Locale.CHINA)
                if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                    e.language = Locale.getDefault()
                }
                e.setSpeechRate(1.0f)
                e.setPitch(1.0f)
                e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) = onChunkDone(utteranceId)

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = onChunkDone(utteranceId)

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?, errorCode: Int) = onChunkDone(utteranceId)

                    override fun onStop(utteranceId: String?, interrupted: Boolean) = onChunkDone(utteranceId)
                })
                ready = true
                val q = queued
                if (q != null) {
                    queued = null
                    startSpeak(q.first, q.second)
                }
            } else {
                // 系统没有可用的语音引擎：下次点朗读时重试，并提示一次。
                engine?.shutdown()
                engine = null
                inited = false
                ready = false
                queued = null
                speakingId = null
                appContext?.let {
                    Toast.makeText(it, "设备上没有可用的语音引擎", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onChunkDone(utteranceId: String?) {
        val root = utteranceId?.substringBeforeLast('#') ?: return
        if (root != rootId) return
        pending -= 1
        if (pending <= 0) {
            pending = 0
            rootId = null
            speakingId = null
        }
    }

    fun speak(context: Context, id: String, text: String) {
        ensure(context)
        stop()
        val clean = sanitize(text)
        if (clean.isBlank()) return
        if (engine == null) {
            // ensure() 同步失败（例如设备没有可用引擎）时不会再有 onInit 回调，这里直接复位。
            speakingId = null
            return
        }
        speakingId = id
        if (!ready) {
            queued = id to clean
            // 有些设备没有 TTS 引擎时 onInit 根本不会回来，加超时兜底，避免动作行图标卡在「停止朗读」。
            val ctx = appContext
            mainHandler.postDelayed({
                if (!ready && queued?.first == id) {
                    queued = null
                    speakingId = null
                    ctx?.let { Toast.makeText(it, "设备上没有可用的语音引擎", Toast.LENGTH_SHORT).show() }
                }
            }, 1600L)
            return
        }
        startSpeak(id, clean)
    }

    private fun startSpeak(id: String, clean: String) {
        val e = engine ?: return
        val chunks = splitSentences(clean)
        if (chunks.isEmpty()) {
            speakingId = null
            return
        }
        rootId = id
        pending = chunks.size
        chunks.forEachIndexed { i, chunk ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            e.speak(chunk, mode, null, id + "#" + i)
        }
    }

    fun toggle(context: Context, id: String, text: String) {
        if (speakingId == id) stop() else speak(context, id, text)
    }

    fun stop() {
        queued = null
        engine?.stop()
        rootId = null
        pending = 0
        speakingId = null
    }

    /** 去掉 markdown 记号，避免朗读时念出星号、井号、反引号、表格竖线。 */
    private fun sanitize(raw: String): String {
        val bt = 96.toChar().toString()
        val fence = bt + bt + bt
        var s = raw
        s = Regex("(?s)" + Regex.escape(fence) + ".*?" + Regex.escape(fence)).replace(s, "，代码块，")
        s = Regex(bt + "[^" + bt + "]*" + bt).replace(s, "")
        s = Regex("(?s)\\\$\\\$.*?\\\$\\\$").replace(s, "，公式，")
        s = Regex("\\\$[^\\\$]*\\\$").replace(s, "，公式，")
        s = Regex("!\\[[^\\]]*\\]\\([^)]*\\)").replace(s, "")
        s = Regex("\\[([^\\]]*)\\]\\([^)]*\\)").replace(s, "\$1")
        s = Regex("\\[\\d+\\]").replace(s, " ")
        s = Regex("https?://\\S+").replace(s, "，链接，")
        s = Regex("^\\s*\\|.*\\$", RegexOption.MULTILINE).replace(s, " ")
        s = Regex("[#>|*_~" + bt + "]+").replace(s, " ")
        s = Regex("\\s+").replace(s, " ")
        return s.trim()
    }

    /** 按中文句末标点切句；过长的句子再按 80 字切成小段。 */
    private fun splitSentences(text: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(ch)
            val end = ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?' ||
                ch == '；' || ch == ';' || ch == '\n'
            if (end || sb.length >= 80) {
                val t = sb.toString().trim()
                if (t.isNotEmpty()) out.add(t)
                sb.clear()
            }
        }
        val rest = sb.toString().trim()
        if (rest.isNotEmpty()) out.add(rest)
        return out
    }
}
