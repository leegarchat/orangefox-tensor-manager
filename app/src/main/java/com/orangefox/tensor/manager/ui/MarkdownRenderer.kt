package com.orangefox.tensor.manager.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap


/**
 * Сессионный кэш изображений в оперативной памяти с дедупликацией параллельных запросов.
 * Если один и тот же URL баннера встречается в нескольких релизах, сетевой запрос
 * выполняется ровно 1 раз за всю сессию работы приложения.
 */
object SessionImageCache {
    private val memoryCache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Bitmap?>>()

    fun getImmediate(rawUrl: String): Bitmap? {
        val url = normalizeGitHubImageUrl(rawUrl)
        return memoryCache.get(url)
    }

    suspend fun loadOrAwait(rawUrl: String): Bitmap? {
        val url = normalizeGitHubImageUrl(rawUrl)
        memoryCache.get(url)?.let { return it }

        val deferred = CompletableDeferred<Bitmap?>()
        val existing = inFlight.putIfAbsent(url, deferred)
        if (existing != null) {
            return existing.await()
        }

        return try {
            val bmp = withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "PixelOFoxApp")
                        .build()
                    com.orangefox.tensor.manager.sharedHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val bytes = resp.body?.bytes() ?: return@use null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            if (bmp != null) {
                memoryCache.put(url, bmp)
            }
            deferred.complete(bmp)
            bmp
        } catch (e: Throwable) {
            deferred.complete(null)
            null
        } finally {
            inFlight.remove(url)
        }
    }
}

/**
 * Преобразует ссылки вида:
 * https://github.com/<owner>/<repo>/blob/<branch>/<path>
 * в прямую ссылку на изображение:
 * https://raw.githubusercontent.com/<owner>/<repo>/<branch>/<path>
 */
fun normalizeGitHubImageUrl(url: String): String {
    val trimmed = url.trim().replace("&amp;", "&")
    val blobRegex = Regex("""^https?://github\.com/([^/]+)/([^/]+)/(?:blob|raw)/([^?#]+)(?:\?.*)?$""", RegexOption.IGNORE_CASE)
    val match = blobRegex.find(trimmed)
    return if (match != null) {
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val branchAndPath = match.groupValues[3]
        "https://raw.githubusercontent.com/$owner/$repo/$branchAndPath"
    } else {
        trimmed
    }
}

private val leadingHtmlImgRegex = Regex(
    """^\s*(?:<p[^>]*>\s*)?<img\b[^>]*?\bsrc\s*=\s*["']([^"']+)["'][^>]*/?>\s*(?:</p>)?""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val leadingLinkedMdImgRegex = Regex(
    """^\s*\[!\[[^\]]*]\((https?://[^)\s]+)\)]\([^)]+\)""",
    RegexOption.IGNORE_CASE
)
private val leadingMdImgRegex = Regex(
    """^\s*!\[[^\]]*]\((https?://[^)\s]+)(?:\s+"[^"]*")?\)""",
    RegexOption.IGNORE_CASE
)
private val inlineHtmlImgRegex = Regex(
    """<img\b[^>]*?\bsrc\s*=\s*["']([^"']+)["'][^>]*/?>""",
    RegexOption.IGNORE_CASE
)
private val inlineMdImgRegex = Regex(
    """^\s*!\[([^\]]*)]\((https?://[^)\s]+)(?:\s+"[^"]*")?\)\s*$""",
    RegexOption.IGNORE_CASE
)

/**
 * Извлекает ссылку на баннер из начала описания релиза (поддерживает <img width="..." src="..." />,
 * Markdown-картинки и ссылки с /blob/) и возвращает очищенный Markdown.
 */
fun extractBannerAndCleanMarkdown(rawMd: String): Pair<String?, String> {
    val trimmedStart = rawMd.trimStart()
    if (trimmedStart.isEmpty()) return null to rawMd

    leadingHtmlImgRegex.find(trimmedStart)?.let { m ->
        val rawUrl = m.groupValues[1]
        val clean = trimmedStart.substring(m.range.last + 1).trimStart('\r', '\n', ' ')
        return normalizeGitHubImageUrl(rawUrl) to clean
    }
    leadingLinkedMdImgRegex.find(trimmedStart)?.let { m ->
        val rawUrl = m.groupValues[1]
        val clean = trimmedStart.substring(m.range.last + 1).trimStart('\r', '\n', ' ')
        return normalizeGitHubImageUrl(rawUrl) to clean
    }
    leadingMdImgRegex.find(trimmedStart)?.let { m ->
        val rawUrl = m.groupValues[1]
        val clean = trimmedStart.substring(m.range.last + 1).trimStart('\r', '\n', ' ')
        return normalizeGitHubImageUrl(rawUrl) to clean
    }

    return null to rawMd
}

@Composable
fun NetworkBannerImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val normalizedUrl = remember(url) { normalizeGitHubImageUrl(url) }
    var bitmap by remember(normalizedUrl) {
        mutableStateOf(SessionImageCache.getImmediate(normalizedUrl))
    }
    var failed by remember(normalizedUrl) { mutableStateOf(false) }

    LaunchedEffect(normalizedUrl) {
        if (bitmap != null) return@LaunchedEffect
        failed = false
        val loaded = SessionImageCache.loadOrAwait(normalizedUrl)
        if (loaded != null) {
            bitmap = loaded
        } else {
            failed = true
        }
    }

    val bmp = bitmap
    when {
        bmp != null -> {
            val ratio = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat())
                .coerceIn(1.3f, 2.5f)
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio),
            )
        }
        !failed -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                ExpressiveLinearProgress(modifier = Modifier.width(120.dp))
            }
        }
    }
}

