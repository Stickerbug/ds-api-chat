package io.github.stickerbug.dsapichat.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stickerbug.dsapichat.R
import androidx.compose.ui.unit.sp
import io.github.stickerbug.dsapichat.ui.theme.Ds
import io.github.stickerbug.dsapichat.ui.theme.DsColors
import io.github.stickerbug.dsapichat.ui.theme.DsType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import ru.noties.jlatexmath.JLatexMathDrawable
import ru.noties.jlatexmath.JLatexMathView
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class BulletItem(val marker: String, val indent: Int, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class Math(val tex: String) : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>, val aligns: List<Int>) : MdBlock
    data object Divider : MdBlock
}

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^(\\s*)([-*+])\\s+(.*)$")
private val ORDERED = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
private val QUOTE = Regex("^>\\s?(.*)$")
private val HR = Regex("^(-{3,}|\\*{3,}|_{3,})$")
private val TABLE_DIVIDER = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$")

private fun isTableDivider(line: String): Boolean {
    val t = line.trim()
    return t.contains("|") && TABLE_DIVIDER.matches(t)
}

private fun splitRow(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|")) s = s.dropLast(1)
    return s.split("|").map { it.trim().replace("\\|", "|") }
}

fun parseBlocks(src: String): List<MdBlock> {
    val text = src.replace("\r\n", "\n")
    val lines = text.split("\n")
    val out = ArrayList<MdBlock>()
    var i = 0
    val para = StringBuilder()

    fun flushPara() {
        if (para.isNotEmpty()) {
            out.add(MdBlock.Paragraph(para.toString().trim()))
            para.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushPara()
            val lang = trimmed.removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                code.append(lines[i]).append("\n")
                i++
            }
            if (i < lines.size) i++
            out.add(MdBlock.Code(lang, code.toString().trimEnd('\n')))
            continue
        }

        // 块公式：$$...$$（单行或跨行）与 \[...\]（单行或跨行）。官方两种定界符都支持。
        if (trimmed.startsWith("\$\$") || trimmed.startsWith("\\[")) {
            flushPara()
            val close = if (trimmed.startsWith("\$\$")) "\$\$" else "\\]"
            val rest = trimmed.substring(close.length)
            val sb = StringBuilder()
            val sameLine = rest.indexOf(close)
            if (sameLine >= 0) {
                sb.append(rest.substring(0, sameLine))
                i++
            } else {
                sb.append(rest)
                i++
                while (i < lines.size) {
                    val l = lines[i].trim()
                    val e = l.indexOf(close)
                    if (e >= 0) {
                        if (sb.isNotEmpty() || e > 0) sb.append('\n')
                        sb.append(l.substring(0, e))
                        i++
                        break
                    }
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(l)
                    i++
                }
            }
            val tex = sb.toString().trim()
            if (tex.isNotEmpty()) out.add(MdBlock.Math(tex))
            continue
        }

        if (trimmed.isEmpty()) {
            flushPara()
            i++
            continue
        }

        if (HR.matches(trimmed)) {
            flushPara()
            out.add(MdBlock.Divider)
            i++
            continue
        }

        if (trimmed.contains("|") && i + 1 < lines.size && isTableDivider(lines[i + 1])) {
            flushPara()
            val headers = splitRow(trimmed)
            val aligns = splitRow(lines[i + 1].trim()).map { spec ->
                val l = spec.startsWith(":")
                val r = spec.endsWith(":")
                when {
                    l && r -> 1
                    r -> 2
                    else -> 0
                }
            }
            i += 2
            val rows = ArrayList<List<String>>()
            while (i < lines.size && lines[i].trim().contains("|")) {
                val row = splitRow(lines[i].trim())
                if (row.any { it.isNotEmpty() }) rows.add(row)
                i++
            }
            out.add(MdBlock.Table(headers, rows, aligns))
            continue
        }

        val h = HEADING.matchEntire(trimmed)
        if (h != null) {
            flushPara()
            out.add(MdBlock.Heading(h.groupValues[1].length, h.groupValues[2].trim()))
            i++
            continue
        }

        val q = QUOTE.matchEntire(trimmed)
        if (q != null) {
            flushPara()
            val sb = StringBuilder(q.groupValues[1])
            i++
            while (i < lines.size && QUOTE.matches(lines[i].trim())) {
                sb.append("\n").append(QUOTE.matchEntire(lines[i].trim())!!.groupValues[1])
                i++
            }
            out.add(MdBlock.Quote(sb.toString()))
            continue
        }

        val b = BULLET.matchEntire(line)
        val o = if (b == null) ORDERED.matchEntire(line) else null
        if (b != null || o != null) {
            flushPara()
            while (i < lines.size) {
                val bl = BULLET.matchEntire(lines[i])
                val ol = if (bl == null) ORDERED.matchEntire(lines[i]) else null
                if (bl == null && ol == null) break
                if (bl != null) {
                    out.add(
                        MdBlock.BulletItem(
                            marker = if (bl.groupValues[1].length >= 2) "◦" else "•",
                            indent = bl.groupValues[1].length,
                            text = bl.groupValues[3]
                        )
                    )
                } else if (ol != null) {
                    out.add(
                        MdBlock.BulletItem(
                            marker = ol.groupValues[2] + ".",
                            indent = ol.groupValues[1].length,
                            text = ol.groupValues[3]
                        )
                    )
                }
                i++
            }
            continue
        }

        if (para.isNotEmpty()) para.append("\n")
        para.append(trimmed)
        i++
    }
    flushPara()
    return out
}


