package com.orangefox.tensor.manager

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orangefox.tensor.manager.ui.ExpressiveLinearProgress
import com.orangefox.tensor.manager.ui.edgeOutline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.util.Log
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class InstalledRecoveryType {
    MY_OFOX,
    OTHER_OFOX,
    OTHER_TWRP,
    LOS_BASED,
    STOCK,
    UNKNOWN,
}

data class SlotRecoveryInfo(
    val slot: String,
    val blockSha256: String,
    val type: InstalledRecoveryType,
    val versionName: String,
    val buildDateUtcSec: Long,
    val formattedBuildDate: String,
    val buildHost: String,
    val foxCodebase: String,
    val romName: String,
    val romVersion: String,
    val losBased: Boolean,
    val buildFingerprint: String,
    val buildIncremental: String,
    val buildFlavor: String,
    val checkedAtMs: Long,
)

/**
 * Продвинутый детект исходной прошивки по пропсам vendor_boot.
 * Порядок важен: сначала именованные кастомы, потом generic-правила.
 * Возвращает (имя ROM, версия ROM).
 */
internal fun identifyRom(props: Map<String, String>): Pair<String, String> {
    fun hasPrefix(prefix: String): Boolean =
        props.keys.any { it.lowercase().startsWith(prefix) }

    val lineageVersion = props["ro.lineage.version"] ?: ""
    if (hasPrefix("ro.rising.") || lineageVersion.startsWith("RisingOS")) {
        return "RisingOS" to (props["ro.lineage.display.version"] ?: lineageVersion)
    }
    if (hasPrefix("ro.crdroid.")) {
        val ver = props["ro.crdroid.display.version"]
            ?: props["ro.crdroid.version"]
            ?: props["ro.crdroid.build.version"]
            ?: ""
        return "crDroid" to ver
    }
    val voltageVer = props["ro.voltage.version"] ?: ""
    if (voltageVer.isNotBlank() || hasPrefix("ro.voltage.")) {
        return "VoltageOS" to voltageVer
    }
    val infinityVer = props["ro.infinity.build.version"] ?: ""
    if (infinityVer.isNotBlank() || hasPrefix("ro.infinity.")) {
        return "Project Infinity" to infinityVer
    }
    val customVer = props["ro.custom.version"] ?: ""
    if (customVer.isNotBlank() || hasPrefix("ro.ascp.") || hasPrefix("ro.custom.")) {
        val name = if (hasPrefix("ro.ascp.") || customVer.contains("ascp", ignoreCase = true)) {
            "ASCP"
        } else {
            "Custom ROM"
        }
        return name to customVer
    }
    if (props.containsKey("net.pixelos.version")) {
        return "PixelOS" to (props["ro.build.display.id"] ?: "")
    }
    if (lineageVersion.isNotBlank()) {
        return "LineageOS" to lineageVersion
    }
    if ((props["ro.build.host"] ?: "") == "r-0123456789abcdef-0123") {
        // Эвристика: GrapheneOS анонимизирует хост сборки; маркеров в пропсах не оставляет.
        return "GrapheneOS" to ""
    }
    val lineageMarkers = hasPrefix("ro.lineage.") ||
        props.values.any { it.contains("lineage", ignoreCase = true) }
    if (lineageMarkers) {
        return "LineageOS" to ""
    }
    val flavor = props["ro.build.flavor"] ?: ""
    val model = props["ro.product.system.model"] ?: ""
    if ("generic_system" in flavor || model == "Generic System") {
        return "GSI" to ""
    }
    val fingerprint = props["ro.build.fingerprint"] ?: ""
    val tags = props["ro.build.tags"] ?: ""
    val stockFlavor = Regex("^[a-z0-9]+-user$").matches(flavor)
    if ((fingerprint.startsWith("google/") || stockFlavor) && "release-keys" in tags) {
        return "Stock" to ""
    }
    return "AOSP" to ""
}

object RecoveryStateCache {
    private const val TAG = "RecoveryInspector"
    private val inspectorScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    var slotInfo = mutableStateMapOf<String, SlotRecoveryInfo>()
    var activeSlot by mutableStateOf("a")
    var isInspecting by mutableStateOf(false)
    var hasRoot by mutableStateOf<Boolean?>(null)
    var initialized by mutableStateOf(false)
    var liveFingerprint by mutableStateOf("")
    var liveIncremental by mutableStateOf("")
    var liveFlavor by mutableStateOf("")

