package io.github.stickerbug.dsapichat.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.github.stickerbug.dsapichat.AppViewModel
import io.github.stickerbug.dsapichat.R
import io.github.stickerbug.dsapichat.data.Settings as DsSettings
import io.github.stickerbug.dsapichat.ui.chat.TtsController
import io.github.stickerbug.dsapichat.ui.theme.Ds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

private val PAGE_L = Color(0xFFF8F8F8)
private val PAGE_D = Color(0xFF1E1E1E)
private val CARD_L = Color(0xFFFFFFFF)
private val CARD_D = Color(0xFF292929)
private val INK = Color(0xFF0F0F0F)
private val MUTED = Color(0xFF8E9094)
private val CHEV = Color(0xFFB9BABB)
private val CHEV_D = Color(0xFF5C5F66)
private val VALUE = Color(0xFF8E9094)
private val VALUE_D = Color(0xFF7C7E82)
private val FOOT = Color(0xFF595959)
private val DIV = Color(0xFFEDEDEF)
private val DIV_D = Color(0xFF3A3A3E)
private val BRAND = Color(0xFF4D6BFE)
private const val ASR_OPENAI_BASE = "https://api.openai.com/v1"
private const val ASR_GROQ_BASE = "https://api.groq.com/openai/v1"
private const val ASR_SF_BASE = "https://api.siliconflow.cn/v1"
private const val TTS_OPENAI_BASE = "https://api.openai.com/v1"
private const val TTS_GROQ_BASE = "https://api.groq.com/openai/v1"
private const val TTS_SF_BASE = "https://api.siliconflow.cn/v1"
private const val GITHUB_REPO = "Stickerbug/ds-api-chat"
private const val GITHUB_RELEASES = "https://github.com/" + GITHUB_REPO + "/releases/latest"
private const val GITHUB_ISSUES = "https://github.com/" + GITHUB_REPO + "/issues"

private val RED = Color(0xFFE04B4B)
private val SWITCH_GREEN = Color(0xFF22C55E)
private val SWITCH_OFF = Color(0xFFE5E5E5)

@Composable
private fun inkColor(): Color = if (Ds.color.dark) Ds.color.textPrimary else INK

@Composable
private fun cardColor(): Color = if (Ds.color.dark) CARD_D else CARD_L

@Composable
private fun pageColor(): Color = if (Ds.color.dark) PAGE_D else PAGE_L

@Composable
private fun valueColor2(): Color = if (Ds.color.dark) VALUE_D else VALUE

@Composable
private fun chevColor(): Color = if (Ds.color.dark) CHEV_D else CHEV

@Composable
private fun divColor(): Color = if (Ds.color.dark) DIV_D else DIV

private enum class Page { Main, Data, Personalize, FontSize }

private enum class Dlg {
    None, ApiKey, BaseUrl, Model, Protocol, Language, Theme, Terms,
    ClearAll, Voice, Asr, Tts
}

