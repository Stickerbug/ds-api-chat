package io.github.stickerbug.dsapichat.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** 录音器：用于云端语音识别（本机系统识别不可用时）。 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var started = false

    fun start(): Boolean {
        cancel()
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val f = File(dir, "rec_" + System.currentTimeMillis() + ".m4a")
        val r: MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16000)
            r.setAudioChannels(1)
            r.setAudioEncodingBitRate(64000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = f
            started = true
            true
        } catch (t: Throwable) {
            runCatching { r.release() }
            f.delete()
            recorder = null
            file = null
            started = false
            false
        }
    }

    /** 停止录音并返回音频文件；录音过短/失败返回 null。 */
    fun stop(): File? {
        val r = recorder ?: return null
        val f = file
        val wasStarted = started
        recorder = null
        file = null
        started = false
        if (!wasStarted) {
            runCatching { r.release() }
            f?.delete()
            return null
        }
        return try {
            r.stop()
            r.release()
            f
        } catch (t: Throwable) {
            runCatching { r.release() }
            f?.delete()
            null
        }
    }

    fun cancel() {
        val r = recorder
        val f = file
        val wasStarted = started
        recorder = null
        file = null
        started = false
        if (r != null) {
            if (wasStarted) runCatching { r.stop() }
            runCatching { r.release() }
        }
        f?.delete()
    }
}
