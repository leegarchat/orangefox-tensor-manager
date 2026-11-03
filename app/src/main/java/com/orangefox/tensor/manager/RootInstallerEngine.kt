package com.orangefox.tensor.manager

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

data class PreparedAioEnv(
    val workDir: File,
    val installerBin: File,
    val payloadLz4: File,
    val modulesDir: File,
    val supportsStrip16k: Boolean,
    val deviceCodename: String,
    val activeSlot: String,
    val availableModuleLgz: File?,
)

data class BackupEntry(
    val id: String,
    val file: File,
    val sourceSlot: String,
    val createdAtMs: Long,
    val formattedDate: String,
    val sizeBytes: Long,
    val device: String,
)

data class ExternalInstallItem(
    val id: String,
    val name: String,
    val file: File,
    val sizeBytes: Long,
    val isAioZip: Boolean,
    val isImg: Boolean,
    val importedAtMs: Long,
)

enum class TargetSlotOption(val cliValue: String, val slots: List<String>) {
    SlotA("a", listOf("a")),
    SlotB("b", listOf("b")),
    Both("both", listOf("a", "b")),
}

enum class VendorBootSourceMode {
    LiveSlot,
    CustomFile,
}

enum class InstallDestinationMode {
    FlashToBlock,
    SaveToDownloads,
}

enum class LayoutTypeOption(val cliFlag: String, val title: String) {
    TypeA("", "Type A (Classic recovery fragment)"),
    TypeB("var1", "Type B (var1 — all-in-platform)"),
    TypeC("var2", "Type C (var2 — payload-only platform)"),
}

enum class TouchModuleMode {
    Auto,
    CustomFile,
    None,
}

data class AioInstallConfig(
    val sourceMode: VendorBootSourceMode = VendorBootSourceMode.LiveSlot,
    val customSourceImg: File? = null,
    val destinationMode: InstallDestinationMode = InstallDestinationMode.FlashToBlock,
    val targetSlot: TargetSlotOption = TargetSlotOption.Both,
    val strip16k: Boolean = false,
    val saveBackupBeforeFlash: Boolean = true,
    val touchMode: TouchModuleMode = TouchModuleMode.Auto,
    val customTouchLgz: File? = null,
    val minFreeMb: Int = 10,
    val layoutType: LayoutTypeOption = LayoutTypeOption.TypeA,
)

sealed interface InstallRunOutcome {
    data class Success(
        val flashedSlots: List<String>,
        val savedDownloadsPaths: List<String>,
        val logs: List<String>,
    ) : InstallRunOutcome

    data class SpaceTightError(
        val message: String,
        val supportsStrip16k: Boolean,
        val logs: List<String>,
    ) : InstallRunOutcome

    data class Failure(
        val message: String,
        val logs: List<String>,
    ) : InstallRunOutcome
}

object RootInstallerEngine {
    @Volatile
    private var cachedAioKey: String? = null
    @Volatile
    private var cachedAioEnv: PreparedAioEnv? = null

    fun invalidatePreparedAioCache() {
        cachedAioKey = null
        cachedAioEnv = null
    }

    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val proc = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            code == 0 && out.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    private fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun runRootCommand(cmd: String): Pair<Int, String> {
        return try {
            val proc = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            val rc = proc.waitFor()
            rc to out.trim()
        } catch (e: Exception) {
            -1 to (e.message ?: "su execution error")
        }
    }

    suspend fun getDeviceOverview(): Pair<String, String> = withContext(Dispatchers.IO) {
        val (_, dev) = runRootCommand("getprop ro.product.device")
        val (_, slotRaw) = runRootCommand("getprop ro.boot.slot_suffix")
        val slot = slotRaw.trim().removePrefix("_").ifEmpty { "a" }
        dev.trim().ifEmpty { "pixel" } to slot
    }

