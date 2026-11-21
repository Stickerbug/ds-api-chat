package io.github.stickerbug.dsapichat.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stickerbug.dsapichat.AppViewModel
import io.github.stickerbug.dsapichat.R
import io.github.stickerbug.dsapichat.api.ApiProtocol
import io.github.stickerbug.dsapichat.api.CloudAsr
import io.github.stickerbug.dsapichat.api.resolveProtocol
import io.github.stickerbug.dsapichat.data.Attachment
import io.github.stickerbug.dsapichat.data.Citation
import io.github.stickerbug.dsapichat.data.Msg
import io.github.stickerbug.dsapichat.data.Role
import io.github.stickerbug.dsapichat.data.SearchSource
import io.github.stickerbug.dsapichat.ui.components.DsIconButton
import io.github.stickerbug.dsapichat.ui.components.DsPill
import io.github.stickerbug.dsapichat.ui.components.DsTopBar
import io.github.stickerbug.dsapichat.ui.components.LocalImage
import io.github.stickerbug.dsapichat.ui.components.SiteIcon
import io.github.stickerbug.dsapichat.ui.home.SidebarContent
import io.github.stickerbug.dsapichat.ui.markdown.MarkdownBody
import io.github.stickerbug.dsapichat.ui.theme.Ds
import io.github.stickerbug.dsapichat.ui.theme.DsType
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private fun manifestMediaPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

// 「设置 - 个性化 - 思考内容自动折叠」控制思考块结束后是否自动收起。
private val LocalCollapseThinking = compositionLocalOf { true }

