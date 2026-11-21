package io.github.stickerbug.dsapichat.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-chat",
    val thinking: Boolean = false,
    val search: Boolean = false,
    val protocol: String = "auto",
    val darkMode: Int = 0,
    val language: String = "zh",
    val autoRead: Boolean = false,
    /** 字体大小档位 0..6，2 = 标准。 */
    val fontSize: Int = 2,
    val followSystemFont: Boolean = true,
    val dataOptIn: Boolean = false,
    val collapseThinking: Boolean = true,
    /** 云端语音识别（OpenAI 兼容 /audio/transcriptions）；留空则只用系统识别。 */
    val asrBaseUrl: String = "",
    val asrApiKey: String = "",
    val asrModel: String = "",
    /** 云端朗读合成（OpenAI 兼容 /audio/speech）；留空则用系统 TTS。 */
    val ttsBaseUrl: String = "",
    val ttsApiKey: String = "",
    val ttsModel: String = "",
    val ttsVoice: String = "",
    /** 朗读方式：auto=有云端配置用云端、否则本地；cloud=强制云端（未配置则提示不可用）；local=强制本地系统引擎。 */
    val ttsMode: String = "auto",
    /** 语音识别方式：auto=有云端配置用云端、否则系统；cloud=强制云端（未配置则提示不可用）；system=强制系统识别。 */
    val asrMode: String = "auto",
    /** 本地系统朗读语速。 */
    val ttsLocalSpeed: Float = 1.0f
) {
    val reasoner: Boolean get() = model.contains("reasoner")
}

class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("ds_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        Settings(
            apiKey = sp.getString("api_key", "") ?: "",
            baseUrl = sp.getString("base_url", "https://api.deepseek.com") ?: "https://api.deepseek.com",
            model = sp.getString("model", "deepseek-chat") ?: "deepseek-chat",
            thinking = sp.getBoolean("thinking", false),
            search = sp.getBoolean("search", false),
            protocol = sp.getString("protocol", "auto") ?: "auto",
            darkMode = sp.getInt("dark_mode", 0),
            language = sp.getString("language", "zh") ?: "zh",
            autoRead = sp.getBoolean("auto_read", false),
            fontSize = sp.getInt("font_size", 2),
            followSystemFont = sp.getBoolean("follow_system_font", true),
            dataOptIn = sp.getBoolean("data_opt_in", false),
            collapseThinking = sp.getBoolean("collapse_thinking", true),
            asrBaseUrl = sp.getString("asr_base_url", "") ?: "",
            asrApiKey = sp.getString("asr_api_key", "") ?: "",
            asrModel = sp.getString("asr_model", "") ?: "",
            ttsBaseUrl = sp.getString("tts_base_url", "") ?: "",
            ttsApiKey = sp.getString("tts_api_key", "") ?: "",
            ttsModel = sp.getString("tts_model", "") ?: "",
            ttsVoice = sp.getString("tts_voice", "") ?: "",
            ttsMode = sp.getString("tts_mode", "auto") ?: "auto",
            asrMode = sp.getString("asr_mode", "auto") ?: "auto",
            ttsLocalSpeed = sp.getFloat("tts_local_speed", 1.0f)
        )
    )
    val settings: StateFlow<Settings> = _settings

    fun current(): Settings = _settings.value

    fun save(s: Settings) {
        sp.edit()
            .putString("api_key", s.apiKey)
            .putString("base_url", s.baseUrl)
            .putString("model", s.model)
            .putBoolean("thinking", s.thinking)
            .putBoolean("search", s.search)
            .putString("protocol", s.protocol)
            .putInt("dark_mode", s.darkMode)
            .putString("language", s.language)
            .putBoolean("auto_read", s.autoRead)
            .putInt("font_size", s.fontSize)
            .putBoolean("follow_system_font", s.followSystemFont)
            .putBoolean("data_opt_in", s.dataOptIn)
            .putBoolean("collapse_thinking", s.collapseThinking)
            .putString("asr_base_url", s.asrBaseUrl)
            .putString("asr_api_key", s.asrApiKey)
            .putString("asr_model", s.asrModel)
            .putString("tts_base_url", s.ttsBaseUrl)
            .putString("tts_api_key", s.ttsApiKey)
            .putString("tts_model", s.ttsModel)
            .putString("tts_voice", s.ttsVoice)
            .putString("tts_mode", s.ttsMode)
            .putString("asr_mode", s.asrMode)
            .putFloat("tts_local_speed", s.ttsLocalSpeed)
            .apply()
        _settings.value = s
    }
}
