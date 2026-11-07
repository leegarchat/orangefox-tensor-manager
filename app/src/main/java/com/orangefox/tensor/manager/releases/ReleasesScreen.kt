package com.orangefox.tensor.manager.releases

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.ui.ExpressiveSegmentedRow
import com.orangefox.tensor.manager.ui.edgeOutline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.orangefox.tensor.manager.AppPrefs
import com.orangefox.tensor.manager.DateFormatMode
import com.orangefox.tensor.manager.R
import com.orangefox.tensor.manager.data.AssetDl
import com.orangefox.tensor.manager.data.ReleaseAsset
import com.orangefox.tensor.manager.data.ReleaseFilterMode
import com.orangefox.tensor.manager.data.ReleaseInfo
import com.orangefox.tensor.manager.data.ReleasesSessionCache
import com.orangefox.tensor.manager.ui.dialogs.ConfirmDeleteDialog

@androidx.compose.runtime.Stable
class FloatingFilesButtonState {
    var targetRelease by mutableStateOf<ReleaseInfo?>(null)
    var lastTargetRelease by mutableStateOf<ReleaseInfo?>(null)
    var isScrolling by mutableStateOf(false)
    var onClick: ((ReleaseInfo) -> Unit)? = null
}

private data class CardViewportMetrics(
    val cardBoundsInRoot: Rect = Rect.Zero,
    val anchorBoundsInRoot: Rect = Rect.Zero,
    val anchorHeightPx: Float = 0f,
)

