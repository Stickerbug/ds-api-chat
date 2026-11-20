package io.github.stickerbug.dsapichat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.stickerbug.dsapichat.R
import io.github.stickerbug.dsapichat.ui.theme.Ds

private val BRAND = Color(0xFF4D6BFE)
private val MUTED = Color(0xFF8E9094)

enum class HelpMode { Short, Long }

@Composable
fun HelpDialog(mode: HelpMode, onClose: () -> Unit) {
    val c = Ds.color
    val card = if (c.dark) Color(0xFF292929) else Color.White
    val ink = if (c.dark) c.textPrimary else Color(0xFF0F0F0F)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(card)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(start = 20.dp, end = 10.dp)
                ) {
                    Text(
                        text = "帮助与反馈",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (c.dark) c.field else Color(0xFFF0F0F2))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close_outline_16),
                            contentDescription = "关闭",
                            modifier = Modifier.size(13.dp),
                            tint = MUTED
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp)
                ) {
                    Text("我能帮你做什么？", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "对以下问题，你可以试试：",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = if (c.dark) c.textSecondary else Color(0xFF5F5F5F)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "• 解释一段代码\n• 写一封邮件\n• 规划一次旅行",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = if (c.dark) c.textSecondary else Color(0xFF5F5F5F)
                    )
                    if (mode == HelpMode.Long) {
                        Spacer(Modifier.height(16.dp))
                        Text("常见问题", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "1. 发不出消息：先在「设置 → 接口」里填写 API Key，接口地址默认 https://api.deepseek.com。\n2. 提示 401：密钥无效或已过期，请重新生成。\n3. 提示网络错误：检查手机网络或代理设置。\n4. 回答中途停止：多为接口超时，可重试或把问题拆小。\n5. 深度思考 / 联网搜索：在输入框上方的胶囊里开关。",
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = if (c.dark) c.textSecondary else Color(0xFF5F5F5F)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("仍然无法解决？", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "复刻版是本地客户端，不提供客服工单通道。你可以到 DeepSeek 官网的帮助中心查看最新说明。",
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = if (c.dark) c.textSecondary else Color(0xFF5F5F5F)
                        )
                    } else {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "如果问题仍然存在，请在「设置 → 帮助与反馈」中查看更详细的排查步骤。",
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = if (c.dark) c.textSecondary else Color(0xFF5F5F5F)
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(BRAND)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("知道了", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}