@Composable
fun ChatRoute(vm: AppViewModel, onOpenSettings: () -> Unit, forceReturning: Boolean = false) {
    val c = Ds.color
    val settings by vm.settings.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val current by vm.current.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val error by vm.error.collectAsState()
    val attachments by vm.attachments.collectAsState()
    val uploading by vm.uploading.collectAsState()

    // 「设置 - 字体大小」：按官方 7 档把聊天内容的 sp 等比缩放（13..19sp，标准 15sp）。
    val baseDensity = LocalDensity.current
    val fontFactor =
        if (settings.followSystemFont) 1f
        else (13 + settings.fontSize.coerceIn(0, 6)) / 15f

    // 发送后收起键盘并清掉焦点，官方发出去就是无光标的占位态。
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var sheetSources by remember { mutableStateOf<List<SearchSource>?>(null) }
    // 长按消息菜单 / 选择文本 / 举报 / 分享页（对齐官方 2.8.0）
    var menuMsg by remember { mutableStateOf<Msg?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var lastMenuMsg by remember { mutableStateOf<Msg?>(null) }
    var selectTextFor by remember { mutableStateOf<String?>(null) }
    var reportOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var shareSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reactions by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // ---- 附件面板 / 相册选择器 / 拍照（对齐官方 2.8.0）----
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attach(uri)
    }
    var panelOpen by remember { mutableStateOf(false) }
    var galleryOpen by remember { mutableStateOf(false) }
    var pendingGallery by remember { mutableStateOf(false) }
    var recentMedia by remember { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var pickerMedia by remember { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var panelPicked by remember { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var pickerPicked by remember { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val mediaPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, manifestMediaPermission()) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val mediaPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            mediaPermission.value = granted
            if (granted) {
                scope.launch {
                    recentMedia = withContext(Dispatchers.IO) { queryMedia(context, 20) }
                    if (pendingGallery) {
                        pendingGallery = false
                        pickerMedia = withContext(Dispatchers.IO) { queryMedia(context, 500) }
                        pickerPicked = emptyList()
                        galleryOpen = true
                    }
                }
            }
        }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        cameraUri = null
        if (ok && uri != null) vm.attach(uri)
    }
    // 语音输入：配置了云端识别接口时录音上传（OpenAI 兼容 /audio/transcriptions）；
    // 否则调用本机 SpeechRecognizer（HyperOS 等移除系统语音搜索的机型会失败，可到设置里配置云端识别）。
    var voiceState by remember { mutableStateOf(VoiceState.Idle) }
    val voiceRecorder = remember { VoiceRecorder(context) }
    fun appendVoiceText(said: String) {
        if (said.isNotBlank()) input = if (input.isBlank()) said else input + " " + said
    }
    val voiceController = remember {
        VoiceInputController(
            context = context,
            onResult = { said -> appendVoiceText(said) },
            onError = { msg ->
                val hint = if (msg.contains("不可用") || msg.contains("没有可用的语音")) "（可在「设置 - 语音识别」里配置云端识别）" else ""
                Toast.makeText(context, msg + hint, Toast.LENGTH_LONG).show()
            },
            onListeningChanged = { listening ->
                voiceState = if (listening) VoiceState.Listening else VoiceState.Idle
            }
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            voiceController.cancel()
            voiceController.destroy()
            voiceRecorder.cancel()
        }
    }
    fun transcribeRecording() {
        val f = voiceRecorder.stop()
        if (f == null) {
            voiceState = VoiceState.Idle
            Toast.makeText(context, "录音太短，请按住说完再点完成", Toast.LENGTH_SHORT).show()
            return
        }
        voiceState = VoiceState.Transcribing
        val base = settings.asrBaseUrl
        val key = settings.asrApiKey
        val model = settings.asrModel
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { CloudAsr.transcribe(base, key, model, f.readBytes(), "voice.m4a", "audio/m4a") }
            }
            f.delete()
            if (voiceState == VoiceState.Transcribing) voiceState = VoiceState.Idle
            result.onSuccess { text ->
                if (text.isBlank()) {
                    Toast.makeText(context, "没有识别到内容，请再试一次", Toast.LENGTH_SHORT).show()
                } else {
                    appendVoiceText(text)
                }
            }.onFailure { e ->
                Toast.makeText(context, "语音识别失败：" + (e.message ?: "请检查接口配置"), Toast.LENGTH_LONG).show()
            }
        }
    }
    fun startVoiceNow() {
        if (settings.asrBaseUrl.isNotBlank()) {
            if (voiceRecorder.start()) {
                voiceState = VoiceState.Recording
            } else {
                Toast.makeText(context, "无法开始录音，请检查麦克风权限", Toast.LENGTH_SHORT).show()
            }
        } else {
            voiceController.start()
        }
    }
    fun cancelVoice() {
        when (voiceState) {
            VoiceState.Recording -> voiceRecorder.cancel()
            VoiceState.Listening -> voiceController.cancel()
            else -> Unit
        }
        voiceState = VoiceState.Idle
    }
    val voicePermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startVoiceNow()
            } else {
                Toast.makeText(context, "没有麦克风权限，无法语音输入", Toast.LENGTH_SHORT).show()
            }
        }
    fun startVoice() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceNow()
        } else {
            voicePermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val cameraDir = remember { File(context.cacheDir, "camera").apply { mkdirs() } }
    fun openPanel() {
        panelOpen = true
        if (!mediaPermission.value) {
            mediaPermissionLauncher.launch(manifestMediaPermission())
        } else {
            scope.launch {
                recentMedia = withContext(Dispatchers.IO) { queryMedia(context, 20) }
            }
        }
    }
    fun openGallery() {
        if (!mediaPermission.value) {
            pendingGallery = true
            mediaPermissionLauncher.launch(manifestMediaPermission())
            return
        }
        scope.launch {
            pickerMedia = withContext(Dispatchers.IO) { queryMedia(context, 500) }
            pickerPicked = emptyList()
            galleryOpen = true
        }
    }
    fun launchCamera() {
        val f = File(cameraDir, UUID.randomUUID().toString() + ".jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    val messages = current?.messages ?: emptyList()
    val returning = forceReturning || conversations.isNotEmpty()
    val lastMsg = messages.lastOrNull()
    // 是否自动贴底：用户手动上滑后停止跟随，滑回底部或点「回到底部」恢复。
    var followBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { dragging ->
            if (dragging) followBottom = !listState.canScrollForward
        }
    }
    // scrollToItem 只把条目顶部对齐；公式/代码等内容还要异步测量，所以逐帧补滚直到真正贴底。
    LaunchedEffect(messages.size, lastMsg?.text?.length, lastMsg?.reasoning?.length) {
        if (messages.isEmpty() || !followBottom) return@LaunchedEffect
        var guard = 0
        while (guard++ < 40) {
            listState.scrollToItem(messages.size - 1)
            listState.scrollBy(100000f)
            withFrameNanos { }
            if (!listState.canScrollForward) break
        }
    }

    // 自动朗读：只在一次真实流式回答结束时念出最后一条回答
    // （否则进入会话页、切换会话时会立刻把历史回答读出来）。
    var wasStreaming by remember { mutableStateOf(false) }
    LaunchedEffect(streaming) {
        if (wasStreaming && !streaming && settings.autoRead) {
            val m = vm.current.value?.messages?.lastOrNull()
            if (m != null && m.role == Role.ASSISTANT && m.text.isNotBlank()) {
                TtsController.speak(context, m.id, m.text)
            }
        }
        wasStreaming = streaming
    }

    // 内容没滚到底时显示「回到底部」浮动圆钮。
    val showToBottom by remember { derivedStateOf { listState.canScrollForward } }

    val drawerModifier = Modifier.width(288.7.dp)

    // 相册选择器需要盖住整个聊天页（含顶栏），所以外面包一层 Box。
    Box(modifier = Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = if (c.dark) androidx.compose.ui.graphics.Color(0xFF1E1E1E) else androidx.compose.ui.graphics.Color.White,
        drawerContent = {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(drawerModifier)
                    .background(c.card)
            ) {
                SidebarContent(
                    conversations = conversations,
                    currentId = current?.id,
                    onNewChat = {
                        vm.newChat()
                        input = ""
                        scope.launch { drawerState.close() }
                    },
                    onSelect = { id ->
                        vm.openChat(id)
                        scope.launch { drawerState.close() }
                    },
                    onDelete = { vm.deleteChat(it) },
                    onRename = { id, title -> vm.renameChat(id, title) },
                    onPin = { vm.togglePin(it) },
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.bg)
                .imePadding()
        ) {
            ChatTopBar(
                title = if (messages.isEmpty()) null else current?.title,
                autoRead = settings.autoRead,
                onToggleAutoRead = { vm.saveSettings(settings.copy(autoRead = !settings.autoRead)) },
                onMenu = { scope.launch { drawerState.open() } },
                onNewChat = {
                    vm.newChat()
                    input = ""
                }
            )

            // 平板宽屏（screenWidthDp ≥ 760）时内容区限宽 760dp 居中；手机屏宽 361dp，像素不变。
            val wideContent = LocalConfiguration.current.screenWidthDp >= 760
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                if (messages.isEmpty()) {
                    WelcomePane(returning = returning)
                } else {
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            density = baseDensity.density,
                            fontScale = baseDensity.fontScale * fontFactor
                        ),
                        LocalCollapseThinking provides settings.collapseThinking
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxHeight()
                                .then(if (wideContent) Modifier.width(760.dp) else Modifier.fillMaxWidth()),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 20.dp)
                        ) {
                            items(items = messages, key = { it.id }) { m ->
                                MessageItem(
                                    m = m,
                                    streaming = streaming,
                                    reaction = reactions[m.id] ?: 0,
                                    onRegenerate = { vm.regenerate() },
                                    onOpenSources = { sheetSources = m.sources },
                                    onLongPress = { menuMsg = m; lastMenuMsg = m; menuOpen = true },
                                    onReact = { v ->
                                        reactions = reactions + (m.id to if (reactions[m.id] == v) 0 else v)
                                    },
                                    onShare = {
                                        shareSelected = setOf(m.id)
                                        shareOpen = true
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showToBottom,
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(120)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = if (wideContent) (23 + (LocalConfiguration.current.screenWidthDp - 760) / 2).dp else 23.dp,
                            bottom = 17.6.dp
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                clip = false,
                                ambientColor = Color(0x1C000000),
                                spotColor = Color(0x38000000)
                            )
                            .clip(CircleShape)
                            .background(c.card)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 14.dp)
                            ) {
                                followBottom = true
                                scope.launch {
                                    if (messages.isNotEmpty()) {
                                        listState.animateScrollToItem(messages.size - 1)
                                        listState.animateScrollBy(200000f)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_down_outline_20),
                            contentDescription = "回到底部",
                            modifier = Modifier.size(16.dp),
                            tint = c.textPrimary
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = if (wideContent) Modifier.width(760.dp) else Modifier.fillMaxWidth()
            ) {
            if (error != null && error != "auth") {
                ErrorBanner(text = errText(error), onOpenSettings = onOpenSettings)
            }

            val anthropic = resolveProtocol(settings.protocol, settings.baseUrl, settings.model) == ApiProtocol.ANTHROPIC
            ChatInputBar(
                text = input,
                onTextChanged = { input = it },
                streaming = streaming,
                thinking = if (anthropic) settings.thinking else settings.model.contains("reasoner"),
                onToggleThinking = {
                    if (anthropic) {
                        vm.saveSettings(settings.copy(thinking = !settings.thinking))
                    } else {
                        val next = if (settings.model.contains("reasoner")) "deepseek-chat" else "deepseek-reasoner"
                        vm.saveSettings(settings.copy(model = next, thinking = next.contains("reasoner")))
                    }
                },
                search = settings.search,
                onToggleSearch = { vm.saveSettings(settings.copy(search = !settings.search)) },
                attachments = attachments,
                uploading = uploading,
                onPickAttachment = { filePicker.launch(arrayOf("*/*")) },
                panelOpen = panelOpen,
                recentMedia = recentMedia,
                panelPicked = panelPicked,
                onTogglePanel = { if (panelOpen) panelOpen = false else openPanel() },
                onToggleRecent = { m ->
                    panelPicked = if (panelPicked.any { it.id == m.id }) {
                        panelPicked.filterNot { it.id == m.id }
                    } else if (panelPicked.size < 50) {
                        vm.attach(m.uri)
                        panelPicked + m
                    } else panelPicked
                },
                onOpenGallery = { openGallery() },
                onCamera = { launchCamera() },
                onRemoveAttachment = { vm.removeAttachment(it) },
                onSend = {
                    val t = input
                    input = ""
                    focusManager.clearFocus()
                    keyboard?.hide()
                    vm.send(t)
                },
                onStop = { vm.stop() },
                onVoice = { startVoice() },
                showCaption = messages.isNotEmpty() && !panelOpen
            )
            }
            }
        }
    }

        // 官方：相册全屏切换约 100ms 交叉淡入，无上滑。
        AnimatedVisibility(
            visible = galleryOpen,
            enter = fadeIn(tween(110)),
            exit = fadeOut(tween(90)),
            modifier = Modifier.fillMaxSize()
        ) {
            MediaPickerOverlay(
                images = pickerMedia,
                selected = pickerPicked,
                onToggle = { m ->
                    pickerPicked = if (pickerPicked.any { it.id == m.id }) {
                        pickerPicked.filterNot { it.id == m.id }
                    } else if (pickerPicked.size < 50) pickerPicked + m else pickerPicked
                },
                onDismiss = { galleryOpen = false },
                onConfirm = {
                    val uris = pickerPicked.map { it.uri }
                    pickerPicked = emptyList()
                    galleryOpen = false
                    vm.attachAll(uris)
                }
            )
        }

        // ---- 长按消息菜单 / 分享页（对齐官方 2.8.0）----
        val mm = menuMsg ?: lastMenuMsg
        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(90)),
            modifier = Modifier.fillMaxSize()
        ) {
        if (mm != null) {
            val isAssistant = mm.role == Role.ASSISTANT
            val entries = ArrayList<MenuEntry>()
            entries.add(MenuEntry(label = "复制", iconRes = R.drawable.ic_copy_outline) {
                clipboard.setText(AnnotatedString(mm.text))
            })
            entries.add(MenuEntry(label = "选择文本", iconRes = R.drawable.ic_select_outline) {
                selectTextFor = mm.text
            })
            if (isAssistant) {
                val r = reactions[mm.id] ?: 0
                entries.add(MenuEntry(label = "重新生成", iconRes = R.drawable.ic_regegerate_outline_24) {
                    vm.regenerate()
                })
                entries.add(MenuEntry(
                    label = "喜欢",
                    iconRes = if (r == 1) R.drawable.ic_like_fill else R.drawable.ic_like_outline
                ) { reactions = reactions + (mm.id to if (r == 1) 0 else 1) })
                entries.add(MenuEntry(
                    label = "不喜欢",
                    iconRes = if (r == -1) R.drawable.ic_dislike_fill else R.drawable.ic_dislike_outline
                ) { reactions = reactions + (mm.id to if (r == -1) 0 else -1) })
                entries.add(MenuEntry(
                    label = if (TtsController.speakingId == mm.id) "停止朗读" else "朗读",
                    iconRes = R.drawable.ic_sound_outline_24
                ) { TtsController.toggle(context, mm.id, mm.text) })
                entries.add(MenuEntry(label = "分享", iconRes = R.drawable.ic_forward_outline_24) {
                    shareSelected = setOf(mm.id)
                    shareOpen = true
                })
                entries.add(MenuEntry(label = "举报", iconRes = R.drawable.ic_report_outline, danger = true) {
                    reportOpen = true
                })
            } else {
                entries.add(MenuEntry(label = "分享", iconRes = R.drawable.ic_forward_outline_24) {
                    shareText(context, mm.text)
                })
            }
            MessageMenuOverlay(entries = entries, onDismiss = { menuOpen = false; menuMsg = null })
        }
        }

        // 语音提示浮层：聆听时可取消；云端识别录音时可点「完成」开始转写。
        AnimatedVisibility(
            visible = voiceState != VoiceState.Idle,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 118.dp)
        ) {
            VoiceListeningPill(
                state = voiceState,
                onCancel = { cancelVoice() },
                onFinish = { transcribeRecording() }
            )
        }

        // 官方平板实测：分享 sheet 约 200ms 淡入，关闭瞬切。
        AnimatedVisibility(
            visible = shareOpen,
            enter = fadeIn(tween(210)),
            exit = fadeOut(tween(0)),
            modifier = Modifier.fillMaxSize()
        ) {
            SharePage(
                messages = messages.filter { it.role == Role.ASSISTANT && it.text.isNotBlank() },
                initialSelected = shareSelected,
                onClose = { shareOpen = false }
            )
        }
    }

    selectTextFor?.let { t -> TextSelectDialog(text = t, onDismiss = { selectTextFor = null }) }
    if (reportOpen) {
        ReportDialog(onDismiss = { reportOpen = false })
    }

    val shownSources = sheetSources
    if (shownSources != null) {
        SearchSheet(sources = shownSources, onDismiss = { sheetSources = null })
    }

    if (error == "auth") {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearError()
                    onOpenSettings()
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { vm.clearError() }) { Text("取消") }
            },
            title = { Text("无法发送消息") },
            text = { Text("还没有填写 API Key。请先在「设置 - API Key」中填入你的 DeepSeek 密钥。") }
        )
    }
}