@Composable
fun SettingsRoute(vm: AppViewModel, onBack: () -> Unit) {
    val c = Ds.color
    val s by vm.settings.collectAsState()
    var page by remember { mutableStateOf(Page.Main) }
    var dlg by remember { mutableStateOf(Dlg.None) }
    val context = LocalContext.current
    fun toast(msg: String) { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            toast("正在导入，请稍候…")
            vm.importConversations(uri) { msg -> toast(msg) }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportConversations(uri) { msg -> toast(msg) }
    }
    val scope = rememberCoroutineScope()
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    val versionName = remember(context) { appVersionName(context) }
    fun openUrl(url: String) {
        val ok = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
        if (!ok) toast("没有可用的浏览器")
    }
    fun checkUpdate() {
        toast("正在检查更新…")
        scope.launch {
            val latest = withContext(Dispatchers.IO) { fetchLatestRelease() }
            val newer = latest != null && isNewerVersion(latest.version, versionName)
            updateInfo = when {
                latest == null -> UpdateInfo("检查更新", "检查失败，请检查网络后重试。", null)
                !newer -> UpdateInfo("检查更新", "当前已是最新版本（v" + versionName + "）。", null)
                else -> UpdateInfo(
                    "发现新版本 v" + latest.version,
                    "当前版本 v" + versionName + "。" + (if (latest.notes.isBlank()) "" else "\n\n" + latest.notes.take(300)),
                    latest.url
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().background(pageColor())) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        val title = when (page) {
            Page.Main -> "设置"
            Page.Data -> "数据管理"
            Page.Personalize -> "个性化"
            Page.FontSize -> "字体大小"
        }
        SettingsTopBar(title = title) {
            if (page == Page.Main) onBack() else page = Page.Main
        }
        // 平板宽屏（≥760dp）时设置列限宽 720dp 居中；手机屏宽 361dp，像素不变。
        val wideContent = LocalConfiguration.current.screenWidthDp >= 760
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(if (wideContent) Modifier.width(720.dp) else Modifier.fillMaxWidth())
            ) {
            // 子页切换动画：官方为纯交叉淡入（~250ms），没有横向位移。
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
                },
                label = "settingsPage"
            ) { p ->
                when (p) {
                Page.Main -> MainPage(
                    s = s,
                    versionName = versionName,
                    openPage = { page = it },
                    openDlg = { dlg = it },
                    save = { vm.saveSettings(it) },
                    onCheckUpdate = { checkUpdate() },
                    onOpenFeedback = { openUrl(GITHUB_ISSUES) }
                )
                Page.Data -> DataPage(
                    s,
                    save = { vm.saveSettings(it) },
                    onDeleteAll = { dlg = Dlg.ClearAll },
                    openDlg = { dlg = it },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onExport = { exportLauncher.launch("ds-api-chat-conversations.json") }
                )
                Page.Personalize -> PersonalizePage(s, save = { vm.saveSettings(it) })
                Page.FontSize -> FontSizePage(s, save = { vm.saveSettings(it) })
                }
            }
            }
        }
    }

    if (dlg == Dlg.Voice) {
        VoiceSheetOverlay(
            s = s,
            onDismiss = { dlg = Dlg.None },
            onConfirm = { cloudVoice, localVoice ->
                vm.saveSettings(s.copy(ttsVoice = cloudVoice, ttsLocalVoice = localVoice))
                dlg = Dlg.None
            }
        )
    }
    }
    when (dlg) {
        Dlg.None -> Unit
        Dlg.Voice -> Unit
        Dlg.Asr -> AsrDialog(
            s = s,
            onDismiss = { dlg = Dlg.None },
            onSave = { ns -> vm.saveSettings(ns); dlg = Dlg.None }
        )
        Dlg.Tts -> TtsDialog(
            s = s,
            onDismiss = { dlg = Dlg.None },
            onSave = { ns -> vm.saveSettings(ns); dlg = Dlg.None }
        )
        Dlg.Language -> RadioDialog(
            title = "语言",
            iconRes = R.drawable.ic_globe_language_regular,
            options = listOf("系统", "中文 (简体中文)", "日本語", "English", "español"),
            selected = if (s.language == "en") "English" else "中文 (简体中文)",
            onDismiss = { dlg = Dlg.None },
            onConfirm = { v ->
                vm.saveSettings(s.copy(language = if (v == "English") "en" else "zh"))
                dlg = Dlg.None
            }
        )
        Dlg.Theme -> RadioDialog(
            title = "外观",
            iconRes = null,
            options = listOf("系统", "浅色", "深色"),
            selected = when (s.darkMode) { 1 -> "浅色"; 2 -> "深色"; else -> "系统" },
            onDismiss = { dlg = Dlg.None },
            onConfirm = { v ->
                val m = when (v) { "浅色" -> 1; "深色" -> 2; else -> 0 }
                vm.saveSettings(s.copy(darkMode = m))
                dlg = Dlg.None
            }
        )
        Dlg.Terms -> InfoDialog(
            title = "服务协议",
            body = "本应用仅提供界面与通用 API 调用能力，不提供任何模型服务。API 使用需遵守服务提供方的条款。界面图标版权归深度求索所有，仅供个人学习使用。",
            onClose = { dlg = Dlg.None }
        )
        Dlg.ClearAll -> AlertDialog(
            onDismissRequest = { dlg = Dlg.None },
            title = { Text("删除所有历史对话", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("本机保存的全部对话记录将被删除，无法恢复。", fontSize = 15.sp) },
            confirmButton = {
                TextButton(onClick = { vm.clearAllConversations(); dlg = Dlg.None }) {
                    Text("删除", color = RED)
                }
            },
            dismissButton = { TextButton(onClick = { dlg = Dlg.None }) { Text("取消", color = MUTED) } },
            containerColor = cardColor(),
            shape = RoundedCornerShape(18.dp)
        )
        Dlg.ApiKey -> TextInputDialog(
            title = "API Key",
            initial = s.apiKey,
            hint = "sk-...",
            description = "在 platform.deepseek.com 创建，只保存在本机。",
            onDismiss = { dlg = Dlg.None },
            onConfirm = { v -> vm.saveSettings(s.copy(apiKey = v.trim())); dlg = Dlg.None }
        )
        Dlg.BaseUrl -> TextInputDialog(
            title = "接口地址",
            initial = s.baseUrl,
            hint = "https://api.deepseek.com",
            description = "填写服务根地址。OpenAI 协议使用 /chat/completions，Anthropic 协议自动使用 /anthropic/v1/messages。",
            onDismiss = { dlg = Dlg.None },
            onConfirm = { v ->
                vm.saveSettings(s.copy(baseUrl = v.trim().ifEmpty { "https://api.deepseek.com" }))
                dlg = Dlg.None
            }
        )
        Dlg.Model -> ChoiceDialog(
            title = "模型",
            options = listOf("deepseek-chat", "deepseek-reasoner", "deepseek-flash", "deepseek-v4-pro"),
            selected = s.model,
            onDismiss = { dlg = Dlg.None },
            onSelect = { v -> vm.saveSettings(s.copy(model = v)); dlg = Dlg.None }
        )
        Dlg.Protocol -> ChoiceDialog(
            title = "接口协议",
            options = listOf("自动识别", "OpenAI 兼容", "Anthropic 兼容"),
            selected = protocolLabel(s.protocol),
            onDismiss = { dlg = Dlg.None },
            onSelect = { v ->
                val p = when (v) {
                    "OpenAI 兼容" -> "openai"
                    "Anthropic 兼容" -> "anthropic"
                    else -> "auto"
                }
                vm.saveSettings(s.copy(protocol = p))
                dlg = Dlg.None
            }
        )
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text(info.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text(info.body, fontSize = 14.sp, color = Ds.color.textTertiary, lineHeight = 21.sp) },
            confirmButton = {
                TextButton(onClick = {
                    val u = info.url
                    updateInfo = null
                    if (u != null) openUrl(u)
                }) {
                    Text(if (info.url != null && info.title.startsWith("发现新版本")) "前往下载" else "知道了", color = BRAND)
                }
            },
            dismissButton = null,
            containerColor = cardColor(),
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun MainPage(
    s: DsSettings,
    versionName: String,
    openPage: (Page) -> Unit,
    openDlg: (Dlg) -> Unit,
    save: (DsSettings) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenFeedback: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.7.dp, end = 14.7.dp, bottom = 40.dp)
    ) {
        item { SectionLabel("账户", topPad = 10.dp) }
        item {
            DsCard {
                IconRow(R.drawable.ic_data_regular, "数据管理") { openPage(Page.Data) }
            }
        }
        item { SectionLabel("应用") }
        item {
            DsCard {
                IconRow(R.drawable.ic_globe_language_regular, "语言", if (s.language == "en") "English" else "中文 (简体中文)") {
                    openDlg(Dlg.Language)
                }
                SettingRow(
                    label = "外观",
                    value = themeLabel(s.darkMode),
                    leading = {
                        Icon(
                            painter = painterResource(if (Ds.color.dark) R.drawable.ic_moon_regular else R.drawable.ic_sun_regular),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp).offset(x = (-1).dp),
                            tint = inkColor()
                        )
                    }
                ) { openDlg(Dlg.Theme) }
                SettingRow(
                    label = "字体大小",
                    leading = { AaIcon() }
                ) { openPage(Page.FontSize) }
                IconRow(R.drawable.ic_wand, "个性化") { openPage(Page.Personalize) }
            }
        }
        item { SectionLabel("语音") }
        item {
            DsCard {
                IconRow(R.drawable.ic_sound_outline_24, "朗读音色") { openDlg(Dlg.Voice) }
                IconRow(
                    R.drawable.ic_mic_filled_20,
                    "语音识别",
                    value = when (s.asrMode) {
                        "cloud" -> if (s.asrBaseUrl.isNotBlank()) "云端" else "云端（未配置）"
                        "system" -> "系统识别"
                        else -> if (s.asrBaseUrl.isNotBlank()) "自动（云端）" else "自动（系统）"
                    }
                ) { openDlg(Dlg.Asr) }
                IconRow(
                    R.drawable.ic_voice_outline_26,
                    "朗读合成",
                    value = when (s.ttsMode) {
                        "cloud" -> if (s.ttsBaseUrl.isNotBlank()) "云端" else "云端（未配置）"
                        "local" -> "本地"
                        else -> if (s.ttsBaseUrl.isNotBlank()) "自动（云端）" else "自动（系统）"
                    }
                ) { openDlg(Dlg.Tts) }
            }
        }
        item { SectionLabel("接口") }
        item {
            DsCard {
                IconRow(R.drawable.ic_lock_fill, "API Key", maskKey(s.apiKey)) { openDlg(Dlg.ApiKey) }
                IconRow(
                    R.drawable.ic_globe_language_outline_20,
                    "接口地址",
                    s.baseUrl.removePrefix("https://").removePrefix("http://")
                ) { openDlg(Dlg.BaseUrl) }
                IconRow(R.drawable.ic_expert_outline_16, "模型", s.model) { openDlg(Dlg.Model) }
                IconRow(R.drawable.ic_list_bullets_outline_20, "接口协议", protocolLabel(s.protocol)) { openDlg(Dlg.Protocol) }
                IconRow(R.drawable.ic_globe_language_outline_16, "联网搜索", if (s.search) "开启" else "关闭") {
                    save(s.copy(search = !s.search))
                }
            }
        }
        item { SectionLabel("关于") }
        item {
            DsCard {
                IconRow(R.drawable.ic_info_outline, "检查更新", "v" + versionName) { onCheckUpdate() }
                IconRow(R.drawable.ic_list_pen_regular, "服务协议") { openDlg(Dlg.Terms) }
            }
        }
        item { Spacer(Modifier.height(33.3.dp)) }
        item {
            DsCard {
                IconRow(R.drawable.ic_help_outline, "帮助与反馈") { onOpenFeedback() }
            }
        }
        item { Footer() }
    }
}

