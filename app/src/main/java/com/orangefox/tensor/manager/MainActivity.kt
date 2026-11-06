package com.orangefox.tensor.manager

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.ui.BottomBlurBar
import com.orangefox.tensor.manager.ui.ExpandableGroupCard
import com.orangefox.tensor.manager.ui.ExpressiveSegmentedRow
import com.orangefox.tensor.manager.ui.edgeOutline
import com.orangefox.tensor.manager.ui.GraphicsLayerRecordingState
import com.orangefox.tensor.manager.ui.MarkdownBody
import com.orangefox.tensor.manager.ui.NetworkBannerImage
import com.orangefox.tensor.manager.ui.SessionImageCache
import com.orangefox.tensor.manager.ui.SettingSlider
import com.orangefox.tensor.manager.ui.TopBarBlurOverlay
import com.orangefox.tensor.manager.ui.TopStatusVeil
import com.orangefox.tensor.manager.ui.extractBannerAndCleanMarkdown
import com.orangefox.tensor.manager.ui.recordLayer
import com.orangefox.tensor.manager.ui.rememberGraphicsLayerRecordingState
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

internal val sharedHttpClient = OkHttpClient()

/** Manager APK in releases: orangefox*tensor*manager*{versionCode}*.apk */
internal fun isManagerApkAsset(name: String): Boolean {
    val low = name.lowercase()
    return low.endsWith(".apk") && "orangefox" in low && "tensor" in low && "manager" in low
}

/** versionCode is the last digit group in the file name. */
internal fun managerApkVersionCode(name: String): Int? {
    if (!isManagerApkAsset(name)) return null
    val base = name.substringBeforeLast('.', name)
    return Regex("""\d+""").findAll(base).lastOrNull()?.value?.toIntOrNull()
}

data class ManagerUpdateOption(
    val release: ReleaseInfo,
    val asset: ReleaseAsset,
    val code: Int,
)

/** Best stable and best beta candidate across ALL releases (including prereleases). */
internal fun findManagerUpdates(releases: List<ReleaseInfo>?): Pair<ManagerUpdateOption?, ManagerUpdateOption?> {
    if (releases.isNullOrEmpty()) return null to null
    var stable: ManagerUpdateOption? = null
    var beta: ManagerUpdateOption? = null
    for (rel in releases) {
        for (a in rel.assets) {
            val code = managerApkVersionCode(a.name) ?: continue
            val opt = ManagerUpdateOption(rel, a, code)
            if (rel.prerelease) {
                if (beta == null || code > beta.code) beta = opt
            } else {
                if (stable == null || code > stable.code) stable = opt
            }
        }
    }
    return stable to beta
}

data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
    val downloads: Long = -1L,
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
    val reactions: Int = 0,
)

sealed interface AssetDl {
    data class Downloading(val progress: Float?) : AssetDl
    data class Done(val path: String) : AssetDl
    data class Error(val message: String) : AssetDl
}

object ReleasesSessionCache {
    private val ioScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val downloadJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val downloadCalls = mutableMapOf<String, okhttp3.Call>()
    private val cancelledKeys = mutableSetOf<String>()
    var releases by mutableStateOf<List<ReleaseInfo>?>(null)
    var listError by mutableStateOf<String?>(null)
    var isRefreshing by mutableStateOf(false)
    val expanded = mutableStateMapOf<String, Boolean>()
    val downloads = mutableStateMapOf<String, AssetDl>()

