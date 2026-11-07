package com.orangefox.tensor.manager.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.ui.ExpandableGroupCard
import com.orangefox.tensor.manager.ui.ExpressiveSegmentedRow
import com.orangefox.tensor.manager.ui.edgeOutline
import com.orangefox.tensor.manager.ui.SettingSlider
import com.orangefox.tensor.manager.AppPrefs
import com.orangefox.tensor.manager.BuildConfig
import com.orangefox.tensor.manager.DateFormatMode
import com.orangefox.tensor.manager.R
import com.orangefox.tensor.manager.ReleaseNotifyMode
import com.orangefox.tensor.manager.ThemeMode
import com.orangefox.tensor.manager.data.ReleaseCheckWorker

@Composable
internal fun SettingsScreen(
    topInset: Dp,
    blurRadius: Float,
    onBlurRadiusChange: (Float) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    showBannerInCollapsed: Boolean,
    onShowBannerInCollapsedChange: (Boolean) -> Unit,
    collapseH2AsSpoilers: Boolean,
    onCollapseH2AsSpoilersChange: (Boolean) -> Unit,
    collapseH2Home: Boolean,
    onCollapseH2HomeChange: (Boolean) -> Unit,
    cacheImages: Boolean,
    onCacheImagesChange: (Boolean) -> Unit,
    forceGithubToken: Boolean = false,
    onForceGithubTokenChange: (Boolean) -> Unit = {},
    releaseNotifyMode: ReleaseNotifyMode,
    onReleaseNotifyModeChange: (ReleaseNotifyMode) -> Unit,
    dateFormatMode: DateFormatMode,
    onDateFormatModeChange: (DateFormatMode) -> Unit,
    onWipeAllFiles: () -> Unit = {},
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var showWipeConfirm by remember { mutableStateOf(false) }
    // Link lists live in resources (string-array); order and names stay unchanged.
    val xdaThreads = remember(context) {
        context.resources.getStringArray(R.array.xda_thread_titles)
            .zip(context.resources.getStringArray(R.array.xda_thread_urls))
    }
    val tgFamilyChats = remember(context) {
        context.resources.getStringArray(R.array.tg_family_chat_titles)
            .zip(context.resources.getStringArray(R.array.tg_family_chat_urls))
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = stringResource(R.string.wipe_confirm_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.wipe_confirm_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirm = false
                        onWipeAllFiles()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.edgeOutline()),
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topInset + 4.dp,
            bottom = 128.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_section_appearance),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(), RoundedCornerShape(24.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = stringResource(R.string.settings_theme_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ExpressiveSegmentedRow(
                                items = ThemeMode.entries,
                                selectedItem = themeMode,
                                onSelect = {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onThemeModeChange(it)
                                },
                                labelProvider = { stringResource(it.labelRes) },
                            )
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )

                        SettingSlider(
                            title = stringResource(R.string.settings_blur_intensity),
                            value = blurRadius,
                            onValueChange = onBlurRadiusChange,
                            valueRange = 0f..150f,
                            step = 1f,
                            defaultValue = AppPrefs.DEFAULT_BLUR,
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_section_releases),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(), RoundedCornerShape(24.dp)),
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_show_banner_collapsed_title),
                            description = stringResource(R.string.settings_show_banner_collapsed_desc),
                            checked = showBannerInCollapsed,
                            onCheckedChange = onShowBannerInCollapsedChange,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_collapse_h2_title),
                            description = stringResource(R.string.settings_collapse_h2_desc),
                            checked = collapseH2AsSpoilers,
                            onCheckedChange = onCollapseH2AsSpoilersChange,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_collapse_h2_home_title),
                            description = stringResource(R.string.settings_collapse_h2_home_desc),
                            checked = collapseH2Home,
                            onCheckedChange = onCollapseH2HomeChange,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_cache_images_title),
                            description = stringResource(R.string.settings_cache_images_desc),
                            checked = cacheImages,
                            onCheckedChange = onCacheImagesChange,
                        )
                        if (BuildConfig.DEBUG) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 18.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                            SettingSwitchRow(
                                title = stringResource(R.string.settings_force_github_token_title),
                                description = stringResource(R.string.settings_force_github_token_desc),
                                checked = forceGithubToken,
                                onCheckedChange = onForceGithubTokenChange,
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_notify_releases_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ExpressiveSegmentedRow(
                                items = ReleaseNotifyMode.entries,
                                selectedItem = releaseNotifyMode,
                                onSelect = { mode ->
                                    onReleaseNotifyModeChange(mode)
                                    ReleaseCheckWorker.schedule(context, mode)
                                },
                                labelProvider = { stringResource(it.labelRes) },
                            )
                            Text(
                                text = stringResource(R.string.settings_notify_releases_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_date_format_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ExpressiveSegmentedRow(
                                items = DateFormatMode.entries,
                                selectedItem = dateFormatMode,
                                onSelect = onDateFormatModeChange,
                                labelProvider = { stringResource(it.labelRes) },
                            )
                            Text(
                                text = stringResource(R.string.settings_date_format_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            val uriHandler = LocalUriHandler.current
            val tgChannelUrl = stringResource(R.string.url_tg_channel)
            val tgDiscussionUrl = stringResource(R.string.url_tg_discussion)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_section_links),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(), RoundedCornerShape(24.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { uriHandler.openUri(tgChannelUrl) },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer.edgeOutline()),
                                modifier = Modifier.weight(1f).height(48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.link_tg_channel),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            FilledTonalButton(
                                onClick = { uriHandler.openUri(tgDiscussionUrl) },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer.edgeOutline()),
                                modifier = Modifier.weight(1f).height(48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.link_tg_discussion),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        ExpandableGroupCard(
                            title = stringResource(R.string.link_tg_family_chats_title),
                            subtitle = stringResource(R.string.link_tg_family_chats_subtitle),
                            keyTag = "settings_tg_family_spoiler",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                tgFamilyChats.forEach { (title, url) ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh.edgeOutline(), RoundedCornerShape(12.dp))
                                            .clickable { uriHandler.openUri(url) },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = url.substringAfterLast('/'),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        ExpandableGroupCard(
                            title = stringResource(R.string.link_xda_threads_title, xdaThreads.size),
                            subtitle = stringResource(R.string.link_xda_threads_subtitle),
                            keyTag = "settings_xda_threads_spoiler",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                xdaThreads.forEach { (deviceTitle, url) ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh.edgeOutline(), RoundedCornerShape(12.dp))
                                            .clickable { uriHandler.openUri(url) },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = deviceTitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = stringResource(R.string.link_xda_open),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showWipeConfirm = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.edgeOutline()),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.action_wipe_all_files),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        item {
            val uriHandler = LocalUriHandler.current
            val donateUrl = stringResource(R.string.url_donate)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_section_support),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(), RoundedCornerShape(24.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_support_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.settings_support_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                uriHandler.openUri(donateUrl)
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.edgeOutline()),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.action_donate_support),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
