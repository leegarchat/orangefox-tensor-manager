package com.orangefox.manager

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orangefox.manager.ui.BottomBlurBar
import com.orangefox.manager.ui.ExpandableGroupCard
import com.orangefox.manager.ui.ExpressiveSegmentedRow
import com.orangefox.manager.ui.GraphicsLayerRecordingState
import com.orangefox.manager.ui.MarkdownBody
import com.orangefox.manager.ui.NetworkBannerImage
import com.orangefox.manager.ui.SettingSlider
import com.orangefox.manager.ui.TopBarBlurOverlay
import com.orangefox.manager.ui.extractBannerAndCleanMarkdown
import com.orangefox.manager.ui.recordLayer
import com.orangefox.manager.ui.rememberGraphicsLayerRecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

private const val RELEASES_URL =
    "https://api.github.com/repos/leegarchat/twrp_device_google_pixels/releases"

private val http = OkHttpClient()

data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
) {
    val isAioZip: Boolean
        get() = name.lowercase().let { it.contains("aio") && it.endsWith(".zip") }
    val isImgFile: Boolean
        get() = name.lowercase().endsWith(".img")
    val isInstallable: Boolean
        get() = isAioZip || isImgFile
}

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val body: String,
    val bannerUrl: String?,
    val cleanBody: String,
    val assets: List<ReleaseAsset>,
    val prerelease: Boolean = false,
)

sealed interface AssetDl {
    data class Downloading(val progress: Float?) : AssetDl
    data class Done(val path: String) : AssetDl
    data class Error(val message: String) : AssetDl
}

object ReleasesSessionCache {
    var releases by mutableStateOf<List<ReleaseInfo>?>(null)
    var listError by mutableStateOf<String?>(null)
    var isRefreshing by mutableStateOf(false)
    val expanded = mutableStateMapOf<String, Boolean>()
    val downloads = mutableStateMapOf<String, AssetDl>()

    fun ensureLoaded(scope: CoroutineScope, cacheDir: File) {
        if (releases != null || isRefreshing) return
        refresh(scope, cacheDir)
    }

    fun refresh(scope: CoroutineScope, cacheDir: File) {
        if (isRefreshing) return
        isRefreshing = true
        listError = null
        scope.launch(Dispatchers.IO) {
            try {
                val loaded = fetchReleases()
                loaded.forEach { rel ->
                    rel.assets.forEach { asset ->
                        val key = "${rel.tag}/${asset.name}"
                        val safeName = asset.name.substringAfterLast('/').ifEmpty { "asset.bin" }
                        val cachedFile = File(cacheDir, "${rel.tag}__$safeName")
                        if (cachedFile.exists() && cachedFile.length() > 0 && downloads[key] !is AssetDl.Downloading) {
                            downloads[key] = AssetDl.Done(cachedFile.absolutePath)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    releases = loaded
                    isRefreshing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listError = e.message ?: "load failed"
                    isRefreshing = false
                }
            }
        }
    }
}

private fun fetchReleases(): List<ReleaseInfo> {
    Request.Builder().url(RELEASES_URL)
        .header("User-Agent", "PixelOFoxApp")
        .header("Accept", "application/vnd.github+json")
        .build().let { req ->
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("releases HTTP ${resp.code}")
                val text = resp.body?.string() ?: error("empty releases")
                val arr = JSONArray(text)
                return buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        if (o.optBoolean("draft", false)) continue
                        val tag = o.optString("tag_name", "untagged")
                        val rawBody = o.optString("body")
                        val (bannerUrl, cleanBody) = extractBannerAndCleanMarkdown(rawBody)
                        val assetsJson = o.optJSONArray("assets")
                        val assets = buildList {
                            if (assetsJson != null) {
                                for (j in 0 until assetsJson.length()) {
                                    val a = assetsJson.getJSONObject(j)
                                    val nm = a.optString("name")
                                    if (nm.startsWith("Source code", ignoreCase = true)) continue
                                    add(
                                        ReleaseAsset(
                                            name = nm,
                                            url = a.optString("browser_download_url"),
                                            size = a.optLong("size", -1L),
                                        )
                                    )
                                }
                            }
                        }
                        add(
                            ReleaseInfo(
                                tag = tag,
                                name = o.optString("name").ifEmpty { tag },
                                body = rawBody,
                                bannerUrl = bannerUrl,
                                cleanBody = cleanBody,
                                assets = assets,
                                prerelease = o.optBoolean("prerelease", false),
                            )
                        )
                    }
                }
            }
        }
}