class InlineResult(val text: AnnotatedString, val maths: List<String>)

private fun inline(src: String, c: DsColors, maths: MutableList<String> = mutableListOf()): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = src.length
    val plain = StringBuilder()

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            append(plain.toString())
            plain.clear()
        }
    }

    while (i < n) {
        val ch = src[i]

        // 行内公式的另一种定界符 \\(...\\) / \\[...\\]（模型常混用）；排在反斜杠转义之前，否则反斜杠会被吃掉。
        if (ch == '\\' && i + 1 < n && (src[i + 1] == '(' || src[i + 1] == '[')) {
            val close = if (src[i + 1] == '(') "\\)" else "\\]"
            val end = src.indexOf(close, i + 2)
            if (end > i + 2) {
                flushPlain()
                val tex = src.substring(i + 2, end).replace('\n', ' ')
                val id = "x" + maths.size
                maths.add(tex)
                appendInlineContent(id, tex)
                i = end + close.length
                continue
            }
        }

        if (ch == '\\' && i + 1 < n) {
            plain.append(src[i + 1])
            i += 2
            continue
        }

        if (ch == '$' && i + 1 < n && src[i + 1] == '$') {
            val end = src.indexOf("$$", i + 2)
            if (end > i + 2) {
                flushPlain()
                val tex = src.substring(i + 2, end).replace('\n', ' ')
                val id = "x" + maths.size
                maths.add(tex)
                appendInlineContent(id, tex)
                i = end + 2
                continue
            }
        }

        if (ch == '$') {
            val end = src.indexOf('$', i + 1)
            if (end > i + 1) {
                // 跨行的行内公式按单行渲染（官方同样渲染，不会显示原始 TeX）
                val tex = src.substring(i + 1, end).replace('\n', ' ')
                flushPlain()
                val id = "x" + maths.size
                maths.add(tex)
                appendInlineContent(id, tex)
                i = end + 1
                continue
            }
        }

        if (ch == '`') {
            val end = src.indexOf('`', i + 1)
            if (end > i) {
                flushPlain()
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = c.inlineCodeBg,
                        fontSize = 14.sp
                    )
                ) { append(src.substring(i + 1, end)) }
                i = end + 1
                continue
            }
        }

        if (ch == '*' && i + 1 < n && src[i + 1] == '*') {
            val end = src.indexOf("**", i + 2)
            if (end > i) {
                flushPlain()
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(src.substring(i + 2, end)) }
                i = end + 2
                continue
            }
        }

        if (ch == '~' && i + 1 < n && src[i + 1] == '~') {
            val end = src.indexOf("~~", i + 2)
            if (end > i) {
                flushPlain()
                withStyle(
                    SpanStyle(
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                    )
                ) { append(src.substring(i + 2, end)) }
                i = end + 2
                continue
            }
        }

        if (ch == '[') {
            val close = src.indexOf(']', i + 1)
            val open = if (close > i) src.indexOf('(', close) else -1
            val end = if (open == close + 1) src.indexOf(')', open) else -1
            if (close > i && end > open) {
                flushPlain()
                withStyle(
                    SpanStyle(
                        color = c.brand,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    )
                ) { append(src.substring(i + 1, close)) }
                i = end + 1
                continue
            }
        }

        if (ch == '*' || ch == '_') {
            val end = src.indexOf(ch, i + 1)
            if (end > i + 1) {
                flushPlain()
                withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(src.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }

        plain.append(ch)
        i++
    }
    flushPlain()
}

