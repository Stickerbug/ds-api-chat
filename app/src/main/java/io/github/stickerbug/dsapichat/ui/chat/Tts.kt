package io.github.stickerbug.dsapichat.ui.chat

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.stickerbug.dsapichat.api.CloudTts
import io.github.stickerbug.dsapichat.data.Settings
import io.github.stickerbug.dsapichat.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 官方「朗读」是云端 TTS（音色在「设置 - 朗读音色」里选），复刻版用系统 TextToSpeech 近似：
 *  - 部分 ROM 没有写入默认 TTS 引擎（Settings.Secure.tts_default_synth 为空），直接 new TextToSpeech 会初始化失败，
 *    因此这里会枚举系统里的全部 TTS 引擎（getEngines）逐个尝试绑定，默认引擎不行就换下一个；
 *  - Android 11+ 需要在 manifest 里声明 TTS_SERVICE 的 <queries>，否则 getEngines 返回空；
 *  - 初始化异步：第一次点朗读时引擎可能还没绑定成功，先把请求排队到 onInit；
 *  - 整段代码块不朗读，只播报一句「代码块」，公式念「公式」，链接念「链接」；
 *  - 按句切分后排队朗读（长回答一次 speak 容易被系统截断，也不利于「停止」及时生效）；
 *  - 最后一句读完才把 speakingId 清空，动作行图标才会变回喇叭。
 */
object TtsController {
    private const val TAG = "TtsController"

    private var engine: TextToSpeech? = null
    private var inited = false
    private var ready = false
    private var appContext: Context? = null

    /** 引擎未就绪时暂存的朗读请求。 */
    private var queued: Pair<String, String>? = null

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 候选引擎包名，null 表示系统默认引擎。 */
    private var candidates: List<String?> = emptyList()
    private var attempt = 0

    /** 本次朗读的根 id（utterance id 形如 "<msgId>#3"）。 */
    private var rootId: String? = null
    private var pending = 0

    /** 正在朗读的消息 id，null 表示空闲。 */
    var speakingId: String? by mutableStateOf(null)
        private set

    // —— 云端朗读（配置了 ttsBaseUrl 时优先使用，音色自然且不依赖系统引擎）——
    private val cloudScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cloudJob: Job? = null
    private var player: MediaPlayer? = null
    private var cloudRoot: String? = null

    fun ensure(context: Context) {
        if (inited) return
        inited = true
        appContext = context.applicationContext
        candidates = buildCandidates(context)
        attempt = 0
        bind(0)
    }

    private fun buildCandidates(context: Context): List<String?> {
        val list = ArrayList<String?>()
        // 系统默认引擎优先；没有设置默认引擎时这一步会失败，继续尝试显式绑定。
        list.add(null)
        try {
            // 用 PackageManager 枚举引擎服务（TextToSpeech.getEngines 是实例方法，初始化失败时拿不到）。
            val ttsIntent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            context.packageManager.queryIntentServices(ttsIntent, 0)?.forEach { info ->
                val pkg = info.serviceInfo?.packageName
                if (!pkg.isNullOrBlank() && !list.contains(pkg)) {
                    Log.i(TAG, "found TTS engine: $pkg (${info.loadLabel(context.packageManager)})")
                    list.add(pkg)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "query TTS engines failed", t)
        }
        // 常见厂商引擎包名兜底（有些 ROM 的引擎没出现在 queryIntentServices 里）。
        listOf(
            "com.xiaomi.mibrain.speech",
            "com.huawei.hiai",
            "com.google.android.tts",
            "com.iflytek.speechcloud",
            "com.iflytek.speechsuite",
        ).forEach { p -> if (!list.contains(p)) list.add(p) }
        return list
    }

    private fun bind(index: Int) {
        val ctx = appContext ?: return
        if (index >= candidates.size) {
            failAll()
            return
        }
        attempt = index
        val name = candidates[index]
        Log.i(TAG, "init attempt #$index engine=$name")
        val listener = TextToSpeech.OnInitListener { status ->
            // 回调可能发生在 engine 赋值之前，统一 post 到主线程保证顺序。
            mainHandler.post { onInitResult(index, status) }
        }
        engine = try {
            if (name == null) TextToSpeech(ctx, listener) else TextToSpeech(ctx, listener, name)
        } catch (t: Throwable) {
            Log.w(TAG, "create TTS failed for $name", t)
            mainHandler.post { onInitResult(index, TextToSpeech.ERROR) }
            null
        }
    }

    private fun onInitResult(index: Int, status: Int) {
        // 忽略过期的回调（已经尝试下一个引擎）。
        if (index != attempt) return
        Log.i(TAG, "init result #$index status=$status")
        if (status == TextToSpeech.SUCCESS) {
            val e = engine
            if (e == null) {
                inited = false
                return
            }
            val lang = e.setLanguage(Locale.CHINA)
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                val fallback = e.setLanguage(Locale.getDefault())
                if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                    e.language = Locale.US
                }
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
            Log.i(TAG, "TTS ready, engine #$index")
            val q = queued
            if (q != null) {
                queued = null
                startSpeak(q.first, q.second)
            }
            return
        }
        // 这个引擎不可用，换下一个。
        try {
            engine?.shutdown()
        } catch (_: Throwable) {
        }
        engine = null
        ready = false
        bind(index + 1)
    }

