package com.orangefox.tensor.manager.data

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.orangefox.tensor.manager.ui.SessionImageCache
import com.orangefox.tensor.manager.ui.extractBannerAndCleanMarkdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import com.orangefox.tensor.manager.AppPrefs
import com.orangefox.tensor.manager.R

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
        refresh(scope, context, cacheDir, force = false)
    }

    fun refresh(scope: CoroutineScope, context: Context, cacheDir: File, force: Boolean = false) {
        if (isRefreshing) return
        isRefreshing = true
        listError = null
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            try {
                // Manual list refresh only: wipe the image disk cache,
                // then everything is re-downloaded from the network exactly once and cached again.
                // Background loads (ensureLoaded) must never wipe the cache.
                com.orangefox.tensor.manager.ui.SessionImageCache.bindDiskCache(
                    appContext.cacheDir,
                    AppPrefs(appContext).cacheImages,
                )
                if (force) {
                    com.orangefox.tensor.manager.ui.SessionImageCache.clearDiskSync()
                }
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
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Scope cancelled (e.g. composition gone): the flag reset below
                // still runs via NonCancellable, then cancellation propagates.
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "load failed"
                withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
                    listError = msg
                }
            } finally {
                // Guaranteed reset: a stuck/cancelled load must never leave
                // isRefreshing=true forever (eternal spinner until restart).
                withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
                    isRefreshing = false
                }
            }
        }
    }

    /**
     * Cancels every in-flight download, deletes fully downloaded asset files
     * and clears their states. Called before a full wipe (see Root wipe flow).
     */
    fun purgeAllDownloadsAndDeleteFiles() {
        cancelAllDownloads()
        downloads.keys.toList().forEach { key ->
            (downloads[key] as? AssetDl.Done)?.let { done ->
                File(done.path).delete()
                downloads.remove(key)
            }
        }
    }
}

private fun fetchReleases(context: Context): List<ReleaseInfo> {
    Request.Builder().url(context.getString(R.string.url_releases_api))
        .header("User-Agent", "PixelOFoxApp")
        .header("Accept", "application/vnd.github+json")
        .build().let { req ->
            sharedHttpClient.executeGithub(req).use { resp ->
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
                                publishedAt = o.optString("published_at").takeIf { it.isNotBlank() }
                                    ?: o.optString("created_at").takeIf { it.isNotBlank() },
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
