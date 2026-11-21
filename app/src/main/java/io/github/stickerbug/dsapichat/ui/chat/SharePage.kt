package io.github.stickerbug.dsapichat.ui.chat

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.github.stickerbug.dsapichat.ui.markdown.MarkdownBody
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.stickerbug.dsapichat.R
import io.github.stickerbug.dsapichat.data.Msg
import io.github.stickerbug.dsapichat.ui.theme.Ds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val SHARE_BLUE = Color(0xFF52A0FF)
private val SHARE_INDIGO = Color(0xFF4E77FF)
private val SHARE_GREEN = Color(0xFF07C160)
private val SHARE_PALE = Color(0xFFE0EDFF)

/**
 * 官方 2.8.0 的「分享」页：整页白底 + 每条回答一张灰卡 + 左侧选择圈，
 * 底部白色圆角 sheet 里是「已选 N 组」和四个圆形动作：生成图片 / 复制链接 / 分享到微信 / 更多分享。
 */
@Composable
internal fun SharePage(
    messages: List<Msg>,
    initialSelected: Set<String>,
    onClose: () -> Unit
) {
    val c = Ds.color
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var selected by remember(messages) {
        mutableStateOf(
            if (initialSelected.isEmpty()) {
                val last = messages.lastOrNull()
                if (last != null) setOf(last.id) else emptySet()
            } else initialSelected
        )
    }

    BackHandler { onClose() }

    val selectedText = messages.filter { selected.contains(it.id) }.joinToString("\n\n") { it.text }
    // 平板宽屏（≥760dp）：答案卡列限宽 760dp、sheet 内容限宽 500dp，整体居中；手机像素不变。
    val wideContent = LocalConfiguration.current.screenWidthDp >= 760

    Box(modifier = Modifier.fillMaxSize().background(c.bg)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectCircle(selected = messages.isNotEmpty() && selected.size == messages.size) {
                    selected = if (selected.size == messages.size) emptySet() else messages.map { it.id }.toSet()
                }
                Spacer(Modifier.width(10.dp))
                Text(text = "全选", fontSize = 16.sp, color = c.textPrimary)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(if (wideContent) Modifier.width(760.dp) else Modifier.fillMaxWidth()),
                contentPadding = PaddingValues(start = 14.7.dp, end = 14.7.dp, top = 6.dp, bottom = 250.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = messages, key = { it.id }) { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(modifier = Modifier.padding(top = 18.dp)) {
                            SelectCircle(selected = selected.contains(m.id)) {
                                selected = if (selected.contains(m.id)) selected - m.id else selected + m.id
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(max = 198.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (c.dark) c.field else Color(0xFFFAFAFA))
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            MarkdownBody(text = m.text)
                        }
                    }
                }
            }
            }
        }

        // 底部 sheet
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(if (c.dark) Color(0xFF242424) else Color.White)
                .navigationBarsPadding()
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = if (wideContent) Modifier.width(500.dp) else Modifier.fillMaxWidth()
            ) {
            Spacer(Modifier.height(34.7.dp))
            Box(modifier = Modifier.fillMaxWidth().height(47.4.dp)) {
                Text(
                    text = "已选 " + selected.size + " 组",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_close_outline_20),
                    contentDescription = "关闭",
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 5.dp)
                        .size(32.dp)
                        .clickable(onClick = onClose)
                        .padding(5.dp),
                    tint = c.textPrimary
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                ShareActionButton(
                    label = "保存图片",
                    iconRes = R.drawable.ic_photo_filled_20,
                    iconSize = 18.dp,
                    background = SHARE_BLUE,
                    tint = Color.White
                ) {
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) {
                            renderShareImage(context, messages.filter { selected.contains(it.id) }.map { it.text })
                        }
                        if (uri == null) {
                            Toast.makeText(context, "生成图片失败", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val saved = withContext(Dispatchers.IO) { saveImageToGallery(context, uri) }
                        if (saved) {
                            Toast.makeText(context, "已保存到相册（Pictures/DS API Chat）", Toast.LENGTH_SHORT).show()
                        } else {
                            // Android 9 及以下没有分区存储，回退到系统分享面板（可在其中保存到相册）。
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(Intent.createChooser(send, "保存或分享图片")) }
                        }
                    }
                }
                ShareActionButton(
                    label = "复制文本",
                    iconRes = R.drawable.ic_link_outline_20,
                    background = SHARE_INDIGO,
                    tint = Color.White
                ) {
                    if (selectedText.isNotBlank()) {
                        clipboard.setText(AnnotatedString(selectedText))
                        Toast.makeText(context, "已复制所选内容", Toast.LENGTH_SHORT).show()
                    }
                }
                ShareActionButton(
                    label = "分享到微信",
                    iconRes = R.drawable.ic_wechat_logo,
                    background = SHARE_GREEN,
                    tint = Color.White
                ) {
                    if (selectedText.isBlank()) return@ShareActionButton
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, selectedText)
                        setPackage("com.tencent.mm")
                    }
                    val ok = runCatching { context.startActivity(send) }.isSuccess
                    if (!ok) Toast.makeText(context, "未安装微信", Toast.LENGTH_SHORT).show()
                }
                ShareActionButton(
                    label = "更多分享",
                    iconRes = R.drawable.ic_ellipsis_outline,
                    iconSize = 20.dp,
                    background = SHARE_PALE,
                    tint = SHARE_INDIGO
                ) {
                    if (selectedText.isBlank()) return@ShareActionButton
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, selectedText)
                    }
                    context.startActivity(Intent.createChooser(send, "分享"))
                }
            }
            Spacer(Modifier.height(3.6.dp))
            }
            }
        }
    }
}

