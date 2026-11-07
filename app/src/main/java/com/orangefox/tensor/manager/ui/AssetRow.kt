package com.orangefox.tensor.manager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.R
import com.orangefox.tensor.manager.data.AssetDl
import com.orangefox.tensor.manager.data.ReleaseAsset

/**
 * Shared release-asset row used by the releases list and the install tab.
 * Lives in ui/ so feature packages only depend on ui/data (never on each other).
 */
@Composable
internal fun AssetRow(
    asset: ReleaseAsset,
    state: AssetDl?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCopyToDownloads: () -> Unit,
    onInstall: () -> Unit,
    onCancelDownload: () -> Unit = {},
    isManagerApk: Boolean = false,
) {
    val assetHaptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (asset.size > 0) {
                        Text(
                            text = stringResource(R.string.asset_size_mb, asset.size / 1048576.0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (asset.downloads >= 0) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.asset_downloads,
                                asset.downloads.toInt().coerceAtLeast(0),
                                asset.downloads,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state is AssetDl.Done) {
                        Text(
                            text = "• ${stringResource(R.string.asset_cached)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (state is AssetDl.Done) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.errorContainer.edgeOutline(), CircleShape)
                        .clickable {
                            assetHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDelete()
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        when (state) {
            null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    FilledTonalButton(
                        onClick = onDownload,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer.edgeOutline()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_download),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            is AssetDl.Downloading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (state.progress != null) {
                            ExpressiveLinearProgress(progress = state.progress)
                            Text(
                                text = stringResource(R.string.asset_downloading, (state.progress * 100).toInt()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            ExpressiveLinearProgress()
                            Text(
                                text = stringResource(R.string.asset_downloading_indeterminate),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.errorContainer.edgeOutline(), CircleShape)
                            .clickable {
                                assetHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCancelDownload()
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.action_cancel_download),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            is AssetDl.Error -> {
                Text(
                    text = stringResource(R.string.releases_error, state.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                FilledTonalButton(
                    onClick = onDownload,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer.edgeOutline()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                ) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            is AssetDl.Done -> {
                if (isManagerApk) {
                    Button(
                        onClick = onInstall,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer.edgeOutline()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.action_install_update),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (asset.isInstallable) {
                        Button(
                            onClick = onInstall,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer.edgeOutline()),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.action_install),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = onCopyToDownloads,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest.edgeOutline()),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_copy_downloads),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                }
            }
        }
    }
}
