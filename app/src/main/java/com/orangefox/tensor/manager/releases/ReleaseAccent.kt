package com.orangefox.tensor.manager.releases

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.orangefox.tensor.manager.ui.SessionImageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.orangefox.tensor.manager.R

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
    var rSum = 0.0
    var gSum = 0.0
    var bSum = 0.0
    var weightSum = 0.0
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val px = bmp.getPixel(x, y)
            if ((px ushr 24) >= 128) {
                val r = ((px shr 16) and 0xFF) / 255f
                val g = ((px shr 8) and 0xFF) / 255f
                val b = (px and 0xFF) / 255f
                val v = maxOf(r, g, b)
                // Ignore dark background pixels: they would drag the average into mud.
                if (v >= 0.35f) {
                    val minC = minOf(r, g, b)
                    val delta = v - minC
                    val s = if (v <= 0f) 0f else delta / v
                    // Bright + saturated weighs more; whites/pastels keep a floor
                    // weight (0.35) so they lighten instead of being drowned out.
                    val weight = v * v * (0.35f + 0.65f * s)
                    rSum += r * weight
                    gSum += g * weight
                    bSum += b * weight
                    weightSum += weight
                }
            }
            x += step
        }
        y += step
    }
    if (weightSum <= 0.0) return null
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (rSum / weightSum * 255f).toInt().coerceIn(0, 255),
        (gSum / weightSum * 255f).toInt().coerceIn(0, 255),
        (bSum / weightSum * 255f).toInt().coerceIn(0, 255),
        hsv,
    )
    // Slightly boost saturation (gray banners still hint a tone),
    // normalize brightness so the veil reads well in both light and dark themes:
    // the raised lower clamp keeps the tint from sinking into dark surfaces.
    hsv[1] = (hsv[1] * 1.2f).coerceIn(0f, 0.7f)
    hsv[2] = hsv[2].coerceIn(0.55f, 0.95f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Loads the banner bitmap (memory → disk → network via [SessionImageCache]),
 * samples its accent and caches it by release tag. Returns null while missing.
 */
private suspend fun loadSampledBannerAccent(tag: String, bannerUrl: String, rawBase: String): Color? {
    val bmp = withContext(Dispatchers.IO) {
        SessionImageCache.getImmediate(bannerUrl, rawBase) ?: SessionImageCache.loadOrAwait(bannerUrl, rawBase)
    } ?: return null
    val sampled = withContext(Dispatchers.Default) { sampleBannerAccentColor(bmp) } ?: return null
    ReleaseHeaderAccentCache.accents[tag] = sampled
    return sampled
}

@Composable
internal fun rememberReleaseBannerAccent(
    tag: String,
    bannerUrl: String?,
    cacheImages: Boolean,
    isExpanded: Boolean,
): Color? {
    var accent by remember(tag) { mutableStateOf(ReleaseHeaderAccentCache.accents[tag]) }
    val context = LocalContext.current
    val rawBase = stringResource(R.string.url_github_raw_base)
    // Independent of banner visibility: warms the accent while the card is still collapsed.
    LaunchedEffect(tag, bannerUrl, rawBase, cacheImages) {
        SessionImageCache.bindDiskCache(context.cacheDir, cacheImages)
        if (accent != null || bannerUrl == null) return@LaunchedEffect
        loadSampledBannerAccent(tag, bannerUrl, rawBase)?.let { accent = it }
    }
    // The banner image is never composed while showBannerInCollapsed=false, so a failed
    // or skipped first load would leave the expanded header unaccented forever.
    // Retry exactly on expand; the 260ms animateColorAsState below morphs to the result.
    LaunchedEffect(tag, bannerUrl, isExpanded) {
        if (!isExpanded || accent != null || bannerUrl == null) return@LaunchedEffect
        loadSampledBannerAccent(tag, bannerUrl, rawBase)?.let { accent = it }
    }
    return accent
}