@Composable
private fun WelcomePane(returning: Boolean) {
    val c = Ds.color
    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset(y = 1.3.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (returning) {
            Icon(
                painter = painterResource(id = R.drawable.chat_welcome_logo),
                contentDescription = "DeepSeek",
                modifier = Modifier
                    .width(38.dp)
                    .height(29.3.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = "欢迎回来，随时开始吧",
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = c.textPrimary
            )
        } else {
            Icon(
                painter = painterResource(id = R.drawable.chat_welcome_logo),
                contentDescription = "DeepSeek",
                modifier = Modifier
                    .width(38.dp)
                    .height(29.3.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.height(19.dp))
            Text(
                text = "你好，我能帮什么忙吗？",
                fontSize = 17.85.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = c.textPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageItem(
    m: Msg,
    streaming: Boolean,
    reaction: Int,
    onRegenerate: () -> Unit,
    onOpenSources: () -> Unit,
    onLongPress: () -> Unit,
    onReact: (Int) -> Unit,
    onShare: () -> Unit
) {
    val c = Ds.color
    if (m.role == Role.USER) {
        val hasImage = m.attachments.any { it.image && it.localPath != null }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 15.dp)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .background(c.bubble, RoundedCornerShape(20.dp))
                    .padding(
                        start = if (hasImage) 6.dp else 15.dp,
                        end = if (hasImage) 6.dp else 15.dp,
                        top = if (hasImage) 6.dp else 10.3.dp,
                        bottom = if (hasImage) 6.dp else 14.dp
                    )
            ) {
                Column {
                    val images = m.attachments.filter { it.image && it.localPath != null }
                    val files = m.attachments.filterNot { it.image && it.localPath != null }
                    if (images.isNotEmpty()) {
                        // 单图铺满气泡宽度（上限 300dp 来自外层），多图按两列网格排。
                        if (images.size == 1) {
                            val path = images.first().localPath
                            if (path != null) {
                                LocalImage(
                                    path = path,
                                    contentDescription = images.first().name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                images.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        pair.forEach { a ->
                                            val path = a.localPath ?: return@forEach
                                            LocalImage(
                                                path = path,
                                                contentDescription = a.name,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(140.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                            )
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    if (files.isNotEmpty()) {
                        if (images.isNotEmpty()) Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            files.forEach { a -> AttachmentChip(a = a, onRemove = null) }
                        }
                    }
                    if (m.attachments.isNotEmpty() && m.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                    if (m.text.isNotBlank()) {
                        Text(
                            text = m.text,
                            style = DsType.body(c).copy(lineHeight = 22.sp),
                            color = c.textPrimary,
                            modifier = if (hasImage) {
                                Modifier.padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = 8.dp)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }
        }
        return
    }

    val showThinking = m.reasoning != null || (streaming && m.text.isBlank())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 7.3.dp)
        ) {
            if (m.sources.isNotEmpty() || m.searchQuery != null) {
                SourcesBlock(m = m, streaming = streaming, onOpenSources = onOpenSources)
                Spacer(Modifier.height(4.dp))
            }
            if (showThinking) {
                ThinkingBlock(m = m, streaming = streaming)
            }
            if (m.text.isNotBlank()) {
                if (showThinking) Spacer(Modifier.height(13.dp))
                MarkdownBody(text = withCitations(m.text, m.citations))
            } else if (streaming && m.reasoning.isNullOrBlank()) {
                TypingDots()
            }
            if (m.err != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = errText(m.err),
                    fontSize = 14.sp,
                    color = c.danger
                )
            }
            if (!streaming && m.text.isNotBlank()) {
                // 官方在每条回答下面都有一行斜体免责声明，颜色与「已思考」标签同系。
                Text(
                    text = "本回答由 AI 生成，内容仅供参考，请仔细甄别",
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF7C7C80),
                    modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)
                )
            }
        }
        AnimatedVisibility(
            visible = !streaming && (m.text.isNotBlank() || m.err != null),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(80))
        ) {
            MessageActions(
                m = m,
                reaction = reaction,
                onRegenerate = onRegenerate,
                onReact = onReact,
                onShare = onShare
            )
        }
    }
}

@Composable
private fun ThinkingBlock(m: Msg, streaming: Boolean) {
    val c = Ds.color
    val collapse = LocalCollapseThinking.current
    var expanded by remember(m.id) { mutableStateOf(streaming || !collapse) }
    // 官方录屏：流式期间思考块保持展开，整条回答结束后才收起为一行。
    LaunchedEffect(streaming) {
        if (!streaming && m.text.isNotEmpty() && collapse) expanded = false
    }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "thinkChevron"
    )
    val sec = if (m.thinkMs > 0) (m.thinkMs / 1000L).coerceAtLeast(1L) else 0L
    val label = when {
        m.text.isBlank() && streaming -> "思考中…"
        sec > 0L -> "已思考（用时 " + sec.toString() + " 秒）"
        else -> "已思考"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 15.5.sp, color = Color(0xFF7C7C80))
            Spacer(Modifier.width(2.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right_outline_20),
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevron),
                tint = Color(0xFF7C7C80)
            )
        }
        AnimatedVisibility(
            visible = expanded && !m.reasoning.isNullOrBlank(),
            enter = expandVertically(animationSpec = tween(130, easing = FastOutSlowInEasing)) +
                fadeIn(tween(100)),
            exit = shrinkVertically(animationSpec = tween(110, easing = FastOutSlowInEasing)) +
                fadeOut(tween(70))
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min).padding(top = 10.dp, start = 7.3.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(top = 8.5.dp)
                        .width(0.7.dp)
                        .background(Color(0xFFE4E4E4))
                )
                Spacer(Modifier.width(13.7.dp))
                Text(
                    text = m.reasoning ?: "",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF7C7C80),
                    modifier = Modifier.drawBehind {
                        drawCircle(
                            color = Color(0xFF9FA0A4),
                            radius = 2.65.dp.toPx(),
                            center = Offset(-14.05.dp.toPx(), 11.dp.toPx())
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SourcesBlock(m: Msg, streaming: Boolean, onOpenSources: () -> Unit) {
    val label = when {
        m.sources.isNotEmpty() -> "已搜索 " + m.sources.size + " 个网页"
        streaming && m.reading != null -> "正在阅读网页…"
        m.searchQuery != null -> "正在搜索「" + m.searchQuery + "」"
        else -> "正在搜索…"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = m.sources.isNotEmpty(), onClick = onOpenSources),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_globe_language_outline_16),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color(0xFF7C7C80)
        )
        Spacer(Modifier.width(5.dp))
        Text(text = label, fontSize = 15.5.sp, color = Color(0xFF7C7C80))
        if (m.sources.isNotEmpty()) {
            Spacer(Modifier.width(2.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right_outline_20),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF7C7C80)
            )
        }
    }
}

/** 把 citations 的角标插回正文引用句末（Unicode 上标数字）。 */
private fun withCitations(text: String, citations: List<Citation>): String {
    if (text.isEmpty() || citations.isEmpty()) return text
    var out = text
    for (c in citations.sortedBy { it.index }) {
        val probe = c.text.trim()
        if (probe.isEmpty()) continue
        val needle = if (probe.length > 48) probe.substring(0, 48) else probe
        val at = out.indexOf(needle)
        if (at < 0) continue
        val marker = superscript(c.index)
        val end = at + needle.length
        if (out.startsWith(marker, end)) continue
        out = out.substring(0, end) + marker + out.substring(end)
    }
    return out
}

private fun superscript(n: Int): String {
    val digits = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    return n.toString().map { digits[it - '0'] }.joinToString("")
}

private fun hostOf(url: String): String = try {
    java.net.URI(url).host ?: url
} catch (e: Exception) {
    url
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchSheet(sources: List<SearchSource>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        scrimColor = Color(0x52000000),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        SearchSheetContent(sources = sources, onDismiss = onDismiss) { openUrl(context, it) }
    }
}

@Composable
internal fun SearchSheetContent(
    sources: List<SearchSource>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        Box(modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(
                    text = "搜索结果",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F0F0F),
                    modifier = Modifier.align(Alignment.Center)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 11.dp)
                        .size(27.dp)
                        .background(Color(0xFFF5F5F5), CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_outline_16),
                        contentDescription = "关闭",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF0F0F0F)
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.892f),
                contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 0.dp, bottom = 28.dp)
            ) {
                itemsIndexed(sources) { index, s ->
                    SourceRow(index = index + 1, s = s) { onOpen(s.url) }
                }
            }
        }
}

