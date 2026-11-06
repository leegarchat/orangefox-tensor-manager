package com.orangefox.tensor.manager

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.Locale

// Experimenting-docs branch. Full base URLs live in resources
// (url_docs_raw_base / url_docs_api_base) and are resolved via Context.
// DOCS_BRANCH must match the branch baked into those URLs.
private const val DOCS_BRANCH = "R12_16"
private const val DOCS_REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L



data class DocInfo(
    val id: String,
    val title: String,
    val rawUrl: String,
    val cacheName: String,
    /** Base URL for resolving relative images inside the file. */
    val baseRawDir: String,
    val isMain: Boolean = false,
)

object DocsSessionCache {
    var docs by mutableStateOf<List<DocInfo>>(emptyList())
    var contents = mutableStateMapOf<String, String>()
    var listError by mutableStateOf<String?>(null)
    var isRefreshing by mutableStateOf(false)

    fun mainDocId(): String? {
        val list = docs
        if (list.isEmpty()) return null
        val ruLangs = setOf("ru", "uk", "be", "kk", "uz", "az", "hy", "ka", "tg", "ky", "tt", "ba", "cv", "sah")
        val preferRu = Locale.getDefault().language in ruLangs
        if (preferRu) {
            list.firstOrNull { it.id == "README_ru.md" }?.let { return it.id }
        }
        return list.firstOrNull { it.id == "README.md" }?.id ?: list.firstOrNull()?.id
    }

    fun ensureLoaded(scope: CoroutineScope, context: Context, cacheDir: File, prefs: AppPrefs) {
        if (docs.isNotEmpty() || isRefreshing) return
        if (loadFromDisk(context, cacheDir) &&
            System.currentTimeMillis() - prefs.docsLastRefresh < DOCS_REFRESH_INTERVAL_MS
        ) return
        refresh(scope, context, cacheDir, prefs, force = false)
    }

    fun refresh(scope: CoroutineScope, context: Context, cacheDir: File, prefs: AppPrefs, force: Boolean = false) {
        if (isRefreshing) return
        if (!force && docs.isNotEmpty() &&
            System.currentTimeMillis() - prefs.docsLastRefresh < DOCS_REFRESH_INTERVAL_MS
        ) return
        isRefreshing = true
        listError = null
        scope.launch(Dispatchers.IO) {
            try {
                val fetched = fetchDocsList(context)
                val dir = File(cacheDir, "docs_cache").apply { mkdirs() }
                val loaded = mutableMapOf<String, String>()
                fetched.forEach { doc ->
                    val dest = File(dir, doc.cacheName)
                    val text = try {
                        downloadText(doc.rawUrl).also { dest.writeText(it) }
                    } catch (_: Exception) {
                        if (dest.exists()) dest.readText() else throw IllegalStateException("no cache for ${doc.id}")
                    }
                    loaded[doc.id] = absolutizeMarkdownResources(text, doc.baseRawDir)
                }
                withContext(Dispatchers.Main) {
                    docs = fetched
                    contents.clear()
                    contents.putAll(loaded)
                    prefs.docsLastRefresh = System.currentTimeMillis()
                    isRefreshing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (docs.isEmpty() && !loadFromDisk(context, cacheDir)) {
                        listError = e.message ?: "load failed"
                    }
                    isRefreshing = false
                }
            }
        }
    }

    private fun loadFromDisk(context: Context, cacheDir: File): Boolean {
        val rawBase = context.getString(R.string.url_docs_raw_base)
        val dir = File(cacheDir, "docs_cache")
        if (!dir.isDirectory) return false
        val files = dir.listFiles { f -> f.isFile && f.length() > 0 } ?: return false
        if (files.isEmpty()) return false
        val rebuilt = mutableListOf<DocInfo>()
        val loaded = mutableMapOf<String, String>()
        files.sortedBy { it.name }.forEach { f ->
            val text = try {
                f.readText()
            } catch (_: Exception) {
                return@forEach
            }
            val doc = when {
                f.name == "README.md" -> DocInfo(
                    id = "README.md", title = "README",
                    rawUrl = "$rawBase/README.md",
                    cacheName = f.name, baseRawDir = rawBase, isMain = true,
                )
                f.name == "README_ru.md" -> DocInfo(
                    id = "README_ru.md", title = "README RU",
                    rawUrl = "$rawBase/README_ru.md",
                    cacheName = f.name, baseRawDir = rawBase,
                )
                f.name.startsWith("docs__") && f.name.endsWith(".md") -> {
                    val name = f.name.removePrefix("docs__")
                    DocInfo(
                        id = "docs/$name", title = prettifyDocTitle(name),
                        rawUrl = "$rawBase/docs/$name",
                        cacheName = f.name, baseRawDir = "$rawBase/docs",
                    )
                }
                else -> return@forEach
            }
            rebuilt.add(doc)
            loaded[doc.id] = absolutizeMarkdownResources(text, doc.baseRawDir)
        }
        if (rebuilt.isEmpty()) return false
        docs = rebuilt.sortedWith(compareBy({ !it.isMain }, { it.title }))
        contents.clear()
        contents.putAll(loaded)
        return true
    }
}

