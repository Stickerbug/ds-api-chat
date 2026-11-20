package io.github.stickerbug.dsapichat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class DsColors(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val sidebar: Color,
    val brand: Color,
    val brandPressed: Color,
    val brandSoft: Color,
    val onBrand: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textCaption: Color,
    val bubble: Color,
    val border: Color,
    val divider: Color,
    val field: Color,
    val pressed: Color,
    val codeBg: Color,
    val inlineCodeBg: Color,
    val danger: Color
)

val DsLight = DsColors(
    dark = false,
    bg = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    sidebar = Color(0xFFF8F8F8),
    brand = Color(0xFF4D6BFE),
    brandPressed = Color(0xFF3D58E0),
    brandSoft = Color(0xFFEDF3FE),
    onBrand = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0C0C0C),
    textSecondary = Color(0xFF81858C),
    textTertiary = Color(0xFF7C7C80),
    textCaption = Color(0xFFACACAC),
    bubble = Color(0xFFECF0FC),
    border = Color(0xFFE6E7EB),
    divider = Color(0xFFEDEEF0),
    field = Color(0xFFF3F4F6),
    pressed = Color(0xFFF0F1F3),
    codeBg = Color(0xFFF6F7F9),
    inlineCodeBg = Color(0xFFF1F2F4),
    danger = Color(0xFFD93025)
)

val DsDark = DsColors(
    dark = true,
    bg = Color(0xFF0F0F0F),
    card = Color(0xFF242424),
    sidebar = Color(0xFF1E1E1E),
    brand = Color(0xFF6B84FF),
    brandPressed = Color(0xFF5871E6),
    brandSoft = Color(0xFF283142),
    onBrand = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFECECEC),
    textSecondary = Color(0xFF9EA1A8),
    textTertiary = Color(0xFF8E9198),
    textCaption = Color(0xFF7B7E85),
    bubble = Color(0xFF262B3D),
    border = Color(0xFF2A2A2E),
    divider = Color(0xFF26262A),
    field = Color(0xFF202024),
    pressed = Color(0xFF1F1F23),
    codeBg = Color(0xFF1B1B1F),
    inlineCodeBg = Color(0xFF232327),
    danger = Color(0xFFFF6B60)
)

val LocalDsColors = staticCompositionLocalOf { DsLight }

object Ds {
    val color: DsColors
        @Composable get() = LocalDsColors.current
}

object DsType {
    val pageTitle = 20.sp
    val navTitle = 16.sp
    val body = 15.5.sp
    val bodyLine = 26.sp
    val bodySmall = 15.sp
    val bodySmallLine = 26.sp
    val label = 14.sp
    val labelLine = 20.sp
    val sectionHeader = 13.sp
    val caption = 12.sp
    val captionLine = 16.sp

    fun body(c: DsColors) = TextStyle(
        fontSize = body,
        lineHeight = bodyLine,
        color = c.textPrimary,
        fontFamily = FontFamily.Default
    )

    fun bodySecondary(c: DsColors) = TextStyle(
        fontSize = bodySmall,
        lineHeight = bodySmallLine,
        color = c.textSecondary
    )

    fun label(c: DsColors) = TextStyle(
        fontSize = label,
        lineHeight = labelLine,
        color = c.textPrimary
    )

    fun caption(c: DsColors) = TextStyle(
        fontSize = caption,
        lineHeight = captionLine,
        color = c.textCaption
    )

    fun title(c: DsColors) = TextStyle(
        fontSize = pageTitle,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        color = c.textPrimary
    )
}

val DsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DsTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DsDark else DsLight
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.brand,
            onPrimary = c.onBrand,
            background = c.bg,
            onBackground = c.textPrimary,
            surface = c.bg,
            onSurface = c.textPrimary,
            surfaceVariant = c.field,
            onSurfaceVariant = c.textSecondary
        )
    } else {
        lightColorScheme(
            primary = c.brand,
            onPrimary = c.onBrand,
            background = c.bg,
            onBackground = c.textPrimary,
            surface = c.bg,
            onSurface = c.textPrimary,
            surfaceVariant = c.field,
            onSurfaceVariant = c.textSecondary
        )
    }
    CompositionLocalProvider(LocalDsColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            shapes = DsShapes,
            content = content
        )
    }
}
