package com.brett.xai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Para(val text: String) : MdBlock
    data class Code(val lang: String, val code: String, val title: String?) : MdBlock
    data class ListItem(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

private val fenceRe = Regex("""^\s*(```|~~~)\s*([\w+#.-]*).*$""")
private val headingRe = Regex("""^(#{1,6})\s+(.*)$""")
private val bulletRe = Regex("""^(\s*)([-*+]|\d+[.)])\s+(.*)$""")
private val fileNameLineRe = Regex("""^\s*(?:\*\*|`)?([\w./~-]+\.[\w]{1,8}|Dockerfile|Makefile|Caddyfile)(?:\*\*|`)?:?\s*$""")

fun parseMarkdown(src: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = src.replace("\r\n", "\n").split("\n")
    var i = 0
    val para = StringBuilder()
    fun flush() { if (para.isNotBlank()) out += MdBlock.Para(para.toString().trim()); para.clear() }
    while (i < lines.size) {
        val line = lines[i]
        val fence = fenceRe.matchEntire(line)
        if (fence != null) {
            // filename on the previous paragraph line? use it as the code block title
            var title: String? = null
            val prevPara = para.toString().trimEnd().lines().lastOrNull().orEmpty()
            fileNameLineRe.matchEntire(prevPara)?.let { m ->
                title = m.groupValues[1]
                val kept = para.toString().trimEnd().lines().dropLast(1).joinToString("\n")
                para.clear(); para.append(kept)
            }
            flush()
            val marker = fence.groupValues[1]
            val lang = fence.groupValues[2]
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith(marker)) { code.appendLine(lines[i]); i++ }
            out += MdBlock.Code(lang, code.toString().trimEnd('\n'), title)
            i++ // skip closing fence (or EOF while streaming)
            continue
        }
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> flush()
            headingRe.matches(trimmed) -> {
                flush(); val m = headingRe.matchEntire(trimmed)!!
                out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim().trimEnd('#').trim())
            }
            trimmed.matches(Regex("""^([-*_])(\s*\1){2,}$""")) -> { flush(); out += MdBlock.Rule }
            trimmed.startsWith("|") -> {
                flush()
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val r = lines[i].trim().trim('|').split("|").map { it.trim() }
                    if (!r.all { it.matches(Regex("""^:?-{2,}:?$""")) }) rows += r
                    i++
                }
                out += MdBlock.Table(rows)
                continue
            }
            trimmed.startsWith(">") -> { flush(); out += MdBlock.Quote(trimmed.removePrefix(">").trim()) }
            bulletRe.matches(line) -> {
                flush(); val m = bulletRe.matchEntire(line)!!
                val mk = m.groupValues[2]
                out += MdBlock.ListItem(if (mk[0].isDigit()) mk else "•", m.groupValues[3], m.groupValues[1].length / 2)
            }
            else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line) }
        }
        i++
    }
    flush()
    return out
}

private val inlineRe = Regex("""(`[^`]+`)|(\*\*[^*]+\*\*)|(__[^_]+__)|(\*[^*\s][^*]*\*)|(_[^_\s][^_]*_)|(\[[^\]]+]\([^)\s]+\))|(https?://[^\s)]+)""")

@Composable
fun mdInline(text: String): AnnotatedString {
    val cs = MaterialTheme.colorScheme
    val x = LocalX.current
    return remember(text, cs.primary) {
        buildAnnotatedString {
            var last = 0
            for (m in inlineRe.findAll(text)) {
                append(text.substring(last, m.range.first))
                val t = m.value
                when {
                    t.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = x.code.copy(alpha = 0.55f), color = x.accent2, fontSize = 13.5.sp)) {
                        append(" " + t.trim('`') + " ")
                    }
                    t.startsWith("**") || t.startsWith("__") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(t.drop(2).dropLast(2)) }
                    t.startsWith("[") -> {
                        val label = t.substringAfter("[").substringBefore("](")
                        val url = t.substringAfter("](").dropLast(1)
                        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = cs.primary, textDecoration = TextDecoration.Underline)))) { append(label) }
                    }
                    t.startsWith("http") -> withLink(LinkAnnotation.Url(t, TextLinkStyles(SpanStyle(color = cs.primary, textDecoration = TextDecoration.Underline)))) { append(t) }
                    else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(t.drop(1).dropLast(1)) }
                }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
}