private fun fetchDocsList(context: Context): List<DocInfo> {
    val rawBase = context.getString(R.string.url_docs_raw_base)
    val apiBase = context.getString(R.string.url_docs_api_base)
    val out = mutableListOf(
        DocInfo(
            id = "README.md",
            title = "README",
            rawUrl = "$rawBase/README.md",
            cacheName = "README.md",
            baseRawDir = rawBase,
            isMain = true,
        ),
        DocInfo(
            id = "README_ru.md",
            title = "README RU",
            rawUrl = "$rawBase/README_ru.md",
            cacheName = "README_ru.md",
            baseRawDir = rawBase,
        ),
    )
    val req = Request.Builder()
        .url("$apiBase/docs?ref=$DOCS_BRANCH")
        .header("User-Agent", "PixelOFoxApp")
        .header("Accept", "application/vnd.github+json")
        .build()
    sharedHttpClient.newCall(req).execute().use { resp ->
        // 403 (rate limit) and other HTTP errors — throw so refresh()
        // falls back to the disk cache and does NOT overwrite the full list with two fallback READMEs.
        if (!resp.isSuccessful) error("docs list HTTP ${resp.code}")
        val arr = JSONArray(resp.body?.string() ?: return out)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("type") != "file") continue
            val name = o.optString("name")
            if (!name.endsWith(".md", ignoreCase = true)) continue
            val downloadUrl = o.optString("download_url")
            if (downloadUrl.isEmpty()) continue
            out.add(
                DocInfo(
                    id = "docs/$name",
                    title = prettifyDocTitle(name),
                    rawUrl = downloadUrl,
                    cacheName = "docs__$name",
                    baseRawDir = "$rawBase/docs",
                )
            )
        }
    }
    return out.sortedWith(compareBy({ !it.isMain }, { it.title }))
}

private fun prettifyDocTitle(fileName: String): String {
    var base = fileName.removeSuffix(".md").removeSuffix(".MD")
    val isRu = base.endsWith("_ru", ignoreCase = true)
    if (isRu) base = base.dropLast(3)
    val words = base.replace('_', ' ').replace('-', ' ').trim()
        .split(Regex("\\s+")).filter { it.isNotEmpty() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    return if (isRu) "$words (RU)" else words.ifEmpty { fileName }
}

private fun downloadText(url: String): String {
    val req = Request.Builder()
        .url(url)
        .header("User-Agent", "PixelOFoxApp")
        .build()
    sharedHttpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) error("docs HTTP ${resp.code}")
        return resp.body?.string() ?: error("empty doc")
    }
}

private val mdImageRelRegex =
    Regex("""!\[[^\]]*]\((?!https?://|data:|#)([^)\s]+)(?:\s+"[^"]*")?\)""")
private val htmlImgRelRegex =
    Regex("""<img\b[^>]*?\bsrc\s*=\s*["'](?!https?://|data:)([^"']+)["']""")

/** Resolves relative images into absolute raw URLs so they load from cache/network. */
private fun absolutizeMarkdownResources(md: String, baseDir: String): String {
    var out = mdImageRelRegex.replace(md) { m ->
        val rel = m.groupValues[1].trim().removePrefix("./")
        m.value.replace(m.groupValues[1], "$baseDir/$rel")
    }
    out = htmlImgRelRegex.replace(out) { m ->
        val rel = m.groupValues[1].trim().removePrefix("./")
        m.value.replace(m.groupValues[1], "$baseDir/$rel")
    }
    return out
}