    private val mutex = Mutex()

    fun ensureLoadedOnStartup(context: Context, @Suppress("UNUSED_PARAMETER") scope: CoroutineScope? = null) {
        if (initialized) return
        initialized = true
        refresh(context, forceUnpack = false)
    }

    fun refresh(
        context: Context,
        @Suppress("UNUSED_PARAMETER") scope: CoroutineScope? = null,
        forceUnpack: Boolean = false,
    ) {
        val appCtx = context.applicationContext
        inspectorScope.launch {
            mutex.withLock {
                isInspecting = true
                try {
                    val cachedMap = loadDiskCache(appCtx)
                    val sysSlot = RootInstallerEngine.readActiveSlotSuffix()
                    val liveFp = RootInstallerEngine.readSystemProp("ro.build.fingerprint")
                    val liveInc = RootInstallerEngine.readSystemProp("ro.build.version.incremental")
                    val liveFlav = RootInstallerEngine.readSystemProp("ro.build.flavor")

                    withContext(Dispatchers.Main) {
                        liveFingerprint = liveFp
                        liveIncremental = liveInc
                        liveFlavor = liveFlav
                        activeSlot = sysSlot
                        if (slotInfo.isEmpty() && cachedMap.isNotEmpty()) {
                            slotInfo.putAll(cachedMap)
                        }
                    }

                    // Одним вызовом su проверяем root и сразу считаем sha256sum для обоих слотов
                    val (rc, probeOut) = RootInstallerEngine.runRootCommand(
                        "id && sha256sum /dev/block/by-name/vendor_boot_a /dev/block/by-name/vendor_boot_b 2>/dev/null",
                        timeoutSec = 30,
                    )
                    val rootGranted = rc == 0 && probeOut.contains("uid=0")
                    withContext(Dispatchers.Main) { hasRoot = rootGranted }
                    if (!rootGranted) return@withLock

                    val hashes = HashMap<String, String>()
                    probeOut.lineSequence().forEach { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 2 && parts[0].length == 64) {
                            when {
                                parts[1].endsWith("_a") -> hashes["a"] = parts[0].lowercase(Locale.US)
                                parts[1].endsWith("_b") -> hashes["b"] = parts[0].lowercase(Locale.US)
                            }
                        }
                    }

                    val updatedMap = cachedMap.toMutableMap()
                    for (slot in listOf("a", "b")) {
                        val blockPath = "/dev/block/by-name/vendor_boot_$slot"
                        val currentHash = hashes[slot] ?: queryBlockSha256(blockPath)
                        val cached = cachedMap[slot]

                        if (!forceUnpack &&
                            currentHash.isNotEmpty() &&
                            cached != null &&
                            cached.blockSha256.equals(currentHash, ignoreCase = true)
                        ) {
                            updatedMap[slot] = cached
                        } else if (currentHash.isNotEmpty()) {
                            val inspected = inspectSlotPartition(appCtx, slot, blockPath, currentHash)
                            if (inspected != null) {
                                updatedMap[slot] = inspected
                            }
                        }
                        withContext(Dispatchers.Main) {
                            updatedMap[slot]?.let { slotInfo[slot] = it }
                        }
                    }
                    saveDiskCache(appCtx, updatedMap)
                } catch (e: Throwable) {
                    Log.e(TAG, "refresh error", e)
                } finally {
                    isInspecting = false
                }
            }
        }
    }

    /** Строгое совпадение: fingerprint часто спуфят, требуем три ключа. */
    fun matchesLiveSystem(info: SlotRecoveryInfo): Boolean {
        return info.buildFingerprint.isNotBlank() &&
            info.buildIncremental.isNotBlank() &&
            info.buildFlavor.isNotBlank() &&
            info.buildFingerprint == liveFingerprint &&
            info.buildIncremental == liveIncremental &&
            info.buildFlavor == liveFlavor
    }

    private fun runSu(cmd: String, timeoutSec: Long = 60): Pair<Int, String> =
        RootInstallerEngine.runRootCommand(cmd, timeoutSec)

    private fun shQuote(value: String): String =
        RootInstallerEngine.shQuote(value)

    private fun queryBlockSha256(blockPath: String): String {
        val (rc, out) = runSu("sha256sum $blockPath", timeoutSec = 25)
        if (rc != 0 || out.isBlank()) return ""
        return out.trim().split(Regex("\\s+")).firstOrNull()?.lowercase(Locale.US) ?: ""
    }

    private fun formatBuildDate(utcSec: Long): String {
        if (utcSec <= 0L) return "—"
        val ms = utcSec * 1000L
        val pattern = if (utcSec % 60L == 0L) "yyyy-MM-dd HH:mm" else "yyyy-MM-dd HH:mm:ss"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
    }

    private fun resolveNativeTool(context: Context, libName: String, fallbackAssetName: String): File? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val fromJni = File(nativeDir, libName)
        if (fromJni.isFile) return fromJni

        val binDir = File(context.filesDir, "inspector_bin").apply { mkdirs() }
        val target = File(binDir, fallbackAssetName)
        if (!target.isFile) {
            runCatching {
                context.assets.open(fallbackAssetName).use { ins ->
                    target.outputStream().use { outs -> ins.copyTo(outs) }
                }
            }
        }
        if (target.isFile) {
            target.setExecutable(true, false)
            runSu("chmod 755 ${shQuote(target.absolutePath)}", timeoutSec = 10)
            return target
        }
        return null
    }

    private fun deleteIfSymlinkOrInvalid(file: File): Boolean {
        val path = file.toPath()
        if (Files.isSymbolicLink(path)) {
            runCatching { Files.deleteIfExists(path) }
            return false
        }
        if (!file.isFile || file.length() == 0L) {
            runCatching { file.delete() }
            return false
        }
        return true
    }

    private fun extractVersionFromTwrpBinary(twrpFile: File): Pair<String?, Boolean> {
        if (!twrpFile.isFile || twrpFile.length() == 0L) return null to false
        val bytes = twrpFile.readBytes()
        val strings = mutableListOf<String>()
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 32..126) {
                sb.append(c.toChar())
            } else {
                if (sb.length >= 4) strings.add(sb.toString())
                sb.setLength(0)
            }
        }
        if (sb.length >= 4) strings.add(sb.toString())

        val hasOrangeFoxMarker = strings.any { it.contains("OrangeFox", ignoreCase = true) }
        val rVersionRegex = Regex("""^R\d+\.\d+_[0-9A-Za-z._-]+$""")
        val rMatch = strings.firstOrNull { rVersionRegex.matches(it.trim()) }
            ?: strings.firstNotNullOfOrNull { line ->
                Regex("""R\d+\.\d+_[0-9A-Za-z._-]+""").find(line)?.value
            }
            ?: strings.firstNotNullOfOrNull { line ->
                Regex("""R\d+\.\d+(?:_[0-9A-Za-z._-]+)?""").find(line)?.value
            }

        return rMatch to hasOrangeFoxMarker
    }

    private fun inspectSlotPartition(
        context: Context,
        slot: String,
        blockPath: String,
        blockSha256: String,
    ): SlotRecoveryInfo? {
        val magiskboot = resolveNativeTool(context, "libmagiskboot.so", "magiskboot")
        Log.d(TAG, "slot $slot: magiskboot=${magiskboot?.absolutePath} exists=${magiskboot?.isFile}")
        if (magiskboot == null) return null
        val bundledLgz = resolveNativeTool(context, "liblgz.so", "lgz-lean-linux-arm64")
        Log.d(TAG, "slot $slot: bundledLgz=${bundledLgz?.absolutePath} exists=${bundledLgz?.isFile}")

        val workDir = File(context.cacheDir, "slot_inspect_$slot")
        runSu("rm -rf ${shQuote(workDir.absolutePath)}", timeoutSec = 15)
        workDir.mkdirs()

        try {
            val imgFile = File(workDir, "vendor_boot.img")
            val mbPath = shQuote(magiskboot.absolutePath)
            // В один вызов su снимаем образ и распаковываем его через magiskboot
            val (unpackRc, _) = runSu(
                "dd if=$blockPath of=${shQuote(imgFile.absolutePath)} bs=1048576 && " +
                    "cd ${shQuote(workDir.absolutePath)} && " +
                    "$mbPath unpack -h ${shQuote(imgFile.absolutePath)} && " +
                    "chmod -R 777 ${shQuote(workDir.absolutePath)}",
                timeoutSec = 45,
            )
            if (unpackRc != 0 && !File(workDir, "vendor_ramdisk").exists() && !File(workDir, "ramdisk.cpio").exists()) {
                return null
            }

            val cpioCandidates = buildList {
                val rec = File(workDir, "vendor_ramdisk/recovery.cpio")
                if (rec.isFile) add(rec)
                File(workDir, "vendor_ramdisk").listFiles()?.filter {
                    it.isFile && it.name.endsWith(".cpio") && it.name != "recovery.cpio"
                }?.let { addAll(it) }
                val rootCpio = File(workDir, "ramdisk.cpio")
                if (rootCpio.isFile) add(rootCpio)
            }

            if (cpioCandidates.isEmpty()) {
                return SlotRecoveryInfo(
                    slot = slot,
                    blockSha256 = blockSha256,
                    type = InstalledRecoveryType.STOCK,
                    versionName = "AOSP / Bootloader",
                    buildDateUtcSec = 0L,
                    formattedBuildDate = "—",
                    buildHost = "",
                    foxCodebase = "",
                    romName = "AOSP",
                    romVersion = "",
                    losBased = false,
                    buildFingerprint = "",
                    buildIncremental = "",
                    buildFlavor = "",
                    checkedAtMs = System.currentTimeMillis(),
                )
            }

            var propText = ""
            var twrpFile: File? = null
            val hasRecoveryRamdisk = File(workDir, "vendor_ramdisk/recovery.cpio").isFile

            for (cpio in cpioCandidates) {
                val cpioPath = shQuote(cpio.absolutePath)
                val (_, lsOut) = runSu("$mbPath cpio $cpioPath ls", timeoutSec = 20)
                val entries = lsOut.lines()
                    .map { it.trim().split(Regex("\\s+")).lastOrNull()?.removePrefix("./") ?: "" }
                    .filter { it.isNotEmpty() }

                // 1. Декодер lgz (сначала из рамдиска для старых билдов, иначе комплектный)
                var activeLgzBin: File? = null
                val lgzCandidates = (entries.filter {
                    it.endsWith("/lgz") || it == "lgz" || it.contains("lgz-lean")
                } + listOf("system/bin/lgz", "sbin/lgz", "bin/lgz")).distinct()

                for (cand in lgzCandidates) {
                    val outBin = File(workDir, "ramdisk_lgz_bin")
                    runCatching { Files.deleteIfExists(outBin.toPath()) }
                    val (lrc, _) = runSu("$mbPath cpio $cpioPath ${shQuote("extract $cand ${outBin.absolutePath}")}", timeoutSec = 15)
                    if (lrc == 0 && deleteIfSymlinkOrInvalid(outBin)) {
                        runSu("chmod 755 ${shQuote(outBin.absolutePath)}", timeoutSec = 10)
                        activeLgzBin = outBin
                        break
                    }
                }
                if (activeLgzBin == null) activeLgzBin = bundledLgz

                // 2. prop.default: один вызов extract+chmod, гейт только isFile+length
                // (magiskboot врёт rc=0 даже для отсутствующих записей — rc не проверяем).
                // Сначала настоящий prop.default, симлинк default.prop пропускаем.
                if (propText.isEmpty()) {
                    val propCandidates = (entries.filter {
                        it == "prop.default" || it.endsWith("/prop.default")
                    }.sortedByDescending { it.length } + listOf(
                        "system/etc/prop.default",
                        "etc/prop.default",
                        "prop.default",
                    )).distinct()

                    for (cand in propCandidates) {
                        val rawProp = File(workDir, "extracted_prop")
                        runSu("$mbPath cpio $cpioPath ${shQuote("extract $cand ${rawProp.absolutePath}")} && chmod 666 ${shQuote(rawProp.absolutePath)}", timeoutSec = 20)
                        if (rawProp.isFile && Files.isSymbolicLink(rawProp.toPath())) {
                            Log.d(TAG, "slot $slot: $cand is symlink, deleting")
                            rawProp.delete()
                            continue
                        }
                        if (!rawProp.isFile || rawProp.length() == 0L) continue
                        val asText = runCatching { rawProp.readText() }.getOrDefault("")
                        if (asText.contains("ro.")) {
                            propText = asText
                            break
                        }
                        if (activeLgzBin != null) {
                            val decProp = File(workDir, "dec_prop")
                            decProp.delete()
                            runSu("${shQuote(activeLgzBin.absolutePath)} decompress ${shQuote(rawProp.absolutePath)} ${shQuote(decProp.absolutePath)} --no-meta || " +
                                "${shQuote(activeLgzBin.absolutePath)} decompress ${shQuote(rawProp.absolutePath)} ${shQuote(decProp.absolutePath)} || " +
                                "${shQuote(activeLgzBin.absolutePath)} -d ${shQuote(rawProp.absolutePath)} ${shQuote(decProp.absolutePath)}", timeoutSec = 25)
                            runSu("chmod 666 ${shQuote(decProp.absolutePath)}", timeoutSec = 10)
                            if (decProp.isFile && decProp.length() > 0L) {
                                val decTxt = runCatching { decProp.readText() }.getOrDefault("")
                                if (decTxt.contains("ro.")) {
                                    propText = decTxt
                                    break
                                }
                            }
                        }
                        rawProp.delete()
                    }
                }

                // 3. twrp напрямую: один вызов extract+chmod, гейт isFile+length.
                // UCOMP01-синглы старых билдов распаковываем найденным lgz-декодером.
                if (twrpFile == null) {
                    val twrpCandidates = (entries.filter {
                        it == "system/bin/twrp" || it == "sbin/twrp"
                    }.sortedByDescending { it.length } + listOf(
                        "system/bin/twrp",
                        "sbin/twrp",
                    )).distinct()

                    for (cand in twrpCandidates) {
                        val rawTwrp = File(workDir, "raw_twrp")
                        runSu("$mbPath cpio $cpioPath ${shQuote("extract $cand ${rawTwrp.absolutePath}")} && chmod 666 ${shQuote(rawTwrp.absolutePath)}", timeoutSec = 20)
                        if (rawTwrp.isFile && Files.isSymbolicLink(rawTwrp.toPath())) {
                            rawTwrp.delete()
                            continue
                        }
                        if (!rawTwrp.isFile || rawTwrp.length() == 0L) continue
                        val header = ByteArray(7)
                        val readLen = runCatching {
                            rawTwrp.inputStream().use { it.read(header) }
                        }.getOrDefault(-1)
                        val isUcomp = readLen >= 7 && header.decodeToString() == "UCOMP01"
                        if (!isUcomp) {
                            twrpFile = rawTwrp
                            break
                        }
                        if (activeLgzBin != null) {
                            val decTwrp = File(workDir, "dec_twrp")
                            decTwrp.delete()
                            runSu("${shQuote(activeLgzBin.absolutePath)} decompress ${shQuote(rawTwrp.absolutePath)} ${shQuote(decTwrp.absolutePath)} --no-meta || " +
                                "${shQuote(activeLgzBin.absolutePath)} decompress ${shQuote(rawTwrp.absolutePath)} ${shQuote(decTwrp.absolutePath)} || " +
                                "${shQuote(activeLgzBin.absolutePath)} -d ${shQuote(rawTwrp.absolutePath)} ${shQuote(decTwrp.absolutePath)}", timeoutSec = 25)
                            runSu("chmod 666 ${shQuote(decTwrp.absolutePath)}", timeoutSec = 10)
                            if (decTwrp.isFile && decTwrp.length() > 0L) {
                                twrpFile = decTwrp
                                break
                            }
                        }
                        rawTwrp.delete()
                    }
                }

                // 4. Кластер lgz (ТОЛЬКО файлы со словом "cluster", чтобы не перебирать сотни мелких .lgz)
                val clusterEntries = (entries.filter {
                    it.endsWith(".lgz", ignoreCase = true) && it.contains("cluster", ignoreCase = true)
                } + listOf("lgz_cluster.lgz")).distinct()

                for (clEntry in clusterEntries) {
                    if (twrpFile != null && propText.isNotEmpty()) break
                    val clFile = File(workDir, "cluster_archive.lgz")
                    runCatching { Files.deleteIfExists(clFile.toPath()) }
                    val (crc, _) = runSu("$mbPath cpio $cpioPath ${shQuote("extract $clEntry ${clFile.absolutePath}")}", timeoutSec = 25)
                    if (crc != 0 || !deleteIfSymlinkOrInvalid(clFile) || clFile.length() < 1_000_000L) continue
                    runSu("chmod 666 ${shQuote(clFile.absolutePath)}", timeoutSec = 10)

                    val lgzExec = bundledLgz ?: activeLgzBin ?: continue
                    val singleDir = File(workDir, "single_out").apply { mkdirs() }
                    if (twrpFile == null) {
                        runSu(
                            "${shQuote(lgzExec.absolutePath)} extract ${shQuote(clFile.absolutePath)} system/bin/twrp ${shQuote(singleDir.absolutePath)} --flatten --no-meta && " +
                                "chmod -R 777 ${shQuote(singleDir.absolutePath)}",
                            timeoutSec = 25,
                        )
                        val extracted = File(singleDir, "twrp")
                        if (deleteIfSymlinkOrInvalid(extracted)) {
                            twrpFile = extracted
                        }
                    }
                    if (propText.isEmpty()) {
                        runSu(
                            "${shQuote(lgzExec.absolutePath)} extract ${shQuote(clFile.absolutePath)} prop.default ${shQuote(singleDir.absolutePath)} --flatten --no-meta && " +
                                "chmod -R 777 ${shQuote(singleDir.absolutePath)}",
                            timeoutSec = 25,
                        )
                        val extractedProp = File(singleDir, "prop.default")
                        if (deleteIfSymlinkOrInvalid(extractedProp)) {
                            propText = runCatching { extractedProp.readText() }.getOrDefault("")
                        }
                    }
                }
                Log.d(TAG, "slot $slot: cpio=${cpio.name} propLen=${propText.length} twrp=${twrpFile != null}")
            }

            val props = HashMap<String, String>()
            propText.lineSequence().forEach { line ->
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) return@forEach
                val k = t.substringBefore("=").trim()
                val v = t.substringAfter("=").trim()
                props[k] = v
            }

            val (twrpVer, hasFoxBinMarker) = twrpFile?.let { extractVersionFromTwrpBinary(it) } ?: (null to false)

            val utcSec = props["ro.build.date.utc_fox"]?.toLongOrNull()
                ?: props["ro.bootimage.build.date.utc_fox"]?.toLongOrNull()
                ?: props["ro.bootimage.build.date.utc"]?.toLongOrNull()
                ?: props["ro.build.date.utc"]?.toLongOrNull()
                ?: props["ro.vendor.build.date.utc"]?.toLongOrNull()
                ?: 0L

            val buildHost = props["ro.build.host"]
                ?: props["ro.bootimage.build.host"]
                ?: props["ro.vendor.build.host"]
                ?: ""

            val foxCodebase = props["ro.build.fox_codebase"] ?: ""
            val hasFoxProps = props.keys.any { it.contains("fox", ignoreCase = true) } || hasFoxBinMarker
            val isLeegarHost = buildHost.contains("leegar", ignoreCase = true) ||
                propText.contains("leegar", ignoreCase = true)
            val isLosProps = props.keys.any { it.contains("lineage", ignoreCase = true) } ||
                propText.contains("lineage", ignoreCase = true)
            val isTwrpProps = twrpFile != null ||
                props.keys.any { it.contains("twrp", ignoreCase = true) } ||
                propText.contains("twrp", ignoreCase = true)

            val type = when {
                hasFoxProps && isLeegarHost -> InstalledRecoveryType.MY_OFOX
                hasFoxProps -> InstalledRecoveryType.OTHER_OFOX
                isTwrpProps -> InstalledRecoveryType.OTHER_TWRP
                isLosProps -> InstalledRecoveryType.LOS_BASED
                !hasRecoveryRamdisk || propText.isNotEmpty() -> InstalledRecoveryType.STOCK
                else -> InstalledRecoveryType.UNKNOWN
            }

            val (rawRomName, romVersion) = identifyRom(props)
            val romName = if (hasFoxProps) "OrangeFox" else rawRomName
            val losBased = isLosProps

            val resolvedVersion = when {
                !twrpVer.isNullOrBlank() -> twrpVer
                romVersion.isNotBlank() -> romVersion
                foxCodebase.isNotBlank() -> foxCodebase
                !props["ro.twrp.version"].isNullOrBlank() -> props["ro.twrp.version"]!!
                !props["ro.build.id"].isNullOrBlank() -> props["ro.build.id"]!!
                type == InstalledRecoveryType.STOCK -> "AOSP Stock"
                else -> "—"
            }

            Log.d(TAG, "slot $slot: props=${props.size} twrpVer=$twrpVer foxBin=$hasFoxBinMarker host=$buildHost utc=$utcSec rom=$romName -> $type")
            return SlotRecoveryInfo(
                slot = slot,
                blockSha256 = blockSha256,
                type = type,
                versionName = resolvedVersion,
                buildDateUtcSec = utcSec,
                formattedBuildDate = formatBuildDate(utcSec),
                buildHost = buildHost,
                foxCodebase = foxCodebase,
                romName = romName,
                romVersion = romVersion,
                losBased = losBased,
                buildFingerprint = props["ro.build.fingerprint"] ?: "",
                buildIncremental = props["ro.build.version.incremental"] ?: "",
                buildFlavor = props["ro.build.flavor"] ?: "",
                checkedAtMs = System.currentTimeMillis(),
            )
        } finally {
            runSu("rm -rf ${shQuote(workDir.absolutePath)}", timeoutSec = 15)
            workDir.deleteRecursively()
        }
    }

    private fun getCacheFile(context: Context): File =
        File(context.filesDir, "recovery_slot_cache.json")

    private fun loadDiskCache(context: Context): Map<String, SlotRecoveryInfo> {
        val f = getCacheFile(context)
        if (!f.isFile) return emptyMap()
        return try {
            val root = JSONObject(f.readText())
            if (root.optInt("v", 1) != 2) return emptyMap()
            val out = mutableMapOf<String, SlotRecoveryInfo>()
            for (slot in listOf("a", "b")) {
                val o = root.optJSONObject(slot) ?: continue
                val utc = o.optLong("buildDateUtcSec", 0L)
                out[slot] = SlotRecoveryInfo(
                    slot = slot,
                    blockSha256 = o.optString("blockSha256", ""),
                    type = runCatching {
                        InstalledRecoveryType.valueOf(o.optString("type", "UNKNOWN"))
                    }.getOrDefault(InstalledRecoveryType.UNKNOWN),
                    versionName = o.optString("versionName", "—"),
                    buildDateUtcSec = utc,
                    formattedBuildDate = formatBuildDate(utc),
                    buildHost = o.optString("buildHost", ""),
                    foxCodebase = o.optString("foxCodebase", ""),
                    romName = o.optString("romName", "AOSP"),
                    romVersion = o.optString("romVersion", ""),
                    losBased = o.optBoolean("losBased", false),
                    buildFingerprint = o.optString("buildFingerprint", ""),
                    buildIncremental = o.optString("buildIncremental", ""),
                    buildFlavor = o.optString("buildFlavor", ""),
                    checkedAtMs = o.optLong("checkedAtMs", 0L),
                )
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveDiskCache(context: Context, map: Map<String, SlotRecoveryInfo>) {
        try {
            val root = JSONObject()
            root.put("v", 2)
            for ((slot, info) in map) {
                root.put(
                    slot,
                    JSONObject().apply {
                        put("blockSha256", info.blockSha256)
                        put("type", info.type.name)
                        put("versionName", info.versionName)
                        put("buildDateUtcSec", info.buildDateUtcSec)
                        put("buildHost", info.buildHost)
                        put("foxCodebase", info.foxCodebase)
                        put("romName", info.romName)
                        put("romVersion", info.romVersion)
                        put("losBased", info.losBased)
                        put("buildFingerprint", info.buildFingerprint)
                        put("buildIncremental", info.buildIncremental)
                        put("buildFlavor", info.buildFlavor)
                        put("checkedAtMs", info.checkedAtMs)
                    },
                )
            }
            getCacheFile(context).writeText(root.toString())
        } catch (_: Exception) {
        }
    }
}

@Composable
fun InstalledRecoveryStatusCard(
    onManualRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeSlot = RecoveryStateCache.activeSlot
    val isInspecting = RecoveryStateCache.isInspecting
    val hasRoot = RecoveryStateCache.hasRoot
    val slotsMap = RecoveryStateCache.slotInfo

    val initialPage = if (activeSlot.equals("b", ignoreCase = true)) 1 else 0
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })

    LaunchedEffect(activeSlot) {
        val targetPage = if (activeSlot.equals("b", ignoreCase = true)) 1 else 0
        pagerState.animateScrollToPage(targetPage)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "RecInspectSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "RecSpinAngle",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(),
                RoundedCornerShape(22.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.recovery_status_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Две точки индикатора слотов (Слот A / Слот B)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(2) { pageIdx ->
                        val selected = pagerState.currentPage == pageIdx
                        val dotWidth by animateDpAsState(
                            targetValue = if (selected) 20.dp else 8.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "SlotDotWidth",
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            label = "SlotDotColor",
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    enabled = !isInspecting,
                    onClick = onManualRefresh,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.action_recheck_recovery),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(if (isInspecting) spinAngle else 0f),
                    )
                }
            }

            if (isInspecting) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpressiveLinearProgress()
                    Text(
                        text = stringResource(R.string.recovery_inspecting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (hasRoot == false && slotsMap.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.recovery_no_root_cached_warn),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else if (hasRoot == false && slotsMap.isEmpty() && !isInspecting) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.recovery_no_root_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (slotsMap.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    val slotKey = if (page == 0) "a" else "b"
                    val info = slotsMap[slotKey]
                    val isCurrentActive = activeSlot.equals(slotKey, ignoreCase = true)

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isCurrentActive) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh.edgeOutline()
                                },
                                shape = RoundedCornerShape(16.dp),
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.recovery_slot_label, slotKey.uppercase()),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (isCurrentActive) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.recovery_slot_active_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                if (info != null && RecoveryStateCache.matchesLiveSystem(info)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.recovery_running_now),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }

                            if (info != null) {
                                val typeLabel = when (info.type) {
                                    InstalledRecoveryType.MY_OFOX -> stringResource(R.string.recovery_type_my_ofox)
                                    InstalledRecoveryType.OTHER_OFOX -> stringResource(R.string.recovery_type_other_ofox)
                                    InstalledRecoveryType.OTHER_TWRP -> stringResource(R.string.recovery_type_twrp)
                                    InstalledRecoveryType.LOS_BASED -> if (info.romName == "LineageOS") {
                                        stringResource(R.string.recovery_type_los)
                                    } else {
                                        stringResource(R.string.recovery_custom_title, info.romName)
                                    }
                                    InstalledRecoveryType.STOCK -> if (info.romName == "Stock" || info.romName == "AOSP") {
                                        stringResource(R.string.recovery_type_stock)
                                    } else {
                                        stringResource(R.string.recovery_custom_title, info.romName)
                                    }
                                    InstalledRecoveryType.UNKNOWN -> stringResource(R.string.recovery_type_unknown)
                                }
                                Text(
                                    text = typeLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = info.versionName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = 10.sp,
                                        maxFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                        stepSize = 1.sp,
                                    ),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.recovery_base_fmt,
                                        when (info.type) {
                                            InstalledRecoveryType.MY_OFOX,
                                            InstalledRecoveryType.OTHER_OFOX,
                                            InstalledRecoveryType.OTHER_TWRP,
                                            -> stringResource(R.string.recovery_base_twrp)
                                            else -> if (info.losBased) {
                                                stringResource(R.string.recovery_base_los)
                                            } else {
                                                stringResource(R.string.recovery_base_aosp)
                                            }
                                        },
                                        info.romName,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.recovery_build_date, info.formattedBuildDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