@Composable
private fun SourceRow(index: Int, s: SearchSource, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SiteIcon(siteIcon = s.siteIcon, size = 18.dp)
            Spacer(Modifier.width(10.dp))
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = s.siteName?.takeIf { it.isNotBlank() } ?: hostOf(s.url),
                    fontSize = 15.sp,
                    color = Color(0xFF0F0F0F),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!s.pageAge.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(text = s.pageAge ?: "", fontSize = 13.sp, color = Color(0xFFACACAC), maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.dp, Color(0xFFECECEC), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = index.toString(), fontSize = 11.sp, color = Color(0xFFB3B3B3))
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = s.title.ifBlank { s.url },
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 23.sp,
            color = Color(0xFF0F0F0F),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!s.snippet.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = s.snippet ?: "",
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = Color(0xFFA0A0A0),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
    }
}

@Composable
private fun TypingDots() {
    val c = Ds.color
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha"
    )
    Text(
        text = "● ● ●",
        fontSize = 11.sp,
        color = c.textCaption.copy(alpha = alpha),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

/** 官方操作行图标色，录屏 2.5% 分位实测 (139,141,141)。 */
private val ACTION_TINT = Color(0xFF8B8D8D)

@Composable
private fun MessageActions(
    m: Msg,
    reaction: Int,
    onRegenerate: () -> Unit,
    onReact: (Int) -> Unit,
    onShare: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val c = Ds.color
    val speaking = TtsController.speakingId == m.id
    // 官方：整行左右 16dp（不是正文的 22dp），按钮 32dp、间距 4.8dp，重新生成靠最右。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 11.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DsIconButton(
            iconRes = R.drawable.ic_copy_outline,
            contentDescription = "复制",
            size = 32.dp,
            iconSize = 18.dp,
            tint = ACTION_TINT
        ) { clipboard.setText(AnnotatedString(m.text)) }
        Spacer(Modifier.width(4.8.dp))
        DsIconButton(
            iconRes = if (reaction == 1) R.drawable.ic_like_fill else R.drawable.ic_like_outline,
            contentDescription = "赞",
            size = 32.dp,
            iconSize = 18.dp,
            tint = if (reaction == 1) c.brand else ACTION_TINT
        ) { onReact(1) }
        Spacer(Modifier.width(4.8.dp))
        DsIconButton(
            iconRes = if (reaction == -1) R.drawable.ic_dislike_fill else R.drawable.ic_dislike_outline,
            contentDescription = "踩",
            size = 32.dp,
            iconSize = 18.dp,
            tint = if (reaction == -1) c.brand else ACTION_TINT
        ) { onReact(-1) }
        Spacer(Modifier.width(4.8.dp))
        DsIconButton(
            iconRes = if (speaking) R.drawable.ic_stop_speech_outline_20 else R.drawable.ic_sound_outline_24,
            contentDescription = "朗读",
            size = 32.dp,
            iconSize = 18.dp,
            tint = ACTION_TINT
        ) { TtsController.toggle(context, m.id, m.text) }
        Spacer(Modifier.width(4.8.dp))
        DsIconButton(
            iconRes = R.drawable.ic_forward_outline_24,
            contentDescription = "分享",
            size = 32.dp,
            iconSize = 18.dp,
            tint = ACTION_TINT
        ) { onShare() }
        Spacer(Modifier.weight(1f))
        DsIconButton(
            iconRes = R.drawable.ic_regegerate_outline_24,
            contentDescription = "重新生成",
            size = 32.dp,
            iconSize = 18.dp,
            tint = ACTION_TINT,
            onClick = onRegenerate
        )
    }
}

