package io.github.stickerbug.dsapichat.ui.settings

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
import io.github.stickerbug.dsapichat.ui.theme.Ds
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

private enum class Page { Main, Account, Data, Personalize, FontSize }

private enum class Dlg {
    None, ApiKey, BaseUrl, Model, Protocol, Language, Theme, Update, Terms, Help, HelpFull,
    ClearAll, Logout, SharedLinks, ExportData, DeviceManage, Voice, Asr, Tts
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

    Box(Modifier.fillMaxSize().background(pageColor())) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        val title = when (page) {
            Page.Main -> "设置"
            Page.Account -> "账号管理"
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
                Page.Main -> MainPage(s, openPage = { page = it }, openDlg = { dlg = it }, save = { vm.saveSettings(it) })
                Page.Account -> AccountPage(openDlg = { dlg = it })
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
        VoiceSheetOverlay(onDismiss = { dlg = Dlg.None }, onConfirm = { dlg = Dlg.None })
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
        Dlg.Update -> InfoDialog(
            title = "检查更新",
            body = "当前版本 2.8.0(297)，已是最新版本。复刻版不联网检查更新。",
            onClose = { dlg = Dlg.None }
        )
        Dlg.Terms -> InfoDialog(
            title = "服务协议",
            body = "本应用仅提供界面与通用 API 调用能力，不提供任何模型服务。API 使用需遵守服务提供方的条款。界面图标版权归深度求索所有，仅供个人学习使用。",
            onClose = { dlg = Dlg.None }
        )
        Dlg.Help, Dlg.HelpFull -> InfoDialog(
            title = "帮助与反馈",
            body = "复刻版为个人学习项目：界面复刻官方 DeepSeek。使用中遇到问题可查看项目 README 或提交反馈。",
            onClose = { dlg = Dlg.None }
        )
        Dlg.Logout -> InfoDialog(
            title = "退出登录",
            body = "复刻版是本地客户端，不需要账号登录。接口凭证请在「设置 → 接口」中填写。",
            onClose = { dlg = Dlg.None }
        )
        Dlg.SharedLinks -> InfoDialog(
            title = "我共享的链接",
            body = "复刻版暂未实现分享链接服务，没有共享记录。",
            onClose = { dlg = Dlg.None }
        )
        Dlg.ExportData -> InfoDialog(
            title = "导出所有历史对话",
            body = "复刻版暂未实现导出。对话记录保存在本机的应用私有目录中。",
            onClose = { dlg = Dlg.None }
        )
        Dlg.DeviceManage -> InfoDialog(
            title = "登录设备管理",
            body = "复刻版不接入账号体系，没有登录设备。",
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
}

@Composable
private fun MainPage(s: DsSettings, openPage: (Page) -> Unit, openDlg: (Dlg) -> Unit, save: (DsSettings) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.7.dp, end = 14.7.dp, bottom = 40.dp)
    ) {
        item { SectionLabel("账户", topPad = 10.dp) }
        item {
            DsCard {
                IconRow(R.drawable.ic_user_regular, "账号管理") { openPage(Page.Account) }
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
                    value = if (s.asrBaseUrl.isNotBlank()) "已配置" else "系统识别"
                ) { openDlg(Dlg.Asr) }
                IconRow(
                    R.drawable.ic_voice_outline_26,
                    "朗读合成",
                    value = if (s.ttsBaseUrl.isNotBlank()) "云端" else "系统"
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
                IconRow(R.drawable.ic_info_outline, "检查更新", "2.8.0(297)") { openDlg(Dlg.Update) }
                IconRow(R.drawable.ic_list_pen_regular, "服务协议") { openDlg(Dlg.Terms) }
            }
        }
        item { Spacer(Modifier.height(33.3.dp)) }
        item {
            DsCard {
                IconRow(R.drawable.ic_help_outline, "帮助与反馈") { openDlg(Dlg.HelpFull) }
            }
        }
        item { Spacer(Modifier.height(33.3.dp)) }
        item {
            DsCard {
                IconRow(R.drawable.ic_exit_out_regular, "退出登录", chevron = false) { openDlg(Dlg.Logout) }
            }
        }
        item { Footer() }
    }
}

