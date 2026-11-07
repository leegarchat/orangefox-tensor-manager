package com.orangefox.tensor.manager.install

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.orangefox.tensor.manager.ui.edgeOutline
import com.orangefox.tensor.manager.ui.SettingSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.orangefox.tensor.manager.AppPrefs
import com.orangefox.tensor.manager.R
import com.orangefox.tensor.manager.data.AioInstallConfig
import com.orangefox.tensor.manager.data.InstallDestinationMode
import com.orangefox.tensor.manager.data.InstallRunOutcome
import com.orangefox.tensor.manager.data.LayoutTypeOption
import com.orangefox.tensor.manager.data.RootInstallerEngine
import com.orangefox.tensor.manager.data.TargetSlotOption
import com.orangefox.tensor.manager.data.TouchModuleMode
import com.orangefox.tensor.manager.data.VendorBootSourceMode
import com.orangefox.tensor.manager.data.RecoveryStateCache

@Composable
internal fun InstallerSubmenuView(
    topInset: Dp,
    target: InstallTargetRequest,
    prefs: AppPrefs,
    onBack: () -> Unit,
    onInstallBusyChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    remember(target) { InstallSubmenuSession.ensureForTarget(target) }
    var preparedEnv by InstallSubmenuSession::preparedEnv
    var isUnpacking by remember(target) {
        mutableStateOf(target is InstallTargetRequest.FromAioFile && preparedEnv == null)
    }
    var unpackTitle by remember { mutableStateOf("") }
    var unpackSubtitle by remember { mutableStateOf("") }
    var unpackProgress by remember { mutableStateOf<Float?>(0.1f) }
    var unpackError by remember { mutableStateOf<String?>(null) }

    var sourceMode by InstallSubmenuSession::sourceMode
    var customSourceImg by InstallSubmenuSession::customSourceImg
    var destinationMode by InstallSubmenuSession::destinationMode
    var targetSlot by InstallSubmenuSession::targetSlot
    var strip16k by InstallSubmenuSession::strip16k
    var spaceErrorSeen by remember { mutableStateOf(prefs.spaceErrorSeen) }
    var saveBackup by remember { mutableStateOf(prefs.backupVendorBootOnFlash) }

    var touchMode by InstallSubmenuSession::touchMode
    var customTouchLgz by InstallSubmenuSession::customTouchLgz
    var minFreeMb by InstallSubmenuSession::minFreeMb
    var layoutType by InstallSubmenuSession::layoutType

    var isRunningInstall by remember { mutableStateOf(false) }
    var runOutcome by InstallSubmenuSession::runOutcome
    val consoleLogs = InstallSubmenuSession.consoleLogs

    val handleBack = {
        InstallSubmenuSession.clear()
        onBack()
    }

    LaunchedEffect(isRunningInstall) {
        onInstallBusyChange(isRunningInstall)
    }

    BackHandler {
        if (!isRunningInstall) handleBack()
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
                customSourceImg?.delete()
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
                customTouchLgz?.delete()
                customTouchLgz = dest
            }
        }
    }

    LaunchedEffect(target) {
        if (target is InstallTargetRequest.FromAioFile && preparedEnv == null) {
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

    fun launchInstallRun(useStrip16k: Boolean = strip16k) {
        if (isRunningInstall) return
        strip16k = useStrip16k
        isRunningInstall = true
        runOutcome = null
        consoleLogs.clear()
        val appendLog: (String) -> Unit = { line ->
            scope.launch(Dispatchers.Main) { consoleLogs.add(line) }
        }
        scope.launch {
            val outcome = try {
                when (target) {
                    is InstallTargetRequest.FromAioFile -> {
                        val env = preparedEnv
                        if (env == null) {
                            InstallRunOutcome.Failure(
                                message = context.getString(R.string.unpack_step_prepare_title),
                                logs = consoleLogs.toList(),
                            )
                        } else {
                            RootInstallerEngine.executeAioInstall(
                                context = context,
                                env = env,
                                config = AioInstallConfig(
                                    sourceMode = sourceMode,
                                    customSourceImg = customSourceImg,
                                    destinationMode = destinationMode,
                                    targetSlot = targetSlot,
                                    strip16k = useStrip16k,
                                    saveBackupBeforeFlash = saveBackup,
                                    touchMode = touchMode,
                                    customTouchLgz = customTouchLgz,
                                    minFreeMb = minFreeMb.toInt(),
                                    layoutType = layoutType,
                                ),
                                onLog = appendLog,
                            )
                        }
                    }
                    is InstallTargetRequest.FromRawImgFile -> {
                        RootInstallerEngine.flashRawImage(
                            context = context,
                            imageFile = target.imgFile,
                            targetSlot = targetSlot,
                            isBackupRestore = false,
                            saveBackupBeforeFlash = saveBackup,
                            onLog = appendLog,
                        )
                    }
                    is InstallTargetRequest.FromBackup -> {
                        RootInstallerEngine.flashRawImage(
                            context = context,
                            imageFile = target.backup.file,
                            targetSlot = targetSlot,
                            isBackupRestore = true,
                            saveBackupBeforeFlash = false,
                            onLog = appendLog,
                        )
                    }
                }
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                appendLog(context.getString(R.string.install_unexpected_error, msg))
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
            if (outcome is InstallRunOutcome.Success && outcome.flashedSlots.isNotEmpty()) {
                RecoveryStateCache.refresh(context, scope, forceUnpack = false)
            }
            isRunningInstall = false
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
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(), RoundedCornerShape(22.dp)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.IconButton(
                        enabled = !isRunningInstall,
                        onClick = { handleBack() },
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
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.errorContainer.edgeOutline(), RoundedCornerShape(20.dp)),
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
                        Button(onClick = { handleBack() }) {
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
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(), RoundedCornerShape(22.dp)),
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
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline()),
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
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(), RoundedCornerShape(22.dp)),
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
                            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.errorContainer.edgeOutline(), RoundedCornerShape(20.dp)),
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
                                        onClick = { launchInstallRun(useStrip16k = true) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError,
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.edgeOutline()),
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
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline()),
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
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(), RoundedCornerShape(22.dp)),
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
            if (target !is InstallTargetRequest.FromBackup &&
                destinationMode != InstallDestinationMode.SaveToDownloads
            ) {
                item(key = "cfg_backup") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(), RoundedCornerShape(22.dp)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = !saveBackup
                                    saveBackup = next
                                    prefs.backupVendorBootOnFlash = next
                                }
                                .padding(16.dp),
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
                                checked = saveBackup,
                                onCheckedChange = {
                                    saveBackup = it
                                    prefs.backupVendorBootOnFlash = it
                                },
                            )
                        }
                    }
                }
            }
            item(key = "run_action_button") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isRunningInstall) {
                        ExpressiveLinearProgress()
                    }
                    Button(
                        enabled = !isRunningInstall,
                        onClick = {
                            launchInstallRun(useStrip16k = strip16k)
                                                },
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.edgeOutline()),
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
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer.edgeOutline()),
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
                            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest.edgeOutline(), RoundedCornerShape(16.dp)),
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