    suspend fun rebootToRecovery(): Boolean = withContext(Dispatchers.IO) {
        val (rc, _) = runRootCommand("reboot recovery || setprop sys.powerctl reboot,recovery")
        rc == 0
    }

    fun getBackupsDir(context: Context): File =
        File(context.filesDir, "vendor_boot_backups").apply { mkdirs() }

    fun getImportedDir(context: Context): File =
        File(context.filesDir, "imported_installers").apply { mkdirs() }

    private const val DEFAULT_VENDOR_BOOT_PART_SIZE = 67_108_864L // 64 MiB

    /**
     * Дописывает выходной патченный .img нулями (0x00) до точного размера блочного устройства (64 МБ).
     */
    private fun padImageWithZerosToSize(context: Context, imgFile: File, targetSizeBytes: Long, onLog: (String) -> Unit): Boolean {
        if (!imgFile.isFile || targetSizeBytes <= 0L) return true
        val currentSize = imgFile.length()
        if (currentSize >= targetSizeBytes) return true

        // Выходной файл создан bootsmasher под root — забираем доступ для процесса приложения.
        runRootCommand("chmod 666 ${shQuote(imgFile.absolutePath)}")
        return try {
            java.io.RandomAccessFile(imgFile, "rw").use { raf ->
                raf.setLength(targetSizeBytes)
            }
            onLog(
                context.getString(
                    R.string.log_pad_image,
                    imgFile.name,
                    String.format(Locale.US, "%.1f", currentSize / 1048576.0),
                    String.format(Locale.US, "%.1f", targetSizeBytes / 1048576.0),
                    targetSizeBytes,
                )
            )
            true
        } catch (e: Exception) {
            onLog(context.getString(R.string.log_pad_failed, imgFile.name, e.message ?: ""))
            false
        }
    }

