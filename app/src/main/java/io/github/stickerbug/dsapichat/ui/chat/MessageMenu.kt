package io.github.stickerbug.dsapichat.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stickerbug.dsapichat.ui.theme.Ds

/** 长按菜单的一项（官方：白色圆角卡、行高 44.3dp、左侧图标 + 文字）。 */
internal data class MenuEntry(
    val label: String,
    val iconRes: Int,
    val danger: Boolean = false,
    val onClick: () -> Unit
)

@Composable
internal fun MessageMenuOverlay(entries: List<MenuEntry>, onDismiss: () -> Unit) {
    val c = Ds.color
    // 弹出动画：遮罩 0→0.2 淡入，卡片 0.9→1 缩放 + 淡入（官方菜单是弹出而非瞬现）。
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(170, easing = FastOutSlowInEasing)) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.20f * appear.value))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .offset(x = (-14).dp)
                .width(190.3.dp)
                .graphicsLayer {
                    scaleX = 0.90f + 0.10f * appear.value
                    scaleY = 0.90f + 0.10f * appear.value
                    alpha = appear.value
                }
                .clip(RoundedCornerShape(14.dp))
                .background(if (c.dark) Color(0xFF292929) else Color.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
        ) {
            entries.forEach { e ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.3.dp)
                        .clickable {
                            onDismiss()
                            e.onClick()
                        }
                        .drawBehind {
                            if (e != entries.last()) {
                                drawLine(
                                    color = if (c.dark) Color(0xFF3A3A3E) else Color(0xFFD4D4D4),
                                    start = androidx.compose.ui.geometry.Offset(0f, size.height - 1f),
                                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - 1f),
                                    strokeWidth = 1f
                                )
                            }
                        }
                        .padding(start = 16.3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = e.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (e.danger) c.danger else c.textPrimary
                    )
                    Spacer(Modifier.width(10.3.dp))
                    Text(
                        text = e.label,
                        fontSize = 14.5.sp,
                        color = if (e.danger) c.danger else c.textPrimary
                    )
                }
            }
        }
    }
}

/** 「选择文本」：可拖选 + 复制，等价于官方的文本选择模式。 */
@Composable
internal fun TextSelectDialog(text: String, onDismiss: () -> Unit) {
    val c = Ds.color
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择文本", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Box(modifier = Modifier.heightIn(max = 380.dp)) {
                SelectionContainer {
                    Text(
                        text = text,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        color = c.textPrimary,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成", color = c.brand) }
        },
        containerColor = if (c.dark) Color(0xFF292929) else Color.White,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
internal fun ReportDialog(onDismiss: () -> Unit) {
    val c = Ds.color
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("举报", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                text = "感谢你的反馈。复刻版是本地演示项目，举报只记录在本机，不会上传服务器。",
                fontSize = 14.5.sp,
                lineHeight = 22.sp,
                color = c.textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("我知道了", color = c.brand) }
        },
        containerColor = if (c.dark) Color(0xFF292929) else Color.White,
        shape = RoundedCornerShape(18.dp)
    )
}