private fun shareText(context: android.content.Context, text: String) {
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "分享"))
}

@Composable
private fun PrimaryCircleButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = Ds.color
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (enabled) c.brand else c.field)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 14.dp, color = c.brand),
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (enabled) c.onBrand else c.textCaption
        )
    }
}

@Composable
private fun CircleOutlineButton(iconRes: Int, desc: String, iconRotation: Float = 0f, onClick: () -> Unit) {
    val c = Ds.color
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(29.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 14.5.dp, color = c.textPrimary),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = desc,
            modifier = Modifier.size(23.7.dp).rotate(iconRotation),
            tint = c.textPrimary
        )
    }
}

@Composable
private fun AttachmentChip(a: Attachment, onRemove: (() -> Unit)?) {
    val c = Ds.color
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.field)
            .padding(
                start = 8.dp,
                end = if (onRemove != null) 3.dp else 10.dp,
                top = 5.dp,
                bottom = 5.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (a.image && a.localPath != null) {
            LocalImage(
                path = a.localPath,
                contentDescription = a.name,
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            Icon(
                painter = painterResource(
                    id = if (a.image) R.drawable.ic_photo_outline_16 else R.drawable.ic_paperclip_outline_20
                ),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = c.textSecondary
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = a.name,
            fontSize = 12.sp,
            color = c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp)
        )
        if (onRemove != null) {
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close_outline_16),
                    contentDescription = "移除附件",
                    modifier = Modifier.size(13.dp),
                    tint = c.textCaption
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    streaming: Boolean,
    thinking: Boolean,
    onToggleThinking: () -> Unit,
    search: Boolean,
    onToggleSearch: () -> Unit,
    attachments: List<Attachment>,
    uploading: Boolean,
    onPickAttachment: () -> Unit,
    panelOpen: Boolean,
    recentMedia: List<LocalMedia>,
    panelPicked: List<LocalMedia>,
    onTogglePanel: () -> Unit,
    onToggleRecent: (LocalMedia) -> Unit,
    onOpenGallery: () -> Unit,
    onCamera: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onVoice: () -> Unit,
    showCaption: Boolean
) {
    val c = Ds.color
    val canSend = text.isNotBlank() && !streaming
    // 底部留白 = 导航条高度 + 5.3 dp（1084 屏上导航条 108 px 时正好等于原 41.3 dp）
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    // 官方：面板展开时输入卡底部不再留导航条间距（面板自带 navigationBarsPadding）。
    val bottomPad by animateDpAsState(
        targetValue = if (imeVisible || panelOpen) 5.3.dp else navBottom + 5.3.dp,
        animationSpec = tween(250, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)),
        label = "inputBottomPad"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp, bottom = bottomPad)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(24.dp),
                    clip = false,
                    ambientColor = Color(0x1F000000),
                    spotColor = Color(0x3D000000)
                )
                .background(c.card, RoundedCornerShape(24.dp))
                .border(0.7.dp, if (c.dark) c.border else Color(0xFFF0F0F0), RoundedCornerShape(24.dp))
                .padding(start = 10.3.dp, end = 6.3.dp, top = 14.dp, bottom = 10.dp)
        ) {
            if (attachments.isNotEmpty() || uploading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 5.3.dp, end = 4.dp, bottom = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    attachments.forEach { a ->
                        AttachmentChip(a = a, onRemove = { onRemoveAttachment(a.fileId) })
                    }
                    if (uploading) {
                        Text(text = "正在上传…", fontSize = 12.sp, color = c.textCaption)
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().padding(start = 5.3.dp)) {
                if (text.isEmpty()) {
                    Text(
                        text = "发消息或按住说话",
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = c.textCaption,
                        modifier = Modifier.offset(y = (-1).dp)
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    textStyle = DsType.body(c),
                    cursorBrush = SolidColor(c.brand),
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 24.dp, max = 180.dp)
                )
            }
            Spacer(Modifier.height(20.5.dp))
            Row(
                modifier = Modifier
                    .height(29.dp)
                    .offset(y = (-1.5).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DsPill(
                    iconRes = R.drawable.ic_think_outline_16,
                    text = "深度思考",
                    active = thinking,
                    onClick = onToggleThinking
                )
                Spacer(Modifier.width(10.dp))
                DsPill(
                    iconRes = R.drawable.ic_globe_language_outline_16,
                    text = "智能搜索",
                    active = search,
                    onClick = onToggleSearch
                )
                Spacer(Modifier.weight(1f))
                Box {
                    // 官方录屏实测：右侧是「⊕ + 语音」常驻；输入了文字时语音槽位换成蓝色发送键；
                    // 流式回答时最右侧再追加蓝色停止键（三键并存），⊕ 与语音整体左移一个槽位。
                    val stopSlot = streaming
                    val stopAlpha by animateFloatAsState(
                        targetValue = if (streaming) 1f else 0f,
                        animationSpec = tween(200),
                        label = "stopAlpha"
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = if (stopSlot) 40.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val plusRotation by animateFloatAsState(
                            targetValue = if (panelOpen) 45f else 0f,
                            animationSpec = tween(250),
                            label = "plusRotation"
                        )
                        CircleOutlineButton(
                            iconRes = R.drawable.ic_add_outline_26,
                            desc = if (panelOpen) "关闭" else "附件",
                            iconRotation = plusRotation,
                            onClick = onTogglePanel
                        )
                        // 语音槽位与发送键共用同一个 40dp 槽位（官方两者右缘对齐，⊕ 位置不随文字变化）。
                        Box(
                            modifier = Modifier.width(40.dp).height(29.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (text.isBlank() || streaming) {
                                CircleOutlineButton(R.drawable.ic_voice_outline_26, "语音输入", onClick = onVoice)
                            } else {
                                PrimaryCircleButton(
                                    iconRes = R.drawable.ic_send_fill_20,
                                    contentDescription = "发送",
                                    enabled = canSend,
                                    iconSize = 14.dp,
                                    onClick = onSend
                                )
                            }
                        }
                    }
                    if (streaming || stopAlpha > 0.01f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(40.dp)
                                .height(29.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PrimaryCircleButton(
                                iconRes = R.drawable.ic_stop_fill_20,
                                contentDescription = "停止",
                                enabled = true,
                                iconSize = 15.dp,
                                modifier = Modifier.alpha(stopAlpha),
                                onClick = onStop
                            )
                        }
                    }
                }
            }
        }
        AnimatedContent(
            targetState = panelOpen,
            transitionSpec = {
                // 官方实测：展开约 260ms easeOutCubic，收起约 240ms。
                val outCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
                if (targetState) {
                    (expandVertically(animationSpec = tween(260, easing = outCubic), expandFrom = Alignment.Top) + fadeIn(tween(150))) togetherWith
                        fadeOut(tween(120))
                } else {
                    fadeIn(tween(120)) togetherWith
                        (shrinkVertically(animationSpec = tween(240, easing = outCubic), shrinkTowards = Alignment.Top) + fadeOut(tween(150)))
                }
            },
            label = "attachmentPanel"
        ) { open ->
            if (open) {
                AttachmentPanel(
                    recent = recentMedia,
                    selected = panelPicked.map { it.id }.toSet(),
                    permissionGranted = recentMedia.isNotEmpty(),
                    onToggle = onToggleRecent,
                    onCamera = onCamera,
                    onGallery = onOpenGallery,
                    onFiles = onPickAttachment
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(3.3.dp))
                    Text(
                        text = "回答由 AI 生成，仅供参考",
                        fontSize = 10.5.sp,
                        lineHeight = 12.sp,
                        color = if (c.dark) Color(0xFF3C3C3C) else Color(0xFFDCDCDC),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).alpha(if (showCaption) 1f else 0f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatTopBar(
    title: String?,
    autoRead: Boolean,
    onToggleAutoRead: () -> Unit,
    onMenu: () -> Unit,
    onNewChat: () -> Unit
) {
    val c = Ds.color
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 2.dp, end = 2.7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DsIconButton(
            iconRes = R.drawable.ic_menu_outline_24,
            contentDescription = "菜单",
            size = 44.dp,
            iconSize = 22.dp,
            tint = c.textPrimary,
            onClick = onMenu
        )
        if (title != null) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.7.dp)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Box(
            modifier = Modifier.size(40.dp).clickable(onClick = onToggleAutoRead),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    id = if (autoRead) R.drawable.ic_sound_outline_24 else R.drawable.ic_mute_audio_outline_24
                ),
                contentDescription = "自动朗读",
                modifier = Modifier.size(21.dp),
                tint = c.textPrimary
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(40.dp).clickable(onClick = onNewChat),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_new_chat_outline_24),
                contentDescription = "新对话",
                modifier = Modifier.size(22.dp),
                tint = c.textPrimary
            )
        }
    }
}

@Composable
private fun ErrorBanner(text: String, onOpenSettings: () -> Unit) {
    val c = Ds.color
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (c.dark) c.field else Color(0xFFF5F5F5))
            .clickable(onClick = onOpenSettings)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_info_outline_16),
            contentDescription = null,
            modifier = Modifier.size(16.dp).offset(y = 2.dp),
            tint = c.textSecondary
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, fontSize = 14.sp, lineHeight = 20.sp, color = c.textSecondary)
    }
}