    /**
     * Проверяет, содержит ли ELF-бинарник текущей сборки подстроку флага (например, "--strip-16k").
     */
    private fun binaryContainsFlag(binFile: File, flag: String): Boolean {
        return try {
            val needle = flag.toByteArray(Charsets.UTF_8)
            val bytes = binFile.readBytes()
            if (needle.isEmpty() || bytes.size < needle.size) return false
            outer@ for (i in 0..bytes.size - needle.size) {
                for (j in needle.indices) {
                    if (bytes[i + j] != needle[j]) continue@outer
                }
                return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun loadBackups(context: Context): List<BackupEntry> {
        val dir = getBackupsDir(context)
        val metaFiles = dir.listFiles { f -> f.name.endsWith(".json") } ?: return emptyList()
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        return metaFiles.mapNotNull { mf ->
            try {
                val o = JSONObject(mf.readText())
                val imgFile = File(dir, o.getString("fileName"))
                if (!imgFile.isFile || imgFile.length() == 0L) return@mapNotNull null
                val ts = o.optLong("createdAtMs", imgFile.lastModified())
                BackupEntry(
                    id = o.optString("id", imgFile.nameWithoutExtension),
                    file = imgFile,
                    sourceSlot = o.optString("sourceSlot", "a"),
                    createdAtMs = ts,
                    formattedDate = fmt.format(Date(ts)),
                    sizeBytes = imgFile.length(),
                    device = o.optString("device", ""),
                )
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.createdAtMs }
    }

    fun deleteBackup(context: Context, entry: BackupEntry) {
        val dir = getBackupsDir(context)
        entry.file.delete()
        File(dir, "${entry.id}.json").delete()
    }

    fun loadImportedItems(context: Context): List<ExternalInstallItem> {
        val dir = getImportedDir(context)
        val files = dir.listFiles { f ->
            f.isFile && (f.name.endsWith(".zip", true) || f.name.endsWith(".img", true))
        } ?: return emptyList()
        return files.map { f ->
            val low = f.name.lowercase()
            ExternalInstallItem(
                id = f.name,
                name = f.name,
                file = f,
                sizeBytes = f.length(),
                isAioZip = low.endsWith(".zip"),
                isImg = low.endsWith(".img"),
                importedAtMs = f.lastModified(),
            )
        }.sortedByDescending { it.importedAtMs }
    }

    suspend fun createSlotBackup(
        context: Context,
        slot: String,
        device: String,
        onLog: (String) -> Unit,
    ): BackupEntry? = withContext(Dispatchers.IO) {
        val dir = getBackupsDir(context)
        val ts = System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(ts))
        val id = "backup_${stamp}_slot_$slot"
        val imgFile = File(dir, "$id.img")
        val blockPath = "/dev/block/by-name/vendor_boot_$slot"

        onLog(context.getString(R.string.log_backup_slot, slot, blockPath))
        val (rc, out) = runRootCommand("dd if=$blockPath of=${shQuote(imgFile.absolutePath)} bs=1048576")
        if (rc != 0 || !imgFile.isFile || imgFile.length() == 0L) {
            onLog(context.getString(R.string.log_backup_slot_fail, slot, out))
            imgFile.delete()
            return@withContext null
        }

        val meta = JSONObject().apply {
            put("id", id)
            put("fileName", imgFile.name)
            put("sourceSlot", slot)
            put("createdAtMs", ts)
            put("device", device)
        }
        File(dir, "$id.json").writeText(meta.toString())
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        onLog(
            context.getString(
                R.string.log_backup_saved,
                slot,
                String.format(Locale.US, "%.1f", imgFile.length() / 1048576.0),
            )
        )
        BackupEntry(
            id = id,
            file = imgFile,
            sourceSlot = slot,
            createdAtMs = ts,
            formattedDate = fmt.format(Date(ts)),
            sizeBytes = imgFile.length(),
            device = device,
        )
    }

    suspend fun prepareAioEnvironment(
        context: Context,
        zipFile: File,
        onStep: (stepTitle: String, stepSubtitle: String, progress: Float?) -> Unit,
    ): Result<PreparedAioEnv> = withContext(Dispatchers.IO) {
        try {
            val (deviceCodename, activeSlot) = getDeviceOverview()
            val cacheKey = "${zipFile.absolutePath}:${zipFile.length()}:${zipFile.lastModified()}:$deviceCodename:$activeSlot"
            val existing = cachedAioEnv
            if (cachedAioKey == cacheKey &&
                existing != null &&
                existing.workDir.isDirectory &&
                existing.installerBin.isFile &&
                existing.payloadLz4.isFile
            ) {
                return@withContext Result.success(existing)
            }

            // Удаляем старый общий кэш бинарников (если остался от прошлых версий)
            File(context.filesDir, "shared_installer_bin").deleteRecursively()

            // Полностью очищаем рабочую директорию перед распаковкой конкретной сборки
            val rootWorkDir = File(context.filesDir, "installer_work")
            runRootCommand("rm -rf ${shQuote(rootWorkDir.absolutePath)}")
            rootWorkDir.deleteRecursively()
            val workDir = File(rootWorkDir, "current_build").apply { mkdirs() }

            onStep(
                context.getString(R.string.unpack_step_extract_title),
                context.getString(R.string.unpack_step_extract_sub, zipFile.name),
                0.15f,
            )

            ZipFile(zipFile).use { zf ->
                val entries = zf.entries().toList()
                val total = entries.size.coerceAtLeast(1)
                entries.forEachIndexed { idx, entry ->
                    if (entry.isDirectory) return@forEachIndexed
                    val name = entry.name.removePrefix("./")
                    val low = name.lowercase()
                    // Пропускаем только десктопные бинарники Windows/macOS
                    if (low.endsWith(".exe") || low.endsWith(".dll") || low.contains("macos") || low.contains("windows")) {
                        return@forEachIndexed
                    }
                    val outFile = File(workDir, name)
                    if (!outFile.canonicalPath.startsWith(workDir.canonicalPath)) return@forEachIndexed
                    outFile.parentFile?.mkdirs()

                    zf.getInputStream(entry).use { ins ->
                        outFile.outputStream().use { outs -> ins.copyTo(outs) }
                    }
                    onStep(
                        context.getString(R.string.unpack_step_extract_title),
                        context.getString(R.string.unpack_step_extract_sub, name.substringAfterLast('/')),
                        0.15f + 0.35f * ((idx + 1).toFloat() / total),
                    )
                }
            }

            // Ищем payload (*.lz4) внутри распакованной сборки
            val finalPayload = workDir.walkTopDown().firstOrNull { f ->
                f.isFile && f.name.endsWith(".lz4", ignoreCase = true)
            } ?: return@withContext Result.failure(
                IllegalStateException(context.getString(R.string.unpack_err_no_payload))
            )

            // Ищем bin.lgz внутри текущей сборки
            val binLgzFile = workDir.walkTopDown().firstOrNull { f ->
                f.isFile && f.name.equals("bin.lgz", ignoreCase = true)
            }

            if (binLgzFile != null) {
                onStep(
                    context.getString(R.string.unpack_step_lgz_title),
                    context.getString(R.string.unpack_step_lgz_sub),
                    0.65f,
                )
                val decoder = workDir.walkTopDown().firstOrNull { f ->
                    f.isFile && f.name.startsWith("lgz-lean-linux-", ignoreCase = true) &&
                        (f.name.endsWith("arm64", true) || f.name.endsWith("aarch64", true))
                } ?: return@withContext Result.failure(
                    IllegalStateException(context.getString(R.string.unpack_err_no_decoder))
                )

                decoder.setExecutable(true, false)
                runRootCommand("chmod 755 ${shQuote(decoder.absolutePath)}")
                val cmd = "${shQuote(decoder.absolutePath)} decompress ${shQuote(binLgzFile.absolutePath)} ${shQuote(workDir.absolutePath)}"
                val (rc, out) = runRootCommand(cmd)
                if (rc != 0) {
                    return@withContext Result.failure(
                        IllegalStateException(context.getString(R.string.unpack_err_lgz_failed, out))
                    )
                }
            }

            // Ищем бинарник установщика строго внутри распакованной папки ТОЛЬКО ЭТОЙ сборки
            val allFiles = workDir.walkTopDown().filter { it.isFile }.toList()
            val resolvedBin = allFiles.firstOrNull { it.name == "install-small-linux-arm64" }
                ?: allFiles.firstOrNull { it.name == "install-linux-arm64" }
                ?: allFiles.firstOrNull {
                    it.name.startsWith("install-") &&
                        (it.name.endsWith("linux-arm64") || it.name.endsWith("linux-aarch64"))
                }
                ?: return@withContext Result.failure(
                    IllegalStateException(context.getString(R.string.unpack_err_no_binary))
                )

            // Ищем модуль тача <device>.lgz и папку modules строго внутри текущей сборки
            val deviceModule = allFiles.firstOrNull { f ->
                f.name.equals("$deviceCodename.lgz", ignoreCase = true)
            }
            val modulesDir = deviceModule?.parentFile
                ?: workDir.walkTopDown().firstOrNull { it.isDirectory && it.name == "modules" }
                ?: File(workDir, "bin/modules")

            onStep(
                context.getString(R.string.unpack_step_check_title),
                context.getString(R.string.unpack_step_check_sub),
                0.9f,
            )
            resolvedBin.setExecutable(true, false)
            runRootCommand("chmod 755 ${shQuote(resolvedBin.absolutePath)}")
            val (_, helpOut) = runRootCommand("${shQuote(resolvedBin.absolutePath)} install --help")
            val (_, expandOut) = runRootCommand("${shQuote(resolvedBin.absolutePath)} install --expand")
            val supportsStrip16k = helpOut.contains("--strip-16k") ||
                expandOut.contains("--strip-16k") ||
                binaryContainsFlag(resolvedBin, "--strip-16k")

            onStep(
                context.getString(R.string.unpack_step_done_title),
                context.getString(R.string.unpack_step_done_sub),
                1f,
            )
            val prepared = PreparedAioEnv(
                workDir = workDir,
                installerBin = resolvedBin,
                payloadLz4 = finalPayload,
                modulesDir = modulesDir,
                supportsStrip16k = supportsStrip16k,
                deviceCodename = deviceCodename,
                activeSlot = activeSlot,
                availableModuleLgz = deviceModule,
            )
            cachedAioKey = cacheKey
            cachedAioEnv = prepared
            Result.success(prepared)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun executeAioInstall(
        context: Context,
        env: PreparedAioEnv,
        config: AioInstallConfig,
        onLog: (String) -> Unit,
    ): InstallRunOutcome = withContext(Dispatchers.IO) {
        val logs = mutableListOf<String>()
        fun log(msg: String) {
            logs.add(msg)
            onLog(msg)
        }

        val slotsToProcess = if (config.sourceMode == VendorBootSourceMode.CustomFile) {
            listOf("file")
        } else {
            config.targetSlot.slots
        }

        val stagedOutputs = mutableMapOf<String, File>()

        for (slot in slotsToProcess) {
            val inputImg = File(env.workDir, "input_$slot.img")
            val outputImg = File(env.workDir, "patched_vendor_boot_$slot.img")
            val logFile = File(env.workDir, "install_$slot.log")
            outputImg.delete()

            val partitionSize: Long = if (config.sourceMode == VendorBootSourceMode.CustomFile) {
                val src = config.customSourceImg
                if (src == null || !src.isFile) {
                    return@withContext InstallRunOutcome.Failure(
                        context.getString(R.string.install_dl_no_source),
                        logs,
                    )
                }
                src.copyTo(inputImg, overwrite = true)
                val (_, bszOut) = runRootCommand("blockdev --getsize64 /dev/block/by-name/vendor_boot_a")
                bszOut.toLongOrNull()?.takeIf { it > 0L } ?: 67108864L
            } else {
                val blockPath = "/dev/block/by-name/vendor_boot_$slot"
                log(context.getString(R.string.log_reading_block, blockPath))
                val (ddRc, ddOut) = runRootCommand("dd if=$blockPath of=${shQuote(inputImg.absolutePath)} bs=1048576")
                if (ddRc != 0 || !inputImg.isFile || inputImg.length() == 0L) {
                    return@withContext InstallRunOutcome.Failure(
                        context.getString(R.string.install_dl_read_failed, blockPath, ddOut),
                        logs,
                    )
                }
                val (_, bszOut) = runRootCommand("blockdev --getsize64 $blockPath")
                bszOut.toLongOrNull()?.takeIf { it > 0L } ?: inputImg.length().coerceAtLeast(67108864L)
            }

            val cmdBuilder = StringBuilder().apply {
                append("${shQuote(env.installerBin.absolutePath)} install --file")
                append(" -i ${shQuote(inputImg.absolutePath)}")
                append(" -c ${shQuote(env.payloadLz4.absolutePath)}")
                append(" -o ${shQuote(outputImg.absolutePath)}")
                append(" --log ${shQuote(logFile.absolutePath)}")
                if (env.deviceCodename.isNotEmpty()) {
                    append(" --device ${env.deviceCodename}")
                }
                if (config.layoutType.cliFlag.isNotEmpty()) {
                    append(" --recovery-is-platform ${config.layoutType.cliFlag}")
                }
                when (config.touchMode) {
                    TouchModuleMode.Auto -> env.availableModuleLgz?.let {
                        append(" --inject-lgz ${shQuote(it.absolutePath)}")
                    }
                    TouchModuleMode.CustomFile -> config.customTouchLgz?.let {
                        append(" --inject-lgz ${shQuote(it.absolutePath)}")
                    }
                    TouchModuleMode.None -> {}
                }
                append(" --partition-size $partitionSize")
                append(" --min-free-mb ${config.minFreeMb}")
                if (config.strip16k && env.supportsStrip16k) {
                    append(" --strip-16k")
                }
            }

            log(context.getString(R.string.log_building_slot, slot))
            val (rc, cliOut) = runRootCommand(cmdBuilder.toString())
            val fullReport = buildString {
                append(cliOut)
                if (logFile.isFile) {
                    append("\n")
                    append(logFile.readText())
                }
            }
            cliOut.lines().filter { it.isNotBlank() }.forEach { log("  $it") }

            if (rc != 0 || !outputImg.isFile) {
                val isSpaceIssue = fullReport.contains("DOES NOT FIT", ignoreCase = true) ||
                    fullReport.contains("free left", ignoreCase = true) ||
                    fullReport.contains("--strip-16k", ignoreCase = true) ||
                    fullReport.contains("BELOW", ignoreCase = true)

                if (isSpaceIssue) {
                    return@withContext InstallRunOutcome.SpaceTightError(
                        message = context.getString(R.string.install_space_message),
                        supportsStrip16k = env.supportsStrip16k,
                        logs = logs,
                    )
                }
                return@withContext InstallRunOutcome.Failure(
                    message = context.getString(R.string.install_build_failed, slot, rc),
                    logs = logs,
                )
            }
            // Добиваем патченный образ нулями ровно до размера блочного устройства (64 МБ)
            val targetPadSize = if (partitionSize > 0L) partitionSize else DEFAULT_VENDOR_BOOT_PART_SIZE
            if (!padImageWithZerosToSize(context, outputImg, targetPadSize, ::log)) {
                return@withContext InstallRunOutcome.Failure(
                    message = context.getString(R.string.install_pad_failed, outputImg.name),
                    logs = logs,
                )
            }
            stagedOutputs[slot] = outputImg
        }

        if (config.destinationMode == InstallDestinationMode.SaveToDownloads) {
            val savedPaths = mutableListOf<String>()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            for ((slot, file) in stagedOutputs) {
                val outName = "patched_vendor_boot_${slot}_$stamp.img"
                val path = copyFileToPublicDownloads(context, file, outName)
                savedPaths.add(path)
                log(context.getString(R.string.log_saved_to, path))
            }
            return@withContext InstallRunOutcome.Success(
                flashedSlots = emptyList(),
                savedDownloadsPaths = savedPaths,
                logs = logs,
            )
        }

        val flashed = mutableListOf<String>()
        for (slot in config.targetSlot.slots) {
            val stagedFile = stagedOutputs[slot] ?: stagedOutputs.values.first()
            if (config.saveBackupBeforeFlash) {
                val backup = createSlotBackup(context, slot, env.deviceCodename, ::log)
                if (backup == null) {
                    return@withContext InstallRunOutcome.Failure(
                        message = context.getString(R.string.install_backup_before_fail, slot),
                        logs = logs,
                    )
                }
            }
            val blockPath = "/dev/block/by-name/vendor_boot_$slot"
            log(context.getString(R.string.log_setrw, blockPath))
            runRootCommand("blockdev --setrw $blockPath")

            log(context.getString(R.string.log_writing_image, blockPath))
            val (flashRc, flashOut) = runRootCommand("dd if=${shQuote(stagedFile.absolutePath)} of=$blockPath bs=1048576")
            if (flashRc != 0) {
                return@withContext InstallRunOutcome.Failure(
                    context.getString(R.string.install_dl_flash_failed, blockPath, flashOut),
                    logs,
                )
            }

            val checkFile = File(env.workDir, "verify_$slot.img")
            val sizeBytes = stagedFile.length()
            val blocksCount = ((sizeBytes + 1048575L) / 1048576L).coerceAtLeast(1L)
            val (verifyRc, _) = runRootCommand(
                "dd if=$blockPath of=${shQuote(checkFile.absolutePath)} bs=1048576 count=$blocksCount && head -c $sizeBytes ${shQuote(checkFile.absolutePath)} | cmp -s - ${shQuote(stagedFile.absolutePath)}"
            )
            checkFile.delete()
            if (verifyRc != 0) {
                return@withContext InstallRunOutcome.Failure(
                    message = context.getString(R.string.install_dl_verify_failed, slot),
                    logs = logs,
                )
            }
            log(context.getString(R.string.log_slot_flashed_ok, slot))
            flashed.add(slot)
        }

        InstallRunOutcome.Success(
            flashedSlots = flashed,
            savedDownloadsPaths = emptyList(),
            logs = logs,
        )
    }

    suspend fun flashRawImage(
        context: Context,
        imageFile: File,
        targetSlot: TargetSlotOption,
        isBackupRestore: Boolean,
        saveBackupBeforeFlash: Boolean,
        onLog: (String) -> Unit,
    ): InstallRunOutcome = withContext(Dispatchers.IO) {
        val logs = mutableListOf<String>()
        fun log(msg: String) {
            logs.add(msg)
            onLog(msg)
        }

        if (!imageFile.isFile || imageFile.length() == 0L) {
            return@withContext InstallRunOutcome.Failure(
                context.getString(R.string.install_raw_no_file),
                logs,
            )
        }

        val (deviceCodename, _) = getDeviceOverview()
        val flashed = mutableListOf<String>()

        for (slot in targetSlot.slots) {
            if (!isBackupRestore && saveBackupBeforeFlash) {
                val backup = createSlotBackup(context, slot, deviceCodename, ::log)
                if (backup == null) {
                    return@withContext InstallRunOutcome.Failure(
                        context.getString(R.string.install_raw_nobackup_fail, slot),
                        logs,
                    )
                }
            }

            val blockPath = "/dev/block/by-name/vendor_boot_$slot"
            val (_, bszOut) = runRootCommand("blockdev --getsize64 $blockPath")
            val partSize = bszOut.toLongOrNull()?.takeIf { it > 0L } ?: DEFAULT_VENDOR_BOOT_PART_SIZE
            if (imageFile.length() > partSize) {
                return@withContext InstallRunOutcome.Failure(
                    context.getString(R.string.install_raw_too_big, imageFile.length(), partSize),
                    logs,
                )
            }
            log(context.getString(R.string.log_setrw, blockPath))
            runRootCommand("blockdev --setrw $blockPath")

            log(context.getString(R.string.log_writing_image, blockPath))
            val (rc, out) = runRootCommand("dd if=${shQuote(imageFile.absolutePath)} of=$blockPath bs=1048576")
            if (rc != 0) {
                return@withContext InstallRunOutcome.Failure(
                    context.getString(R.string.install_raw_write_fail, slot, out),
                    logs,
                )
            }

            val verifyTmp = File(context.cacheDir, "verify_raw_$slot.img")
            val sizeBytes = imageFile.length()
            val blocksCount = ((sizeBytes + 1048575L) / 1048576L).coerceAtLeast(1L)
            val (cmpRc, _) = runRootCommand(
                "dd if=$blockPath of=${shQuote(verifyTmp.absolutePath)} bs=1048576 count=$blocksCount && head -c $sizeBytes ${shQuote(verifyTmp.absolutePath)} | cmp -s - ${shQuote(imageFile.absolutePath)}"
            )
            verifyTmp.delete()
            if (cmpRc != 0) {
                return@withContext InstallRunOutcome.Failure(
                    context.getString(R.string.install_raw_verify_fail, slot),
                    logs,
                )
            }

            log(context.getString(R.string.log_slot_written_ok, slot))
            flashed.add(slot)
        }

        InstallRunOutcome.Success(
            flashedSlots = flashed,
            savedDownloadsPaths = emptyList(),
            logs = logs,
        )
    }
}
