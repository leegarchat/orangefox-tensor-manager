package com.orangefox.tensor.manager.install

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.ui.ExpressiveLinearProgress
import com.orangefox.tensor.manager.ui.ExpressiveSegmentedRow
import com.orangefox.tensor.manager.ui.edgeOutline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.orangefox.tensor.manager.AppPrefs
import com.orangefox.tensor.manager.R
import com.orangefox.tensor.manager.data.AssetDl
import com.orangefox.tensor.manager.data.BackupEntry
import com.orangefox.tensor.manager.data.ReleaseAsset
import com.orangefox.tensor.manager.data.ReleaseFilterMode
import com.orangefox.tensor.manager.data.ReleaseInfo
import com.orangefox.tensor.manager.data.ReleasesSessionCache
import com.orangefox.tensor.manager.data.RecoveryStateCache
import com.orangefox.tensor.manager.data.RootInstallerEngine
import com.orangefox.tensor.manager.ui.dialogs.ConfirmDeleteDialog

@Composable
fun RootRequiredDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
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
            Button(
                onClick = onDismiss,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.edgeOutline()),
            ) {
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
    onInstallBusyChange: (Boolean) -> Unit = {},
    submenuTarget: InstallTargetRequest?,
    onSubmenuTargetChange: (InstallTargetRequest?) -> Unit,
    filesEpoch: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var backupRestoreDialogTarget by remember { mutableStateOf<BackupEntry?>(null) }
    var showNoRootDialog by remember { mutableStateOf(false) }
    var pendingDeleteRequest by remember { mutableStateOf<InstallDeleteRequest?>(null) }

    var importedItems by remember { mutableStateOf(RootInstallerEngine.loadImportedItems(context)) }
    var backups by remember { mutableStateOf(RootInstallerEngine.loadBackups(context)) }

    fun refreshLocalLists() {
        importedItems = RootInstallerEngine.loadImportedItems(context)
        backups = RootInstallerEngine.loadBackups(context)
    }

    LaunchedEffect(filesEpoch) {
        ReleasesSessionCache.ensureLoaded(scope, context, context.cacheDir)
        refreshLocalLists()
    }

    LaunchedEffect(externalLaunchRequest) {
        if (externalLaunchRequest != null) {
            onSubmenuTargetChange(externalLaunchRequest)
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
                    onSubmenuTargetChange(request)
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

    pendingDeleteRequest?.let { req ->
        val fileName = when (req) {
            is InstallDeleteRequest.Imported -> req.file.name
            is InstallDeleteRequest.Backup -> "vendor_boot_${req.backup.sourceSlot}.img"
            is InstallDeleteRequest.CachedAsset -> req.asset.name.substringAfterLast('/')
        }
        ConfirmDeleteDialog(
            fileName = fileName,
            onConfirm = {
                pendingDeleteRequest = null
                when (req) {
                    is InstallDeleteRequest.Imported -> req.file.delete()
                    is InstallDeleteRequest.Backup -> RootInstallerEngine.deleteBackup(context, req.backup)
                    is InstallDeleteRequest.CachedAsset -> ReleasesSessionCache.deleteCachedAsset(
                        context, req.release, req.asset, showToast = true,
                    )
                }
                refreshLocalLists()
            },
            onDismiss = { pendingDeleteRequest = null },
        )
    }

    backupRestoreDialogTarget?.let { backupEntry ->
        BackupRestoreModal(
            backup = backupEntry,
            onDismiss = { backupRestoreDialogTarget = null },
            onBackupRestored = {
                backupRestoreDialogTarget = null
                refreshLocalLists()
            },
            onInstallBusyChange = onInstallBusyChange,
        )
    }

    AnimatedContent(
        targetState = submenuTarget,
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
                onBack = {
                    onSubmenuTargetChange(null)
                    refreshLocalLists()
                },
                onInstallBusyChange = onInstallBusyChange,
            )
        } else {
            val releases = ReleasesSessionCache.releases
            val downloads = ReleasesSessionCache.downloads
            var releaseFilterMode by remember {
                mutableStateOf(ReleaseFilterMode.fromOrdinal(prefs.releaseFilterMode))
            }

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
                item(key = "installed_recovery_status") {
                    InstalledRecoveryStatusCard(
                        onManualRefresh = {
                            RecoveryStateCache.refresh(context, scope, forceUnpack = true)
                        },
                    )
                }

                item(key = "top_controls") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(), RoundedCornerShape(22.dp)),
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
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.edgeOutline()),
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
                                ReleasesSessionCache.copyLocalFileToDownloads(context, item.file, item.name)
                            },
                            onDelete = {
                                pendingDeleteRequest = InstallDeleteRequest.Imported(item.file)
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
                                ReleasesSessionCache.copyLocalFileToDownloads(context, backup.file, backup.file.name)
                            },
                            onDelete = {
                                pendingDeleteRequest = InstallDeleteRequest.Backup(backup)
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

                item(key = "release_filter_bar") {
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                        ExpressiveSegmentedRow(
                            items = ReleaseFilterMode.entries,
                            selectedItem = releaseFilterMode,
                            onSelect = { mode ->
                                releaseFilterMode = mode
                                prefs.releaseFilterMode = mode.ordinal
                            },
                            labelProvider = { stringResource(it.labelRes) },
                        )
                    }
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
                    val filteredReleases = when (releaseFilterMode) {
                        ReleaseFilterMode.Main -> releases.filter { !it.prerelease }
                        ReleaseFilterMode.Prerelease -> releases.filter { it.prerelease }
                        ReleaseFilterMode.All -> releases
                    }
                    filteredReleases.forEach { rel ->
                        val assets = selectInstallAssetsForRelease(rel, downloads)
                        if (assets.isNotEmpty()) {
                            item(key = "rel_inst_${rel.tag}") {
                                ReleaseInstallCard(
                                    release = rel,
                                    assets = assets,
                                    downloads = downloads,
                                    onOpenRelease = { onOpenReleaseOnReleasesTab(rel.tag) },
                                    onDownload = { asset -> onStartDownload(rel, asset) },
                                    onDelete = { asset ->
                                        pendingDeleteRequest = InstallDeleteRequest.CachedAsset(rel, asset)
                                    },
                                    onCancelDownload = { asset ->
                                        ReleasesSessionCache.cancelDownload(rel, asset)
                                    },
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
