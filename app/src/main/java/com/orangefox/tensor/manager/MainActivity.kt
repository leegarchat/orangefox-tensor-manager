package com.orangefox.tensor.manager

import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.ui.OrangeFoxTheme
import com.orangefox.tensor.manager.ui.BottomBlurBar
import com.orangefox.tensor.manager.ui.edgeOutline
import com.orangefox.tensor.manager.ui.TopBarBlurOverlay
import com.orangefox.tensor.manager.ui.TopStatusVeil
import com.orangefox.tensor.manager.ui.recordLayer
import com.orangefox.tensor.manager.ui.rememberGraphicsLayerRecordingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import com.orangefox.tensor.manager.data.DocsSessionCache
import com.orangefox.tensor.manager.data.GithubAuth
import com.orangefox.tensor.manager.data.ReleaseCheckWorker
import com.orangefox.tensor.manager.data.RootInstallerEngine
import com.orangefox.tensor.manager.home.HomeChipsState
import com.orangefox.tensor.manager.home.HomeNavState
import com.orangefox.tensor.manager.home.HomeScreen
import com.orangefox.tensor.manager.install.InstallScreen
import com.orangefox.tensor.manager.install.InstallTargetRequest
import com.orangefox.tensor.manager.data.RecoveryStateCache
import com.orangefox.tensor.manager.data.AssetDl
import com.orangefox.tensor.manager.data.ReleasesSessionCache
import com.orangefox.tensor.manager.releases.FloatingFilesButtonState
import com.orangefox.tensor.manager.releases.ManagerUpdateHost
import com.orangefox.tensor.manager.releases.ReleasesScreen
import com.orangefox.tensor.manager.releases.isManagerApkAsset
import com.orangefox.tensor.manager.releases.rememberManagerUpdateController
import com.orangefox.tensor.manager.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { AppPrefs(context) }
            var blurRadius by remember { mutableFloatStateOf(prefs.blurRadius) }
            var themeMode by remember { mutableStateOf(ThemeMode.fromOrdinal(prefs.themeMode)) }
            var showBannerInCollapsed by remember { mutableStateOf(prefs.showBannerInCollapsed) }
            var collapseH2AsSpoilers by remember { mutableStateOf(prefs.collapseH2AsSpoilers) }
            var collapseH2Home by remember { mutableStateOf(prefs.collapseH2Home) }
            var releaseNotifyMode by remember { mutableStateOf(ReleaseNotifyMode.fromOrdinal(prefs.releaseNotifyMode)) }
            var dateFormatMode by remember { mutableStateOf(DateFormatMode.fromName(prefs.dateFormatMode)) }
            var cacheImages by remember { mutableStateOf(prefs.cacheImages) }
            var forceGithubToken by remember { mutableStateOf(prefs.forceGithubToken) }
            val notifPermLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            OrangeFoxTheme(themeMode = themeMode) {
                Root(
                    blurRadius = blurRadius,
                    onBlurRadiusChange = {
                        blurRadius = it
                        prefs.blurRadius = it
                    },
                    themeMode = themeMode,
                    onThemeModeChange = {
                        themeMode = it
                        prefs.themeMode = it.ordinal
                    },
                    showBannerInCollapsed = showBannerInCollapsed,
                    onShowBannerInCollapsedChange = {
                        showBannerInCollapsed = it
                        prefs.showBannerInCollapsed = it
                    },
                    collapseH2AsSpoilers = collapseH2AsSpoilers,
                    onCollapseH2AsSpoilersChange = {
                        collapseH2AsSpoilers = it
                        prefs.collapseH2AsSpoilers = it
                    },
                    collapseH2Home = collapseH2Home,
                    onCollapseH2HomeChange = {
                        collapseH2Home = it
                        prefs.collapseH2Home = it
                    },
                    forceGithubToken = forceGithubToken,
                    onForceGithubTokenChange = {
                        forceGithubToken = it
                        prefs.forceGithubToken = it
                    },
                    cacheImages = cacheImages,
                    onCacheImagesChange = {
                        cacheImages = it
                        prefs.cacheImages = it
                    },
                    releaseNotifyMode = releaseNotifyMode,
                    onReleaseNotifyModeChange = {
                        releaseNotifyMode = it
                        prefs.releaseNotifyMode = it.ordinal
                        if (it != ReleaseNotifyMode.None && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        ReleaseCheckWorker.schedule(context, it)
                    },
                    dateFormatMode = dateFormatMode,
                    onDateFormatModeChange = {
                        dateFormatMode = it
                        prefs.dateFormatMode = it.name
                    },
                )
            }
        }
    }
}