/**
 * Результат резолва ссылки из markdown: внешняя (открыть в браузере)
 * или внутренняя (открыть документ по id внутри приложения).
 */
sealed interface DocLink {
    data class External(val url: String) : DocLink
    data class Internal(val docId: String) : DocLink
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class SectionSpoiler(val title: String, val children: List<MdBlock>) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class CodeBlock(val lang: String, val code: String) : MdBlock
    data class Quote(val alertType: String?, val children: List<MdBlock>) : MdBlock
    data class ListItem(
        val indent: Int,
        val bullet: String,
        val checked: Boolean?,
        val text: String,
    ) : MdBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<TextAlign>,
        val rows: List<List<String>>,
    ) : MdBlock
    data class Spoiler(val summary: String, val children: List<MdBlock>) : MdBlock
    data class ImageBlock(val alt: String, val url: String) : MdBlock
    data object Divider : MdBlock
}

private fun parseMarkdownBlocks(
    md: String,
    defaultSpoilerTitle: String,
    collapseH2Sections: Boolean = true,
): List<MdBlock> {
    val lines = md.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MdBlock>()
    var i = 0

    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 0. Секция ## как раскрывающийся спойлер до следующего ## (или # ) или конца текста
        if (collapseH2Sections && trimmed.startsWith("## ") && !trimmed.startsWith("###")) {
            val sectionTitle = trimmed.removePrefix("## ").trim()
            i++
            val sectionLines = mutableListOf<String>()
            var inFence = false
            while (i < lines.size) {
                val curTrimmed = lines[i].trim()
                if (curTrimmed.startsWith("```")) {
                    inFence = !inFence
                }
                if (!inFence && (
                        (curTrimmed.startsWith("## ") && !curTrimmed.startsWith("###")) ||
                            (curTrimmed.startsWith("# ") && !curTrimmed.startsWith("##"))
                        )
                ) {
                    break
                }
                sectionLines.add(lines[i])
                i++
            }
            val children = parseMarkdownBlocks(
                md = sectionLines.joinToString("\n"),
                defaultSpoilerTitle = defaultSpoilerTitle,
                collapseH2Sections = false,
            )
            blocks.add(MdBlock.SectionSpoiler(title = sectionTitle, children = children))
            continue
        }

        // 1. HTML <details><summary>...</summary>...</details> или :::spoiler
        if (trimmed.startsWith("<details", ignoreCase = true) || trimmed.startsWith(":::spoiler", ignoreCase = true)) {
            val isHtml = trimmed.startsWith("<details", ignoreCase = true)
            var summary = defaultSpoilerTitle
            val bodyLines = mutableListOf<String>()

            if (isHtml) {
                val inlineSummary = Regex("""<summary[^>]*>(.*?)</summary>""", RegexOption.IGNORE_CASE).find(raw)
                if (inlineSummary != null) {
                    summary = inlineSummary.groupValues[1].replace(Regex("<[^>]+>"), "").trim().ifEmpty { defaultSpoilerTitle }
                }
                i++
                var depth = 1
                while (i < lines.size && depth > 0) {
                    val l = lines[i]
                    if (Regex("""<details\b""", RegexOption.IGNORE_CASE).containsMatchIn(l)) depth++
                    if (Regex("""</details>""", RegexOption.IGNORE_CASE).containsMatchIn(l)) {
                        depth--
                        if (depth == 0) {
                            i++
                            break
                        }
                    }
                    if (depth == 1 && summary == defaultSpoilerTitle) {
                        val sMatch = Regex("""<summary[^>]*>(.*?)</summary>""", RegexOption.IGNORE_CASE).find(l)
                        if (sMatch != null) {
                            summary = sMatch.groupValues[1].replace(Regex("<[^>]+>"), "").trim().ifEmpty { defaultSpoilerTitle }
                            i++
                            continue
                        }
                    }
                    bodyLines.add(l)
                    i++
                }
            } else {
                summary = trimmed.removePrefix(":::spoiler").trim().ifEmpty { defaultSpoilerTitle }
                i++
                while (i < lines.size && !lines[i].trim().equals(":::", ignoreCase = true)) {
                    bodyLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++
            }
            blocks.add(
                MdBlock.Spoiler(
                    summary = summary,
                    children = parseMarkdownBlocks(bodyLines.joinToString("\n"), defaultSpoilerTitle, collapseH2Sections = false),
                )
            )
            continue
        }

        // 2. Блок кода ```
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++
            blocks.add(MdBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            continue
        }

        // 3. Разделитель
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MdBlock.Divider)
            i++
            continue
        }

        // 4. Заголовки H1..H6
        val headingMatch = Regex("""^(#{1,6})\s+(.*)$""").find(trimmed)
        if (headingMatch != null) {
            blocks.add(
                MdBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2].trim(),
                )
            )
            i++
            continue
        }

        // 5. Картинка отдельной строкой (Markdown или HTML <img>)
        val imgMd = inlineMdImgRegex.find(trimmed)
        val imgHtml = inlineHtmlImgRegex.find(trimmed)
        if (imgMd != null || imgHtml != null) {
            val url = imgMd?.groupValues?.get(2) ?: imgHtml!!.groupValues[1]
            val alt = imgMd?.groupValues?.get(1) ?: ""
            blocks.add(MdBlock.ImageBlock(alt, normalizeGitHubImageUrl(url)))
            i++
            continue
        }

        // 6. Таблица GFM
        if (trimmed.contains("|") && i + 1 < lines.size && isTableDelimiterLine(lines[i + 1].trim())) {
            val headers = splitTableCells(trimmed)
            val alignments = parseTableAlignments(lines[i + 1].trim(), headers.size)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().contains("|") && lines[i].trim().isNotEmpty()) {
                rows.add(splitTableCells(lines[i].trim()))
                i++
            }
            blocks.add(MdBlock.Table(headers, alignments, rows))
            continue
        }

        // 7. Цитата или GFM Alert (> [!NOTE], > [!WARNING] и т.д.)
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").removePrefix(" "))
                i++
            }
            var alertType: String? = null
            if (quoteLines.isNotEmpty()) {
                val alertMatch = Regex("""^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\s*$""", RegexOption.IGNORE_CASE)
                    .find(quoteLines.first().trim())
                if (alertMatch != null) {
                    alertType = alertMatch.groupValues[1].uppercase()
                    quoteLines.removeAt(0)
                }
            }
            blocks.add(
                MdBlock.Quote(
                    alertType = alertType,
                    children = parseMarkdownBlocks(quoteLines.joinToString("\n"), defaultSpoilerTitle, collapseH2Sections = false),
                )
            )
            continue
        }

        // 8. Элементы списков и чекбоксы
        val listMatch = Regex("""^(\s*)([-*+]|\d+[.)])\s+(.*)$""").find(raw)
        if (listMatch != null) {
            val indent = listMatch.groupValues[1].length / 2
            val marker = listMatch.groupValues[2]
            val content = listMatch.groupValues[3]
            val checkboxMatch = Regex("""^\[([ xX])]\s+(.*)$""").find(content)
            val checked = checkboxMatch?.let { it.groupValues[1].equals("x", ignoreCase = true) }
            val itemText = checkboxMatch?.groupValues?.get(2) ?: content
            val bullet = if (marker.length == 1) "•" else marker
            blocks.add(MdBlock.ListItem(indent, bullet, checked, itemText))
            i++
            continue
        }

        // 9. Обычный параграф
        val paraLines = mutableListOf(trimmed)
        i++
        while (i < lines.size) {
            val next = lines[i].trim()
            if (next.isEmpty() ||
                next.startsWith("#") ||
                next.startsWith("```") ||
                next.startsWith(">") ||
                next.startsWith("<details", ignoreCase = true) ||
                next.startsWith(":::spoiler", ignoreCase = true) ||
                inlineHtmlImgRegex.containsMatchIn(next) ||
                inlineMdImgRegex.matches(next) ||
                next == "---" || next == "***" || next == "___" ||
                Regex("""^(\s*)([-*+]|\d+[.)])\s+""").containsMatchIn(lines[i]) ||
                (next.contains("|") && i + 1 < lines.size && isTableDelimiterLine(lines[i + 1].trim()))
            ) {
                break
            }
            paraLines.add(next)
            i++
        }
        blocks.add(MdBlock.Paragraph(paraLines.joinToString(" ")))
    }

    return blocks
}