@Composable
private fun DataPage(
    s: DsSettings,
    save: (DsSettings) -> Unit,
    onDeleteAll: () -> Unit,
    openDlg: (Dlg) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 14.7.dp, end = 14.7.dp)
    ) {
        Spacer(Modifier.height(11.3.dp))
        DsCard { PlainRow("导入对话", value = "zip / JSON") { onImport() } }
        Spacer(Modifier.height(26.dp))
        DsCard { PlainRow("导出所有历史对话") { onExport() } }
        Spacer(Modifier.height(26.dp))
        DsCard { PlainRow("删除所有历史对话", color = RED, chevron = false) { onDeleteAll() } }
    }
}

@Composable
private fun PersonalizePage(s: DsSettings, save: (DsSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 14.7.dp, end = 14.7.dp)
    ) {
        Spacer(Modifier.height(11.3.dp))
        DsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(start = 15.3.dp, end = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("思考内容自动折叠", fontSize = 14.5.sp, color = inkColor(), modifier = Modifier.weight(1f))
                DsSwitch(s.collapseThinking) { save(s.copy(collapseThinking = it)) }
            }
        }
        Text(
            text = "开启后，思考结束后将自动折叠思考内容",
            fontSize = 12.5.sp,
            lineHeight = 19.3.sp,
            color = MUTED,
            modifier = Modifier.padding(start = 15.3.dp, end = 22.7.dp, top = 8.dp)
        )
    }
}