private fun errText(kind: String?): String = when (kind) {
    "AUTH" -> "API Key 无效或已过期，请到「设置 - API Key」检查。"
    "BALANCE" -> "账户余额不足，请到 DeepSeek 开放平台充值。"
    "RATE" -> "请求太频繁了，请稍后再试。"
    "NETWORK" -> "网络连接失败，请检查网络或代理设置。"
    "attach-protocol" -> "当前接口不支持附件，请把接口协议切到 Anthropic 兼容端点。"
    "attach-size" -> "文件超过 64 MB，无法上传。"
    else -> "服务暂时不可用，请稍后重试。"
}

private enum class VoiceState { Idle, Listening, Recording, Transcribing }

/** 语音输入浮层：聆听/录音/识别中三种状态（呼吸圆点 + 取消/完成）。 */
@Composable
private fun VoiceListeningPill(state: VoiceState, onCancel: () -> Unit, onFinish: () -> Unit) {
    val c = Ds.color
    val pulse by rememberInfiniteTransition(label = "voicePulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "voicePulseAlpha"
    )
    val label = when (state) {
        VoiceState.Listening -> "正在聆听…"
        VoiceState.Recording -> "正在录音…"
        VoiceState.Transcribing -> "正在识别…"
        VoiceState.Idle -> ""
    }
    Row(
        modifier = Modifier
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(c.card)
            .padding(start = 16.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF4E77FF).copy(alpha = if (state == VoiceState.Transcribing) 1f else pulse))
        )
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, color = c.textPrimary)
        if (state == VoiceState.Recording) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF4E77FF))
                    .clickable { onFinish() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("完成", fontSize = 14.sp, color = Color.White)
            }
        }
        if (state != VoiceState.Transcribing) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onCancel() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("取消", fontSize = 14.sp, color = Color(0xFF4E77FF))
            }
        }
    }
}