private fun isTableDelimiterLine(line: String): Boolean {
    val cells = splitTableCells(line)
    return cells.isNotEmpty() && cells.all { Regex("""^:?-+:?$""").matches(it.trim()) }
}

private fun splitTableCells(line: String): List<String> {
    val cleaned = line.trim().removePrefix("|").removeSuffix("|")
    return cleaned.split(Regex("""(?<!\\)\|""")).map { it.trim().replace("""\|""", "|") }
}

private fun parseTableAlignments(line: String, count: Int): List<TextAlign> {
    val raw = splitTableCells(line)
    return List(count) { idx ->
        val cell = raw.getOrNull(idx)?.trim() ?: ""
        when {
            cell.startsWith(":") && cell.endsWith(":") -> TextAlign.Center
            cell.endsWith(":") -> TextAlign.End
            else -> TextAlign.Start
        }
    }
}

private fun stripMarkdownFormattingLength(s: String): Int {
    return s
        .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
        .replace("`", "")
        .replace("**", "")
        .replace("__", "")
        .replace("~~", "")
        .length
}

@Composable
fun MarkdownBody(
    md: String,
    collapseH2Sections: Boolean = true,
    modifier: Modifier = Modifier,
    docLinkResolver: ((String) -> DocLink)? = null,
    onDocLinkClick: ((String) -> Unit)? = null,
) {
    val defaultSpoilerTitle = stringResource(R.string.md_spoiler_title)
    val blocks = remember(md, defaultSpoilerTitle, collapseH2Sections) {
        parseMarkdownBlocks(md, defaultSpoilerTitle, collapseH2Sections = collapseH2Sections)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            RenderMdBlock(block, docLinkResolver, onDocLinkClick)
        }
    }
}