private fun buildInline(src: String, c: DsColors): InlineResult {
    val maths = mutableListOf<String>()
    return InlineResult(inline(src, c, maths), maths)
}

@Composable
private fun latexInline(tex: String, c: DsColors): InlineTextContent {
    val density = LocalDensity.current
    val px = with(density) { 17.sp.toPx() }
    val drawable = remember(tex, c.dark, px) {
        runCatching {
            JLatexMathDrawable.builder(tex)
                .textSize(px)
                .color(c.textPrimary.toArgb())
                .background(0)
                .build()
        }.getOrNull()
    }
    // drawable 构建失败时退化显示原始 TeX，避免公式静默消失
    val w = if (drawable != null) with(density) { drawable.intrinsicWidth.coerceAtLeast(1).toSp() }
    else (tex.length.coerceAtLeast(2) * 9.4f).sp
    val h = if (drawable != null) with(density) { drawable.intrinsicHeight.coerceAtLeast(1).toSp() }
    else 20.sp
    return InlineTextContent(
        Placeholder(
            width = w,
            height = h,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
        )
    ) {
        if (drawable != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                drawIntoCanvas { canvas -> drawable.draw(canvas.nativeCanvas) }
            }
        } else {
            Text(
                text = tex,
                fontSize = 13.sp,
                color = c.textSecondary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun InlineText(
    src: String,
    style: TextStyle,
    c: DsColors,
    modifier: Modifier = Modifier
) {
    val res = remember(src, c) { buildInline(src, c) }
    if (res.maths.isEmpty()) {
        Text(text = res.text, style = style, modifier = modifier)
    } else {
        val content = HashMap<String, InlineTextContent>()
        res.maths.forEachIndexed { idx, tex -> content["x" + idx] = latexInline(tex, c) }
        Text(text = res.text, style = style, modifier = modifier, inlineContent = content)
    }
}

@Composable
fun MarkdownBody(text: String, modifier: Modifier = Modifier) {
    val c = Ds.color
    val blocks = remember(text) { parseBlocks(text) }
    // 不用 SelectionContainer：官方长按出的是自定义菜单，「选择文本」走菜单项，
    // 否则长按时系统会先弹 Copy / Select all 工具条。
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEachIndexed { i, block -> BlockView(block, c, first = i == 0) }
    }
}

@Composable
private fun BlockView(b: MdBlock, c: DsColors, first: Boolean = false) {
    when (b) {
        is MdBlock.Paragraph -> InlineText(b.text, DsType.body(c), c)

        is MdBlock.Heading -> {
            val size = when (b.level) {
                1 -> 22.sp
                2 -> 20.sp
                3 -> 18.sp
                else -> 16.sp
            }
            InlineText(
                src = b.text,
                style = TextStyle(
                    fontSize = size,
                    lineHeight = size * 1.4f,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary
                ),
                c = c,
                modifier = Modifier.padding(top = if (first) 2.dp else 36.3.dp, bottom = 4.3.dp)
            )
        }

        is MdBlock.BulletItem -> Row(modifier = Modifier.fillMaxWidth().padding(top = if (first) 0.dp else 4.7.dp)) {
            val sub = b.marker == "\u25E6"
            Spacer(Modifier.width(((b.indent * 2) + if (sub) 10 else 0).dp))
            Text(
                text = b.marker,
                style = DsType.body(c),
                modifier = Modifier.width(if (sub) 12.dp else 22.dp)
            )
            InlineText(
                src = b.text,
                style = DsType.body(c),
                c = c,
                modifier = Modifier.weight(1f)
            )
        }

        is MdBlock.Quote -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(c.border, RoundedCornerShape(1.dp))
            )
            Spacer(Modifier.width(10.dp))
            InlineText(
                src = b.text,
                style = DsType.body(c).copy(color = c.textSecondary),
                c = c,
                modifier = Modifier.weight(1f)
            )
        }

        is MdBlock.Code -> CodeBlock(b.lang, b.code, c)

        is MdBlock.Math -> LatexBlock(b.tex, c)

        is MdBlock.Table -> TableBlock(b, c)

        MdBlock.Divider -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(c.divider)
        )
    }
}

