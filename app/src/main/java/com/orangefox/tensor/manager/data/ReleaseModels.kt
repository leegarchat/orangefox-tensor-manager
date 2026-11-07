package com.orangefox.tensor.manager.data

import androidx.annotation.StringRes
import com.orangefox.tensor.manager.R

/**
 * Release metadata shared by the releases, install and update flows.
 * Lives in data/ so feature packages only depend on data/ (never on each other).
 */
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
    val publishedAt: String? = null,
)

sealed interface AssetDl {
    data class Downloading(val progress: Float?) : AssetDl
    data class Done(val path: String) : AssetDl
    data class Error(val message: String) : AssetDl
}

enum class ReleaseFilterMode(@StringRes val labelRes: Int) {
    Main(R.string.filter_releases_main),
    Prerelease(R.string.filter_releases_prerelease),
    All(R.string.filter_releases_all);

    companion object {
        fun fromOrdinal(o: Int): ReleaseFilterMode = entries.getOrElse(o) { All }
    }
}
