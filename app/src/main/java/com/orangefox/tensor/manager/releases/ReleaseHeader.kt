package com.orangefox.tensor.manager.releases

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.ui.edgeOutline
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.orangefox.tensor.manager.DateFormatMode
import com.orangefox.tensor.manager.R
import com.orangefox.tensor.manager.ThemeMode
import com.orangefox.tensor.manager.data.ReleaseInfo

@Composable
internal fun OpenGitHubReleaseButton(tag: String) {
    val uriHandler = LocalUriHandler.current
    val tagBase = stringResource(R.string.url_release_tag_base)
    FilledTonalButton(
        onClick = {
            uriHandler.openUri(tagBase + tag)
        },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer.edgeOutline()),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.action_open_github_release),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_open_external),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}

private val releaseDateIsoFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
private val releaseDateEuFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT)
private val releaseDateUsFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a", Locale.US)

/**
 * Formats a GitHub ISO-8601 timestamp (`published_at`/`created_at`) in the
 * device timezone. Returns null when the input is missing/unparseable
 * (caller hides the date capsule entirely in that case).
 * Auto = system date+time format for the device locale
 * (MEDIUM date + SHORT time, e.g. US locale gives MM/dd/yy + 12-hour clock).
 */
private fun formatReleaseDate(iso: String?, mode: DateFormatMode = DateFormatMode.Auto): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        val zoned = OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
        when (mode) {
            DateFormatMode.Auto -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(Locale.getDefault())
                .format(zoned)
            DateFormatMode.Iso -> zoned.format(releaseDateIsoFormatter)
            DateFormatMode.Eu -> zoned.format(releaseDateEuFormatter)
            DateFormatMode.Us -> zoned.format(releaseDateUsFormatter)
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
internal fun ReleaseHeaderRow(
    info: ReleaseInfo,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    isLatestStable: Boolean = false,
    dateFormatMode: DateFormatMode = DateFormatMode.Auto,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "HeaderChevron",
    )
    // GitHub-style status badges: explicit Color() values (M3 has no green/yellow tokens).
    // Darkness is read from the actual scheme so forced Light/Dark ThemeMode also works.
    // Light:  Latest container #DAFBE1 / text #1A7F37; Pre-release #FFF8C5 / #9A6700.
    // Dark:   muted Latest container #1C3D2A / text #3FB950; Pre-release #3B2E16 / #D29922.
    val badgesDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // Formatted once per release and mode (remember), not on every recomposition/frame.
    val dateText = remember(info.publishedAt, dateFormatMode) {
        formatReleaseDate(info.publishedAt, dateFormatMode)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = info.tag,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (info.reactions > 0) {
                    Text(
                        text = stringResource(R.string.release_reactions, info.reactions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (dateText != null || isLatestStable || info.prerelease) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dateText != null) {
                        ReleaseStatusBadge(
                            text = dateText,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isLatestStable) {
                        ReleaseStatusBadge(
                            text = stringResource(R.string.badge_latest),
                            containerColor = if (badgesDark) Color(0xFF1C3D2A) else Color(0xFFDAFBE1),
                            contentColor = if (badgesDark) Color(0xFF3FB950) else Color(0xFF1A7F37),
                        )
                    }
                    if (info.prerelease) {
                        ReleaseStatusBadge(
                            text = stringResource(R.string.badge_prerelease),
                            containerColor = if (badgesDark) Color(0xFF3B2E16) else Color(0xFFFFF8C5),
                            contentColor = if (badgesDark) Color(0xFFD29922) else Color(0xFF9A6700),
                        )
                    }
                }
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation),
        )
    }
}

@Composable
internal fun ReleaseStatusBadge(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, containerColor.edgeOutline(), RoundedCornerShape(8.dp)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