    fun startDownload(context: Context, r: ReleaseInfo, asset: ReleaseAsset) {
        val key = "${r.tag}/${asset.name}"
        if (downloads[key] is AssetDl.Downloading) return
        val cacheDir = context.applicationContext.cacheDir
        val safeName = asset.name.substringAfterLast('/').ifEmpty { "asset.bin" }
        downloads[key] = AssetDl.Downloading(null)
        val job = ioScope.launch {
            try {
                val dest = File(cacheDir, "${r.tag}__$safeName")
                downloadAsset(
                    url = asset.url,
                    dest = dest,
                    onProgress = { p -> downloads[key] = AssetDl.Downloading(p) },
                    onCallCreated = { call -> downloadCalls[key] = call },
                )
                downloads.remove(key)
                withContext(Dispatchers.Main) {
                    downloads[key] = AssetDl.Done(dest.absolutePath)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                synchronized(cancelledKeys) { cancelledKeys.remove(key) }
                downloads.remove(key)
                File(cacheDir, "${r.tag}__$safeName").delete()
                throw e
            } catch (e: Exception) {
                val wasCancelled = synchronized(cancelledKeys) { cancelledKeys.remove(key) }
                if (wasCancelled) {
                    downloads.remove(key)
                    File(cacheDir, "${r.tag}__$safeName").delete()
                } else {
                    withContext(Dispatchers.Main) {
                        downloads[key] = AssetDl.Error(e.message ?: "download failed")
                    }
                }
            } finally {
                downloadCalls.remove(key)
            }
        }
        downloadJobs[key] = job
        job.invokeOnCompletion { downloadJobs.remove(key) }
    }

    fun cancelDownload(r: ReleaseInfo, asset: ReleaseAsset) {
        val key = "${r.tag}/${asset.name}"
        synchronized(cancelledKeys) { cancelledKeys.add(key) }
        downloadCalls.remove(key)?.cancel()
        downloadJobs.remove(key)?.cancel()
        downloads.remove(key)
    }

    fun cancelAllDownloads() {
        downloadCalls.values.forEach { runCatching { it.cancel() } }
        downloadJobs.values.forEach { it.cancel() }
        synchronized(cancelledKeys) { cancelledKeys.addAll(downloadJobs.keys) }
        downloadCalls.clear()
        downloadJobs.clear()
        downloads.keys.toList().forEach { key ->
            if (downloads[key] is AssetDl.Downloading) downloads.remove(key)
        }
    }

    fun deleteCachedAsset(context: Context, r: ReleaseInfo, asset: ReleaseAsset, showToast: Boolean = false) {
        val key = "${r.tag}/${asset.name}"
        val done = downloads[key] as? AssetDl.Done ?: return
        File(done.path).delete()
        downloads.remove(key)
        if (showToast) {
            Toast.makeText(context, context.getString(R.string.asset_deleted), Toast.LENGTH_SHORT).show()
        }
    }

    fun copyLocalFileToDownloads(context: Context, sourceFile: File, displayName: String) {
        val appCtx = context.applicationContext
        ioScope.launch {
            try {
                val saved = copyFileToPublicDownloads(appCtx, sourceFile, displayName)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appCtx,
                        appCtx.getString(R.string.asset_copied_to_downloads, saved),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appCtx,
                        appCtx.getString(R.string.asset_copy_failed, e.message ?: ""),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    fun copyCachedAssetToDownloads(context: Context, r: ReleaseInfo, asset: ReleaseAsset) {
        val key = "${r.tag}/${asset.name}"
        val done = downloads[key] as? AssetDl.Done ?: return
        copyLocalFileToDownloads(context, File(done.path), asset.name)
    }

    fun ensureLoaded(scope: CoroutineScope, context: Context, cacheDir: File) {
        if (releases != null || isRefreshing) return
        refresh(scope, context, cacheDir)
    }

    fun refresh(scope: CoroutineScope, context: Context, cacheDir: File) {
        if (isRefreshing) return
        isRefreshing = true
        listError = null
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            try {
                // Manual list refresh: wipe the image disk cache,
                // then everything is re-downloaded from the network exactly once and cached again.
                com.orangefox.tensor.manager.ui.SessionImageCache.bindDiskCache(
                    appContext.cacheDir,
                    AppPrefs(appContext).cacheImages,
                )
                com.orangefox.tensor.manager.ui.SessionImageCache.clearDiskSync()
                val loaded = fetchReleases(appContext)
                loaded.forEach { rel ->
                    rel.assets.forEach { asset ->
                        val key = "${rel.tag}/${asset.name}"
                        val safeName = asset.name.substringAfterLast('/').ifEmpty { "asset.bin" }
                        val cachedFile = File(cacheDir, "${rel.tag}__$safeName")
                        val validSize = if (asset.size > 0L) cachedFile.length() == asset.size else cachedFile.length() > 0L
                        if (cachedFile.exists() && validSize && downloads[key] !is AssetDl.Downloading) {
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

private fun fetchReleases(context: Context): List<ReleaseInfo> {
    Request.Builder().url(context.getString(R.string.url_releases_api))
        .header("User-Agent", "PixelOFoxApp")
        .header("Accept", "application/vnd.github+json")
        .build().let { req ->
            sharedHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("releases HTTP ${resp.code}")
                val text = resp.body?.string() ?: error("empty releases")
                val arr = JSONArray(text)
                return buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        if (o.optBoolean("draft", false)) continue
                        val tag = o.optString("tag_name", "untagged")
                        val rawBody = o.optString("body")
                        val rawBase = context.getString(R.string.url_github_raw_base)
                        val (bannerUrl, cleanBody) = extractBannerAndCleanMarkdown(rawBody, rawBase)
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
                                            downloads = a.optLong("download_count", -1L),
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
                                reactions = o.optJSONObject("reactions")?.optInt("total_count", 0) ?: 0,
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
    onCallCreated: (okhttp3.Call) -> Unit = {},
): File {
    val partFile = File(dest.parentFile, "${dest.name}.part")
    Request.Builder().url(url)
        .header("User-Agent", "PixelOFoxApp")
        .build().let { req ->
            val call = sharedHttpClient.newCall(req)
            onCallCreated(call)
            call.execute().use { resp ->
                if (!resp.isSuccessful) error("download HTTP ${resp.code}")
                val body = resp.body ?: error("empty body")
                val total = body.contentLength()
                var lastReportedProgress = -1f
                try {
                    partFile.outputStream().use { out ->
                        body.byteStream().use { ins ->
                            val buf = ByteArray(256 * 1024)
                            var done = 0L
                            while (true) {
                                val n = ins.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val p = done.toFloat() / total
                                    if (p - lastReportedProgress >= 0.01f || done == total) {
                                        lastReportedProgress = p
                                        onProgress(p)
                                    }
                                }
                            }
                            if (total <= 0) onProgress(null)
                        }
                    }
                    if (total > 0L && partFile.length() != total) {
                        error("Incomplete download: ${partFile.length()} / $total")
                    }
                    dest.delete()
                    if (!partFile.renameTo(dest)) partFile.copyTo(dest, overwrite = true)
                    return dest
                } finally {
                    partFile.delete()
                }
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
            var cacheImages by remember { mutableStateOf(prefs.cacheImages) }
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
        // Light theme -> dark status-bar icons, dark theme -> light ones.
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
    cacheImages: Boolean,
    onCacheImagesChange: (Boolean) -> Unit,
    releaseNotifyMode: ReleaseNotifyMode,
    onReleaseNotifyModeChange: (ReleaseNotifyMode) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var externalLaunchRequest by remember { mutableStateOf<InstallTargetRequest?>(null) }
    var installBusy by remember { mutableStateOf(false) }
    // The installer submenu lives in Root so it survives tab switches.
    var installSubmenuTarget by remember { mutableStateOf<InstallTargetRequest?>(null) }
    var filesEpoch by remember { mutableStateOf(0) }
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

    // ---- Manager self-update from releases ----
    var managerUpdateOffer by remember { mutableStateOf<Pair<ManagerUpdateOption?, ManagerUpdateOption?>?>(null) }
    var updatePromptedForCode by remember { mutableStateOf(0) }
    var pendingManagerApk by remember { mutableStateOf<ManagerUpdateOption?>(null) }
    var managerUpdating by remember { mutableStateOf(false) }
    var pendingNativeFile by remember { mutableStateOf<File?>(null) }





    fun installManagerApkNative(file: File) {
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            context.startActivity(intent)
        }
    }

    val unknownAppLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) {
        if (context.packageManager.canRequestPackageInstalls()) {
            pendingNativeFile?.let { installManagerApkNative(it) }
        }
        pendingNativeFile = null
    }

    fun startManagerInstall(file: File) {
        if (managerUpdating) return
        scope.launch(Dispatchers.IO) {
            val hasRoot = RootInstallerEngine.checkRootAccess()
            if (hasRoot) {
                withContext(Dispatchers.Main) { managerUpdating = true }
                val err = RootInstallerEngine.installApkViaRoot(context, file)
                withContext(Dispatchers.Main) {
                    managerUpdating = false
                    Toast.makeText(
                        context,
                        if (err == null) {
                            context.getString(R.string.update_install_root_ok)
                        } else {
                            context.getString(R.string.update_install_root_fail, err)
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    if (context.packageManager.canRequestPackageInstalls()) {
                        installManagerApkNative(file)
                    } else {
                        pendingNativeFile = file
                        Toast.makeText(
                            context,
                            context.getString(R.string.update_need_unknown_sources),
                            Toast.LENGTH_LONG,
                        ).show()
                        unknownAppLauncher.launch(
                            android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                android.net.Uri.parse("package:${context.packageName}"),
                            )
                        )
                    }
                }
            }
        }
    }

    fun beginManagerUpdate(opt: ManagerUpdateOption) {
        managerUpdateOffer = null
        val key = "${opt.release.tag}/${opt.asset.name}"
        val done = ReleasesSessionCache.downloads[key] as? AssetDl.Done
        if (done != null && File(done.path).isFile) {
            startManagerInstall(File(done.path))
        } else {
            pendingManagerApk = opt
            ReleasesSessionCache.startDownload(context, opt.release, opt.asset)
        }
    }

    val releasesSnapshot = ReleasesSessionCache.releases
    LaunchedEffect(releasesSnapshot) {
        val (stable, beta) = findManagerUpdates(releasesSnapshot)
        val best = maxOf(stable?.code ?: 0, beta?.code ?: 0)
        val current = BuildConfig.VERSION_CODE
        val refused = docsPrefs.refusedManagerUpdateCode
        if (best > current && best > refused && updatePromptedForCode != best) {
            updatePromptedForCode = best
            managerUpdateOffer = stable to beta
        }
    }

    pendingManagerApk?.let { opt ->
        val key = "${opt.release.tag}/${opt.asset.name}"
        val st = ReleasesSessionCache.downloads[key]
        LaunchedEffect(st) {
            if (st is AssetDl.Done && File(st.path).isFile) {
                pendingManagerApk = null
                startManagerInstall(File(st.path))
            } else if (st is AssetDl.Error) {
                pendingManagerApk = null
            }
        }
    }

    managerUpdateOffer?.let { (stable, beta) ->
        val best = maxOf(stable?.code ?: 0, beta?.code ?: 0)
        val stableTxt = stable?.code?.toString() ?: "—"
        val betaTxt = beta?.code?.toString() ?: "—"
        AlertDialog(
            onDismissRequest = {
                docsPrefs.refusedManagerUpdateCode = best
                managerUpdateOffer = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(22.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            title = {
                Text(
                    text = stringResource(R.string.update_manager_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = stringResource(
                            R.string.update_manager_text,
                            BuildConfig.VERSION_CODE,
                            stringResource(R.string.update_manager_channels, stableTxt, betaTxt),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stable?.let { opt ->
                        Button(
                            onClick = { beginManagerUpdate(opt) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.edgeOutline()),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.update_channel_stable, opt.code))
                        }
                    }
                    beta?.let { opt ->
                        FilledTonalButton(
                            onClick = { beginManagerUpdate(opt) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer.edgeOutline()),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.update_channel_beta, opt.code))
                        }
                    }
                    TextButton(
                        onClick = {
                            docsPrefs.refusedManagerUpdateCode = best
                            managerUpdateOffer = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (managerUpdating) {
        AlertDialog(
            onDismissRequest = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(22.dp),
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = {
                Text(
                    text = stringResource(R.string.update_install_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    com.orangefox.tensor.manager.ui.ExpressiveLinearProgress()
                    Text(
                        text = stringResource(R.string.update_installing_root),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
        )
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
                            focusReleaseTag = focusReleaseTag,
                            onFocusReleaseHandled = { focusReleaseTag = null },
                            onInstallAsset = { rel, asset ->
                                if (isManagerApkAsset(asset.name)) {
                                    val key = "${rel.tag}/${asset.name}"
                                    val done = ReleasesSessionCache.downloads[key] as? AssetDl.Done
                                        ?: return@ReleasesScreen
                                    val file = File(done.path)
                                    if (!file.isFile) return@ReleasesScreen
                                    startManagerInstall(file)
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
                            cacheImages = cacheImages,
                            onCacheImagesChange = onCacheImagesChange,
                            releaseNotifyMode = releaseNotifyMode,
                            onReleaseNotifyModeChange = onReleaseNotifyModeChange,
                            onWipeAllFiles = {
                                scope.launch(Dispatchers.IO) {
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
                                    ReleasesSessionCache.refresh(scope, context, context.cacheDir)
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

            BottomBlurBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
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
    cacheImages: Boolean,
    onCacheImagesChange: (Boolean) -> Unit,
    releaseNotifyMode: ReleaseNotifyMode,
    onReleaseNotifyModeChange: (ReleaseNotifyMode) -> Unit,
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

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val switchHaptics = LocalHapticFeedback.current
    fun fire(next: Boolean) {
        switchHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        onCheckedChange(next)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { fire(!checked) }
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
            onCheckedChange = { fire(it) },
        )
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
    cacheImages: Boolean,
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
    val cardMetrics = remember { mutableStateMapOf<String, CardViewportMetrics>() }
    var highlightedReleaseTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ReleasesSessionCache.ensureLoaded(scope, context, cacheDir)
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
                        Button(onClick = { ReleasesSessionCache.refresh(scope, context, cacheDir) }) {
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
                            showBannerInCollapsed = showBannerInCollapsed,
                            collapseH2AsSpoilers = collapseH2AsSpoilers,
                            cacheImages = cacheImages,
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ConfirmDeleteDialog(
    fileName: String,
    title: String = stringResource(R.string.delete_confirm_title),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.delete_confirm_text, fileName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Subtle accent of the expanded release header based on the banner.
 * Average-color sampling is manual (no Palette), off the UI thread,
 * the result is cached by release tag. The bitmap is reused from
 * the session cache [SessionImageCache] — no repeated network loads.
 */
private object ReleaseHeaderAccentCache {
    val accents = java.util.concurrent.ConcurrentHashMap<String, Color>()
}

private fun sampleBannerAccentColor(bmp: Bitmap): Color? {
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return null
    // At most ~1024 samples: stride from area, no heavy computation in composition.
    val step = maxOf(1, kotlin.math.sqrt((w * h) / 1024.0).toInt())
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0L
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val px = bmp.getPixel(x, y)
            if ((px ushr 24) >= 128) {
                rSum += ((px shr 16) and 0xFF).toLong()
                gSum += ((px shr 8) and 0xFF).toLong()
                bSum += (px and 0xFF).toLong()
                count++
            }
            x += step
        }
        y += step
    }
    if (count == 0L) return null
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (rSum / count).toInt(),
        (gSum / count).toInt(),
        (bSum / count).toInt(),
        hsv,
    )
    // Slightly boost saturation (gray banners still hint a tone),
    // normalize brightness so the veil reads well in both light and dark themes.
    hsv[1] = (hsv[1] * 1.25f).coerceIn(0f, 0.65f)
    hsv[2] = hsv[2].coerceIn(0.35f, 0.9f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
private fun rememberReleaseBannerAccent(tag: String, bannerUrl: String?, cacheImages: Boolean): Color? {
    var accent by remember(tag) { mutableStateOf(ReleaseHeaderAccentCache.accents[tag]) }
    val context = LocalContext.current
    val rawBase = stringResource(R.string.url_github_raw_base)
    LaunchedEffect(tag, bannerUrl, rawBase, cacheImages) {
        SessionImageCache.bindDiskCache(context.cacheDir, cacheImages)
        if (accent != null || bannerUrl == null) return@LaunchedEffect
        if (accent != null || bannerUrl == null) return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            SessionImageCache.getImmediate(bannerUrl, rawBase) ?: SessionImageCache.loadOrAwait(bannerUrl, rawBase)
        }
        if (bmp != null) {
            val sampled = withContext(Dispatchers.Default) { sampleBannerAccentColor(bmp) }
            if (sampled != null) {
                ReleaseHeaderAccentCache.accents[tag] = sampled
                accent = sampled
            }
        }
    }
    return accent
}

@Composable
private fun ReleaseCard(
    info: ReleaseInfo,
    isExpanded: Boolean,
    isAssetsHighlighted: Boolean,
    showBannerInCollapsed: Boolean,
    collapseH2AsSpoilers: Boolean,
    cacheImages: Boolean,
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
    // the accent is applied with low alpha over surfaceContainerLow and reads well in both themes.
    // Without a banner — a delicate primaryContainer fallback of the same strength.
    val cardBaseColor = MaterialTheme.colorScheme.surfaceContainerLow
    val bannerAccent = rememberReleaseBannerAccent(tag = info.tag, bannerUrl = info.bannerUrl, cacheImages = cacheImages)
    val expandedHeaderTarget = if (bannerAccent != null) {
        bannerAccent.copy(alpha = 0.13f).compositeOver(cardBaseColor)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f).compositeOver(cardBaseColor)
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
                        isExpanded = isExpanded,
                        modifier = Modifier
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
                            isExpanded = false,
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
                            isExpanded = true,
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

@Composable
private fun OpenGitHubReleaseButton(tag: String) {
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
                            com.orangefox.tensor.manager.ui.ExpressiveLinearProgress(progress = state.progress)
                            Text(
                                text = stringResource(R.string.asset_downloading, (state.progress * 100).toInt()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            com.orangefox.tensor.manager.ui.ExpressiveLinearProgress()
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