@Composable
private fun SelectCircle(selected: Boolean, onClick: () -> Unit) {
    val c = Ds.color
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) c.brand else Color.Transparent)
            .border(1.5.dp, if (selected) c.brand else Color(0xFFCCCDD2), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            androidx.compose.material3.Icon(
                painter = painterResource(id = R.drawable.ic_check_outline_20),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ShareActionButton(
    label: String,
    iconRes: Int,
    iconSize: Dp = 22.dp,
    background: Color,
    tint: Color,
    onClick: () -> Unit
) {
    val c = Ds.color
    Column(modifier = Modifier.width(85.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(47.3.dp)
                .clip(CircleShape)
                .background(background)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                modifier = Modifier.size(iconSize),
                tint = tint
            )
        }
        Spacer(Modifier.height(0.dp))
        Text(text = label, fontSize = 12.sp, color = c.textPrimary, maxLines = 1, softWrap = false)
    }
}

/** 把选中的回答渲染成一张竖版长图，返回 FileProvider uri。 */
private fun renderShareImage(context: Context, texts: List<String>): Uri? {
    if (texts.isEmpty()) return null
    return runCatching {
        val widthPx = 1080
        val pad = 56f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(24, 24, 26)
            textSize = 38f
            typeface = Typeface.DEFAULT
        }
        val lineHeight = 58f
        val maxWidth = widthPx - pad * 2
        val lines = ArrayList<String>()
        for (t in texts) {
            for (para in t.split("\n")) {
                lines.addAll(wrapLine(para, paint, maxWidth))
                lines.add("")
            }
            lines.add("")
        }
        val height = (pad * 2 + lines.size * lineHeight).toInt()
        val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        var y = pad + paint.textSize
        for (line in lines) {
            canvas.drawText(line, pad, y, paint)
            y += lineHeight
        }
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "share_" + System.currentTimeMillis() + ".png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
    }.getOrNull()
}

/** 保存到系统相册（Android 10+ 分区存储）；旧系统返回 false 由调用方回退分享面板。 */
private fun saveImageToGallery(context: Context, pngUri: Uri): Boolean = runCatching {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "ds_chat_" + System.currentTimeMillis() + ".png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DS API Chat")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    resolver.openOutputStream(target)?.use { out ->
        resolver.openInputStream(pngUri)?.use { it.copyTo(out) } ?: return false
    } ?: return false
    val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
    resolver.update(target, done, null, null)
    true
}.getOrDefault(false)

private fun wrapLine(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (text.isBlank()) return listOf("")
    val out = ArrayList<String>()
    var start = 0
    while (start < text.length) {
        var end = text.length
        while (end > start && paint.measureText(text, start, end) > maxWidth) {
            end -= 1
        }
        if (end == start) end = (start + 1).coerceAtMost(text.length)
        out.add(text.substring(start, end))
        start = end
    }
    return out
}