    private fun failAll() {
        Log.w(TAG, "no usable TTS engine")
        engine = null
        inited = false
        ready = false
        queued = null
        speakingId = null
        appContext?.let { ctx ->
            Toast.makeText(ctx, "没有可用的语音引擎，可先到系统设置里开启文字转语音", Toast.LENGTH_LONG).show()
            // 尝试打开系统的「文字转语音输出」设置页，方便用户启用引擎。
            try {
                ctx.startActivity(
                    Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {
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
        stop()
        val clean = sanitize(text)
        if (clean.isBlank()) return
        val s = runCatching { SettingsStore(context).current() }.getOrNull()
        if (s != null && s.ttsBaseUrl.isNotBlank()) {
            startCloudSpeak(context, id, clean, s)
            return
        }
        ensure(context)
        if (engine == null) {
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
                    ctx?.let { Toast.makeText(it, "语音引擎尚未就绪，请稍后再试", Toast.LENGTH_SHORT).show() }
                }
            }, 3000L)
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
            val params = Bundle()
            val r = e.speak(chunk, mode, params, id + "#" + i)
            if (r != TextToSpeech.SUCCESS) Log.w(TAG, "speak chunk $i returned $r")
        }
    }

    fun toggle(context: Context, id: String, text: String) {
        if (speakingId == id) stop() else speak(context, id, text)
    }

    fun stop() {
        queued = null
        cloudJob?.cancel()
        cloudJob = null
        cloudRoot = null
        releasePlayer()
        try {
            engine?.stop()
        } catch (_: Throwable) {
        }
        rootId = null
        pending = 0
        speakingId = null
    }

    private fun releasePlayer() {
        val p = player ?: return
        player = null
        try {
            p.stop()
        } catch (_: Throwable) {
        }
        try {
            p.release()
        } catch (_: Throwable) {
        }
    }

    /** 用云端 /audio/speech 合成，按句依次播放；失败时回退系统引擎。 */
    private fun startCloudSpeak(context: Context, id: String, clean: String, s: Settings) {
        val chunks = splitSentences(clean)
        if (chunks.isEmpty()) {
            speakingId = null
            return
        }
        speakingId = id
        cloudRoot = id
        ensure(context)
        val dir = File(context.cacheDir, "tts").apply { mkdirs() }
        cloudJob = cloudScope.launch {
            var fellBack = false
            try {
                for ((i, chunk) in chunks.withIndex()) {
                    if (cloudRoot != id) return@launch
                    val bytes = runCatching {
                        CloudTts.synth(
                            s.ttsBaseUrl,
                            s.ttsApiKey.ifBlank { s.asrApiKey },
                            s.ttsModel,
                            s.ttsVoice,
                            chunk
                        )
                    }.getOrNull()
                    if (bytes == null || bytes.isEmpty()) {
                        Log.w(TAG, "cloud tts failed at chunk " + i)
                        if (!fellBack) {
                            fellBack = true
                            Toast.makeText(context, "云端朗读失败，已改用系统朗读", Toast.LENGTH_SHORT).show()
                        }
                        cloudRoot = null
                        if (ready && engine != null) {
                            startSpeak(id, chunks.drop(i).joinToString(""))
                        } else {
                            speakingId = null
                        }
                        return@launch
                    }
                    val f = File(dir, "tts_" + id.hashCode() + "_" + i + ".mp3")
                    f.writeBytes(bytes)
                    playFile(f)
                }
            } finally {
                if (cloudRoot == id) {
                    cloudRoot = null
                    speakingId = null
                }
            }
        }
    }

    private suspend fun playFile(f: File) {
        val cont = suspendCancellableCoroutine<Unit> { c ->
            val mp = MediaPlayer()
            player = mp
            val done = java.util.concurrent.atomic.AtomicBoolean(false)
            fun finish() {
                if (done.compareAndSet(false, true)) {
                    if (player === mp) player = null
                    runCatching { mp.release() }
                    f.delete()
                    c.resume(Unit)
                }
            }
            try {
                mp.setDataSource(f.absolutePath)
                mp.setOnPreparedListener { it.start() }
                mp.setOnCompletionListener { finish() }
                mp.setOnErrorListener { _, _, _ -> finish(); true }
                mp.prepareAsync()
            } catch (t: Throwable) {
                Log.w(TAG, "play cloud tts failed", t)
                finish()
            }
            c.invokeOnCancellation { finish() }
        }
        cont
    }

    /** 去掉 markdown 记号，避免朗读时念出星号、井号、反引号、表格竖线。 */
    private fun sanitize(raw: String): String {
        val bt = 96.toChar().toString()
        val fence = bt + bt + bt
        var s = raw
        s = Regex("(?s)" + Regex.escape(fence) + ".*?" + Regex.escape(fence)).replace(s, "，代码块，")
        s = Regex(bt + "[^" + bt + "]*" + bt).replace(s, "")
        s = Regex("(?s)\\$\\$.*?\\$\\$").replace(s, "，公式，")
        s = Regex("\\$[^\\$]*\\$").replace(s, "，公式，")
        s = Regex("!\\[[^\\]]*\\]\\([^)]*\\)").replace(s, "")
        s = Regex("\\[([^\\]]*)\\]\\([^)]*\\)").replace(s, "\$1")
        s = Regex("\\[\\d+\\]").replace(s, " ")
        s = Regex("https?://\\S+").replace(s, "，链接，")
        s = Regex("^\\s*\\|.*$", RegexOption.MULTILINE).replace(s, " ")
        s = Regex("[#>|*_~" + bt + "]+").replace(s, " ")
        s = Regex("\\s+").replace(s, " ")
        return s.trim()
    }

    /**
     * 按句切分，再把过短的句子合并成一个更自然的朗读块（30~90 字），避免逐词停顿显得生硬。
     */
    private fun splitSentences(text: String): List<String> {
        val sentences = ArrayList<String>()
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(ch)
            val end = ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?' ||
                ch == '；' || ch == ';' || ch == '\n'
            if (end) {
                val t = sb.toString().trim()
                if (t.isNotEmpty()) sentences.add(t)
                sb.clear()
            } else if (sb.length >= 160) {
                // 超长且没有句末标点：退到最近的逗号/顿号处断开。
                val s = sb.toString()
                var cut = -1
                for (i in s.indices.reversed()) {
                    if (s[i] == '，' || s[i] == ',' || s[i] == '、' || s[i] == '：' || s[i] == ':') {
                        cut = i
                        break
                    }
                }
                if (cut > 20) {
                    sentences.add(s.substring(0, cut + 1).trim())
                    val rest = s.substring(cut + 1).trim()
                    sb.clear()
                    sb.append(rest)
                }
            }
        }
        val rest = sb.toString().trim()
        if (rest.isNotEmpty()) sentences.add(rest)

        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (s in sentences) {
            if (cur.isNotEmpty() && cur.length + s.length > 90) {
                out.add(cur.toString())
                cur.clear()
            }
            cur.append(s)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }
}