@Composable
fun Markdown(
    text: String,
    modifier: Modifier = Modifier,
    onCopyCode: (String) -> Unit,
    onSaveCode: (lang: String, code: String, title: String?) -> Unit,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    val body = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, lineHeight = 23.sp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Heading -> Text(
                    mdInline(b.text),
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
                        else -> MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp)
                    }.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = if (b.level <= 2) 6.dp else 2.dp),
                )
                is MdBlock.Para -> SelectionContainer { Text(mdInline(b.text), style = body) }
                is MdBlock.ListItem -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
                    Text(b.marker, style = body, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(if (b.marker == "•") 16.dp else 26.dp))
                    SelectionContainer { Text(mdInline(b.text), style = body) }
                }
                is MdBlock.Quote -> Row {
                    Spacer(Modifier.width(3.dp).size(3.dp, 22.dp).background(MaterialTheme.colorScheme.secondary))
                    Spacer(Modifier.width(10.dp))
                    Text(mdInline(b.text), style = body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is MdBlock.Code -> CodeBlock(b, onCopyCode, onSaveCode)
                is MdBlock.Table -> TableBlock(b)
                MdBlock.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun CodeBlock(b: MdBlock.Code, onCopy: (String) -> Unit, onSave: (String, String, String?) -> Unit) {
    val x = LocalX.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(x.code)
    ) {
        Row(
            Modifier.fillMaxWidth().background(x.codeHeader).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                b.title ?: b.lang.ifBlank { "code" },
                color = x.muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onCopy(b.code) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.ContentCopy, "Copy", tint = x.muted, modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = { onSave(b.lang, b.code, b.title) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.SaveAlt, "Save file", tint = x.muted, modifier = Modifier.size(18.dp))
            }
        }
        SelectionContainer {
            Text(
                b.code,
                color = x.codeText,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            )
        }
    }
}

@Composable
private fun TableBlock(b: MdBlock.Table) {
    if (b.rows.isEmpty()) return
    val cols = b.rows.maxOf { it.size }
    val widths = (0 until cols).map { c -> b.rows.maxOf { r -> r.getOrNull(c)?.length ?: 0 }.coerceIn(4, 34) }
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
    ) {
        b.rows.forEachIndexed { ri, r ->
            Row(Modifier.padding(horizontal = 6.dp)) {
                (0 until cols).forEach { c ->
                    Text(
                        mdInline(r.getOrNull(c).orEmpty()),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (ri == 0) FontWeight.Bold else FontWeight.Normal),
                        color = if (ri == 0) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        modifier = Modifier.width((widths[c] * 7.5 + 16).dp).padding(horizontal = 6.dp, vertical = 5.dp),
                    )
                }
            }
            if (ri == 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

fun suggestedFileName(lang: String, title: String?): String {
    if (!title.isNullOrBlank()) return title.substringAfterLast('/')
    return when (lang.lowercase()) {
        "bash", "sh", "shell", "zsh" -> "script.sh"
        "python", "py" -> "main.py"
        "kotlin", "kt" -> "Main.kt"
        "kts" -> "build.gradle.kts"
        "javascript", "js" -> "index.js"
        "typescript", "ts" -> "index.ts"
        "json" -> "config.json"
        "yaml", "yml" -> "config.yml"
        "toml" -> "config.toml"
        "ini", "conf", "nginx" -> "site.conf"
        "dockerfile", "docker" -> "Dockerfile"
        "markdown", "md" -> "README.md"
        "html" -> "index.html"
        "css" -> "style.css"
        "sql" -> "schema.sql"
        "go" -> "main.go"
        "rust", "rs" -> "main.rs"
        "xml" -> "file.xml"
        "env", "dotenv" -> ".env.example"
        "systemd", "service" -> "app.service"
        else -> "snippet.txt"
    }
}
