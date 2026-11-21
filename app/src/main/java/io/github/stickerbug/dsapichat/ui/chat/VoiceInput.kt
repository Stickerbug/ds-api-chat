package io.github.stickerbug.dsapichat.ui.chat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * 语音输入控制器。
 *  - 官方语音是云端 ASR（「按住说话」），复刻版优先调用系统 SpeechRecognizer；
 *  - HyperOS 等系统移除了语音搜索入口后，默认识别服务可能不可用，这里会继续显式绑定厂商识别服务（如小米 AsrService）；
 *  - 全部失败时通过 onError 提示用户，避免点了麦克风没有任何反应。
 */
class VoiceInputController(
    private val context: Context,
    /** auto / zh / en / ja；auto 时不指定语言，交给系统识别服务决定。 */
    private val language: String = "auto",
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit
) {
    private val tag = "VoiceInput"
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var attempt = 0
    private var heardSpeech = false

    // 先试系统默认服务，再试小米识别服务（部分 HyperOS 设备默认引擎为空但服务仍导出）。
    private val candidates: List<ComponentName?> = listOf(
        null,
        ComponentName("com.xiaomi.mibrain.speech", "com.xiaomi.mibrain.speech.asr.AsrService")
    )

    private val listenIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        when (language) {
            "zh" -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            "en" -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            "ja" -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
        }
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private val timeoutRunnable = Runnable {
        if (!listening) return@Runnable
        if (!heardSpeech) fail("没有听到声音，请再试一次") else fail("识别超时，请再试一次")
    }

    fun start() {
        if (listening) return
        listening = true
        heardSpeech = false
        attempt = 0
        onListeningChanged(true)
        bind()
        handler.postDelayed(timeoutRunnable, 12000)
    }

    fun cancel() {
        if (!listening) return
        listening = false
        handler.removeCallbacks(timeoutRunnable)
        try {
            recognizer?.cancel()
        } catch (_: Throwable) {
        }
        destroy()
        onListeningChanged(false)
    }

    fun destroy() {
        try {
            recognizer?.destroy()
        } catch (_: Throwable) {
        }
        recognizer = null
    }

    private fun bind() {
        destroy()
        val cn = candidates.getOrNull(attempt)
        Log.i(tag, "bind attempt=$attempt component=$cn")
        recognizer = try {
            if (cn == null) SpeechRecognizer.createSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context, cn)
        } catch (t: Throwable) {
            Log.w(tag, "createSpeechRecognizer failed", t)
            null
        }
        val r = recognizer
        if (r == null) {
            nextOrFail("本机没有可用的语音识别服务")
            return
        }
        r.setRecognitionListener(listener)
        try {
            r.startListening(listenIntent)
        } catch (t: Throwable) {
            Log.w(tag, "startListening failed", t)
            nextOrFail("语音识别启动失败")
        }
    }

    private fun nextOrFail(message: String) {
        if (attempt + 1 < candidates.size) {
            attempt += 1
            bind()
        } else {
            fail(message)
        }
    }

    private fun finish(success: Boolean) {
        listening = false
        handler.removeCallbacks(timeoutRunnable)
        destroy()
        onListeningChanged(false)
    }

    private fun fail(message: String) {
        Log.w(tag, "fail: $message")
        finish(false)
        onError(message)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {
            heardSpeech = true
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            Log.w(tag, "onError attempt=$attempt error=$error")
            if (!listening) return
            val message = when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限，无法语音输入"
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别需要网络，请检查网络后重试"
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听清，请再说一遍"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别服务忙，请稍后再试"
                else -> "本机语音识别不可用"
            }
            // 默认服务不可用时换下一个候选（厂商识别服务）再试一次。
            if (attempt + 1 < candidates.size) {
                attempt += 1
                bind()
            } else {
                fail(message)
            }
        }

        override fun onResults(results: Bundle?) {
            if (!listening) return
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty().trim()
            finish(true)
            if (text.isNotBlank()) onResult(text) else onError("没有识别到内容，请再试一次")
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}