private fun downloadAsset(
    url: String,
    dest: File,
    onProgress: (Float?) -> Unit,
): File {
    Request.Builder().url(url)
        .header("User-Agent", "PixelOFoxApp")
        .build().let { req ->
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("download HTTP ${resp.code}")
                val body = resp.body ?: error("empty body")
                val total = body.contentLength()
                dest.outputStream().use { out ->
                    body.byteStream().use { ins ->
                        val buf = ByteArray(256 * 1024)
                        var done = 0L
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress(if (total > 0) done.toFloat() / total else null)
                        }
                    }
                }
                return dest
            }
        }
}

internal fun copyFileToPublicDownloads(context: Context, sourceFile: File, displayFileName: String): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayFileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: error("MediaStore URI null")
        resolver.openOutputStream(uri)?.use { out ->
            FileInputStream(sourceFile).use { ins ->
                ins.copyTo(out, bufferSize = 256 * 1024)
            }
        } ?: error("Cannot open output stream")
        contentValues.clear()
        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)
        return "Downloads/$displayFileName"
    } else {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .apply { mkdirs() }
        val target = File(downloadsDir, displayFileName)
        FileInputStream(sourceFile).use { ins ->
            FileOutputStream(target).use { out ->
                ins.copyTo(out, bufferSize = 256 * 1024)
            }
        }
        return target.absolutePath
    }
}

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
                    releaseNotifyMode = releaseNotifyMode,
                    onReleaseNotifyModeChange = {
                        releaseNotifyMode = it
                        prefs.releaseNotifyMode = it.ordinal
                        ReleaseCheckWorker.schedule(context, it)
                    },
                )
            }
        }
    }
}

@Composable
fun OrangeFoxTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        // Светлая тема -> тёмные иконки статус-бара, тёмная тема -> светлые.
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

private enum class Tab(val label: Int, val icon: Int) {
    Home(R.string.tab_home, R.drawable.ic_home),
    Releases(R.string.tab_releases, R.drawable.ic_releases),
    Install(R.string.tab_install, R.drawable.ic_bolt),
    Settings(R.string.tab_settings, R.drawable.ic_settings),
}

@androidx.compose.runtime.Stable
class FloatingFilesButtonState {
    var targetRelease by mutableStateOf<ReleaseInfo?>(null)
    var lastTargetRelease by mutableStateOf<ReleaseInfo?>(null)
    var isScrolling by mutableStateOf(false)
    var onClick: ((ReleaseInfo) -> Unit)? = null
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
    releaseNotifyMode: ReleaseNotifyMode,
    onReleaseNotifyModeChange: (ReleaseNotifyMode) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var externalLaunchRequest by remember { mutableStateOf<InstallTargetRequest?>(null) }
    var focusReleaseTag by rememberSaveable { mutableStateOf<String?>(null) }
    val homeNav = remember { HomeNavState() }
    val homeChips = remember { HomeChipsState() }
    val homeChipsBarH = 60.dp
    val docsPrefs = remember(context) { AppPrefs(context) }
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

