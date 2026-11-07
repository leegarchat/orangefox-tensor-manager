package com.orangefox.tensor.manager.install

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.ui.ExpressiveLinearProgress
import com.orangefox.tensor.manager.ui.ExpressiveSegmentedRow
import com.orangefox.tensor.manager.ui.edgeOutline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.orangefox.tensor.manager.R
import com.orangefox.tensor.manager.data.BackupEntry
import com.orangefox.tensor.manager.data.InstallRunOutcome
import com.orangefox.tensor.manager.data.RootInstallerEngine
import com.orangefox.tensor.manager.data.TargetSlotOption
import com.orangefox.tensor.manager.data.RecoveryStateCache

@Composable
internal fun BackupRestoreModal(
    backup: BackupEntry,
    onDismiss: () -> Unit,
    onBackupRestored: () -> Unit,
    onInstallBusyChange: (Boolean) -> Unit = {},
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

    LaunchedEffect(isFlashing) {
        onInstallBusyChange(isFlashing)
    }

    AlertDialog(
        onDismissRequest = { if (!isFlashing) onDismiss() },
        shape = RoundedCornerShape(22.dp),
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
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.secondaryContainer.edgeOutline(), RoundedCornerShape(12.dp)),
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
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest.edgeOutline(), RoundedCornerShape(12.dp)),
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
                Button(
                    onClick = onBackupRestored,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.edgeOutline()),
                ) {
                    Text(stringResource(R.string.action_done))
                }
            } else {
                Button(
                    enabled = !isFlashing,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.edgeOutline()),
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
                            if (runOutcome is InstallRunOutcome.Success) {
                                RecoveryStateCache.refresh(context, scope, forceUnpack = false)
                            }
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