@Composable
fun ReleasesScreen(
    topInset: Dp,
    floatingFilesState: FloatingFilesButtonState,
    showBannerInCollapsed: Boolean,
    collapseH2AsSpoilers: Boolean,
    cacheImages: Boolean,
    dateFormatMode: DateFormatMode,
    focusReleaseTag: String?,
    onFocusReleaseHandled: () -> Unit,
    onInstallAsset: (ReleaseInfo, ReleaseAsset) -> Unit,
) {
    val context = LocalContext.current
    val cacheDir = context.cacheDir
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val prefs = remember(context) { AppPrefs(context) }

    val releases = ReleasesSessionCache.releases
    val listError = ReleasesSessionCache.listError
    val expanded = ReleasesSessionCache.expanded
    val downloads = ReleasesSessionCache.downloads
    val listState = rememberLazyListState()

    var filterMode by remember {
        mutableStateOf(ReleaseFilterMode.fromOrdinal(prefs.releaseFilterMode))
    }
    val filteredReleases = remember(releases, filterMode) {
        releases?.filter { rel ->
            when (filterMode) {
                ReleaseFilterMode.Main -> !rel.prerelease
                ReleaseFilterMode.Prerelease -> rel.prerelease
                ReleaseFilterMode.All -> true
            }
        }
    }
    // Latest stable = first non-prerelease in API order (newest first) of the FULL list.
    val latestStableTag = remember(releases) {
        releases?.firstOrNull { !it.prerelease }?.tag
    }

    var viewportTopInRoot by remember { mutableFloatStateOf(0f) }
    var viewportBottomInRoot by remember { mutableFloatStateOf(0f) }
    val bottomDockClearancePx = with(density) { 94.dp.toPx() }
    val topInsetPx = with(density) { topInset.toPx() }
    val cardMetrics = remember { mutableStateMapOf<String, CardViewportMetrics>() }
    var highlightedReleaseTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ReleasesSessionCache.ensureLoaded(scope, context, cacheDir)
    }

    LaunchedEffect(focusReleaseTag, releases) {
        val tag = focusReleaseTag ?: return@LaunchedEffect
        val all = releases ?: return@LaunchedEffect
        all.firstOrNull { it.tag == tag } ?: return@LaunchedEffect
        val visibleAtEntry = when (filterMode) {
            ReleaseFilterMode.Main -> all.filter { !it.prerelease }
            ReleaseFilterMode.Prerelease -> all.filter { it.prerelease }
            ReleaseFilterMode.All -> all
        }
        // Target is not visible under the current filter (either direction) — fall back to All.
        val visibleList = if (visibleAtEntry.none { it.tag == tag }) {
            filterMode = ReleaseFilterMode.All
            prefs.releaseFilterMode = ReleaseFilterMode.All.ordinal
            all
        } else {
            visibleAtEntry
        }
        expanded[tag] = true
        val idx = visibleList.indexOfFirst { it.tag == tag }
        if (idx >= 0) {
            listState.animateScrollToItem((idx + 1).coerceAtLeast(0))
        }
        highlightedReleaseTag = tag
        onFocusReleaseHandled()
        delay(1200)
        if (highlightedReleaseTag == tag) {
            highlightedReleaseTag = null
        }
    }

    var scrollingToFiles by remember { mutableStateOf(false) }
    var pendingDeleteAsset by remember { mutableStateOf<Pair<ReleaseInfo, ReleaseAsset>?>(null) }
    val haptics = LocalHapticFeedback.current

    val targetRelease: ReleaseInfo? by remember(filteredReleases, topInsetPx) {
        derivedStateOf {
            val list = filteredReleases ?: return@derivedStateOf null
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty() || viewportBottomInRoot <= 0f) return@derivedStateOf null

            val usableTop = viewportTopInRoot + topInsetPx
            val usableBottom = viewportBottomInRoot
            val focusLineY = usableTop + (usableBottom - usableTop) * 0.35f

            visibleItems.firstNotNullOfOrNull { itemInfo ->
                val itemTag = itemInfo.key as? String ?: return@firstNotNullOfOrNull null
                val rel = list.firstOrNull { it.tag == itemTag } ?: return@firstNotNullOfOrNull null
                if (expanded[rel.tag] != true || rel.assets.isEmpty()) return@firstNotNullOfOrNull null

                val cardTopY = viewportTopInRoot + topInsetPx + itemInfo.offset
                val cardBottomY = cardTopY + itemInfo.size
                val anchorH = cardMetrics[rel.tag]?.anchorHeightPx?.takeIf { it > 0f }
                    ?: with(density) { 175.dp.toPx() }
                val anchorTopY = cardBottomY - anchorH - with(density) { 16.dp.toPx() }

                val isUserReadingThisPost = cardTopY <= focusLineY + 120f && cardBottomY > usableBottom
                val isFilesSectionBelow = anchorTopY > usableBottom - 20f

                if (isUserReadingThisPost && isFilesSectionBelow) rel else null
            }
        }
    }
    val showGoToFiles = targetRelease != null

    fun scrollToAnchorCentered(r: ReleaseInfo) {
        if (scrollingToFiles) return
        scrollingToFiles = true
        scope.launch {
            try {
                val list = filteredReleases ?: return@launch
                if (list.none { it.tag == r.tag }) return@launch

                val usableTop = viewportTopInRoot + topInsetPx
                val usableBottom = viewportBottomInRoot
                val usableCenterY = (usableTop + usableBottom) / 2f

                val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == r.tag }
                if (itemInfo != null) {
                    val anchorH = cardMetrics[r.tag]?.anchorHeightPx?.takeIf { it > 0f }
                        ?: with(density) { 180.dp.toPx() }
                    val bottomPadPx = with(density) { 14.dp.toPx() }
                    val anchorCenterFromCardTop = itemInfo.size - bottomPadPx - (anchorH / 2f)
                    val currentAnchorCenterOnScreen = viewportTopInRoot + topInsetPx + itemInfo.offset + anchorCenterFromCardTop
                    val delta = currentAnchorCenterOnScreen - usableCenterY

                    if (delta > 6f) {
                        listState.animateScrollBy(
                            value = delta,
                            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                        )
                    }
                }

                withFrameNanos { }
                val mFinal = cardMetrics[r.tag]
                if (mFinal != null && mFinal.anchorBoundsInRoot.height > 0f) {
                    val actualCenter = (mFinal.anchorBoundsInRoot.top + mFinal.anchorBoundsInRoot.bottom) / 2f
                    val correction = actualCenter - usableCenterY
                    if (abs(correction) in 4f..600f) {
                        listState.animateScrollBy(
                            value = correction,
                            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                        )
                    }
                }
            } finally {
                scrollingToFiles = false
            }

            highlightedReleaseTag = r.tag
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(850)
            if (highlightedReleaseTag == r.tag) {
                highlightedReleaseTag = null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val b = coords.boundsInRoot()
                viewportTopInRoot = b.top
                viewportBottomInRoot = b.bottom - bottomDockClearancePx
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = topInset,
                bottom = 140.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                listError != null && releases == null -> item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.releases_error, listError))
                        Button(
                            onClick = { ReleasesSessionCache.refresh(scope, context, cacheDir, force = true) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.edgeOutline()),
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
                releases == null -> item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.orangefox.tensor.manager.ui.ExpressiveLinearProgress()
                        Text(stringResource(R.string.releases_loading))
                    }
                }
                releases.isEmpty() -> item {
                    Text(
                        text = stringResource(R.string.releases_empty),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                else -> {
                    item(key = "release_filter_bar") {
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                            ExpressiveSegmentedRow(
                                items = ReleaseFilterMode.entries,
                                selectedItem = filterMode,
                                onSelect = { mode ->
                                    filterMode = mode
                                    prefs.releaseFilterMode = mode.ordinal
                                },
                                labelProvider = { stringResource(it.labelRes) },
                            )
                        }
                    }
                    itemsIndexed(filteredReleases ?: emptyList(), key = { _, it -> it.tag }) { _, r ->
                        val isExp = expanded[r.tag] == true

                        ReleaseCard(
                            info = r,
                            isExpanded = isExp,
                            isAssetsHighlighted = highlightedReleaseTag == r.tag,
                            isLatestStable = r.tag == latestStableTag,
                            showBannerInCollapsed = showBannerInCollapsed,
                            collapseH2AsSpoilers = collapseH2AsSpoilers,
                            cacheImages = cacheImages,
                            dateFormatMode = dateFormatMode,
                            onToggle = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                val next = !isExp
                                expanded[r.tag] = next
                                if (!next) {
                                    cardMetrics.remove(r.tag)
                                }
                            },
                            onCardBoundsChanged = { cardRect ->
                                val prev = cardMetrics[r.tag] ?: CardViewportMetrics()
                                cardMetrics[r.tag] = prev.copy(cardBoundsInRoot = cardRect)
                            },
                            onAnchorBoundsChanged = { anchorRect ->
                                val prev = cardMetrics[r.tag] ?: CardViewportMetrics()
                                cardMetrics[r.tag] = prev.copy(
                                    anchorBoundsInRoot = anchorRect,
                                    anchorHeightPx = if (anchorRect.height > 0f) anchorRect.height else prev.anchorHeightPx,
                                )
                            },
                            downloads = downloads,
                            onCancelDownload = { asset -> ReleasesSessionCache.cancelDownload(r, asset) },
                            onDownload = { asset -> ReleasesSessionCache.startDownload(context, r, asset) },
                            onDeleteAsset = { asset -> pendingDeleteAsset = r to asset },
                            onCopyToDownloads = { asset -> ReleasesSessionCache.copyCachedAssetToDownloads(context, r, asset) },
                            onInstallAsset = { asset -> onInstallAsset(r, asset) },
                        )
                    }
                }
            }
        }

        LaunchedEffect(targetRelease) {
            floatingFilesState.targetRelease = targetRelease
            if (targetRelease != null) {
                floatingFilesState.lastTargetRelease = targetRelease
            }
        }
        LaunchedEffect(scrollingToFiles) {
            floatingFilesState.isScrolling = scrollingToFiles
        }

        pendingDeleteAsset?.let { (rel, asset) ->
            ConfirmDeleteDialog(
                fileName = asset.name.substringAfterLast('/'),
                onConfirm = {
                    pendingDeleteAsset = null
                    ReleasesSessionCache.deleteCachedAsset(context, rel, asset, showToast = true)
                },
                onDismiss = { pendingDeleteAsset = null },
            )
        }
        val currentScrollToAnchor by androidx.compose.runtime.rememberUpdatedState(
            newValue = { rel: ReleaseInfo -> scrollToAnchorCentered(rel) }
        )
        DisposableEffect(Unit) {
            floatingFilesState.onClick = { rel -> currentScrollToAnchor(rel) }
            onDispose {
                floatingFilesState.targetRelease = null
                floatingFilesState.onClick = null
            }
        }
    }
}
