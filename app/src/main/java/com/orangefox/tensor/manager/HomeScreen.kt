package com.orangefox.tensor.manager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import com.orangefox.tensor.manager.ui.ExpressiveLinearProgress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orangefox.tensor.manager.ui.DocLink
import com.orangefox.tensor.manager.ui.MarkdownBody
import com.orangefox.tensor.manager.ui.NetworkBannerImage
import com.orangefox.tensor.manager.ui.extractBannerAndCleanMarkdown

/** Состояние навигации Home для круглых кнопок в Root (вне записываемого слоя блюра). */
class HomeNavState {
    var visible by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var onBack: (() -> Unit)? = null
    var onHome: (() -> Unit)? = null
}

/** Состояние полосы чипов документов Home (сама полоса живёт в Root, вне записываемого слоя). */
class HomeChipsState {
    var docs by mutableStateOf<List<DocInfo>>(emptyList())
    var currentId by mutableStateOf<String?>(null)
    var onSelect: ((String) -> Unit)? = null
}

private const val GH_BLOB_BASE =
    "https://github.com/leegarchat/twrp_device_google_pixels/blob/R12_16"

/** Резолвит ссылку из markdown в internal (свой док) или external (браузер). */
fun resolveDocLink(target: String, currentId: String?, docs: List<DocInfo>): DocLink {
    val t = target.trim()
    if (t.isEmpty()) return DocLink.External(t)
    if (t.startsWith("http://", ignoreCase = true) ||
        t.startsWith("https://", ignoreCase = true) ||
        t.startsWith("mailto:", ignoreCase = true)
    ) return DocLink.External(t)
    if (t.startsWith("#")) return DocLink.External(t)
    val path = t.substringBefore("#").trim().substringBefore("?")
    if (path.isEmpty()) return DocLink.External(t)

    // Абсолютные github-ссылки вида /<owner>/<repo>/blob/<branch>/docs/x.md
    val blobMatch = Regex("""/blob/[^/]+/(.+)$""").find(path)
    val norm = if (blobMatch != null) {
        normalizeDocPath("", blobMatch.groupValues[1])
    } else {
        val baseDir = currentId?.let { if (it.contains("/")) it.substringBeforeLast("/") else "" } ?: ""
        normalizeDocPath(baseDir, path)
    }
    val known = docs.firstOrNull { it.id.equals(norm, ignoreCase = true) }
    return if (known != null) DocLink.Internal(known.id)
    else DocLink.External("$GH_BLOB_BASE/$norm")
}

private fun normalizeDocPath(baseDir: String, rel: String): String {
    val segs = mutableListOf<String>()
    val cleanRel = rel.removePrefix("/")
    if (!rel.startsWith("/")) {
        if (baseDir.isNotEmpty()) segs.addAll(baseDir.split("/").filter { it.isNotEmpty() })
    }
    cleanRel.split("/").forEach { s ->
        when (s) {
            "", "." -> {}
            ".." -> if (segs.isNotEmpty()) segs.removeLast()
            else -> segs.add(s)
        }
    }
    return segs.joinToString("/")
}

@Composable
fun HomeScreen(
    topInset: Dp,
    chipsBarHeight: Dp,
    collapseH2Home: Boolean,
    nav: HomeNavState,
    chips: HomeChipsState,
) {
    val context = LocalContext.current
    val cacheDir = context.cacheDir
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { AppPrefs(context) }

    val docs = DocsSessionCache.docs
    val contents = DocsSessionCache.contents
    val listError = DocsSessionCache.listError
    val isRefreshing = DocsSessionCache.isRefreshing

    var currentId by rememberSaveable { mutableStateOf<String?>(null) }
    val history = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        DocsSessionCache.ensureLoaded(scope, cacheDir, prefs)
    }
    LaunchedEffect(docs) {
        if (currentId == null) {
            currentId = DocsSessionCache.mainDocId()
        }
    }
    LaunchedEffect(currentId) {
        if (currentId != null) listState.scrollToItem(0)
    }

    fun openDoc(id: String) {
        val cur = currentId
        if (cur != null && cur != id) history.add(cur)
        currentId = id
    }
    fun goBack() {
        if (history.isNotEmpty()) {
            currentId = history.removeLast()
        }
    }
    fun goHome() {
        history.clear()
        currentId = DocsSessionCache.mainDocId()
    }

    androidx.activity.compose.BackHandler(enabled = history.isNotEmpty()) {
        goBack()
    }

    LaunchedEffect(currentId, history.size, docs) {
        nav.visible = docs.isNotEmpty() && currentId != null
        nav.canGoBack = history.isNotEmpty()
        chips.docs = docs
        chips.currentId = currentId
    }

    DisposableEffect(Unit) {
        nav.onBack = { goBack() }
        nav.onHome = { goHome() }
        chips.onSelect = { id -> openDoc(id) }
        onDispose {
            nav.visible = false
            nav.onBack = null
            nav.onHome = null
            chips.onSelect = null
            chips.docs = emptyList()
            chips.currentId = null
        }
    }

    val current = docs.firstOrNull { it.id == currentId }
    val raw = current?.let { contents[it.id] }
    val (bannerUrl, cleanBody) = remember(raw) {
        if (raw != null) extractBannerAndCleanMarkdown(raw) else null to ""
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val listTopInset = topInset + (if (docs.size > 1) chipsBarHeight else 0.dp) + 8.dp
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = listTopInset,
                bottom = 128.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                listError != null && docs.isEmpty() -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.releases_error, listError ?: ""))
                        Button(onClick = {
                            DocsSessionCache.refresh(scope, cacheDir, prefs, force = true)
                        }) { Text(stringResource(R.string.action_retry)) }
                    }
                }
                raw == null -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExpressiveLinearProgress()
                        Text(stringResource(R.string.releases_loading))
                    }
                }
                else -> {
                    if (bannerUrl != null) {
                        item(key = "banner") {
                            NetworkBannerImage(
                                url = bannerUrl,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    item(key = "body-$currentId") {
                        MarkdownBody(
                            md = cleanBody,
                            collapseH2Sections = collapseH2Home,
                            docLinkResolver = { target -> resolveDocLink(target, currentId, docs) },
                            onDocLinkClick = { docId -> openDoc(docId) },
                        )
                    }
                }
            }
        }

        if (isRefreshing && docs.isNotEmpty()) {
            ExpressiveLinearProgress(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset),
            )
        }
    }
}