private val FONT_LABELS = listOf("极小", "小", "标准", "大", "较大", "超大", "巨大")

@Composable
private fun FontSizePage(s: DsSettings, save: (DsSettings) -> Unit) {
    val idx = s.fontSize.coerceIn(0, 6)
    val previewSp = (13 + idx).sp
    val previewLine = (22.3 + idx).sp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageColor())
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 32.3.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (Ds.color.dark) Ds.color.bubble else Color(0xFFEDF1F9))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text("预览文字大小", fontSize = previewSp, color = inkColor())
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("你可以拖动滑块来调整字体大小。", fontSize = previewSp, lineHeight = previewLine, color = inkColor())
            Spacer(Modifier.height(21.6.dp))
            Text(
                "如果在使用过程中存在问题或建议，可反馈给 DeepSeek 团队。",
                fontSize = previewSp,
                lineHeight = previewLine,
                color = inkColor()
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(cardColor())
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.height(18.2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(start = 26.dp, end = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("跟随系统", fontSize = 14.5.sp, color = inkColor(), modifier = Modifier.weight(1f))
                DsSwitch(s.followSystemFont) { save(s.copy(followSystemFont = it)) }
            }
            Spacer(Modifier.height(25.9.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.7.dp)
            ) {
                SliderRow(idx, enabled = !s.followSystemFont) { v -> save(s.copy(fontSize = v)) }
            }
            Spacer(Modifier.height(35.dp))
        }
    }
}

@Composable
private fun SliderRow(index: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val ink = inkColor()
    val trackColor = if (Ds.color.dark) Color(0xFF444448) else Color(0xFFB9BABB)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val startDp = 11.7.dp
        val endDp = maxWidth - 12.3.dp
        val span = endDp - startDp
        val labelMax = maxWidth - 40.dp
        val frac = index / 6f
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    paint.color = ink.toArgb()
                    paint.typeface = android.graphics.Typeface.DEFAULT
                    val baseline = size.height * 0.8f
                    paint.textSize = 14.sp.toPx()
                    drawContext.canvas.nativeCanvas.drawText("A", 6.7.dp.toPx(), baseline, paint)
                    paint.textSize = 20.sp.toPx()
                    val wA = paint.measureText("A")
                    drawContext.canvas.nativeCanvas.drawText("A", size.width - 6.3.dp.toPx() - wA, baseline, paint)
                }
                Text(
                    text = FONT_LABELS[index],
                    fontSize = 12.sp,
                    color = ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset(x = (startDp + span * frac - 20.dp).coerceIn(0.dp, labelMax))
                        .width(40.dp)
                        .align(Alignment.CenterStart)
                )
            }
            Spacer(Modifier.height(5.9.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .pointerInput(index, enabled) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { pos ->
                            val sp = span.toPx()
                            if (sp <= 0f) return@detectTapGestures
                            val f = ((pos.x - startDp.toPx()) / sp).coerceIn(0f, 1f)
                            onChange((f * 6f).roundToInt())
                        }
                    }
                    .pointerInput(index, enabled) {
                        if (!enabled) return@pointerInput
                        detectHorizontalDragGestures { change, _ ->
                            val sp = span.toPx()
                            if (sp <= 0f) return@detectHorizontalDragGestures
                            val f = ((change.position.x - startDp.toPx()) / sp).coerceIn(0f, 1f)
                            val v = (f * 6f).roundToInt()
                            if (v != index) onChange(v)
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val y = size.height / 2f
                    val sx = startDp.toPx()
                    val ex = endDp.toPx()
                    drawLine(trackColor, Offset(sx, y), Offset(ex, y), strokeWidth = 2.dp.toPx())
                    for (i in 0..6) {
                        val x = sx + (ex - sx) * (i / 6f)
                        drawLine(
                            trackColor,
                            Offset(x, y - 3.dp.toPx()),
                            Offset(x, y + 3.dp.toPx()),
                            strokeWidth = 1.7.dp.toPx()
                        )
                    }
                    val tx = sx + (ex - sx) * frac
                    drawCircle(Color(0x22000000), radius = 15.5.dp.toPx(), center = Offset(tx, y + 0.8.dp.toPx()))
                    drawCircle(Color.White, radius = 15.dp.toPx(), center = Offset(tx, y))
                }
            }
        }
    }
}