@Composable
private fun LatexBlock(tex: String, c: DsColors) {
    val density = LocalDensity.current
    val basePx = with(density) { 17.sp.toPx() }
    val minPx = with(density) { 9.sp.toPx() }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.codeBg, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val availPx = with(density) { maxWidth.toPx() }
        val drawable = remember(tex, availPx, c.dark) {
            fun build(px: Float) = runCatching {
                JLatexMathDrawable.builder(tex)
                    .textSize(px)
                    .color(c.textPrimary.toArgb())
                    .background(0)
                    .build()
            }.getOrNull()
            val base = build(basePx)
            // 超宽公式按比例缩到卡片宽度内，否则 AndroidView 会被右侧裁掉。
            if (base != null && availPx > 0f && base.intrinsicWidth > availPx) {
                build((basePx * availPx / base.intrinsicWidth * 0.97f).coerceAtLeast(minPx)) ?: base
            } else base
        }
        if (drawable == null) {
            Text(
                text = tex,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = c.textSecondary
            )
        } else {
            AndroidView(
                factory = { ctx -> JLatexMathView(ctx).apply { setLatexDrawable(drawable) } },
                update = { it.setLatexDrawable(drawable) }
            )
        }
    }
}

/**
 * 估算一个单元格（可含行内公式/行内代码/加粗/链接）渲染后的宽度。
 * 之前直接按原始 TeX 文本量宽，公式列会被撑得离谱、后面的列被挤出屏幕。
 */
private fun inlineWidth(src: String, measurer: TextMeasurer, density: Density, style: TextStyle, c: DsColors): Float {
    val mathPx = with(density) { 17.sp.toPx() }
    var w = 0f
    val plain = StringBuilder()
    fun widthOf(text: String, st: TextStyle): Float =
        if (text.isEmpty()) 0f else measurer.measure(AnnotatedString(text), st, softWrap = false).size.width.toFloat()
    fun flush() {
        if (plain.isNotEmpty()) { w += widthOf(plain.toString(), style); plain.clear() }
    }
    var i = 0
    val n = src.length
    while (i < n) {
        val ch = src[i]
        var tex: String? = null
        var adv = 0
        if (ch == '\\' && i + 1 < n && (src[i + 1] == '(' || src[i + 1] == '[')) {
            val close = if (src[i + 1] == '(') "\\)" else "\\]"
            val e = src.indexOf(close, i + 2)
            if (e > i + 2) { tex = src.substring(i + 2, e); adv = e + close.length - i }
        }
        if (tex == null && ch == '$' && i + 1 < n && src[i + 1] == '$') {
            val e = src.indexOf("$$", i + 2)
            if (e > i + 2) { tex = src.substring(i + 2, e); adv = e + 2 - i }
        }
        if (tex == null && ch == '$') {
            val e = src.indexOf('$', i + 1)
            if (e > i + 1) { tex = src.substring(i + 1, e); adv = e + 1 - i }
        }
        if (tex != null) {
            flush()
            val tw = runCatching {
                JLatexMathDrawable.builder(tex.replace('\n', ' '))
                    .textSize(mathPx)
                    .color(c.textPrimary.toArgb())
                    .background(0)
                    .build()
                    .intrinsicWidth
            }.getOrNull()
            w += (tw ?: (tex.length * 7)).toFloat()
            i += adv
            continue
        }
        if (ch == '`') {
            val e = src.indexOf('`', i + 1)
            if (e > i) {
                flush()
                w += widthOf(src.substring(i + 1, e), style.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp))
                i = e + 1
                continue
            }
        }
        if (ch == '*' && i + 1 < n && src[i + 1] == '*') {
            val e = src.indexOf("**", i + 2)
            if (e > i + 1) {
                flush()
                w += widthOf(src.substring(i + 2, e), style.copy(fontWeight = FontWeight.SemiBold))
                i = e + 2
                continue
            }
        }
        if (ch == '~' && i + 1 < n && src[i + 1] == '~') {
            val e = src.indexOf("~~", i + 2)
            if (e > i + 1) {
                flush()
                w += widthOf(src.substring(i + 2, e), style)
                i = e + 2
                continue
            }
        }
        if (ch == '[') {
            val close = src.indexOf(']', i + 1)
            val open = if (close > i) src.indexOf('(', close) else -1
            val end = if (open == close + 1) src.indexOf(')', open) else -1
            if (close > i && end > open) {
                flush()
                w += widthOf(src.substring(i + 1, close), style)
                i = end + 1
                continue
            }
        }
        if (ch == '\\' && i + 1 < n) { plain.append(src[i + 1]); i += 2; continue }
        plain.append(ch)
        i++
    }
    flush()
    return w
}

