package com.orangefox.tensor.manager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign

/** Checks the http/https scheme without hardcoding URL literals (scheme != resource address). */
internal fun isWebUrl(text: String): Boolean {
    val scheme = text.substringBefore(':', missingDelimiterValue = "").lowercase()
    return scheme == "http" || scheme == "https"
}

/** Same for a position inside a string (inline parsing), without a scheme literal in code. */
internal fun isWebSchemeAt(text: String, index: Int): Boolean {
    if (index < 0 || index + 7 > text.length) return false
    if (!text.regionMatches(index, "http", 0, 4, ignoreCase = true)) return false
    var p = index + 4
    if (p < text.length && (text[p] == 's' || text[p] == 'S')) p++
    return p + 2 < text.length && text[p] == ':' && text[p + 1] == '/' && text[p + 2] == '/'
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
 * Extracts the banner link from the start of a release description (supports <img width="..." src="..." />,
 * Markdown images and /blob/ links) and returns the cleaned Markdown.
 */
fun extractBannerAndCleanMarkdown(rawMd: String, rawBase: String): Pair<String?, String> {
    val trimmedStart = rawMd.trimStart()
    if (trimmedStart.isEmpty()) return null to rawMd

    leadingHtmlImgRegex.find(trimmedStart)?.let { m ->
        val rawUrl = m.groupValues[1]
        val clean = trimmedStart.substring(m.range.last + 1).trimStart('\r', '\n', ' ')
        return normalizeGitHubImageUrl(rawUrl, rawBase) to clean
    }
    leadingLinkedMdImgRegex.find(trimmedStart)?.let { m ->
        val rawUrl = m.groupValues[1]
        val clean = trimmedStart.substring(m.range.last + 1).trimStart('\r', '\n', ' ')
        return normalizeGitHubImageUrl(rawUrl, rawBase) to clean
    }
    leadingMdImgRegex.find(trimmedStart)?.let { m ->
        val rawUrl = m.groupValues[1]
        val clean = trimmedStart.substring(m.range.last + 1).trimStart('\r', '\n', ' ')
        return normalizeGitHubImageUrl(rawUrl, rawBase) to clean
    }

    return null to rawMd
}


/**
 * Markdown link resolution result: external (open in browser)
 * or internal (open the document by id inside the app).
 */
sealed interface DocLink {
    data class External(val url: String) : DocLink
    data class Internal(val docId: String) : DocLink
}

internal sealed interface MdBlock {
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

internal fun parseMarkdownBlocks(
    md: String,
    defaultSpoilerTitle: String,
    rawBase: String,
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

        // 0. ## section as an expandable spoiler until the next ## (or # ) or end of text
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
                rawBase = rawBase,
                collapseH2Sections = false,
            )
            blocks.add(MdBlock.SectionSpoiler(title = sectionTitle, children = children))
            continue
        }

        // 1. HTML <details><summary>...</summary>...</details> or :::spoiler
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
                    children = parseMarkdownBlocks(bodyLines.joinToString("\n"), defaultSpoilerTitle, rawBase, collapseH2Sections = false),
                )
            )
            continue
        }

        // 2. Code block ```
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

        // 3. Divider
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MdBlock.Divider)
            i++
            continue
        }

        // 4. Headings H1..H6
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

        // 5. Image on its own line (Markdown or HTML <img>)
        val imgMd = inlineMdImgRegex.find(trimmed)
        val imgHtml = inlineHtmlImgRegex.find(trimmed)
        if (imgMd != null || imgHtml != null) {
            val url = imgMd?.groupValues?.get(2) ?: imgHtml!!.groupValues[1]
            val alt = imgMd?.groupValues?.get(1) ?: ""
            blocks.add(MdBlock.ImageBlock(alt, normalizeGitHubImageUrl(url, rawBase)))
            i++
            continue
        }

        // 6. GFM table
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

        // 7. Quote or GFM Alert (> [!NOTE], > [!WARNING], etc.)
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
                    children = parseMarkdownBlocks(quoteLines.joinToString("\n"), defaultSpoilerTitle, rawBase, collapseH2Sections = false),
                )
            )
            continue
        }

        // 8. List items and checkboxes
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

        // 9. Plain paragraph
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

internal fun stripMarkdownFormattingLength(s: String): Int {
    return s
        .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
        .replace("`", "")
        .replace("**", "")
        .replace("__", "")
        .replace("~~", "")
        .length
}