@Composable
private fun DsSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p by animateFloatAsState(if (checked) 1f else 0f, tween(200))
    val track = lerp(SWITCH_OFF, SWITCH_GREEN, p)
    Canvas(
        modifier = Modifier
            .size(width = 47.7.dp, height = 29.3.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onChange(!checked) }
    ) {
        val h = size.height
        val w = size.width
        val r = h / 2f
        drawRoundRect(track, cornerRadius = CornerRadius(r, r))
        val knobD = (14.7f + 7.3f * p) * density
        val inset = 3.65f * density
        val cx = inset + knobD / 2f + (w - 2f * inset - knobD) * p
        drawCircle(Color(0x1A000000), radius = knobD / 2f + 0.5f * density, center = Offset(cx, h / 2f + 0.8f * density))
        drawCircle(Color.White, radius = knobD / 2f, center = Offset(cx, h / 2f))
    }
}

@Composable
private fun Footer() {
    Text(
        text = "模型名称：Deepseek Chat\n备案号：Beijing-DeepseekChat-202404280016\n浙ICP备2023025841号-3A\n内容由 AI 生成，请仔细甄别，并合法使用",
        fontSize = 12.sp,
        lineHeight = 18.3.sp,
        color = FOOT,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 24.dp, start = 6.dp, end = 6.dp)
    )
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    val c = Ds.color
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(pageColor())
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.7.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(if (c.dark) c.field else Color(0xFFEDEDED))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left_outline_20),
                contentDescription = "返回",
                modifier = Modifier.size(18.dp),
                tint = inkColor()
            )
        }
        Text(
            text = title,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = inkColor(),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun SectionLabel(text: String, topPad: Dp = 24.7.dp) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = MUTED,
        modifier = Modifier.padding(start = 14.3.dp, top = topPad, bottom = 7.3.dp)
    )
}

@Composable
private fun DsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.3.dp))
            .background(cardColor())
    ) { content() }
}

@Composable
private fun AaIcon() {
    val color = inkColor()
    Canvas(Modifier.size(20.dp)) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = color.toArgb()
        paint.typeface = android.graphics.Typeface.DEFAULT
        val nc = drawContext.canvas.nativeCanvas
        val baseline = size.height * 0.767f
        paint.textSize = 11.sp.toPx()
        nc.drawText("A", size.width * 0.04f, baseline, paint)
        paint.textSize = 15.3.sp.toPx()
        nc.drawText("A", size.width * 0.42f, baseline, paint)
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String? = null,
    chevron: Boolean = true,
    labelColor: Color? = null,
    leading: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable { onClick() }
            .padding(start = 14.dp, end = 7.8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(9.7.dp))
        Text(
            text = label,
            fontSize = 14.5.sp,
            color = labelColor ?: inkColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                fontSize = 14.sp,
                color = valueColor2(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        if (chevron) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_outline_20),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = chevColor()
            )
        }
    }
}

@Composable
private fun IconRow(
    iconRes: Int,
    label: String,
    value: String? = null,
    chevron: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit
) {
    SettingRow(
        label = label,
        value = value,
        chevron = chevron,
        leading = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp).offset(x = (-1).dp),
                tint = tint ?: inkColor()
            )
        }
    ) { onClick() }
}

@Composable
private fun PlainRow(label: String, value: String? = null, color: Color? = null, chevron: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable { onClick() }
            .padding(start = 15.3.dp, end = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.5.sp,
            color = color ?: inkColor(),
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                fontSize = 14.sp,
                color = valueColor2(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
        }
        if (chevron) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_outline_20),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = chevColor()
            )
        }
    }
}

