package com.orangefox.tensor.manager.releases

import com.orangefox.tensor.manager.data.ReleaseAsset
import com.orangefox.tensor.manager.data.ReleaseInfo

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
