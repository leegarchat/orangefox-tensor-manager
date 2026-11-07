package com.orangefox.tensor.manager.ui

import com.orangefox.tensor.manager.data.executeGithub
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import com.orangefox.tensor.manager.data.sharedHttpClient

/**
 * In-memory session image cache with parallel-request deduplication.
 * When the same banner URL appears in several releases, the network request
 * runs exactly once per app session.
 *
 * Read order: memory → disk → network. The disk layer is the `images`
 * subfolder in [appCacheDir] (file key is sha256(url), bytes only, no metadata).
 * With [diskCacheEnabled] off, disk is neither read nor written:
 * images live only in the current session's memory.
 */
object SessionImageCache {
    private const val DISK_SUBDIR = "images"
    private val memoryCache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Bitmap?>>()

    @Volatile
    var diskCacheEnabled: Boolean = true

    @Volatile
    private var diskBaseDir: File? = null

    /** Binds the disk cache to [appCacheDir] and enables/disables it. Safe to call from the UI thread. */
    fun bindDiskCache(appCacheDir: File, enabled: Boolean) {
        diskBaseDir = appCacheDir
        diskCacheEnabled = enabled
    }

    private fun diskFileFor(url: String): File? {
        val base = diskBaseDir ?: return null
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(base, DISK_SUBDIR), hash)
    }

    private fun readDiskBitmap(url: String): Bitmap? {
        return try {
            val file = diskFileFor(url) ?: return null
            if (!file.isFile || file.length() == 0L) return null
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeDiskBytes(url: String, bytes: ByteArray) {
        try {
            val file = diskFileFor(url) ?: return
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.part")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    /** Deletes all image disk-cache files. Call only from Dispatchers.IO. */
    fun clearDiskSync() {
        val base = diskBaseDir ?: return
        try {
            File(base, DISK_SUBDIR).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    /** Deletes all image disk-cache files (safe from any thread). */
    suspend fun clearDiskCache() = withContext(Dispatchers.IO) {
        clearDiskSync()
    }

    fun getImmediate(rawUrl: String, rawBase: String): Bitmap? {
        val url = normalizeGitHubImageUrl(rawUrl, rawBase)
        return memoryCache.get(url)
    }

    /**
     * Memory → disk probe without network (safe from any thread, IO internally).
     * A disk hit is promoted to memory, so the next [getImmediate] hits.
     * Used to avoid flashing a loading indicator when the disk cache hits.
     */
    suspend fun loadFromCacheOnly(rawUrl: String, rawBase: String): Bitmap? =
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            val url = normalizeGitHubImageUrl(rawUrl, rawBase)
            memoryCache.get(url) ?: if (diskCacheEnabled) {
                readDiskBitmap(url)?.also { memoryCache.put(url, it) }
            } else {
                null
            }
        }

    suspend fun loadOrAwait(rawUrl: String, rawBase: String): Bitmap? {
        val url = normalizeGitHubImageUrl(rawUrl, rawBase)
        memoryCache.get(url)?.let { return it }

        val deferred = CompletableDeferred<Bitmap?>()
        val existing = inFlight.putIfAbsent(url, deferred)
        if (existing != null) {
            return existing.await()
        }

        return try {
            val bmp = withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                val useDisk = diskCacheEnabled
                if (useDisk) {
                    readDiskBitmap(url)?.let { return@withContext it }
                }
                val bytes: ByteArray? = try {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "PixelOFoxApp")
                        .build()
                    // Timeouts come from sharedHttpClient (connect/read);
                    // a stalled fetch throws and lands in the catch below,
                    // so the deferred below ALWAYS completes.
                    sharedHttpClient.executeGithub(req).use { resp ->
                        if (!resp.isSuccessful) return@use null
                        resp.body?.bytes()
                    }
                } catch (_: Exception) {
                    null
                }
                if (bytes == null || bytes.isEmpty()) return@withContext null
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (decoded != null && useDisk) {
                    writeDiskBytes(url, bytes)
                }
                decoded
            }
            if (bmp != null) {
                memoryCache.put(url, bmp)
            }
            deferred.complete(bmp)
            bmp
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Owner cancelled: unblock co-awaiters first, then propagate.
            deferred.complete(null)
            throw e
        } catch (e: Throwable) {
            deferred.complete(null)
            null
        } finally {
            // Remove only our own entry: a newer fetch for the same URL
            // (created after a force-refresh race) must survive.
            inFlight.remove(url, deferred)
        }
    }
}

/**
 * Converts links of the form:
 * https://github.com/<owner>/<repo>/blob/<branch>/<path>
 * into a direct image link:
 * https://raw.githubusercontent.com/<owner>/<repo>/<branch>/<path>
 *
 * The raw-content host base comes from resources (url_github_raw_base),
 * assembly stays in code since owner/repo/path are dynamic.
 */
fun normalizeGitHubImageUrl(url: String, rawBase: String): String {
    val trimmed = url.trim().replace("&amp;", "&")
    val blobRegex = Regex("""^https?://github\.com/([^/]+)/([^/]+)/(?:blob|raw)/([^?#]+)(?:\?.*)?$""", RegexOption.IGNORE_CASE)
    val match = blobRegex.find(trimmed)
    return if (match != null) {
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val branchAndPath = match.groupValues[3]
        "$rawBase$owner/$repo/$branchAndPath"
    } else {
        trimmed
    }
}

@Composable
fun NetworkBannerImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cacheImagesEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val rawBase = stringResource(R.string.url_github_raw_base)
    val normalizedUrl = remember(url, rawBase) { normalizeGitHubImageUrl(url, rawBase) }
    var bitmap by remember(normalizedUrl) {
        mutableStateOf(SessionImageCache.getImmediate(normalizedUrl, rawBase))
    }
    var failed by remember(normalizedUrl) { mutableStateOf(false) }
    // Becomes true only after the memory → disk probe missed: from that point
    // the load is a real network fetch and the progress indicator is legit.
    var diskMiss by remember(normalizedUrl) { mutableStateOf(false) }

    LaunchedEffect(normalizedUrl, rawBase, cacheImagesEnabled) {
        SessionImageCache.bindDiskCache(context.cacheDir, cacheImagesEnabled)
        if (bitmap != null) return@LaunchedEffect
        failed = false
        diskMiss = false
        // Fast cache probe (memory → disk) with no loading indicator:
        // a cache hit must never flash progress, not even for a frame.
        val cached = SessionImageCache.loadFromCacheOnly(normalizedUrl, rawBase)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        diskMiss = true
        val loaded = SessionImageCache.loadOrAwait(normalizedUrl, rawBase)
        if (loaded != null) {
            bitmap = loaded
        } else {
            failed = true
        }
    }

    val bmp = bitmap
    when {
        bmp != null -> {
            val ratio = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat())
                .coerceIn(1.3f, 2.5f)
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio),
            )
        }
        !failed && diskMiss -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                ExpressiveLinearProgress(modifier = Modifier.width(120.dp))
            }
        }
        !failed -> {
            // Cache probe in flight: reserve the 16:9 slot, but show no progress —
            // a disk hit must resolve straight to the image.
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            )
        }
    }
}