    fun startDownloadAsset(r: ReleaseInfo, asset: ReleaseAsset) {
        val key = "${r.tag}/${asset.name}"
        if (ReleasesSessionCache.downloads[key] is AssetDl.Downloading) return
        ReleasesSessionCache.downloads[key] = AssetDl.Downloading(null)
        scope.launch(Dispatchers.IO) {
            try {
                val safeName = asset.name.substringAfterLast('/').ifEmpty { "asset.bin" }
                val dest = File(context.cacheDir, "${r.tag}__$safeName")
                downloadAsset(asset.url, dest) { p ->
                    ReleasesSessionCache.downloads[key] = AssetDl.Downloading(p)
                }
                withContext(Dispatchers.Main) {
                    ReleasesSessionCache.downloads[key] = AssetDl.Done(dest.absolutePath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    ReleasesSessionCache.downloads[key] = AssetDl.Error(e.message ?: "download failed")
                }
            }
        }
    }

    fun deleteCachedAsset(r: ReleaseInfo, asset: ReleaseAsset) {
        val key = "${r.tag}/${asset.name}"
        val done = ReleasesSessionCache.downloads[key] as? AssetDl.Done ?: return
        File(done.path).delete()
        ReleasesSessionCache.downloads.remove(key)
    }

    fun copyCachedAsset(r: ReleaseInfo, asset: ReleaseAsset) {
        val key = "${r.tag}/${asset.name}"
        val done = ReleasesSessionCache.downloads[key] as? AssetDl.Done ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val saved = copyFileToPublicDownloads(context, File(done.path), asset.name)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.asset_copied_to_downloads, saved),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.asset_copy_failed, e.message ?: ""),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

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
                            nav = homeNav,
                            chips = homeChips,
                        )
                        Tab.Releases -> ReleasesScreen(
                            topInset = topBarH + 8.dp,
                            floatingFilesState = floatingFilesState,
                            showBannerInCollapsed = showBannerInCollapsed,
                            collapseH2AsSpoilers = collapseH2AsSpoilers,
                            focusReleaseTag = focusReleaseTag,
                            onFocusReleaseHandled = { focusReleaseTag = null },
                            onInstallAsset = { rel, asset ->
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
                            onStartDownload = { rel, asset -> startDownloadAsset(rel, asset) },
                            onDeleteCachedAsset = { rel, asset -> deleteCachedAsset(rel, asset) },
                            onCopyCachedAssetToDownloads = { rel, asset -> copyCachedAsset(rel, asset) },
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
                            releaseNotifyMode = releaseNotifyMode,
                            onReleaseNotifyModeChange = onReleaseNotifyModeChange,
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
                                    DocsSessionCache.refresh(scope, context.cacheDir, docsPrefs, force = true)
                                } else {
                                    ReleasesSessionCache.refresh(scope, context.cacheDir)
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

            // Фиксированная блюр-полоса чипов Home — продолжение шапки, вне записываемого слоя (цикла нет).
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

            // Плавающая блюр-кнопка «К релизам» — сиблинг recordLayer (вне записываемого слоя C, цикла нет!)
            AnimatedVisibility(
                visible = tab == Tab.Releases && floatingFilesState.targetRelease != null,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 } + scaleIn(initialScale = 0.92f),
                exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 3 } + scaleOut(targetScale = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 92.dp),
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
                        modifier = Modifier.graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        },
                        blurState = blurState,
                        blurRadius = blurRadius,
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

            // Круглые кнопки Home-навигации: Назад (слева) и Домой (справа),
            // обе с блюром, обе сиблинги recordLayer (вне записываемого слоя C, цикла нет!)
            val homeHaptics = LocalHapticFeedback.current
            AnimatedVisibility(
                visible = tab == Tab.Home && homeNav.visible && homeNav.canGoBack,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.9f),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 92.dp),
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
                    .padding(end = 20.dp, bottom = 92.dp),
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

            BottomBlurBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                blurState = blurState,
                blurRadius = blurRadius,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tab.entries.forEach { t ->
                        val selected = t == tab
                        val pillBg by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "TabBgColor",
                        )
                        val contentTint by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(220),
                            label = "TabContentTint",
                        )
                        val horizontalPad by animateDpAsState(
                            targetValue = if (selected) 16.dp else 12.dp,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            ),
                            label = "TabHorizontalPadding",
                        )

                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(pillBg)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { tab = t }
                                .padding(horizontal = horizontalPad, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(t.icon),
                                contentDescription = stringResource(t.label),
                                tint = contentTint,
                            )
                            AnimatedVisibility(
                                visible = selected,
                                enter = fadeIn(tween(200)) + expandHorizontally(
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                    ),
                                    expandFrom = Alignment.Start,
                                ),
                                exit = fadeOut(tween(150)) + shrinkHorizontally(
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
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

private val XDA_THREADS = listOf(
    "Pixel 6" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-6.4806927/",
    "Pixel 6 Pro" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-6-pro.4806929/",
    "Pixel 6a" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-6a.4806930/",
    "Pixel 7" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-7.4783086/",
    "Pixel 7 Pro" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-7-pro.4783087/",
    "Pixel 7a" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-7a.4783088/",
    "Pixel 8" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-8.4783089/",
    "Pixel 8 Pro" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-8-pro.4783091/",
    "Pixel 8a" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-8a.4783092/",
    "Pixel 9" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-9.4783093/",
    "Pixel 9 Pro / Pro XL" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-9-pro-pro-xl.4783094/",
    "Pixel 9a" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-9a.4783096/",
    "Pixel Fold" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-fold.4806920/",
    "Pixel Tablet" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-tablet.4806921/",
    "Pixel 9 Pro Fold" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-9-pro-fold.4806922/",
    "Pixel 10" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-10.4806924/",
    "Pixel 10 Pro / Pro XL" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-10-pro-pro-xl.4806925/",
    "Pixel 10a" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-10a.4806923/",
    "Pixel 10 Pro Fold" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-10-pro-fold.4806926/",
    "Pixel 11" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-11.4806931/",
    "Pixel 11 Pro / Pro XL" to "https://xdaforums.com/t/recovery-unofficial-r12-0-orangefox-recovery-project-google-pixel-11-pro-pro-xl.4806932/",
)

private val TELEGRAM_FAMILY_CHATS = listOf(
    "G1 · Pixel 6" to "https://t.me/OFRP_tensor_g1",
    "G2 · Pixel 7" to "https://t.me/OFRP_tensor_g2",
    "G3 · Pixel 8" to "https://t.me/OFRP_tensor_g3",
    "G4 · Pixel 9" to "https://t.me/OFRP_tensor_g4",
    "G5 · Pixel 10" to "https://t.me/OFRP_tensor_g5",
    "G6 · Pixel 11" to "https://t.me/OFRP_tensor_g6",
)

@Composable
private fun SettingsScreen(
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
    releaseNotifyMode: ReleaseNotifyMode,
    onReleaseNotifyModeChange: (ReleaseNotifyMode) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

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
                    modifier = Modifier.fillMaxWidth(),
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ThemeMode.entries.forEach { mode ->
                                    val selected = themeMode == mode
                                    val cornerRadius by animateDpAsState(
                                        targetValue = if (selected) 28.dp else 12.dp,
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMedium,
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                        ),
                                        label = "ThemePillCorner",
                                    )
                                    val bgColor by animateColorAsState(
                                        targetValue = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                        animationSpec = tween(200),
                                        label = "ThemePillBg",
                                    )
                                    val contentColor by animateColorAsState(
                                        targetValue = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        animationSpec = tween(200),
                                        label = "ThemePillContent",
                                    )

                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(cornerRadius))
                                            .background(bgColor)
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                onThemeModeChange(mode)
                                            }
                                            .padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AnimatedVisibility(
                                            visible = selected,
                                            enter = fadeIn(tween(160)) + expandHorizontally(),
                                            exit = fadeOut(tween(120)) + shrinkHorizontally(),
                                        ) {
                                            Text(
                                                text = "✓ ",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = contentColor,
                                            )
                                        }
                                        Text(
                                            text = stringResource(mode.labelRes),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = contentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_show_banner_collapsed_title),
                            description = stringResource(R.string.settings_show_banner_collapsed_desc),
                            checked = showBannerInCollapsed,
                            onCheckedChange = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onShowBannerInCollapsedChange(it)
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_collapse_h2_title),
                            description = stringResource(R.string.settings_collapse_h2_desc),
                            checked = collapseH2AsSpoilers,
                            onCheckedChange = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onCollapseH2AsSpoilersChange(it)
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_collapse_h2_home_title),
                            description = stringResource(R.string.settings_collapse_h2_home_desc),
                            checked = collapseH2Home,
                            onCheckedChange = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onCollapseH2HomeChange(it)
                            },
                        )
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
                    }
                }
            }
        }

        item {
            val uriHandler = LocalUriHandler.current
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
                    modifier = Modifier.fillMaxWidth(),
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
                                onClick = { uriHandler.openUri("https://t.me/OFRPforTensor") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("@OFRPforTensor")
                            }
                            FilledTonalButton(
                                onClick = { uriHandler.openUri("https://t.me/OFRPforTensorDiscussion") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.link_tg_discussion))
                            }
                        }

                        ExpandableGroupCard(
                            title = stringResource(R.string.link_tg_family_chats_title),
                            subtitle = stringResource(R.string.link_tg_family_chats_subtitle),
                            keyTag = "settings_tg_family_spoiler",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                TELEGRAM_FAMILY_CHATS.forEach { (title, url) ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
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
                            title = stringResource(R.string.link_xda_threads_title, XDA_THREADS.size),
                            subtitle = stringResource(R.string.link_xda_threads_subtitle),
                            keyTag = "settings_xda_threads_spoiler",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                XDA_THREADS.forEach { (deviceTitle, url) ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
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
            val uriHandler = LocalUriHandler.current
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
                    modifier = Modifier.fillMaxWidth(),
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
                                uriHandler.openUri("https://github.com/leegarchat/twrp_device_google_pixels/tree/R12_16#readme")
                            },
                            shape = RoundedCornerShape(16.dp),
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

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun StubScreen(
    @androidx.annotation.StringRes title: Int,
    topInset: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset, start = 24.dp, end = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(stringResource(R.string.stub_soon))
    }
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

    var viewportTopInRoot by remember { mutableFloatStateOf(0f) }
    var viewportBottomInRoot by remember { mutableFloatStateOf(0f) }
    val bottomDockClearancePx = with(density) { 94.dp.toPx() }
    val topInsetPx = with(density) { topInset.toPx() }
    val cardMetrics = remember { HashMap<String, CardViewportMetrics>() }
    var highlightedReleaseTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ReleasesSessionCache.ensureLoaded(scope, cacheDir)
    }

    LaunchedEffect(focusReleaseTag, releases) {
        val tag = focusReleaseTag ?: return@LaunchedEffect
        val all = releases ?: return@LaunchedEffect
        val target = all.firstOrNull { it.tag == tag } ?: return@LaunchedEffect
        if (target.prerelease && filterMode == ReleaseFilterMode.Main) {
            filterMode = ReleaseFilterMode.All
            prefs.releaseFilterMode = ReleaseFilterMode.All.ordinal
        }
        expanded[tag] = true
        val visibleList = when (filterMode) {
            ReleaseFilterMode.Main -> all.filter { !it.prerelease }
            ReleaseFilterMode.Prerelease -> all.filter { it.prerelease }
            ReleaseFilterMode.All -> all
        }
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

    fun startDownload(r: ReleaseInfo, asset: ReleaseAsset) {
        val key = "${r.tag}/${asset.name}"
        if (downloads[key] is AssetDl.Downloading) return
        downloads[key] = AssetDl.Downloading(null)
        scope.launch(Dispatchers.IO) {
            try {
                val safeName = asset.name.substringAfterLast('/').ifEmpty { "asset.bin" }
                val dest = File(cacheDir, "${r.tag}__$safeName")
                downloadAsset(asset.url, dest) { p ->
                    downloads[key] = AssetDl.Downloading(p)
                }
                withContext(Dispatchers.Main) {
                    downloads[key] = AssetDl.Done(dest.absolutePath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloads[key] = AssetDl.Error(e.message ?: "download failed")
                }
            }
        }
    }

    fun deleteAsset(r: ReleaseInfo, asset: ReleaseAsset) {
        val key = "${r.tag}/${asset.name}"
        val done = downloads[key] as? AssetDl.Done ?: return
        File(done.path).delete()
        downloads.remove(key)
        Toast.makeText(context, context.getString(R.string.asset_deleted), Toast.LENGTH_SHORT).show()
    }

    fun copyAssetToDownloads(r: ReleaseInfo, asset: ReleaseAsset) {
        val key = "${r.tag}/${asset.name}"
        val done = downloads[key] as? AssetDl.Done ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val savedPath = copyFileToPublicDownloads(
                    context = context,
                    sourceFile = File(done.path),
                    displayFileName = asset.name,
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.asset_copied_to_downloads, savedPath),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.asset_copy_failed, e.message ?: ""),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    var scrollingToFiles by remember { mutableStateOf(false) }
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
                        Button(onClick = { ReleasesSessionCache.refresh(scope, cacheDir) }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
                releases == null -> item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.orangefox.manager.ui.ExpressiveLinearProgress()
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
                            showBannerInCollapsed = showBannerInCollapsed,
                            collapseH2AsSpoilers = collapseH2AsSpoilers,
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
                            onDownload = { asset -> startDownload(r, asset) },
                            onDeleteAsset = { asset -> deleteAsset(r, asset) },
                            onCopyToDownloads = { asset -> copyAssetToDownloads(r, asset) },
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
        DisposableEffect(Unit) {
            floatingFilesState.onClick = { rel -> scrollToAnchorCentered(rel) }
            onDispose {
                floatingFilesState.targetRelease = null
                floatingFilesState.onClick = null
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ReleaseCard(
    info: ReleaseInfo,
    isExpanded: Boolean,
    isAssetsHighlighted: Boolean,
    showBannerInCollapsed: Boolean,
    collapseH2AsSpoilers: Boolean,
    onToggle: () -> Unit,
    onCardBoundsChanged: (Rect) -> Unit,
    onAnchorBoundsChanged: (Rect) -> Unit,
    downloads: Map<String, AssetDl>,
    onDownload: (ReleaseAsset) -> Unit,
    onDeleteAsset: (ReleaseAsset) -> Unit,
    onCopyToDownloads: (ReleaseAsset) -> Unit,
    onInstallAsset: (ReleaseAsset) -> Unit,
) {
    // 1. Фиксированный боковой отступ (8.dp) сохраняет неизменную ширину карточки:
    // текст Markdown, спойлеры и таблицы измеряются ровно 1 раз и не вызывают покадрового
    // релейаута текста, обеспечивая плавные 60/120 FPS без просадок до 30 кадров.
    val horizontalPad = 8.dp
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 18.dp else 22.dp,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "CardCornerRadius",
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
            },
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        SharedTransitionLayout {
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
                // Баннер и заголовок меняются местами ТОЛЬКО если баннер показан в закрытой карточке.
                // Если баннер отключен — заголовок уже находится вверху и не должен искажаться sharedBounds!
                val canMorphBanner = info.bannerUrl != null && showBannerInCollapsed

                if (!expandedState) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onToggle),
                    ) {
                        if (info.bannerUrl != null && showBannerInCollapsed) {
                            NetworkBannerImage(
                                url = info.bannerUrl,
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
                            isExpanded = false,
                            modifier = Modifier
                                .then(
                                    if (canMorphBanner) {
                                        Modifier.sharedBounds(
                                            sharedContentState = rememberSharedContentState(key = "header_${info.tag}"),
                                            animatedVisibilityScope = this@AnimatedContent,
                                            boundsTransform = { _, _ -> morphSpring },
                                            // RemeasureToBounds полностью исключает зумирование и растяжение шрифта
                                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ReleaseHeaderRow(
                            info = info,
                            isExpanded = true,
                            modifier = Modifier
                                .then(
                                    if (canMorphBanner) {
                                        Modifier.sharedBounds(
                                            sharedContentState = rememberSharedContentState(key = "header_${info.tag}"),
                                            animatedVisibilityScope = this@AnimatedContent,
                                            boundsTransform = { _, _ -> morphSpring },
                                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(onClick = onToggle)
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
                                        .clickable(onClick = onToggle),
                                )
                            }

                            if (info.cleanBody.isNotBlank()) {
                                MarkdownBody(
                                    md = info.cleanBody,
                                    collapseH2Sections = collapseH2AsSpoilers,
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
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseHeaderRow(
    info: ReleaseInfo,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "HeaderChevron",
    )
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
            Text(
                text = info.tag,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun ReleaseAssetsSection(
    info: ReleaseInfo,
    isHighlighted: Boolean,
    downloads: Map<String, AssetDl>,
    onDownload: (ReleaseAsset) -> Unit,
    onDeleteAsset: (ReleaseAsset) -> Unit,
    onCopyToDownloads: (ReleaseAsset) -> Unit,
    onInstallAsset: (ReleaseAsset) -> Unit,
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
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AssetRow(
    asset: ReleaseAsset,
    state: AssetDl?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCopyToDownloads: () -> Unit,
    onInstall: () -> Unit,
) {
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
                        .clickable(onClick = onDelete),
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
                if (state.progress != null) {
                    com.orangefox.manager.ui.ExpressiveLinearProgress(progress = state.progress)
                    Text(
                        text = stringResource(R.string.asset_downloading, (state.progress * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                } else {
                    com.orangefox.manager.ui.ExpressiveLinearProgress()
                    Text(
                        text = stringResource(R.string.asset_downloading_indeterminate),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                ) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            is AssetDl.Done -> {
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
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
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
                        )
                    }
                }
            }
        }
    }
}
