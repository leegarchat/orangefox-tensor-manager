package com.orangefox.tensor.manager.install

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File
import com.orangefox.tensor.manager.data.BackupEntry
import com.orangefox.tensor.manager.data.InstallDestinationMode
import com.orangefox.tensor.manager.data.InstallRunOutcome
import com.orangefox.tensor.manager.data.LayoutTypeOption
import com.orangefox.tensor.manager.data.PreparedAioEnv
import com.orangefox.tensor.manager.data.TargetSlotOption
import com.orangefox.tensor.manager.data.TouchModuleMode
import com.orangefox.tensor.manager.data.VendorBootSourceMode
import com.orangefox.tensor.manager.data.AssetDl
import com.orangefox.tensor.manager.data.ReleaseAsset
import com.orangefox.tensor.manager.data.ReleaseInfo

sealed interface InstallTargetRequest {
    data class FromAioFile(val title: String, val zipFile: File) : InstallTargetRequest
    data class FromRawImgFile(val title: String, val imgFile: File) : InstallTargetRequest
    data class FromBackup(val backup: BackupEntry) : InstallTargetRequest
}

sealed interface InstallDeleteRequest {
    data class Imported(val file: File) : InstallDeleteRequest
    data class Backup(val backup: BackupEntry) : InstallDeleteRequest
    data class CachedAsset(val release: ReleaseInfo, val asset: ReleaseAsset) : InstallDeleteRequest
}

object InstallSubmenuSession {
    var activeTarget: InstallTargetRequest? = null
    var preparedEnv by mutableStateOf<PreparedAioEnv?>(null)
    var sourceMode by mutableStateOf(VendorBootSourceMode.LiveSlot)
    var customSourceImg by mutableStateOf<File?>(null)
    var destinationMode by mutableStateOf(InstallDestinationMode.FlashToBlock)
    var targetSlot by mutableStateOf(TargetSlotOption.Both)
    var strip16k by mutableStateOf(false)
    var touchMode by mutableStateOf(TouchModuleMode.Auto)
    var customTouchLgz by mutableStateOf<File?>(null)
    var minFreeMb by mutableFloatStateOf(10f)
    var layoutType by mutableStateOf(LayoutTypeOption.TypeA)
    var runOutcome by mutableStateOf<InstallRunOutcome?>(null)
    val consoleLogs = mutableStateListOf<String>()

    fun ensureForTarget(target: InstallTargetRequest) {
        if (activeTarget == target) return
        customSourceImg?.delete()
        customTouchLgz?.delete()
        activeTarget = target
        preparedEnv = null
        sourceMode = VendorBootSourceMode.LiveSlot
        customSourceImg = null
        destinationMode = InstallDestinationMode.FlashToBlock
        targetSlot = TargetSlotOption.Both
        strip16k = false
        touchMode = TouchModuleMode.Auto
        customTouchLgz = null
        minFreeMb = 10f
        layoutType = LayoutTypeOption.TypeA
        runOutcome = null
        consoleLogs.clear()
    }

    fun clear() {
        customSourceImg?.delete()
        customTouchLgz?.delete()
        activeTarget = null
        preparedEnv = null
        customSourceImg = null
        customTouchLgz = null
        runOutcome = null
        consoleLogs.clear()
    }
}

fun selectInstallAssetsForRelease(
    release: ReleaseInfo,
    downloads: Map<String, AssetDl>,
): List<ReleaseAsset> {
    val installable = release.assets.filter { it.isInstallable }
    if (installable.isEmpty()) return emptyList()

    val cachedAssets = installable.filter { asset ->
        downloads["${release.tag}/${asset.name}"] is AssetDl.Done
    }
    if (cachedAssets.isNotEmpty()) return cachedAssets

    val aioAssets = installable.filter { it.isAioZip }
    val imgAssets = installable.filter { it.isImgFile }
    val preferredFullAio = aioAssets.firstOrNull { asset ->
        val low = asset.name.lowercase()
        low.contains("full") && !low.contains("compressed")
    } ?: aioAssets.firstOrNull { asset ->
        asset.name.lowercase().contains("full")
    } ?: aioAssets.firstOrNull()

    return buildList {
        if (preferredFullAio != null) add(preferredFullAio)
        addAll(imgAssets)
    }
}

internal fun resolveUriFileName(context: Context, uri: Uri): String {
    var result: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) result = cursor.getString(idx)
        }
    }
    return result ?: uri.lastPathSegment?.substringAfterLast('/') ?: "imported_${System.currentTimeMillis()}.zip"
}
