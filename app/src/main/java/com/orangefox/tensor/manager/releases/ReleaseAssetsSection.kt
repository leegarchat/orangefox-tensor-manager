package com.orangefox.tensor.manager.releases

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.orangefox.tensor.manager.ui.edgeOutline
import com.orangefox.tensor.manager.ui.AssetRow
import com.orangefox.tensor.manager.data.AssetDl
import com.orangefox.tensor.manager.data.ReleaseAsset
import com.orangefox.tensor.manager.data.ReleaseInfo
import com.orangefox.tensor.manager.R

@Composable
internal fun ReleaseAssetsSection(
    info: ReleaseInfo,
    isHighlighted: Boolean,
    downloads: Map<String, AssetDl>,
    onDownload: (ReleaseAsset) -> Unit,
    onDeleteAsset: (ReleaseAsset) -> Unit,
    onCopyToDownloads: (ReleaseAsset) -> Unit,
    onInstallAsset: (ReleaseAsset) -> Unit,
    onCancelDownload: (ReleaseAsset) -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        },
        animationSpec = tween(260),
        label = "AssetsHighlightBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isHighlighted) 2.dp else 1.dp,
        animationSpec = tween(260),
        label = "AssetsHighlightBorderWidth",
    )
    val bgColor by animateColorAsState(
        targetValue = if (isHighlighted) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(260),
        label = "AssetsHighlightBg",
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(18.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.releases_files_title, info.assets.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (info.assets.isEmpty()) {
                Text(
                    text = stringResource(R.string.releases_no_files),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    info.assets.forEachIndexed { index, asset ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            )
                        }
                        AssetRow(
                            asset = asset,
                            state = downloads["${info.tag}/${asset.name}"],
                            onDownload = { onDownload(asset) },
                            onDelete = { onDeleteAsset(asset) },
                            onCopyToDownloads = { onCopyToDownloads(asset) },
                            onInstall = { onInstallAsset(asset) },
                            onCancelDownload = { onCancelDownload(asset) },
                            isManagerApk = isManagerApkAsset(asset.name),
                        )
                    }
                }
            }
        }
    }
}