@Composable
private fun RadioDialog(
    title: String,
    iconRes: Int?,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var sel by remember { mutableStateOf(selected) }
    // 官方外观/语言弹窗：遮罩与卡片同步淡入（~180ms），无缩放无位移；关闭 ~140ms。
    var closing by remember { mutableStateOf(false) }
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(190, easing = FastOutSlowInEasing)) }
    LaunchedEffect(closing) {
        if (closing) {
            anim.animateTo(0f, tween(150))
            afterClose?.invoke()
        }
    }
    fun closeThen(after: () -> Unit) {
        if (!closing) {
            afterClose = after
            closing = true
        }
    }
    Dialog(
        onDismissRequest = { closeThen(onDismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(Unit) { dialogWindow?.setDimAmount(0f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.54f * anim.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { closeThen(onDismiss) },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(265.dp)
                    .graphicsLayer { alpha = anim.value }
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardColor())
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                if (iconRes != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = inkColor()
                        )
                    }
                }
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = inkColor(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (iconRes != null) 10.dp else 22.dp, bottom = 16.dp)
                )
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(divColor()))
                options.forEach { opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.7.dp)
                            .clickable { sel = opt }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioDot(selected = opt == sel)
                        Spacer(Modifier.width(14.dp))
                        Text(opt, fontSize = 16.5.sp, color = inkColor())
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(divColor()))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable { closeThen { onConfirm(sel) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text("确认", fontSize = 16.5.sp, color = BRAND, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val color = if (selected) inkColor() else (if (Ds.color.dark) Color(0xFF6E7278) else Color(0xFFB7BABF))
    Canvas(Modifier.size(20.dp)) {
        val r = size.minDimension / 2f
        drawCircle(color, radius = r - 1.2.dp.toPx(), style = Stroke(width = if (selected) 2.dp.toPx() else 1.4.dp.toPx()))
        if (selected) {
            drawCircle(color, radius = r * 0.52f)
        }
    }
}

@Composable
private fun VoiceSheetOverlay(
    s: DsSettings,
    onDismiss: () -> Unit,
    onConfirm: (cloudVoice: String, localVoice: String) -> Unit
) {
    // 官方朗读音色弹窗：遮罩与面板同步淡入（~220ms），无缩放；关闭 ~160ms。
    var closing by remember { mutableStateOf(false) }
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    LaunchedEffect(closing) {
        if (closing) {
            anim.animateTo(0f, tween(160))
            afterClose?.invoke()
        }
    }
    fun closeThen(after: () -> Unit) {
        if (!closing) {
            afterClose = after
            closing = true
        }
    }
    val context = LocalContext.current
    var cloudVoice by remember { mutableStateOf(s.ttsVoice) }
    var localVoice by remember { mutableStateOf(s.ttsLocalVoice) }
    var localVoices by remember { mutableStateOf<List<TtsController.VoiceOption>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (TtsController.awaitReady(context, silent = true)) localVoices = TtsController.localVoices()
    }
    val cloudVoices = listOf(
        "" to "默认（服务端默认）",
        "alloy" to "alloy · 中性",
        "echo" to "echo · 男声",
        "fable" to "fable · 英式",
        "onyx" to "onyx · 低沉",
        "nova" to "nova · 女声",
        "shimmer" to "shimmer · 柔和"
    )
    Dialog(
        onDismissRequest = { closeThen(onDismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(Unit) { dialogWindow?.setDimAmount(0f) }
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x8A000000).copy(alpha = 0.54f * anim.value))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { closeThen(onDismiss) }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = anim.value }
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(if (Ds.color.dark) PAGE_D else Color.White)
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = "朗读音色",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = inkColor(),
                        modifier = Modifier.align(Alignment.Center)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 13.7.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (Ds.color.dark) Ds.color.field else Color(0xFFF0F0F2))
                            .clickable { closeThen(onDismiss) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close_outline_16),
                            contentDescription = "关闭",
                            modifier = Modifier.size(13.dp),
                            tint = inkColor()
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 22.dp, end = 22.dp)
                ) {
                    Text(
                        "云端朗读音色（用于「朗读合成」的云端接口）",
                        fontSize = 12.5.sp,
                        color = MUTED
                    )
                    Spacer(Modifier.height(4.dp))
                    cloudVoices.forEach { (value, label) ->
                        VoiceRow(label = label, selected = cloudVoice == value) { cloudVoice = value }
                    }
                    Spacer(Modifier.height(4.dp))
                    AsrField(cloudVoice, { cloudVoice = it }, "自定义音色", "alloy / Fritz-PlayAI / alex")
                    Spacer(Modifier.height(16.dp))
                    Text("本地朗读音色（系统语音引擎）", fontSize = 12.5.sp, color = MUTED)
                    Spacer(Modifier.height(4.dp))
                    if (localVoices.isEmpty()) {
                        Text(
                            "系统引擎未就绪或没有可用音色，将使用系统默认音色。",
                            fontSize = 12.5.sp,
                            color = MUTED,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        VoiceRow(label = "系统默认", selected = localVoice.isEmpty()) { localVoice = "" }
                        localVoices.forEach { v ->
                            VoiceRow(label = v.label, selected = localVoice == v.name) { localVoice = v.name }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(BRAND)
                        .clickable { closeThen { onConfirm(cloudVoice.trim(), localVoice) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text("确认", fontSize = 16.5.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(14.4.dp))
            }
        }
    }
}

@Composable
private fun VoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.5.sp,
            color = inkColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        RadioDot(selected)
    }
}


@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    hint: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(description, fontSize = 13.sp, color = MUTED, lineHeight = 19.sp)
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = v,
                    onValueChange = { v = it },
                    textStyle = TextStyle(fontSize = 15.sp, color = inkColor()),
                    cursorBrush = SolidColor(BRAND),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (Ds.color.dark) Ds.color.field else Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    decorationBox = { inner ->
                        if (v.isEmpty()) Text(hint, fontSize = 15.sp, color = MUTED)
                        inner()
                    }
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(v) }) { Text("保存", color = BRAND) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = MUTED) } },
        containerColor = cardColor(),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val sel = opt == selected
            Box(
                modifier = Modifier
                    .background(
                        if (sel) BRAND.copy(alpha = 0.14f)
                        else (if (Ds.color.dark) Ds.color.field else Color(0xFFF5F5F5)),
                        RoundedCornerShape(9.dp)
                    )
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = opt,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                    color = if (sel) BRAND else inkColor()
                )
            }
        }
    }
}