@Composable
private fun RenderMdBlock(
    block: MdBlock,
    docLinkResolver: ((String) -> DocLink)? = null,
    onDocLinkClick: ((String) -> Unit)? = null,
) {
    when (block) {
        is MdBlock.Divider -> HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 4.dp),
        )
        is MdBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                4 -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.labelLarge
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RichInlineText(
                    raw = block.text,
                    style = style.copy(fontWeight = FontWeight.SemiBold),
                    docLinkResolver = docLinkResolver,
                    onDocLinkClick = onDocLinkClick,
                )
                if (block.level <= 2) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
        is MdBlock.SectionSpoiler -> {
            var expanded by rememberSaveable(block.title) { mutableStateOf(false) }
            val chevronRotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "SectionSpoilerChevron",
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                    ),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RichInlineText(
                            raw = block.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                            docLinkResolver = docLinkResolver,
                            onDocLinkClick = onDocLinkClick,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(chevronRotation),
                        )
                    }
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                start = 10.dp,
                                end = 10.dp,
                                bottom = 14.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                            block.children.forEach { child ->
                                RenderMdBlock(child, docLinkResolver, onDocLinkClick)
                            }
                        }
                    }
                }
            }
        }
        is MdBlock.Paragraph -> {
            RichInlineText(
                raw = block.text,
                style = MaterialTheme.typography.bodyMedium,
                docLinkResolver = docLinkResolver,
                onDocLinkClick = onDocLinkClick,
            )
        }
        is MdBlock.CodeBlock -> {
            val clipboard = LocalClipboardManager.current
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = block.lang.ifEmpty { "code" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { clipboard.setText(AnnotatedString(block.code)) },
                        ) {
                            Text(
                                text = stringResource(R.string.action_copy_code),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
        is MdBlock.Quote -> {
            val accentColor = when (block.alertType) {
                "WARNING", "CAUTION" -> MaterialTheme.colorScheme.error
                "IMPORTANT" -> MaterialTheme.colorScheme.tertiary
                "TIP" -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.08f))
                    .height(IntrinsicSize.Min),
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(accentColor),
                )
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (block.alertType != null) {
                        Text(
                            text = block.alertType,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                    }
                    block.children.forEach { child ->
                        RenderMdBlock(child, docLinkResolver, onDocLinkClick)
                    }
                }
            }
        }
        is MdBlock.ListItem -> {
            Row(
                modifier = Modifier.padding(start = (block.indent * 14).dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (block.checked != null) {
                    Checkbox(
                        checked = block.checked,
                        onCheckedChange = null,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Text(
                        text = "${block.bullet} ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                RichInlineText(
                    raw = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    docLinkResolver = docLinkResolver,
                    onDocLinkClick = onDocLinkClick,
                )
            }
        }
        is MdBlock.Table -> {
            val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            val headerBg = MaterialTheme.colorScheme.surfaceContainerHighest
            val zebraBg = MaterialTheme.colorScheme.surfaceContainerLow

            val colCount = block.headers.size
            val colWidths = remember(block) {
                List(colCount) { colIdx ->
                    val headerLen = stripMarkdownFormattingLength(block.headers.getOrNull(colIdx) ?: "")
                    val maxRowLen = block.rows.maxOfOrNull { row ->
                        stripMarkdownFormattingLength(row.getOrNull(colIdx) ?: "")
                    } ?: 0
                    val maxChars = maxOf(headerLen, maxRowLen)
                    (maxChars * 7.8f + 28f).coerceIn(92f, 240f).dp
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .background(headerBg)
                            .height(IntrinsicSize.Min),
                    ) {
                        for (colIdx in 0 until colCount) {
                            val cell = block.headers.getOrNull(colIdx) ?: ""
                            val align = block.alignments.getOrElse(colIdx) { TextAlign.Start }
                            Box(
                                modifier = Modifier
                                    .width(colWidths[colIdx])
                                    .fillMaxHeight()
                                    .border(0.5.dp, borderColor)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = when (align) {
                                    TextAlign.Center -> Alignment.Center
                                    TextAlign.End -> Alignment.CenterEnd
                                    else -> Alignment.CenterStart
                                },
                            ) {
                                RichInlineText(
                                    raw = cell,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        textAlign = align,
                                    ),
                                    docLinkResolver = docLinkResolver,
                                    onDocLinkClick = onDocLinkClick,
                                )
                            }
                        }
                    }
                    block.rows.forEachIndexed { rowIdx, row ->
                        val rowBg = if (rowIdx % 2 == 1) zebraBg else Color.Transparent
                        Row(
                            modifier = Modifier
                                .background(rowBg)
                                .height(IntrinsicSize.Min),
                        ) {
                            for (colIdx in 0 until colCount) {
                                val cell = row.getOrNull(colIdx) ?: ""
                                val align = block.alignments.getOrElse(colIdx) { TextAlign.Start }
                                Box(
                                    modifier = Modifier
                                        .width(colWidths[colIdx])
                                        .fillMaxHeight()
                                        .border(0.5.dp, borderColor)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = when (align) {
                                        TextAlign.Center -> Alignment.Center
                                        TextAlign.End -> Alignment.CenterEnd
                                        else -> Alignment.CenterStart
                                    },
                                ) {
                                    RichInlineText(
                                        raw = cell,
                                        style = MaterialTheme.typography.bodySmall.copy(textAlign = align),
                                        docLinkResolver = docLinkResolver,
                                        onDocLinkClick = onDocLinkClick,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        is MdBlock.Spoiler -> {
            var expanded by rememberSaveable(block.summary) { mutableStateOf(false) }
            val chevronRotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                label = "SpoilerChevron",
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                    ),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RichInlineText(
                            raw = block.summary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            docLinkResolver = docLinkResolver,
                            onDocLinkClick = onDocLinkClick,
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(chevronRotation),
                        )
                    }
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                start = 10.dp,
                                end = 10.dp,
                                bottom = 12.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                            block.children.forEach { child ->
                                RenderMdBlock(child, docLinkResolver, onDocLinkClick)
                            }
                        }
                    }
                }
            }
        }
        is MdBlock.ImageBlock -> {
            NetworkBannerImage(
                url = block.url,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
private fun RichInlineText(
    raw: String,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier,
    docLinkResolver: ((String) -> DocLink)? = null,
    onDocLinkClick: ((String) -> Unit)? = null,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val spoilerMaskColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    val revealedSpoilers = remember(raw) { mutableStateListOf<Int>() }

    val annotated = remember(raw, primaryColor, codeBg, spoilerMaskColor, revealedSpoilers.toList()) {
        buildInlineAnnotatedString(
            s = raw,
            linkColor = primaryColor,
            codeBg = codeBg,
            spoilerMaskColor = spoilerMaskColor,
            revealedSpoilers = revealedSpoilers,
            docLinkResolver = docLinkResolver,
            onDocLinkClick = onDocLinkClick,
        )
    }

    Text(
        text = annotated,
        style = style,
        modifier = modifier,
    )
}

private val bareUrlRegex = Regex("""^https?://[^\s<>")\]]+""", RegexOption.IGNORE_CASE)

private fun buildInlineAnnotatedString(
    s: String,
    linkColor: Color,
    codeBg: Color,
    spoilerMaskColor: Color,
    revealedSpoilers: MutableList<Int>,
    docLinkResolver: ((String) -> DocLink)? = null,
    onDocLinkClick: ((String) -> Unit)? = null,
): AnnotatedString {
    var spoilerCounter = 0
    var docLinkCounter = 0
    fun parseSegment(text: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("||", i) -> {
                    val end = text.indexOf("||", i + 2)
                    if (end < 0) {
                        append(text.substring(i))
                        break
                    }
                    val idx = spoilerCounter++
                    val revealed = idx in revealedSpoilers
                    val content = text.substring(i + 2, end)
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "spoiler_$idx",
                            linkInteractionListener = {
                                if (idx in revealedSpoilers) revealedSpoilers.remove(idx)
                                else revealedSpoilers.add(idx)
                            },
                        )
                    ) {
                        withStyle(
                            SpanStyle(
                                color = if (revealed) Color.Unspecified else Color.Transparent,
                                background = if (revealed) codeBg else spoilerMaskColor,
                            )
                        ) {
                            append(" $content ")
                        }
                    }
                    i = end + 2
                }
                text.startsWith("***", i) -> {
                    val end = text.indexOf("***", i + 3)
                    if (end < 0) {
                        append(text.substring(i))
                        break
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(parseSegment(text.substring(i + 3, end)))
                    }
                    i = end + 3
                }
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val delim = text.substring(i, i + 2)
                    val end = text.indexOf(delim, i + 2)
                    if (end < 0) {
                        append(text.substring(i))
                        break
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(parseSegment(text.substring(i + 2, end)))
                    }
                    i = end + 2
                }
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end < 0) {
                        append(text.substring(i))
                        break
                    }
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(parseSegment(text.substring(i + 2, end)))
                    }
                    i = end + 2
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) {
                        append(text.substring(i))
                        break
                    }
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBg,
                        )
                    ) {
                        append(" ${text.substring(i + 1, end)} ")
                    }
                    i = end + 1
                }
                text[i] == '[' -> {
                    val mid = text.indexOf("](", i)
                    val end = if (mid >= 0) text.indexOf(')', mid + 2) else -1
                    if (mid >= 0 && end >= 0) {
                        val label = text.substring(i + 1, mid)
                        val url = text.substring(mid + 2, end).trim()
                        val internal = docLinkResolver?.invoke(url) as? DocLink.Internal
                        if (internal != null && onDocLinkClick != null) {
                            val tagIdx = docLinkCounter++
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "doclink_$tagIdx",
                                    linkInteractionListener = { onDocLinkClick(internal.docId) },
                                )
                            ) {
                                append(parseSegment(label))
                            }
                        } else {
                            val finalUrl = (docLinkResolver?.invoke(url) as? DocLink.External)?.url ?: url
                            withLink(
                                LinkAnnotation.Url(
                                    url = finalUrl,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = linkColor,
                                            textDecoration = TextDecoration.Underline,
                                        )
                                    ),
                                )
                            ) {
                                append(parseSegment(label))
                            }
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text.startsWith("http://", i, ignoreCase = true) ||
                    text.startsWith("https://", i, ignoreCase = true) -> {
                    val m = bareUrlRegex.find(text.substring(i))
                    if (m != null) {
                        val url = m.value.trimEnd('.', ',', ';', ':')
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                    )
                                ),
                            )
                        ) {
                            append(url)
                        }
                        i += url.length
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text[i] == '*' || text[i] == '_' -> {
                    val delim = text[i]
                    val end = text.indexOf(delim, i + 1)
                    if (end > i + 1 && !text.substring(i + 1, end).contains(' ')) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(parseSegment(text.substring(i + 1, end)))
                        }
                        i = end + 1
                    } else {
                        append(delim)
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
    return parseSegment(s)
}
