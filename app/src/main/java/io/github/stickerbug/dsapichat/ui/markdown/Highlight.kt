package io.github.stickerbug.dsapichat.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

data class CodeTheme(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val function: Color,
    val type: Color,
    val plain: Color
)

fun codeTheme(dark: Boolean): CodeTheme = if (dark) CodeTheme(
    keyword = Color(0xFFFF7B72),
    string = Color(0xFF9FD89B),
    comment = Color(0xFF8B949E),
    number = Color(0xFF79C0FF),
    function = Color(0xFFD2A8FF),
    type = Color(0xFF7EE787),
    plain = Color(0xFFE6EDF3)
) else CodeTheme(
    keyword = Color(0xFFCF222E),
    string = Color(0xFF1A7F37),
    comment = Color(0xFF9AA0A6),
    number = Color(0xFF0550AE),
    function = Color(0xFF8250DF),
    type = Color(0xFF953800),
    plain = Color(0xFF0C0C0C)
)

private val COMMON = setOf(
    "if", "else", "when", "for", "while", "do", "return", "break", "continue",
    "fun", "val", "var", "class", "object", "interface", "enum", "data", "sealed",
    "import", "package", "private", "public", "protected", "internal", "override",
    "open", "abstract", "const", "suspend", "inline", "try", "catch", "finally",
    "throw", "throws", "new", "this", "super", "null", "true", "false", "is", "as",
    "in", "out", "by", "lazy", "def", "lambda", "from", "not", "and", "or", "elif",
    "pass", "with", "global", "yield", "async", "await", "let", "function",
    "export", "default", "typeof", "instanceof", "extends", "implements", "static",
    "void", "int", "long", "float", "double", "bool", "boolean", "char", "byte",
    "short", "struct", "impl", "fn", "mut", "pub", "use", "mod", "match", "trait",
    "where", "self", "select", "insert", "update", "delete", "group", "order",
    "join", "left", "right", "inner", "outer", "on", "limit", "create", "table",
    "echo", "then", "fi", "done", "esac", "local", "readonly"
)

private fun isIdentStart(ch: Char) = ch.isLetter() || ch == '_' || ch == '$'
private fun isIdentPart(ch: Char) = ch.isLetterOrDigit() || ch == '_' || ch == '$'

private fun isCommentStart(code: String, i: Int, marker: String): Boolean {
    if (marker != "#") return code.startsWith(marker, i)
    if (!code.startsWith("#", i)) return false
    // shell/python 的 # 只有行首或空白后才算注释，避免 #FFF、C# 被误判
    return i == 0 || code[i - 1] == '\n' || code[i - 1].isWhitespace()
}

fun highlightCode(code: String, lang: String, theme: CodeTheme): AnnotatedString = buildAnnotatedString {
    val l = lang.lowercase()
    val hashComment = l in setOf("python", "py", "sh", "bash", "shell", "zsh", "yaml", "yml", "toml", "r", "ruby", "rb", "dockerfile", "makefile", "ini")
    val lineComment = if (hashComment) "#" else if (l == "sql" || l == "lua" || l == "haskell") "--" else "//"
    var i = 0
    val n = code.length
    while (i < n) {
        val ch = code[i]
        if (ch == '/' && i + 1 < n && code[i + 1] == '*') {
            var end = code.indexOf("*/", i + 2)
            end = if (end < 0) n else end + 2
            withStyle(SpanStyle(color = theme.comment)) { append(code.substring(i, end)) }
            i = end
            continue
        }
        if (isCommentStart(code, i, lineComment)) {
            var end = code.indexOf('\n', i)
            if (end < 0) end = n
            withStyle(SpanStyle(color = theme.comment)) { append(code.substring(i, end)) }
            i = end
            continue
        }
        if (ch == '"' || ch == '\'' || ch == '`') {
            var j = i + 1
            while (j < n) {
                if (code[j] == '\\') {
                    j += 2
                    continue
                }
                if (code[j] == ch) {
                    j += 1
                    break
                }
                if (code[j] == '\n') break
                j += 1
            }
            withStyle(SpanStyle(color = theme.string)) { append(code.substring(i, minOf(j, n))) }
            i = minOf(j, n)
            continue
        }
        if (ch.isDigit()) {
            var j = i
            while (j < n && (code[j].isDigit() || code[j] == '.' || code[j] == '_')) j += 1
            withStyle(SpanStyle(color = theme.number)) { append(code.substring(i, j)) }
            i = j
            continue
        }
        if (ch == '@' && i + 1 < n && isIdentStart(code[i + 1])) {
            var j = i + 1
            while (j < n && isIdentPart(code[j])) j += 1
            withStyle(SpanStyle(color = theme.type)) { append(code.substring(i, j)) }
            i = j
            continue
        }
        if (isIdentStart(ch)) {
            var j = i
            while (j < n && isIdentPart(code[j])) j += 1
            val word = code.substring(i, j)
            var k = j
            while (k < n && code[k] == ' ') k += 1
            val isCall = k < n && code[k] == '('
            val color = when {
                COMMON.contains(word) -> theme.keyword
                word.first().isUpperCase() && word.length > 1 -> theme.type
                isCall -> theme.function
                else -> null
            }
            if (color != null) withStyle(SpanStyle(color = color)) { append(word) } else append(word)
            i = j
            continue
        }
        append(ch)
        i += 1
    }
}