@Composable
private fun AsrDialog(
    s: DsSettings,
    onDismiss: () -> Unit,
    onSave: (DsSettings) -> Unit
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(if (s.asrMode == "cloud" || s.asrMode == "system") s.asrMode else "auto") }
    var base by remember { mutableStateOf(s.asrBaseUrl) }
    var key by remember { mutableStateOf(s.asrApiKey) }
    var model by remember { mutableStateOf(s.asrModel) }
    var lang by remember { mutableStateOf(s.asrLanguage) }
    val preset = when (base) {
        ASR_OPENAI_BASE -> "OpenAI"
        ASR_GROQ_BASE -> "Groq"
        ASR_SF_BASE -> "SiliconFlow"
        else -> "自定义"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("语音识别", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "识别方式：自动 = 填了云端接口就用云端，否则用系统识别；云端 = 必须填接口，没填会提示不可用；系统 = 只用本机识别（部分机型已被移除）。",
                    fontSize = 13.sp, color = MUTED, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                Text("识别方式", fontSize = 12.5.sp, color = MUTED)
                Spacer(Modifier.height(6.dp))
                ChipRow(
                    options = listOf("自动", "云端", "系统"),
                    selected = when (mode) { "cloud" -> "云端"; "system" -> "系统"; else -> "自动" }
                ) { label ->
                    mode = when (label) { "云端" -> "cloud"; "系统" -> "system"; else -> "auto" }
                }
                Spacer(Modifier.height(12.dp))
                Text("快速预设（可修改）", fontSize = 12.5.sp, color = MUTED)
                Spacer(Modifier.height(6.dp))
                ChipRow(
                    options = listOf("OpenAI", "Groq", "SiliconFlow", "自定义"),
                    selected = preset
                ) { p ->
                    when (p) {
                        "OpenAI" -> { base = ASR_OPENAI_BASE; model = "whisper-1" }
                        "Groq" -> { base = ASR_GROQ_BASE; model = "whisper-large-v3-turbo" }
                        "SiliconFlow" -> { base = ASR_SF_BASE; model = "FunAudioLLM/SenseVoiceSmall" }
                    }
                }
                Spacer(Modifier.height(12.dp))
                AsrField(base, { base = it }, "接口地址", "https://api.openai.com/v1")
                Spacer(Modifier.height(8.dp))
                AsrField(key, { key = it }, "API Key（可留空）", "sk-...")
                Spacer(Modifier.height(8.dp))
                AsrField(model, { model = it }, "模型", "whisper-1")
                Spacer(Modifier.height(12.dp))
                Text("识别语言（云端识别与系统识别通用）", fontSize = 12.5.sp, color = MUTED)
                Spacer(Modifier.height(6.dp))
                ChipRow(
                    options = listOf("自动", "中文", "英文", "日语"),
                    selected = when (lang) { "zh" -> "中文"; "en" -> "英文"; "ja" -> "日语"; else -> "自动" }
                ) { label ->
                    lang = when (label) { "中文" -> "zh"; "英文" -> "en"; "日语" -> "ja"; else -> "auto" }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val nb = base.trim()
                if (mode == "cloud" && nb.isEmpty()) {
                    Toast.makeText(context, "已保存；云端接口还没填，语音输入时会提示不可用", Toast.LENGTH_LONG).show()
                }
                onSave(s.copy(asrMode = mode, asrBaseUrl = nb, asrApiKey = key.trim(), asrModel = model.trim(), asrLanguage = lang))
            }) { Text("保存", color = BRAND) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = MUTED) } },
        containerColor = cardColor(),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun TtsDialog(
    s: DsSettings,
    onDismiss: () -> Unit,
    onSave: (DsSettings) -> Unit
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(if (s.ttsMode == "cloud" || s.ttsMode == "local") s.ttsMode else "auto") }
    var base by remember { mutableStateOf(s.ttsBaseUrl) }
    var key by remember { mutableStateOf(s.ttsApiKey) }
    var model by remember { mutableStateOf(s.ttsModel) }
    var voice by remember { mutableStateOf(s.ttsVoice) }
    var speed by remember { mutableStateOf(if (s.ttsLocalSpeed in listOf(0.8f, 1.0f, 1.2f, 1.5f)) s.ttsLocalSpeed else 1.0f) }
    val preset = when (base) {
        TTS_OPENAI_BASE -> "OpenAI"
        TTS_GROQ_BASE -> "Groq"
        TTS_SF_BASE -> "SiliconFlow"
        else -> "自定义"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("朗读合成", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "朗读方式：自动 = 填了云端接口就用云端，否则用系统朗读；云端 = 必须填接口，没填会提示不可用；本地 = 系统语音引擎（可调语速）。云端合成更自然，且不依赖系统引擎。",
                    fontSize = 13.sp, color = MUTED, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                Text("朗读方式", fontSize = 12.5.sp, color = MUTED)
                Spacer(Modifier.height(6.dp))
                ChipRow(
                    options = listOf("自动", "云端", "本地"),
                    selected = when (mode) { "cloud" -> "云端"; "local" -> "本地"; else -> "自动" }
                ) { label ->
                    mode = when (label) { "云端" -> "cloud"; "本地" -> "local"; else -> "auto" }
                }
                Spacer(Modifier.height(12.dp))
                Text("快速预设（可修改）", fontSize = 12.5.sp, color = MUTED)
                Spacer(Modifier.height(6.dp))
                ChipRow(
                    options = listOf("OpenAI", "Groq", "SiliconFlow", "自定义"),
                    selected = preset
                ) { p ->
                    when (p) {
                        "OpenAI" -> { base = TTS_OPENAI_BASE; model = "tts-1"; voice = "alloy" }
                        "Groq" -> { base = TTS_GROQ_BASE; model = "playai-tts"; voice = "Fritz-PlayAI" }
                        "SiliconFlow" -> { base = TTS_SF_BASE; model = "FunAudioLLM/CosyVoice2-0.5B"; voice = "alex" }
                    }
                }
                Spacer(Modifier.height(12.dp))
                AsrField(base, { base = it }, "接口地址", "https://api.openai.com/v1")
                Spacer(Modifier.height(8.dp))
                AsrField(key, { key = it }, "API Key（留空用语音识别的）", "sk-...")
                Spacer(Modifier.height(8.dp))
                AsrField(model, { model = it }, "模型", "tts-1")
                Spacer(Modifier.height(8.dp))
                AsrField(voice, { voice = it }, "音色", "alloy")
                Spacer(Modifier.height(12.dp))
                Text("本地朗读语速", fontSize = 12.5.sp, color = MUTED)
                Spacer(Modifier.height(6.dp))
                ChipRow(
                    options = listOf("0.8×", "1.0×", "1.2×", "1.5×"),
                    selected = when (speed) { 0.8f -> "0.8×"; 1.2f -> "1.2×"; 1.5f -> "1.5×"; else -> "1.0×" }
                ) { label ->
                    speed = when (label) { "0.8×" -> 0.8f; "1.2×" -> 1.2f; "1.5×" -> 1.5f; else -> 1.0f }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val nb = base.trim()
                if (mode == "cloud" && nb.isEmpty()) {
                    Toast.makeText(context, "已保存；云端接口还没填，朗读时会提示不可用", Toast.LENGTH_LONG).show()
                }
                onSave(
                    s.copy(
                        ttsMode = mode,
                        ttsBaseUrl = nb,
                        ttsApiKey = key.trim(),
                        ttsModel = model.trim(),
                        ttsVoice = voice.trim(),
                        ttsLocalSpeed = speed
                    )
                )
            }) { Text("保存", color = BRAND) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = MUTED) } },
        containerColor = cardColor(),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun AsrField(value: String, onChange: (String) -> Unit, label: String, hint: String) {
    Column {
        Text(label, fontSize = 12.5.sp, color = MUTED)
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(fontSize = 15.sp, color = inkColor()),
            cursorBrush = SolidColor(BRAND),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (Ds.color.dark) Ds.color.field else Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, fontSize = 15.sp, color = MUTED)
                inner()
            }
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                options.forEach { opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable { onSelect(opt) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = opt,
                            fontSize = 16.sp,
                            color = if (opt == selected) BRAND else inkColor(),
                            modifier = Modifier.weight(1f)
                        )
                        if (opt == selected) Text("✓", fontSize = 16.sp, color = BRAND)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = MUTED) } },
        containerColor = cardColor(),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun InfoDialog(title: String, body: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text(body, fontSize = 14.sp, color = Ds.color.textTertiary, lineHeight = 21.sp) },
        confirmButton = { TextButton(onClick = onClose) { Text("知道了", color = BRAND) } },
        containerColor = cardColor(),
        shape = RoundedCornerShape(18.dp)
    )
}

