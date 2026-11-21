package io.github.stickerbug.dsapichat.ui.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.sp
import io.github.stickerbug.dsapichat.R
import io.github.stickerbug.dsapichat.ui.theme.Ds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 相册里的一张图片（官方附件面板/相册选择器都用它）。 */
data class LocalMedia(val id: Long, val uri: Uri)

/** 按最近添加排序读取本机图片（官方也只列出图片，不含视频）。 */
fun queryMedia(context: Context, limit: Int = 500): List<LocalMedia> {
    val out = ArrayList<LocalMedia>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val selection = "${MediaStore.Images.Media.SIZE} > 0"
    runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cur ->
            val idCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cur.moveToNext() && out.size < limit) {
                val id = cur.getLong(idCol)
                out.add(
                    LocalMedia(
                        id = id,
                        uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    )
                )
            }
        }
    }
    return out
}

private fun decodeScaled(context: Context, uri: Uri, sizePx: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest / sample > sizePx * 2) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()

@Composable
fun rememberMediaBitmap(uri: Uri, sizePx: Int): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(initialValue = null, uri, sizePx) {
        value = withContext(Dispatchers.IO) { decodeScaled(context, uri, sizePx)?.asImageBitmap() }
    }
    return state.value
}

/** 输入卡下方的附件面板：最近图片 + 拍照 / 相册 / 文件 三个按钮。 */
@Composable
fun AttachmentPanel(
    recent: List<LocalMedia>,
    selected: Set<Long>,
    permissionGranted: Boolean,
    onToggle: (LocalMedia) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFiles: () -> Unit
) {
    val c = Ds.color
    // 平板宽屏时面板内容限宽 560dp 居中；手机宽度不足 560dp，像素不变。
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Column(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 41.dp)
            .navigationBarsPadding()
    ) {
        if (permissionGranted && recent.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 18.3.dp, end = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(7.3.dp)
            ) {
                items(recent, key = { it.id }) { m ->
                    Box(
                        modifier = Modifier
                            .size(73.3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.field)
                            .clickable { onToggle(m) }
                    ) {
                        val bmp = rememberMediaBitmap(m.uri, 220)
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.3.dp)
                                .size(20.dp)
                        ) {
                            if (selected.contains(m.id)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_check_outline_20),
                                        contentDescription = "已选择",
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.Black
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .border(2.dp, Color.White, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.7.dp))
        } else {
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.3.dp),
            horizontalArrangement = Arrangement.spacedBy(7.7.dp)
        ) {
            PanelButton(R.drawable.ic_camera_outline_20, "拍照", Modifier.weight(1f), onCamera)
            PanelButton(R.drawable.ic_photo_outline_20, "相册", Modifier.weight(1f), onGallery)
            PanelButton(R.drawable.ic_paperclip_outline_20, "文件", Modifier.weight(1f), onFiles)
        }
    }
    }
}

@Composable
private fun PanelButton(iconRes: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Ds.color
    Column(
        modifier = modifier
            .height(77.dp)
            .clip(RoundedCornerShape(15.3.dp))
            .background(c.field)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = c.textPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(text = label, fontSize = 16.sp, color = c.textPrimary)
    }
}

/** 官方同款相册选择器：4 列贴边网格 + 底部计数/完成 + 已选缩略图条。 */
@Composable
fun MediaPickerOverlay(
    images: List<LocalMedia>,
    selected: List<LocalMedia>,
    onToggle: (LocalMedia) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    maxCount: Int = 50
) {
    // 直接在当前 Activity 窗口内铺满：官方相册页整页覆盖（含状态栏区域），
    // Dialog 窗口被系统限制在状态栏下方，底色会露出聊天页。
    MediaPickerContent(images, selected, onToggle, onDismiss, onConfirm, maxCount)
}

@Composable
private fun MediaPickerContent(
    images: List<LocalMedia>,
    selected: List<LocalMedia>,
    onToggle: (LocalMedia) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    maxCount: Int
) {
    BackHandler(onBack = onDismiss)
    val pageBg = Color(0xFFF1F3F5)
    val ink = Color(0xFF181818)
    val brand = Color(0xFF2758EE)
    // 官方相册页是整页浅灰：状态栏也画成页面底色（本复刻内容整体在状态栏下方）。
    val view = LocalView.current
    DisposableEffect(Unit) {
        val act = view.context as? Activity
            ?: (view.context as? ContextWrapper)?.baseContext as? Activity
        val w = act?.window
        val prevColor = w?.statusBarColor
        val prevLight = w?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars }
        if (w != null) {
            w.statusBarColor = pageBg.toArgb()
            WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = true
        }
        onDispose {
            if (w != null) {
                prevColor?.let { w.statusBarColor = it }
                prevLight?.let { WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = it }
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_left_outline_24),
                    contentDescription = "返回",
                    modifier = Modifier.size(24.dp),
                    tint = ink
                )
            }
            Text(
                text = "所有照片",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink,
                modifier = Modifier.padding(start = 3.7.dp).offset(y = 1.dp)
            )
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_album_outline_24),
                    contentDescription = "相册",
                    modifier = Modifier.size(22.dp),
                    tint = ink
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                // 手机 4 列（每列 90dp），平板按屏宽自适应列数。
                columns = GridCells.Fixed(
                    (LocalConfiguration.current.screenWidthDp / 90f).roundToInt().coerceAtLeast(4)
                ),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(images, key = { it.id }) { m ->
                    val order = selected.indexOfFirst { it.id == m.id }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color(0xFFE4E6E8))
                            .clickable { onToggle(m) }
                    ) {
                        val bmp = rememberMediaBitmap(m.uri, 320)
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 2.dp, bottom = 2.dp)
                                .size(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (order >= 0) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(brand),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${order + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(Color.White)
        ) {
            Spacer(Modifier.height(6.4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.7.dp)
                    .padding(start = 24.dp, end = 23.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selected.size}/$maxCount",
                    fontSize = 18.sp,
                    color = Color(0xFF191919)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "完成",
                    fontSize = 14.sp,
                    color = if (selected.isEmpty()) Color(0xFFAEC0F9) else brand,
                    modifier = Modifier.clickable(enabled = selected.isNotEmpty()) { onConfirm() }
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(109.dp)) {
                if (selected.isEmpty()) {
                    Text(
                        text = "请选择你想要的素材",
                        fontSize = 12.7.sp,
                        color = Color(0xFF666666),
                        modifier = Modifier.align(Alignment.Center).offset(y = (-5.6).dp)
                    )
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 13.7.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        items(selected, key = { it.id }) { m ->
                            Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))) {
                                val bmp = rememberMediaBitmap(m.uri, 216)
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize().background(Color(0xFFE4E6E8)))
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(24.dp)
                                        .clickable { onToggle(m) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF8E9094)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_close_outline_16),
                                            contentDescription = "取消选择",
                                            modifier = Modifier.size(11.dp),
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
