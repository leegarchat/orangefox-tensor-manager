package com.orangefox.tensor.manager

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.ui.ExpandableGroupCard
import com.orangefox.tensor.manager.ui.ExpressiveUnpackCard
import com.orangefox.tensor.manager.ui.ExpressiveLinearProgress
import com.orangefox.tensor.manager.ui.ExpressiveSegmentedRow
import com.orangefox.tensor.manager.ui.SettingSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface InstallTargetRequest {
    data class FromAioFile(val title: String, val zipFile: File) : InstallTargetRequest
    data class FromRawImgFile(val title: String, val imgFile: File) : InstallTargetRequest
    data class FromBackup(val backup: BackupEntry) : InstallTargetRequest
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

private fun resolveUriFileName(context: Context, uri: Uri): String {
    var result: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) result = cursor.getString(idx)
        }
    }
    return result ?: uri.lastPathSegment?.substringAfterLast('/') ?: "imported_${System.currentTimeMillis()}.zip"
}

@Composable
fun RootRequiredDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_bolt),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.root_required_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.root_required_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        },
    )
}

@Composable
fun InstallScreen(
    topInset: Dp,
    prefs: AppPrefs,
    externalLaunchRequest: InstallTargetRequest?,
    onConsumeExternalRequest: () -> Unit,
    onOpenReleaseOnReleasesTab: (String) -> Unit,
    onStartDownload: (ReleaseInfo, ReleaseAsset) -> Unit,
    onDeleteCachedAsset: (ReleaseInfo, ReleaseAsset) -> Unit,
    onCopyCachedAssetToDownloads: (ReleaseInfo, ReleaseAsset) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var activeSubmenuTarget by remember { mutableStateOf<InstallTargetRequest?>(null) }
    var backupRestoreDialogTarget by remember { mutableStateOf<BackupEntry?>(null) }
    var showNoRootDialog by remember { mutableStateOf(false) }

    var saveBackupOnFlash by remember { mutableStateOf(prefs.backupVendorBootOnFlash) }
    var importedItems by remember { mutableStateOf(RootInstallerEngine.loadImportedItems(context)) }
    var backups by remember { mutableStateOf(RootInstallerEngine.loadBackups(context)) }

    fun refreshLocalLists() {
        importedItems = RootInstallerEngine.loadImportedItems(context)
        backups = RootInstallerEngine.loadBackups(context)
    }

    LaunchedEffect(Unit) {
        ReleasesSessionCache.ensureLoaded(scope, context.cacheDir)
        refreshLocalLists()
    }

    LaunchedEffect(externalLaunchRequest) {
        if (externalLaunchRequest != null) {
            activeSubmenuTarget = externalLaunchRequest
            onConsumeExternalRequest()
        }
    }

    fun requestInstallWithRootCheck(request: InstallTargetRequest) {
        scope.launch {
            val hasRoot = RootInstallerEngine.checkRootAccess()
            if (!hasRoot) {
                showNoRootDialog = true
            } else {
                if (request is InstallTargetRequest.FromBackup) {
                    backupRestoreDialogTarget = request.backup
                } else {
                    activeSubmenuTarget = request
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val rawName = resolveUriFileName(context, uri)
                val lower = rawName.lowercase()
                if (!lower.endsWith(".zip") && !lower.endsWith(".img")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.import_only_zip_or_img),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@launch
                }
                val destDir = RootInstallerEngine.getImportedDir(context)
                val destFile = File(destDir, rawName)
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    destFile.outputStream().use { outs -> ins.copyTo(outs) }
                }
                withContext(Dispatchers.Main) {
                    refreshLocalLists()
                    Toast.makeText(
                        context,
                        context.getString(R.string.import_success, rawName),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.import_failed, e.message ?: ""),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    if (showNoRootDialog) {
        RootRequiredDialog(onDismiss = { showNoRootDialog = false })
    }

    backupRestoreDialogTarget?.let { backupEntry ->
        BackupRestoreModal(
            backup = backupEntry,
            onDismiss = { backupRestoreDialogTarget = null },
            onBackupRestored = {
                backupRestoreDialogTarget = null
                refreshLocalLists()
            },
        )
    }

    AnimatedContent(
        targetState = activeSubmenuTarget,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(240)) { -it / 4 } + fadeOut(tween(160)))
            } else {
                (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(160)))
            }
        },
        label = "InstallScreenSubmenuTransition",
    ) { submenuTarget ->
        if (submenuTarget != null) {
            InstallerSubmenuView(
                topInset = topInset,
                target = submenuTarget,
                prefs = prefs,
                saveBackupDefault = saveBackupOnFlash,
                onBack = {
                    activeSubmenuTarget = null
                    refreshLocalLists()
                },
            )
        } else {
            val releases = ReleasesSessionCache.releases
            val downloads = ReleasesSessionCache.downloads

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = topInset + 4.dp,
                    bottom = 136.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "top_controls") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Button(
                                onClick = {
                                    importLauncher.launch(arrayOf("*/*"))
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_download),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.action_import_aio_or_img),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { val next = !saveBackupOnFlash; saveBackupOnFlash = next; prefs.backupVendorBootOnFlash = next },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.install_backup_toggle_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(R.string.install_backup_toggle_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                androidx.compose.material3.Switch(
                                    checked = saveBackupOnFlash,
                                    onCheckedChange = {
                                        saveBackupOnFlash = it
                                        prefs.backupVendorBootOnFlash = it
                                    },
                                )
                            }
                        }
                    }
                }

                if (importedItems.isNotEmpty()) {
                    item(key = "header_imported") {
                        Text(
                            text = stringResource(R.string.install_section_imported),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    items(importedItems, key = { "imp_${it.id}" }) { item ->
                        LocalFileInstallCard(
                            title = item.name,
                            subtitle = stringResource(R.string.asset_size_mb, item.sizeBytes / 1048576.0),
                            badgeText = stringResource(R.string.badge_external),
                            badgeContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            badgeContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onInstall = {
                                val req = if (item.isAioZip) {
                                    InstallTargetRequest.FromAioFile(item.name, item.file)
                                } else {
                                    InstallTargetRequest.FromRawImgFile(item.name, item.file)
                                }
                                requestInstallWithRootCheck(req)
                            },
                            onCopyToDownloads = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val saved = copyFileToPublicDownloads(context, item.file, item.name)
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
                            },
                            onDelete = {
                                item.file.delete()
                                refreshLocalLists()
                            },
                        )
                    }
                }

                if (backups.isNotEmpty()) {
                    item(key = "header_backups") {
                        Text(
                            text = stringResource(R.string.install_section_backups),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    items(backups, key = { "bak_${it.id}" }) { backup ->
                        LocalFileInstallCard(
                            title = "vendor_boot_${backup.sourceSlot}.img • ${backup.formattedDate}",
                            subtitle = stringResource(
                                R.string.backup_card_subtitle,
                                backup.sourceSlot.uppercase(),
                                backup.sizeBytes / 1048576.0,
                            ),
                            badgeText = stringResource(R.string.badge_backup),
                            badgeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            badgeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onInstall = {
                                requestInstallWithRootCheck(InstallTargetRequest.FromBackup(backup))
                            },
                            onCopyToDownloads = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val saved = copyFileToPublicDownloads(context, backup.file, backup.file.name)
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
                            },
                            onDelete = {
                                RootInstallerEngine.deleteBackup(context, backup)
                                refreshLocalLists()
                            },
                        )
                    }
                }

                item(key = "header_releases") {
                    Text(
                        text = stringResource(R.string.install_section_releases),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                if (releases == null) {
                    item(key = "loading_rel") {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ExpressiveLinearProgress()
                            Text(stringResource(R.string.releases_loading))
                        }
                    }
                } else {
                    releases.forEach { rel ->
                        val assets = selectInstallAssetsForRelease(rel, downloads)
                        if (assets.isNotEmpty()) {
                            item(key = "rel_inst_${rel.tag}") {
                                ReleaseInstallCard(
                                    release = rel,
                                    assets = assets,
                                    downloads = downloads,
                                    onOpenRelease = { onOpenReleaseOnReleasesTab(rel.tag) },
                                    onDownload = { asset -> onStartDownload(rel, asset) },
                                    onDelete = { asset -> onDeleteCachedAsset(rel, asset) },
                                    onCopyToDownloads = { asset -> onCopyCachedAssetToDownloads(rel, asset) },
                                    onInstall = { asset ->
                                        val done = downloads["${rel.tag}/${asset.name}"] as? AssetDl.Done
                                        if (done != null) {
                                            val file = File(done.path)
                                            val title = "${rel.name} (${asset.name})"
                                            val req = if (asset.isAioZip) {
                                                InstallTargetRequest.FromAioFile(title, file)
                                            } else {
                                                InstallTargetRequest.FromRawImgFile(title, file)
                                            }
                                            requestInstallWithRootCheck(req)
                                        }
                                    },
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
private fun LocalFileInstallCard(
    title: String,
    subtitle: String,
    badgeText: String,
    badgeContainerColor: androidx.compose.ui.graphics.Color,
    badgeContentColor: androidx.compose.ui.graphics.Color,
    onInstall: () -> Unit,
    onCopyToDownloads: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            color = badgeContainerColor,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeContentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                    )
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
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
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

@Composable
private fun ReleaseInstallCard(
    release: ReleaseInfo,
    assets: List<ReleaseAsset>,
    downloads: Map<String, AssetDl>,
    onOpenRelease: () -> Unit,
    onDownload: (ReleaseAsset) -> Unit,
    onDelete: (ReleaseAsset) -> Unit,
    onCopyToDownloads: (ReleaseAsset) -> Unit,
    onInstall: (ReleaseAsset) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = release.tag,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onOpenRelease,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.action_open_release),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            assets.forEachIndexed { idx, asset ->
                if (idx > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
                AssetRow(
                    asset = asset,
                    state = downloads["${release.tag}/${asset.name}"],
                    onDownload = { onDownload(asset) },
                    onDelete = { onDelete(asset) },
                    onCopyToDownloads = { onCopyToDownloads(asset) },
                    onInstall = { onInstall(asset) },
                )
            }
        }
    }
}

@Composable
private fun BackupRestoreModal(
    backup: BackupEntry,
    onDismiss: () -> Unit,
    onBackupRestored: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSlot by remember {
        mutableStateOf(
            if (backup.sourceSlot.equals("b", ignoreCase = true)) TargetSlotOption.SlotB else TargetSlotOption.SlotA
        )
    }
    var isFlashing by remember { mutableStateOf(false) }
    var runOutcome by remember { mutableStateOf<InstallRunOutcome?>(null) }
    val logs = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = { if (!isFlashing) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.restore_backup_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(
                            R.string.restore_backup_slot_hint,
                            backup.sourceSlot.uppercase(),
                            backup.formattedDate,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.install_select_target_slot),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                ExpressiveSegmentedRow(
                    items = TargetSlotOption.entries,
                    selectedItem = selectedSlot,
                    onSelect = { if (!isFlashing) selectedSlot = it },
                    labelProvider = { opt ->
                        when (opt) {
                            TargetSlotOption.SlotA -> stringResource(R.string.install_slot_a)
                            TargetSlotOption.SlotB -> stringResource(R.string.install_slot_b)
                            TargetSlotOption.Both -> stringResource(R.string.install_slot_both)
                        }
                    },
                )
                if (isFlashing) {
                    ExpressiveLinearProgress()
                }
                if (logs.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            logs.takeLast(6).forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (runOutcome is InstallRunOutcome.Success) {
                Button(onClick = onBackupRestored) {
                    Text(stringResource(R.string.action_done))
                }
            } else {
                Button(
                    enabled = !isFlashing,
                    onClick = {
                        isFlashing = true
                        logs.clear()
                        scope.launch {
                            runOutcome = RootInstallerEngine.flashRawImage(
                                context = context,
                                imageFile = backup.file,
                                targetSlot = selectedSlot,
                                isBackupRestore = true,
                                saveBackupBeforeFlash = false,
                                onLog = { line ->
                                    scope.launch(Dispatchers.Main) { logs.add(line) }
                                },
                            )
                            isFlashing = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_confirm_restore))
                }
            }
        },
        dismissButton = {
            if (!isFlashing && runOutcome !is InstallRunOutcome.Success) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun InstallerSubmenuView(
    topInset: Dp,
    target: InstallTargetRequest,
    prefs: AppPrefs,
    saveBackupDefault: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isUnpacking by remember { mutableStateOf(target is InstallTargetRequest.FromAioFile) }
    var unpackTitle by remember { mutableStateOf("") }
    var unpackSubtitle by remember { mutableStateOf("") }
    var unpackProgress by remember { mutableStateOf<Float?>(0.1f) }
    var unpackError by remember { mutableStateOf<String?>(null) }
    var preparedEnv by remember { mutableStateOf<PreparedAioEnv?>(null) }

    var sourceMode by remember { mutableStateOf(VendorBootSourceMode.LiveSlot) }
    var customSourceImg by remember { mutableStateOf<File?>(null) }
    var destinationMode by remember { mutableStateOf(InstallDestinationMode.FlashToBlock) }
    var targetSlot by remember { mutableStateOf(TargetSlotOption.Both) }
    var strip16k by remember { mutableStateOf(false) }
    var spaceErrorSeen by remember { mutableStateOf(prefs.spaceErrorSeen) }
    var saveBackup by remember { mutableStateOf(saveBackupDefault) }

    var touchMode by remember { mutableStateOf(TouchModuleMode.Auto) }
    var customTouchLgz by remember { mutableStateOf<File?>(null) }
    var minFreeMb by remember { mutableFloatStateOf(10f) }
    var layoutType by remember { mutableStateOf(LayoutTypeOption.TypeA) }

    var isRunningInstall by remember { mutableStateOf(false) }
    var runOutcome by remember { mutableStateOf<InstallRunOutcome?>(null) }
    val consoleLogs = remember { mutableStateListOf<String>() }

    BackHandler(enabled = !isRunningInstall) {
        onBack()
    }

    val customVendorBootPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val dest = File(context.cacheDir, "Picked_vendor_boot_${System.currentTimeMillis()}.img")
            context.contentResolver.openInputStream(uri)?.use { ins ->
                dest.outputStream().use { outs -> ins.copyTo(outs) }
            }
            withContext(Dispatchers.Main) {
                customSourceImg = dest
            }
        }
    }

    val customTouchPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            var name: String? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
            val dest = File(context.cacheDir, name ?: "picked_touch_${System.currentTimeMillis()}.lgz")
            context.contentResolver.openInputStream(uri)?.use { ins ->
                dest.outputStream().use { outs -> ins.copyTo(outs) }
            }
            withContext(Dispatchers.Main) {
                customTouchLgz = dest
            }
        }
    }

    LaunchedEffect(target) {
        if (target is InstallTargetRequest.FromAioFile) {
            isUnpacking = true
            unpackError = null
            RootInstallerEngine.prepareAioEnvironment(
                context = context,
                zipFile = target.zipFile,
                onStep = { t, sub, p ->
                    scope.launch(Dispatchers.Main) {
                        unpackTitle = t
                        unpackSubtitle = sub
                        unpackProgress = p
                    }
                },
            ).onSuccess { env ->
                preparedEnv = env
                isUnpacking = false
            }.onFailure { err ->
                unpackError = err.message ?: "unpack failed"
                isUnpacking = false
            }
        }
    }

    val titleText = when (target) {
        is InstallTargetRequest.FromAioFile -> target.title
        is InstallTargetRequest.FromRawImgFile -> target.title
        is InstallTargetRequest.FromBackup -> target.backup.file.name
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = topInset + 4.dp,
            bottom = 140.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "submenu_header") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.IconButton(
                        enabled = !isRunningInstall,
                        onClick = onBack,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.install_submenu_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (isUnpacking) {
            item(key = "unpacking_card") {
                ExpressiveUnpackCard(
                    title = if (unpackTitle.isNotEmpty()) unpackTitle else stringResource(R.string.unpack_step_prepare_title),
                    subtitle = unpackSubtitle,
                    progress = unpackProgress,
                )
            }
        } else if (unpackError != null) {
            item(key = "unpack_err") {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = unpackError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = onBack) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
            }
        } else if (target is InstallTargetRequest.FromAioFile) {
            val env = preparedEnv
            if (env != null) {
                item(key = "cfg_source") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.install_source_vendor_boot),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ExpressiveSegmentedRow(
                                items = VendorBootSourceMode.entries,
                                selectedItem = sourceMode,
                                onSelect = { sourceMode = it },
                                labelProvider = { mode ->
                                    when (mode) {
                                        VendorBootSourceMode.LiveSlot -> stringResource(R.string.source_from_block)
                                        VendorBootSourceMode.CustomFile -> stringResource(R.string.source_from_file)
                                    }
                                },
                            )
                            AnimatedVisibility(visible = sourceMode == VendorBootSourceMode.CustomFile) {
                                OutlinedButton(
                                    onClick = { customVendorBootPicker.launch(arrayOf("*/*")) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        customSourceImg?.name
                                            ?: stringResource(R.string.action_pick_vendor_boot_img)
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "cfg_dest") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.install_destination_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ExpressiveSegmentedRow(
                                items = InstallDestinationMode.entries,
                                selectedItem = destinationMode,
                                onSelect = { destinationMode = it },
                                labelProvider = { mode ->
                                    when (mode) {
                                        InstallDestinationMode.FlashToBlock -> stringResource(R.string.dest_flash_block)
                                        InstallDestinationMode.SaveToDownloads -> stringResource(R.string.dest_save_downloads)
                                    }
                                },
                            )
                            AnimatedVisibility(
                                visible = destinationMode == InstallDestinationMode.FlashToBlock ||
                                    sourceMode == VendorBootSourceMode.LiveSlot
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(
                                            R.string.install_slot_with_active_hint,
                                            env.activeSlot.uppercase(),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ExpressiveSegmentedRow(
                                        items = TargetSlotOption.entries,
                                        selectedItem = targetSlot,
                                        onSelect = { targetSlot = it },
                                        labelProvider = { opt ->
                                            when (opt) {
                                                TargetSlotOption.SlotA -> stringResource(R.string.install_slot_a)
                                                TargetSlotOption.SlotB -> stringResource(R.string.install_slot_b)
                                                TargetSlotOption.Both -> stringResource(R.string.install_slot_both)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                val show16kSection = env.supportsStrip16k

                if (runOutcome is InstallRunOutcome.SpaceTightError) {
                    val err = runOutcome as InstallRunOutcome.SpaceTightError
                    item(key = "space_error_banner") {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.space_error_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    text = err.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                if (env.supportsStrip16k && !strip16k) {
                                    Button(
                                        onClick = {
                                            strip16k = true
                                            isRunningInstall = true
                                            runOutcome = null
                                            consoleLogs.clear()
                                            scope.launch {
                                                val retryOutcome = RootInstallerEngine.executeAioInstall(
                                                    context = context,
                                                    env = env,
                                                    config = AioInstallConfig(
                                                        sourceMode = sourceMode,
                                                        customSourceImg = customSourceImg,
                                                        destinationMode = destinationMode,
                                                        targetSlot = targetSlot,
                                                        strip16k = true,
                                                        saveBackupBeforeFlash = saveBackup,
                                                        touchMode = touchMode,
                                                        customTouchLgz = customTouchLgz,
                                                        minFreeMb = minFreeMb.toInt(),
                                                        layoutType = layoutType,
                                                    ),
                                                    onLog = { line ->
                                                        scope.launch(Dispatchers.Main) { consoleLogs.add(line) }
                                                    },
                                                )
                                                runOutcome = retryOutcome
                                                isRunningInstall = false
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_strip_16k_retry),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (show16kSection) {
                    item(key = "cfg_strip_16k") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(22.dp),
                                ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { strip16k = !strip16k }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.strip_16k_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = stringResource(R.string.strip_16k_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                androidx.compose.material3.Switch(
                                    checked = strip16k,
                                    onCheckedChange = { strip16k = it },
                                )
                            }
                        }
                    }
                }

                item(key = "cfg_advanced") {
                    ExpandableGroupCard(
                        title = stringResource(R.string.install_advanced_title),
                        subtitle = "${env.deviceCodename} • min free ${minFreeMb.toInt()} MB • ${layoutType.name}",
                        keyTag = "adv_installer_opts",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = stringResource(R.string.install_touch_modules_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ExpressiveSegmentedRow(
                                items = TouchModuleMode.entries,
                                selectedItem = touchMode,
                                onSelect = { touchMode = it },
                                labelProvider = { m ->
                                    when (m) {
                                        TouchModuleMode.Auto -> stringResource(
                                            R.string.install_touch_auto,
                                            env.deviceCodename,
                                        )
                                        TouchModuleMode.CustomFile -> stringResource(R.string.install_touch_file)
                                        TouchModuleMode.None -> stringResource(R.string.install_touch_none)
                                    }
                                },
                            )
                            if (touchMode == TouchModuleMode.CustomFile) {
                                OutlinedButton(
                                    onClick = { customTouchPicker.launch(arrayOf("*/*")) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(customTouchLgz?.name ?: stringResource(R.string.install_pick_lgz))
                                }
                            }

                            SettingSlider(
                                title = stringResource(R.string.install_min_free_mb_title),
                                value = minFreeMb,
                                onValueChange = { minFreeMb = it },
                                valueRange = 0f..32f,
                                step = 1f,
                                defaultValue = 10f,
                                valueLabel = { stringResource(R.string.install_min_free_value, it.toInt()) },
                            )

                            Text(
                                text = stringResource(R.string.install_layout_type_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ExpressiveSegmentedRow(
                                items = LayoutTypeOption.entries,
                                selectedItem = layoutType,
                                onSelect = { layoutType = it },
                                labelProvider = { it.name },
                            )
                        }
                    }
                }
            }
        } else if (target is InstallTargetRequest.FromRawImgFile) {
            item(key = "raw_img_cfg") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.install_select_target_slot),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        ExpressiveSegmentedRow(
                            items = TargetSlotOption.entries,
                            selectedItem = targetSlot,
                            onSelect = { targetSlot = it },
                            labelProvider = { opt ->
                                when (opt) {
                                    TargetSlotOption.SlotA -> stringResource(R.string.install_slot_a)
                                    TargetSlotOption.SlotB -> stringResource(R.string.install_slot_b)
                                    TargetSlotOption.Both -> stringResource(R.string.install_slot_both)
                                }
                            },
                        )
                    }
                }
            }
        }

        if (!isUnpacking && unpackError == null) {
            item(key = "run_action_button") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isRunningInstall) {
                        ExpressiveLinearProgress()
                    }
                    Button(
                        enabled = !isRunningInstall,
                        onClick = {
                            isRunningInstall = true
                            runOutcome = null
                            consoleLogs.clear()
                            scope.launch {
                                val outcome = try {
                                    when (target) {
                                    is InstallTargetRequest.FromAioFile -> {
                                        val env = preparedEnv!!
                                        RootInstallerEngine.executeAioInstall(
                                            context = context,
                                            env = env,
                                            config = AioInstallConfig(
                                                sourceMode = sourceMode,
                                                customSourceImg = customSourceImg,
                                                destinationMode = destinationMode,
                                                targetSlot = targetSlot,
                                                strip16k = strip16k,
                                                saveBackupBeforeFlash = saveBackup,
                                                touchMode = touchMode,
                                                customTouchLgz = customTouchLgz,
                                                minFreeMb = minFreeMb.toInt(),
                                                layoutType = layoutType,
                                            ),
                                            onLog = { line ->
                                                scope.launch(Dispatchers.Main) { consoleLogs.add(line) }
                                            },
                                        )
                                    }
                                    is InstallTargetRequest.FromRawImgFile -> {
                                        RootInstallerEngine.flashRawImage(
                                            context = context,
                                            imageFile = target.imgFile,
                                            targetSlot = targetSlot,
                                            isBackupRestore = false,
                                            saveBackupBeforeFlash = saveBackup,
                                            onLog = { line ->
                                                scope.launch(Dispatchers.Main) { consoleLogs.add(line) }
                                            },
                                        )
                                    }
                                    is InstallTargetRequest.FromBackup -> {
                                        RootInstallerEngine.flashRawImage(
                                            context = context,
                                            imageFile = target.backup.file,
                                            targetSlot = targetSlot,
                                            isBackupRestore = true,
                                            saveBackupBeforeFlash = false,
                                            onLog = { line ->
                                                scope.launch(Dispatchers.Main) { consoleLogs.add(line) }
                                            },
                                        )
                                    }
                                }
                                } catch (e: Exception) {
                                    val msg = e.message ?: e.toString()
                                    scope.launch(Dispatchers.Main) {
                                        consoleLogs.add(
                                            context.getString(R.string.install_unexpected_error, msg)
                                        )
                                    }
                                    InstallRunOutcome.Failure(
                                        message = context.getString(R.string.install_unexpected_error, msg),
                                        logs = consoleLogs.toList(),
                                    )
                                }
                                if (outcome is InstallRunOutcome.SpaceTightError) {
                                    spaceErrorSeen = true
                                    prefs.spaceErrorSeen = true
                                }
                                runOutcome = outcome
                                isRunningInstall = false
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(
                            text = if (destinationMode == InstallDestinationMode.SaveToDownloads &&
                                target is InstallTargetRequest.FromAioFile
                            ) {
                                stringResource(R.string.action_build_and_save_downloads)
                            } else {
                                stringResource(R.string.action_start_flash)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (runOutcome is InstallRunOutcome.Success) {
                        val suc = runOutcome as InstallRunOutcome.Success
                        if (suc.flashedSlots.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch { RootInstallerEngine.rebootToRecovery() }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_reboot_recovery),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    if (consoleLogs.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                consoleLogs.forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
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