@Composable
private fun AccountPage(openDlg: (Dlg) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 14.7.dp, end = 14.7.dp)
    ) {
        SectionLabel("账户", topPad = 10.dp)
        DsCard {
            IconRow(R.drawable.ic_phone_outline_24, "手机号", "未绑定") { openDlg(Dlg.Logout) }
            IconRow(R.drawable.ic_letter_regular, "电子邮箱", "未绑定") { openDlg(Dlg.Logout) }
            IconRow(R.drawable.ic_wechat_logo, "微信", "未绑定", tint = Color.Unspecified) { openDlg(Dlg.Logout) }
        }
        Spacer(Modifier.height(26.dp))
        DsCard {
            PlainRow("未成年人模式", value = "已关闭") { openDlg(Dlg.Logout) }
        }
        SectionLabel("安全")
        DsCard {
            IconRow(R.drawable.ic_device_outline_22, "登录设备管理") { openDlg(Dlg.DeviceManage) }
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable { openDlg(Dlg.Logout) },
            contentAlignment = Alignment.Center
        ) {
            Text("注销账号", fontSize = 13.sp, color = RED)
        }
        Spacer(Modifier.height(24.dp))
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
        DsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(start = 15.3.dp, end = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("数据用于优化体验", fontSize = 14.5.sp, color = inkColor(), modifier = Modifier.weight(1f))
                DsSwitch(s.dataOptIn) { save(s.copy(dataOptIn = it)) }
            }
        }
        Text(
            text = "允许我们将你的对话内容用于优化 DeepSeek 的使用体验。我们保障你的数据隐私安全。",
            fontSize = 12.5.sp,
            lineHeight = 19.3.sp,
            color = MUTED,
            modifier = Modifier.padding(start = 15.3.dp, end = 22.7.dp, top = 8.dp)
        )
        Spacer(Modifier.height(25.3.dp))
        DsCard { PlainRow("我共享的链接") { openDlg(Dlg.SharedLinks) } }
        Spacer(Modifier.height(26.dp))
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
private fun VoiceSheetOverlay(onDismiss: () -> Unit, onConfirm: () -> Unit) {
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
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
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
                Spacer(Modifier.height(132.3.dp))
                Image(
                    painter = painterResource(R.drawable.voice_profile_card),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(165.dp)
                        .height(91.7.dp)
                        .clip(RoundedCornerShape(46.dp))
                )
                Spacer(Modifier.height(15.2.dp))
                Text(
                    text = "贝壳",
                    fontSize = 16.sp,
                    lineHeight = 19.2.sp,
                    fontWeight = FontWeight.Medium,
                    color = inkColor(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "百变活泼",
                    fontSize = 12.5.sp,
                    lineHeight = 15.sp,
                    color = MUTED,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(40.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.65.dp)
                                .size(5.7.dp)
                                .clip(CircleShape)
                                .background(if (i == 0) BRAND else Color(0xFFD9D9D9))
                        )
                    }
                }
                Spacer(Modifier.height(78.6.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(BRAND)
                        .clickable { closeThen { onConfirm() } },
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
private fun AsrDialog(
    s: DsSettings,
    onDismiss: () -> Unit,
    onSave: (DsSettings) -> Unit
) {
    var base by remember { mutableStateOf(s.asrBaseUrl) }
    var key by remember { mutableStateOf(s.asrApiKey) }
    var model by remember { mutableStateOf(s.asrModel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("语音识别", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "本机没有可用的语音识别服务时，可配置 OpenAI 兼容的云端识别接口（POST /audio/transcriptions）。留空则只用系统识别。",
                    fontSize = 13.sp, color = MUTED, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                AsrField(base, { base = it }, "接口地址", "https://api.openai.com/v1")
                Spacer(Modifier.height(8.dp))
                AsrField(key, { key = it }, "API Key（可留空）", "sk-...")
                Spacer(Modifier.height(8.dp))
                AsrField(model, { model = it }, "模型", "whisper-1")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(s.copy(asrBaseUrl = base.trim(), asrApiKey = key.trim(), asrModel = model.trim()))
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
    var base by remember { mutableStateOf(s.ttsBaseUrl) }
    var key by remember { mutableStateOf(s.ttsApiKey) }
    var model by remember { mutableStateOf(s.ttsModel) }
    var voice by remember { mutableStateOf(s.ttsVoice) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("朗读合成", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "配置 OpenAI 兼容的云端语音合成接口（POST /audio/speech），朗读音色更自然，且不依赖系统语音引擎。留空则用系统朗读。",
                    fontSize = 13.sp, color = MUTED, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                AsrField(base, { base = it }, "接口地址", "https://api.openai.com/v1")
                Spacer(Modifier.height(8.dp))
                AsrField(key, { key = it }, "API Key（留空用语音识别的）", "sk-...")
                Spacer(Modifier.height(8.dp))
                AsrField(model, { model = it }, "模型", "tts-1")
                Spacer(Modifier.height(8.dp))
                AsrField(voice, { voice = it }, "音色", "alloy")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(s.copy(ttsBaseUrl = base.trim(), ttsApiKey = key.trim(), ttsModel = model.trim(), ttsVoice = voice.trim()))
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