private enum class Tab(val label: Int, val icon: Int) {
    Home(R.string.tab_home, R.drawable.ic_home),
    Releases(R.string.tab_releases, R.drawable.ic_releases),
    Install(R.string.tab_install, R.drawable.ic_bolt),
    Settings(R.string.tab_settings, R.drawable.ic_settings),
}

@Composable
fun Root(
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docsPrefs = remember(context) { AppPrefs(context) }
    var tab by rememberSaveable {
        mutableStateOf(
            try {
                Tab.valueOf(docsPrefs.lastTab)
            } catch (_: IllegalArgumentException) {
                Tab.Releases
            }
        )
    }
    LaunchedEffect(tab) {
        docsPrefs.lastTab = tab.name
    }
    var externalLaunchRequest by remember { mutableStateOf<InstallTargetRequest?>(null) }
    var installBusy by remember { mutableStateOf(false) }
    // The installer submenu lives in Root so it survives tab switches.
    var installSubmenuTarget by remember { mutableStateOf<InstallTargetRequest?>(null) }
    var filesEpoch by remember { mutableStateOf(0) }
    LaunchedEffect(forceGithubToken) {
        GithubAuth.forceToken = forceGithubToken && BuildConfig.DEBUG
    }
    var focusReleaseTag by rememberSaveable { mutableStateOf<String?>(null) }
    val homeNav = remember { HomeNavState() }
    val homeChips = remember { HomeChipsState() }
    val homeChipsBarH = 60.dp
    val blurState = rememberGraphicsLayerRecordingState()
    val density = LocalDensity.current
    val statusTop = remember(density) {
        val res = context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        with(density) { (if (id > 0) res.getDimensionPixelSize(id) else 0).toDp() }
    }
    val topBarH = 64.dp + statusTop
    val contentBg = MaterialTheme.colorScheme.surfaceContainer
    val floatingFilesState = remember { FloatingFilesButtonState() }
    LaunchedEffect(Unit) {
        RecoveryStateCache.ensureLoadedOnStartup(context, scope)
    }

    // One-time root check at startup: without root the manager cannot patch vendor_boot.
    var rootMissingNotice by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!docsPrefs.rootCheckDone) {
            docsPrefs.rootCheckDone = true
            if (!RootInstallerEngine.checkRootAccess()) {
                rootMissingNotice = true
            }
        }
    }
    if (rootMissingNotice) {
        AlertDialog(
            onDismissRequest = { rootMissingNotice = false },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(22.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            title = {
                Text(
                    text = stringResource(R.string.root_missing_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.root_missing_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = { rootMissingNotice = false },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.edgeOutline()),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.action_ok),
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }


    // Manager self-update (offer state, effects, dialogs): releases/ManagerUpdateFlow.kt.
    val releasesSnapshot = ReleasesSessionCache.releases
    val managerUpdate = rememberManagerUpdateController(docsPrefs)
    ManagerUpdateHost(controller = managerUpdate, releasesSnapshot = releasesSnapshot)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = contentBg,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .recordLayer(
                        state = blurState,
                        backgroundColor = contentBg,
                        blurRadius = blurRadius,
                        topEdgeFadeHeight = statusTop + 12.dp,
                        bottomEdgeFadeHeight = 20.dp,
                    )
                    .background(contentBg)
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> (fullWidth * 0.18f * direction).toInt() }
                        ) + fadeIn(
                            animationSpec = tween(260)
                        ) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                        )).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(260, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> (-fullWidth * 0.18f * direction).toInt() }
                            ) + fadeOut(
                                animationSpec = tween(200)
                            ) + scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(260, easing = FastOutSlowInEasing)
                            )
                        ).using(SizeTransform(clip = false))
                    },
                    label = "ScreenTransition",
                ) { currentTab ->
                    when (currentTab) {
                        Tab.Home -> HomeScreen(
                            topInset = topBarH + 8.dp,
                            chipsBarHeight = homeChipsBarH,
                            collapseH2Home = collapseH2Home,
                            cacheImages = cacheImages,
                            nav = homeNav,
                            chips = homeChips,
                        )
                        Tab.Releases -> ReleasesScreen(
                            topInset = topBarH + 8.dp,
                            floatingFilesState = floatingFilesState,
                            showBannerInCollapsed = showBannerInCollapsed,
                            collapseH2AsSpoilers = collapseH2AsSpoilers,
                            cacheImages = cacheImages,
                            dateFormatMode = dateFormatMode,
                            focusReleaseTag = focusReleaseTag,
                            onFocusReleaseHandled = { focusReleaseTag = null },
                            onInstallAsset = { rel, asset ->
                                if (isManagerApkAsset(asset.name)) {
                                    val key = "${rel.tag}/${asset.name}"
                                    val done = ReleasesSessionCache.downloads[key] as? AssetDl.Done
                                        ?: return@ReleasesScreen
                                    val file = File(done.path)
                                    if (!file.isFile) return@ReleasesScreen
                                    managerUpdate.startManagerInstall(file)
                                    return@ReleasesScreen
                                }
                                val key = "${rel.tag}/${asset.name}"
                                val done = ReleasesSessionCache.downloads[key] as? AssetDl.Done
                                    ?: return@ReleasesScreen
                                val file = File(done.path)
                                val title = "${rel.name} (${asset.name})"
                                externalLaunchRequest = if (asset.isAioZip) {
                                    InstallTargetRequest.FromAioFile(title, file)
                                } else {
                                    InstallTargetRequest.FromRawImgFile(title, file)
                                }
                                tab = Tab.Install
                            },
                        )
                        Tab.Install -> InstallScreen(
                            topInset = topBarH + 8.dp,
                            prefs = docsPrefs,
                            externalLaunchRequest = externalLaunchRequest,
                            onConsumeExternalRequest = { externalLaunchRequest = null },
                            onOpenReleaseOnReleasesTab = { tag ->
                                tab = Tab.Releases
                                focusReleaseTag = tag
                            },
                            onStartDownload = { rel, asset -> ReleasesSessionCache.startDownload(context, rel, asset) },
                            onDeleteCachedAsset = { rel, asset -> ReleasesSessionCache.deleteCachedAsset(context, rel, asset, showToast = false) },
                            onCopyCachedAssetToDownloads = { rel, asset -> ReleasesSessionCache.copyCachedAssetToDownloads(context, rel, asset) },
                            onInstallBusyChange = { installBusy = it },
                            submenuTarget = installSubmenuTarget,
                            onSubmenuTargetChange = { installSubmenuTarget = it },
                            filesEpoch = filesEpoch,
                        )
                        Tab.Settings -> SettingsScreen(
                            topInset = topBarH + 8.dp,
                            blurRadius = blurRadius,
                            onBlurRadiusChange = onBlurRadiusChange,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            showBannerInCollapsed = showBannerInCollapsed,
                            onShowBannerInCollapsedChange = onShowBannerInCollapsedChange,
                            collapseH2AsSpoilers = collapseH2AsSpoilers,
                            onCollapseH2AsSpoilersChange = onCollapseH2AsSpoilersChange,
                            collapseH2Home = collapseH2Home,
                            onCollapseH2HomeChange = onCollapseH2HomeChange,
                            forceGithubToken = forceGithubToken,
                            onForceGithubTokenChange = onForceGithubTokenChange,
                            cacheImages = cacheImages,
                            onCacheImagesChange = onCacheImagesChange,
                            releaseNotifyMode = releaseNotifyMode,
                            onReleaseNotifyModeChange = onReleaseNotifyModeChange,
                            dateFormatMode = dateFormatMode,
                            onDateFormatModeChange = onDateFormatModeChange,
                            onWipeAllFiles = {
                                scope.launch(Dispatchers.IO) {
                                    // Was the prologue of wipeAllAppFiles: cancel downloads,
                                    // delete downloaded files, clear their states — then wipe files.
                                    ReleasesSessionCache.purgeAllDownloadsAndDeleteFiles()
                                    RootInstallerEngine.wipeAllAppFiles(context)
                                    withContext(Dispatchers.Main) {
                                        filesEpoch++
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.wipe_done_toast),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topBarH),
                contentAlignment = Alignment.CenterStart,
            ) {
                TopBarBlurOverlay(
                    modifier = Modifier.fillMaxWidth(),
                    topBarHeight = topBarH,
                    blurState = blurState,
                    blurRadius = blurRadius,
                )
                TopStatusVeil(
                    backgroundColor = contentBg,
                    veilHeight = statusTop + 14.dp,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusTop, start = 24.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            (slideInVertically { h -> h / 2 } + fadeIn(tween(220)))
                                .togetherWith(slideOutVertically { h -> -h / 2 } + fadeOut(tween(160)))
                        },
                        modifier = Modifier.weight(1f),
                        label = "TopBarTitleTransition",
                    ) { currentTab ->
                        Text(
                            text = stringResource(currentTab.label),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    AnimatedVisibility(
                        visible = tab == Tab.Releases || tab == Tab.Home,
                        enter = fadeIn(tween(180)) + scaleIn(tween(180)),
                        exit = fadeOut(tween(140)) + scaleOut(tween(140)),
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "RefreshSpin")
                        val spinAngle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "RefreshSpinAngle",
                        )
                        val refreshing = if (tab == Tab.Home) {
                            DocsSessionCache.isRefreshing
                        } else {
                            ReleasesSessionCache.isRefreshing
                        }
                        IconButton(
                            onClick = {
                                if (tab == Tab.Home) {
                                    DocsSessionCache.refresh(scope, context, context.cacheDir, docsPrefs, force = true)
                                } else {
                                    ReleasesSessionCache.refresh(scope, context, context.cacheDir, force = true)
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.rotate(if (refreshing) spinAngle else 0f),
                            )
                        }
                    }
                }
            }

            // Fixed Home chips blur strip — a header continuation, outside the recorded layer (no loop).
            if (tab == Tab.Home && homeChips.docs.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = topBarH)
                        .height(homeChipsBarH),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    TopBarBlurOverlay(
                        modifier = Modifier.fillMaxWidth(),
                        topBarHeight = homeChipsBarH,
                        blurState = blurState,
                        blurRadius = blurRadius,
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(homeChips.docs, key = { it.id }) { doc ->
                            FilterChip(
                                selected = doc.id == homeChips.currentId,
                                onClick = { homeChips.onSelect?.invoke(doc.id) },
                                label = { Text(doc.title) },
                            )
                        }
                    }
                }
            }

            // Floating "To releases" blur button — a recordLayer sibling (outside recorded layer C, no loop!)
            AnimatedVisibility(
                visible = tab == Tab.Releases && floatingFilesState.targetRelease != null,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 } + scaleIn(initialScale = 0.92f),
                exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 3 } + scaleOut(targetScale = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 106.dp),
            ) {
                val floatingRel = floatingFilesState.targetRelease ?: floatingFilesState.lastTargetRelease
                if (floatingRel != null) {
                    val haptics = LocalHapticFeedback.current
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.93f else 1f,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                        ),
                        label = "GoToFilesScale",
                    )
                    val pillTint by animateColorAsState(
                        targetValue = if (isPressed || floatingFilesState.isScrolling) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
                        },
                        animationSpec = tween(150),
                        label = "GoToFilesTint",
                    )

                    BottomBlurBar(
                        blurState = blurState,
                        blurRadius = blurRadius,
                        scale = pressScale,
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .background(pillTint)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    floatingFilesState.onClick?.invoke(floatingRel)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            AnimatedContent(
                                targetState = floatingRel.name,
                                transitionSpec = {
                                    (fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 3 })
                                        .togetherWith(fadeOut(tween(130)) + slideOutVertically(tween(160)) { -it / 3 })
                                        .using(
                                            SizeTransform(
                                                clip = false,
                                                sizeAnimationSpec = { _, _ ->
                                                    spring(
                                                        stiffness = Spring.StiffnessMediumLow,
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                    )
                                                }
                                            )
                                        )
                                },
                                label = "ReleaseNameButtonMorph",
                            ) { releaseName ->
                                Text(
                                    text = stringResource(R.string.action_go_to_files, releaseName),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            // Round Home navigation buttons: Back (left) and Home (right),
            // both blurred, both recordLayer siblings (outside recorded layer C, no loop!)
            val homeHaptics = LocalHapticFeedback.current
            AnimatedVisibility(
                visible = tab == Tab.Home && homeNav.visible && homeNav.canGoBack,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.9f),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 106.dp),
            ) {
                BottomBlurBar(
                    blurState = blurState,
                    blurRadius = blurRadius,
                    shape = CircleShape,
                ) {
                    Box(
                        modifier = Modifier.size(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = {
                            homeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            homeNav.onBack?.invoke()
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_back),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = tab == Tab.Home && homeNav.visible,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.9f),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 106.dp),
            ) {
                BottomBlurBar(
                    blurState = blurState,
                    blurRadius = blurRadius,
                    shape = CircleShape,
                ) {
                    Box(
                        modifier = Modifier.size(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = {
                            homeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            homeNav.onHome?.invoke()
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_home),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            val dockHaptics = LocalHapticFeedback.current
            var isDockPressed by remember { mutableStateOf(false) }
            var pressedTab by remember { mutableStateOf<Tab?>(null) }
            val tabBounds = remember { mutableStateMapOf<Tab, ClosedFloatingPointRange<Float>>() }

            val dockScale by animateFloatAsState(
                targetValue = if (isDockPressed) 1.032f else 1f,
                animationSpec = spring(
                    stiffness = 260f,
                    dampingRatio = 0.78f,
                ),
                label = "DockBounceScale",
            )

            // Full-width tap catcher for the bottom strip (bar + 24.dp inset down to
            // the screen edge): taps beside/below the bar die here (up is consumed,
            // the LazyColumn underneath never sees them). Scrolls are untouched —
            // once slop/a consumer claims the gesture we break WITHOUT consuming.
            // No visuals, no size changes: only a gesture modifier on the container.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                    ?: break
                                // Scroll/slop claimed elsewhere: let the list scroll as usual.
                                if (change.isConsumed) break
                                if (!change.pressed) {
                                    // Pure tap release inside the strip: swallow it.
                                    change.consume()
                                    break
                                }
                                // Finger still down: wait, consuming nothing
                                // (dock scrub + list scroll stay alive).
                            }
                        }
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                BottomBlurBar(
                    modifier = Modifier.padding(bottom = 24.dp),
                    blurState = blurState,
                    blurRadius = blurRadius,
                    scale = dockScale,
                    shape = CircleShape,
                ) {
                fun resolveTabAtX(x: Float): Tab? {
                    tabBounds.entries.firstOrNull { x in it.value }?.let { return it.key }
                    return tabBounds.entries.minByOrNull { entry ->
                        val center = (entry.value.start + entry.value.endInclusive) * 0.5f
                        kotlin.math.abs(x - center)
                    }?.key
                }

                fun selectTabFromGesture(targetTab: Tab) {
                    if (targetTab == tab) return
                    if (installBusy) {
                        dockHaptics.performHapticFeedback(HapticFeedbackType.Reject)
                        Toast.makeText(
                            context,
                            context.getString(R.string.install_busy_cannot_switch),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        dockHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tab = targetTab
                    }
                }

                // Concentric 6.dp inset on all sides:
                // capsule height is 46.dp, corner radius always exactly follows the outer contour.
                Row(
                    modifier = Modifier
                        .padding(6.dp)
                        .pointerInput(installBusy) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                isDockPressed = true
                                val initialHit = resolveTabAtX(down.position.x)
                                pressedTab = initialHit
                                if (initialHit != null && initialHit != tab) {
                                    selectTabFromGesture(initialHit)
                                } else {
                                    dockHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                }

                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: event.changes.firstOrNull()
                                        if (change == null || !change.pressed) break
                                        val hit = resolveTabAtX(change.position.x)
                                        pressedTab = hit
                                        if (hit != null && hit != tab) {
                                            selectTabFromGesture(hit)
                                        }
                                        change.consume()
                                    }
                                } finally {
                                    isDockPressed = false
                                    pressedTab = null
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tab.entries.forEach { t ->
                        val selected = t == tab
                        val isFingerHere = pressedTab == t && isDockPressed

                        val itemScale by animateFloatAsState(
                            targetValue = when {
                                isFingerHere && selected -> 0.96f
                                isFingerHere -> 1.05f
                                selected && isDockPressed -> 1.02f
                                else -> 1f
                            },
                            animationSpec = spring(
                                stiffness = 280f,
                                dampingRatio = 0.78f,
                            ),
                            label = "TabItemBounce",
                        )

                        val pillBg by animateColorAsState(
                            targetValue = when {
                                selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                isFingerHere -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f)
                                else -> Color.Transparent
                            },
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            label = "TabBgColor",
                        )
                        val contentTint by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(180),
                            label = "TabContentTint",
                        )
                        val horizontalPad by animateDpAsState(
                            targetValue = if (selected) 16.dp else 14.dp,
                            animationSpec = spring(
                                stiffness = 300f,
                                dampingRatio = 0.85f,
                            ),
                            label = "TabHorizontalPadding",
                        )

                        Row(
                            modifier = Modifier
                                .height(46.dp)
                                .onPlaced { coords ->
                                    val x = coords.positionInParent().x
                                    tabBounds[t] = x..(x + coords.size.width)
                                }
                                .graphicsLayer {
                                    scaleX = itemScale
                                    scaleY = itemScale
                                }
                                .clip(CircleShape)
                                .background(pillBg)
                                .then(
                                    if (selected) {
                                        Modifier.border(1.dp, pillBg.edgeOutline(), CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = horizontalPad),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(t.icon),
                                contentDescription = stringResource(t.label),
                                tint = contentTint,
                                modifier = Modifier.size(20.dp),
                            )
                            AnimatedVisibility(
                                visible = selected,
                                enter = fadeIn(tween(220)) + expandHorizontally(
                                    animationSpec = spring(
                                        stiffness = 320f,
                                        dampingRatio = 0.85f,
                                    ),
                                    expandFrom = Alignment.Start,
                                ),
                                exit = fadeOut(tween(160)) + shrinkHorizontally(
                                    animationSpec = spring(
                                        stiffness = 320f,
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                    ),
                                    shrinkTowards = Alignment.Start,
                                ),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(t.label),
                                        color = contentTint,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
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
}