private fun protocolLabel(p: String): String = when (p) {
    "openai" -> "OpenAI 兼容"
    "anthropic" -> "Anthropic 兼容"
    else -> "自动识别"
}

private fun themeLabel(m: Int): String = when (m) {
    1 -> "浅色"
    2 -> "深色"
    else -> "系统"
}

private fun maskKey(k: String): String =
    if (k.isBlank()) "未填写" else if (k.length <= 10) "已填写" else k.take(4) + "****" + k.takeLast(4)

private data class UpdateInfo(val title: String, val body: String, val url: String?)
private data class ReleaseInfo(val version: String, val url: String, val notes: String)

private fun versionParts(v: String): List<Int> =
    v.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

/** GitHub 的 tag 是否比当前版本更新（逐段数字比较，避免把旧版本当成新版本）。 */
private fun isNewerVersion(latest: String, current: String): Boolean {
    val a = versionParts(latest)
    val b = versionParts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

private fun appVersionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
}.getOrDefault("?")

/** 查 GitHub Releases 的最新版本；失败（无网络、限流等）返回 null。 */
private fun fetchLatestRelease(): ReleaseInfo? = runCatching {
    val conn = URL("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest")
        .openConnection() as HttpURLConnection
    conn.connectTimeout = 8000
    conn.readTimeout = 8000
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    conn.setRequestProperty("User-Agent", "ds-api-chat")
    try {
        if (conn.responseCode !in 200..299) return null
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val version = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(text)
            ?.groupValues?.get(1)?.removePrefix("v").orEmpty()
        val url = Regex("\"html_url\"\\s*:\\s*\"([^\"]+)\"").find(text)
            ?.groupValues?.get(1).orEmpty()
        val notes = Regex("\"body\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(text)
            ?.groupValues?.get(1)
            ?.replace("\\n", "\n")?.replace("\\\"", "\"")?.replace("\\r", "").orEmpty()
        if (version.isBlank()) null
        else ReleaseInfo(version, url.ifBlank { GITHUB_RELEASES }, notes)
    } finally {
        conn.disconnect()
    }
}.getOrNull()
