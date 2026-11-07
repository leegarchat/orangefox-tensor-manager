package com.orangefox.tensor.manager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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

@Composable
fun MarkdownBody(
    md: String,
    collapseH2Sections: Boolean = true,
    modifier: Modifier = Modifier,
    docLinkResolver: ((String) -> DocLink)? = null,
    onDocLinkClick: ((String) -> Unit)? = null,
    cacheImagesEnabled: Boolean = true,
) {
    val defaultSpoilerTitle = stringResource(R.string.md_spoiler_title)
    val rawBase = stringResource(R.string.url_github_raw_base)
    val blocks = remember(md, defaultSpoilerTitle, rawBase, collapseH2Sections) {
        parseMarkdownBlocks(md, defaultSpoilerTitle, rawBase, collapseH2Sections = collapseH2Sections)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            RenderMdBlock(block, docLinkResolver, onDocLinkClick, cacheImagesEnabled)
        }
    }
}

@Composable
private fun RenderMdBlock(
    block: MdBlock,
    docLinkResolver: ((String) -> DocLink)? = null,
    onDocLinkClick: ((String) -> Unit)? = null,
    cacheImagesEnabled: Boolean = true,
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
                                RenderMdBlock(child, docLinkResolver, onDocLinkClick, cacheImagesEnabled)
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
                modifier = Modifier.fillMaxWidth().border(
                    1.dp,
                    MaterialTheme.colorScheme.surfaceContainerHighest.edgeOutline(),
                    RoundedCornerShape(12.dp),
                ),
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
                        RenderMdBlock(child, docLinkResolver, onDocLinkClick, cacheImagesEnabled)
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
                                RenderMdBlock(child, docLinkResolver, onDocLinkClick, cacheImagesEnabled)
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
                cacheImagesEnabled = cacheImagesEnabled,
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
                isWebSchemeAt(text, i) -> {
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