@Composable
private fun TableBlock(t: MdBlock.Table, c: DsColors) {
    val measurer = rememberTextMeasurer()
    val cellStyle = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, color = c.textPrimary)
    val headerStyle = cellStyle.copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    val cols = maxOf(t.headers.size, t.rows.maxOfOrNull { it.size } ?: 0)
    val widths = remember(t, c.dark) {
        (0 until cols).map { ci ->
            val head = if (ci < t.headers.size) t.headers[ci] else null
            val headW = head?.let { inlineWidth(it, measurer, density, headerStyle, c) } ?: 0f
            val bodyW = t.rows.maxOfOrNull { r ->
                if (ci < r.size) inlineWidth(r[ci], measurer, density, cellStyle, c) else 0f
            } ?: 0f
            with(density) { (maxOf(headW, bodyW).toDp() + 24.dp) }
        }
    }
    val shape = RoundedCornerShape(8.dp)
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(modifier = Modifier.clip(shape).border(0.5.dp, c.border, shape)) {
            TableRow(t.headers, widths, t.aligns, true, c)
            t.rows.forEach { row ->
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.border))
                TableRow(row, widths, t.aligns, false, c)
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, widths: List<Dp>, aligns: List<Int>, header: Boolean, c: DsColors) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        widths.forEachIndexed { ci, w ->
            if (ci > 0) Box(Modifier.width(0.5.dp).fillMaxHeight().background(c.border))
            Box(
                modifier = Modifier
                    .width(w)
                    .fillMaxHeight()
                    .background(if (header) c.codeBg else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = when (aligns.getOrElse(ci) { 0 }) {
                    1 -> Alignment.Center
                    2 -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                }
            ) {
                InlineText(
                    src = cells.getOrElse(ci) { "" },
                    style = TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = c.textPrimary,
                        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    c = c
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String, c: DsColors) {
    val clipboard = LocalClipboardManager.current
    // 官方 2.8.0：卡片 #FAFAFA、圆角 12dp、header 40.3dp、语言 11sp、右侧「复制」图标+文案、
    // 分隔线 #EBEBEB 左右内缩 13.8dp、正文 start 15 / top 16.7dp。
    val ink = if (c.dark) c.textCaption else Color(0xFF8E9094)
    val line = if (c.dark) c.border else Color(0xFFEBEBEB)
    val cardBg = if (c.dark) c.codeBg else Color(0xFFFAFAFA)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBg, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.3.dp)
                .padding(start = 13.7.dp, end = 16.7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (lang.isBlank()) "code" else lang,
                fontSize = 11.sp,
                color = ink
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { clipboard.setText(AnnotatedString(code)) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_copy_outline),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = ink
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "复制", fontSize = 13.sp, color = ink)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.8.dp)
                .height(0.5.dp)
                .background(line)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 15.dp, end = 15.dp, top = 16.7.dp, bottom = 16.dp)
        ) {
            Text(
                text = highlightCode(code, lang, codeTheme(c.dark).copy(plain = c.textPrimary)),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = c.textPrimary
            )
        }
    }
}

@Composable
fun PlainCode(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val c = Ds.color
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = color ?: c.textPrimary,
        modifier = modifier
    )
}

@Composable
fun MarkdownSpacer() {
    Spacer(Modifier.size(0.dp))
}