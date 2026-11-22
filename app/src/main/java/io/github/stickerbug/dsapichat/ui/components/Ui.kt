package io.github.stickerbug.dsapichat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stickerbug.dsapichat.ui.theme.Ds
import io.github.stickerbug.dsapichat.ui.theme.DsType

@Composable
fun DsIconButton(
    iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val c = Ds.color
    val ink = tint ?: c.textPrimary
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = ink),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = ink
        )
    }
}

@Composable
fun DsPill(
    iconRes: Int,
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Ds.color
    val shape = RoundedCornerShape(14.5.dp)
    // 官方录屏逐帧实测：胶囊切换是瞬时的（相邻帧 34~50ms 内完成），没有颜色渐变；
    // ON = 浅蓝底 #ECF2FE + 蓝字，OFF = 白底 + 黑字 + 浅灰边；
    // 按压是无界圆形 ripple，但半径很小（逐帧量约 18dp，直径约 1.25 倍胶囊高），不会糊满整块区域。
    val bg = when {
        active -> c.brandSoft
        c.dark -> Color(0xFF2A2A2A)
        else -> c.card
    }
    val fg = if (active) c.brand else c.textPrimary
    val stroke = when {
        active -> if (c.dark) Color(0xFF35406B) else Color(0xFFDCE7FC)
        c.dark -> Color(0xFF3C3C3C)
        else -> Color(0xFFEDEDED)
    }
    Row(
        modifier = modifier
            .height(29.dp)
            .background(bg, shape)
            .border(1.dp, stroke, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 18.dp, color = fg),
                onClick = onClick
            )
            .padding(start = 11.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = fg
        )
        Text(
            text = text,
            fontSize = 12.5.sp,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DsSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val c = Ds.color
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(10.dp))
    ) {
        content()
    }
}

@Composable
fun DsDivider(modifier: Modifier = Modifier) {
    val c = Ds.color
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(c.divider)
    )
}

@Composable
fun DsTopBar(
    title: String,
    leftIcon: Int,
    onLeft: () -> Unit,
    rightIcon: Int? = null,
    onRight: (() -> Unit)? = null
) {
    val c = Ds.color
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DsIconButton(iconRes = leftIcon, contentDescription = "menu", size = 44.dp, onClick = onLeft)
        Spacer(Modifier.weight(1f))
        Text(
            text = title,
            style = TextStyle(fontSize = DsType.navTitle, color = c.textPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.weight(1f))
        if (rightIcon != null && onRight != null) {
            DsIconButton(iconRes = rightIcon, contentDescription = "action", size = 44.dp, onClick = onRight)
        } else {
            Spacer(Modifier.width(44.dp))
        }
    }
}

@Composable
fun DsAvatarLogo(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(id = io.github.stickerbug.dsapichat.R.drawable.chat_welcome_logo),
        contentDescription = "logo",
        modifier = modifier,
        tint = Color.Unspecified
    )
}

@Composable
fun Dot(color: Color, size: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).background(color, CircleShape))
}

@Composable
fun Badge(text: String, bg: Color, fg: Color) {
    Text(
        text = text,
        fontSize = 10.sp,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

@Composable
fun IconOrNull(painter: Painter?, contentDescription: String?, size: Dp, tint: Color) {
    if (painter != null) {
        Icon(painter = painter, contentDescription = contentDescription, modifier = Modifier.size(size), tint = tint)
    }
}
