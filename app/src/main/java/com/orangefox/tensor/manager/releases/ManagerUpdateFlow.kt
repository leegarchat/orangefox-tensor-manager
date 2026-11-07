package com.orangefox.tensor.manager.releases

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.compose.ManagedActivityResultLauncher
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.ui.edgeOutline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.orangefox.tensor.manager.AppPrefs
import com.orangefox.tensor.manager.BuildConfig
import com.orangefox.tensor.manager.MainActivity
import com.orangefox.tensor.manager.R
import com.orangefox.tensor.manager.data.AssetDl
import com.orangefox.tensor.manager.data.ReleaseInfo
import com.orangefox.tensor.manager.data.ReleasesSessionCache
import com.orangefox.tensor.manager.data.RootInstallerEngine

/**
 * Manager self-update flow: offer state, effects and dialogs.
 * Moved verbatim from Root (MainActivity): identical behavior, only the
 * surrounding scope changed (Root locals -> controller properties).
 */
internal class ManagerUpdateController(
    val scope: CoroutineScope,
    val context: Context,
    val prefs: AppPrefs,
) {
    var unknownSourcesLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>? = null
    var managerUpdateOffer by mutableStateOf<Pair<ManagerUpdateOption?, ManagerUpdateOption?>?>(null)
    var updatePromptedForCode by mutableStateOf(0)
    var pendingManagerApk by mutableStateOf<ManagerUpdateOption?>(null)
    var managerUpdating by mutableStateOf(false)
    var pendingNativeFile by mutableStateOf<File?>(null)

    fun installManagerApkNative(file: File) {
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            context.startActivity(intent)
        }
    }

    fun onUnknownSourcesResult() {
        if (context.packageManager.canRequestPackageInstalls()) {
            pendingNativeFile?.let { installManagerApkNative(it) }
        }
        pendingNativeFile = null
    }

    fun startManagerInstall(file: File) {
        if (managerUpdating) return
        scope.launch(Dispatchers.IO) {
            val hasRoot = RootInstallerEngine.checkRootAccess()
            if (hasRoot) {
                withContext(Dispatchers.Main) { managerUpdating = true }
                val err = RootInstallerEngine.installApkViaRoot(context, file)
                withContext(Dispatchers.Main) {
                    managerUpdating = false
                    Toast.makeText(
                        context,
                        if (err == null) {
                            context.getString(R.string.update_install_root_ok)
                        } else {
                            context.getString(R.string.update_install_root_fail, err)
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    if (context.packageManager.canRequestPackageInstalls()) {
                        installManagerApkNative(file)
                    } else {
                        pendingNativeFile = file
                        Toast.makeText(
                            context,
                            context.getString(R.string.update_need_unknown_sources),
                            Toast.LENGTH_LONG,
                        ).show()
                        unknownSourcesLauncher?.launch(
                            android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                android.net.Uri.parse("package:${context.packageName}"),
                            )
                        )
                    }
                }
            }
        }
    }

        fun beginManagerUpdate(opt: ManagerUpdateOption) {
            managerUpdateOffer = null
            val key = "${opt.release.tag}/${opt.asset.name}"
            val done = ReleasesSessionCache.downloads[key] as? AssetDl.Done
            if (done != null && File(done.path).isFile) {
                startManagerInstall(File(done.path))
            } else {
                pendingManagerApk = opt
                ReleasesSessionCache.startDownload(context, opt.release, opt.asset)
            }
        }

    fun onReleasesSnapshot(releases: List<ReleaseInfo>?) {
        val (stable, beta) = findManagerUpdates(releases)
        val best = maxOf(stable?.code ?: 0, beta?.code ?: 0)
        val current = BuildConfig.VERSION_CODE
        val refused = prefs.refusedManagerUpdateCode
        if (best > current && best > refused && updatePromptedForCode != best) {
            updatePromptedForCode = best
            managerUpdateOffer = stable to beta
        }
    }
}

@Composable
internal fun rememberManagerUpdateController(prefs: AppPrefs): ManagerUpdateController {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val controller = remember(scope, context) { ManagerUpdateController(scope, context, prefs) }
    controller.unknownSourcesLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) {
        controller.onUnknownSourcesResult()
    }
    return controller
}

@Composable
internal fun ManagerUpdateHost(
    controller: ManagerUpdateController,
    releasesSnapshot: List<ReleaseInfo>?,
) {
    LaunchedEffect(releasesSnapshot) {
        controller.onReleasesSnapshot(releasesSnapshot)
    }
    controller.pendingManagerApk?.let { opt ->
        val key = "${opt.release.tag}/${opt.asset.name}"
        val st = ReleasesSessionCache.downloads[key]
        LaunchedEffect(st) {
            if (st is AssetDl.Done && File(st.path).isFile) {
                controller.pendingManagerApk = null
                controller.startManagerInstall(File(st.path))
            } else if (st is AssetDl.Error) {
                controller.pendingManagerApk = null
            }
        }
    }

    controller.managerUpdateOffer?.let { (stable, beta) ->
        val best = maxOf(stable?.code ?: 0, beta?.code ?: 0)
        val stableTxt = stable?.code?.toString() ?: "—"
        val betaTxt = beta?.code?.toString() ?: "—"
        AlertDialog(
            onDismissRequest = {
                controller.prefs.refusedManagerUpdateCode = best
                controller.managerUpdateOffer = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(22.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            title = {
                Text(
                    text = stringResource(R.string.update_manager_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = stringResource(
                            R.string.update_manager_text,
                            BuildConfig.VERSION_CODE,
                            stringResource(R.string.update_manager_channels, stableTxt, betaTxt),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stable?.let { opt ->
                        Button(
                            onClick = { controller.beginManagerUpdate(opt) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.edgeOutline()),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.update_channel_stable, opt.code))
                        }
                    }
                    beta?.let { opt ->
                        FilledTonalButton(
                            onClick = { controller.beginManagerUpdate(opt) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer.edgeOutline()),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.update_channel_beta, opt.code))
                        }
                    }
                    TextButton(
                        onClick = {
                            controller.prefs.refusedManagerUpdateCode = best
                            controller.managerUpdateOffer = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (controller.managerUpdating) {
        AlertDialog(
            onDismissRequest = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(22.dp),
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = {
                Text(
                    text = stringResource(R.string.update_install_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    com.orangefox.tensor.manager.ui.ExpressiveLinearProgress()
                    Text(
                        text = stringResource(R.string.update_installing_root),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
        )
    }

}
