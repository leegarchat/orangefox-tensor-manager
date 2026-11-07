package com.orangefox.tensor.manager.releases

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.ui.edgeOutline
import com.orangefox.tensor.manager.ui.MarkdownBody
import com.orangefox.tensor.manager.ui.NetworkBannerImage
import com.orangefox.tensor.manager.data.AssetDl
import com.orangefox.tensor.manager.data.ReleaseAsset
import com.orangefox.tensor.manager.data.ReleaseInfo
import com.orangefox.tensor.manager.DateFormatMode

@Composable
internal fun ReleaseCard(
    info: ReleaseInfo,
    isExpanded: Boolean,
    isAssetsHighlighted: Boolean,
    isLatestStable: Boolean = false,
    showBannerInCollapsed: Boolean,
    collapseH2AsSpoilers: Boolean,
    cacheImages: Boolean,
    dateFormatMode: DateFormatMode,
    onToggle: () -> Unit,
    onCardBoundsChanged: (Rect) -> Unit,
    onAnchorBoundsChanged: (Rect) -> Unit,
    downloads: Map<String, AssetDl>,
    onDownload: (ReleaseAsset) -> Unit,
    onDeleteAsset: (ReleaseAsset) -> Unit,
    onCopyToDownloads: (ReleaseAsset) -> Unit,
    onInstallAsset: (ReleaseAsset) -> Unit,
    onCancelDownload: (ReleaseAsset) -> Unit,
) {
    // In the expanded state the card takes almost the full screen width (4.dp margin),
    // giving nested blocks more room. The animation is synced with the radius morph.
    val horizontalPad by animateDpAsState(
        targetValue = if (isExpanded) 4.dp else 8.dp,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "CardHorizontalPad",
    )
    val cardHaptics = LocalHapticFeedback.current
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 18.dp else 22.dp,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "CardCornerRadius",
    )
    // Subtle veil of the expanded banner-based header. Text does not fight the background:
    // the accent is applied at 0.22 alpha over surfaceContainerLow and reads well in both themes.
    // Without a banner — a delicate primaryContainer fallback of the same strength.
    val cardBaseColor = MaterialTheme.colorScheme.surfaceContainerLow
    val bannerAccent = rememberReleaseBannerAccent(
        tag = info.tag,
        bannerUrl = info.bannerUrl,
        cacheImages = cacheImages,
        isExpanded = isExpanded,
    )
    val expandedHeaderTarget = if (bannerAccent != null) {
        bannerAccent.copy(alpha = 0.22f).compositeOver(cardBaseColor)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f).compositeOver(cardBaseColor)
    }
    val headerAccentBg by animateColorAsState(
        targetValue = if (isExpanded) expandedHeaderTarget else cardBaseColor,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "ReleaseHeaderAccent",
    )

    val morphSpring = spring<Rect>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy,
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPad)
            .onGloballyPositioned { coords ->
                onCardBoundsChanged(coords.boundsInRoot())
            }
            .border(
                1.dp,
                MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(),
                RoundedCornerShape(cornerRadius),
            ),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        SharedTransitionLayout {
            // Banner and header swap places ONLY when the banner is shown in the collapsed card.
            // Without a banner the header is static above AnimatedContent: no sharedBounds
            // and no text cross-fade — only the body animates (divider + content).
            val canMorphBanner = info.bannerUrl != null && showBannerInCollapsed
            if (!canMorphBanner) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ReleaseHeaderRow(
                        info = info,
                        isLatestStable = isLatestStable,
                        isExpanded = isExpanded,
                        dateFormatMode = dateFormatMode,
                        modifier = Modifier
                            .background(headerAccentBg)
                            .clickable {
                                cardHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onToggle()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                    AnimatedContent(
                        targetState = isExpanded,
                        transitionSpec = {
                            (expandVertically(
                                animationSpec = tween(240, easing = FastOutSlowInEasing)
                            ) + fadeIn(
                                animationSpec = tween(180, easing = FastOutSlowInEasing)
                            )) togetherWith (shrinkVertically(
                                animationSpec = tween(200, easing = FastOutSlowInEasing)
                            ) + fadeOut(
                                animationSpec = tween(140, easing = FastOutSlowInEasing)
                            )) using SizeTransform(
                                clip = true,
                                sizeAnimationSpec = { _, _ ->
                                    spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                    )
                                }
                            )
                        },
                        label = "ReleaseCardBody_${info.tag}",
                    ) { expandedState ->
                        if (expandedState) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (info.bannerUrl != null) {
                                        NetworkBannerImage(
                                            url = info.bannerUrl,
                                            cacheImagesEnabled = cacheImages,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable {
                                                    cardHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                    onToggle()
                                                },
                                        )
                                    }

                                    OpenGitHubReleaseButton(tag = info.tag)

                                    if (info.cleanBody.isNotBlank()) {
                                        MarkdownBody(
                                            md = info.cleanBody,
                                            collapseH2Sections = collapseH2AsSpoilers,
                                            cacheImagesEnabled = cacheImages,
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { coords ->
                                                onAnchorBoundsChanged(coords.boundsInRoot())
                                            },
                                    ) {
                                        ReleaseAssetsSection(
                                            info = info,
                                            isHighlighted = isAssetsHighlighted,
                                            downloads = downloads,
                                            onDownload = onDownload,
                                            onDeleteAsset = onDeleteAsset,
                                            onCopyToDownloads = onCopyToDownloads,
                                            onInstallAsset = onInstallAsset,
                                            onCancelDownload = onCancelDownload,
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            } else {
            AnimatedContent(
                targetState = isExpanded,
                transitionSpec = {
                    fadeIn(
                        animationSpec = tween(220, delayMillis = 40, easing = FastOutSlowInEasing)
                    ) togetherWith fadeOut(
                        animationSpec = tween(140, easing = FastOutSlowInEasing)
                    ) using SizeTransform(
                        clip = true,
                        sizeAnimationSpec = { _, _ ->
                            spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            )
                        }
                    )
                },
                label = "ReleaseCardMorph_${info.tag}",
            ) { expandedState ->
                if (!expandedState) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                cardHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onToggle()
                            },
                    ) {
                        // The header always morphs via sharedBounds (shift), not cross-fade,
                        // so on expand/collapse the text travels by offset instead of fading.
                        if (info.bannerUrl != null && showBannerInCollapsed) {
                            NetworkBannerImage(
                                url = info.bannerUrl,
                                cacheImagesEnabled = cacheImages,
                                modifier = Modifier
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "banner_${info.tag}"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = { _, _ -> morphSpring },
                                        clipInOverlayDuringTransition = OverlayClip(
                                            RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                                        ),
                                    )
                                    .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)),
                            )
                        }
                        ReleaseHeaderRow(
                            info = info,
                            isLatestStable = isLatestStable,
                            isExpanded = false,
                            dateFormatMode = dateFormatMode,
                            modifier = Modifier
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "header_${info.tag}"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    boundsTransform = { _, _ -> morphSpring },
                                    // RemeasureToBounds fully eliminates zooming and font stretching
                                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                )
                                .background(headerAccentBg)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ReleaseHeaderRow(
                            info = info,
                            isLatestStable = isLatestStable,
                            isExpanded = true,
                            dateFormatMode = dateFormatMode,
                            modifier = Modifier
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "header_${info.tag}"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    boundsTransform = { _, _ -> morphSpring },
                                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                )
                                .background(headerAccentBg)
                                .clickable {
                                    cardHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onToggle()
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (info.bannerUrl != null) {
                                NetworkBannerImage(
                                    url = info.bannerUrl,
                                    cacheImagesEnabled = cacheImages,
                                    modifier = Modifier
                                        .then(
                                            if (canMorphBanner) {
                                                Modifier.sharedBounds(
                                                    sharedContentState = rememberSharedContentState(key = "banner_${info.tag}"),
                                                    animatedVisibilityScope = this@AnimatedContent,
                                                    boundsTransform = { _, _ -> morphSpring },
                                                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(14.dp)),
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            cardHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            onToggle()
                                        },
                                )
                            }

                            OpenGitHubReleaseButton(tag = info.tag)

                            if (info.cleanBody.isNotBlank()) {
                                MarkdownBody(
                                    md = info.cleanBody,
                                    collapseH2Sections = collapseH2AsSpoilers,
                                    cacheImagesEnabled = cacheImages,
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coords ->
                                        onAnchorBoundsChanged(coords.boundsInRoot())
                                    },
                            ) {
                                ReleaseAssetsSection(
                                    info = info,
                                    isHighlighted = isAssetsHighlighted,
                                    downloads = downloads,
                                    onDownload = onDownload,
                                    onDeleteAsset = onDeleteAsset,
                                    onCopyToDownloads = onCopyToDownloads,
                                    onInstallAsset = onInstallAsset,
                                    onCancelDownload = onCancelDownload,
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
