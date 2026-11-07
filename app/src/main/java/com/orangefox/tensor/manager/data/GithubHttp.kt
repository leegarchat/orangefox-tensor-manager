package com.orangefox.tensor.manager.data

import okhttp3.OkHttpClient
import okhttp3.Request
import com.orangefox.tensor.manager.BuildConfig

internal val sharedHttpClient = OkHttpClient.Builder()
    // No callTimeout on purpose: the same client streams large APK/ZIP
    // downloads that may legitimately take minutes. Stalls are bounded
    // by the per-read timeout instead; short metadata calls fail fast.
    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
    .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
    .build()

/**
 * Shipped read-only token (Variant B): used ONLY as fallback when the
 * anonymous quota is exhausted. Order: anonymous first, token on 403/429.
 */
internal object GithubAuth {
    @Volatile var tokenInvalid = false
    @Volatile var tokenExhaustedUntilMs = 0L
    /** Debug override (Settings, debug builds only): token first instead of anonymous. */
    @Volatile var forceToken = false

    fun token(): String = try {
        BuildConfig.GITHUB_TOKEN
    } catch (_: Exception) {
        ""
    }

    fun usable(): Boolean {
        if (tokenInvalid) return false
        if (token().isBlank()) return false
        return System.currentTimeMillis() >= tokenExhaustedUntilMs
    }

    fun markInvalid() {
        tokenInvalid = true
    }

    fun markExhausted(resetEpochSec: Long) {
        tokenExhaustedUntilMs = if (resetEpochSec > 0) {
            resetEpochSec * 1000L + 60_000L
        } else {
            System.currentTimeMillis() + 10 * 60_000L
        }
    }
}

private fun isGithubRateLimited(code: Int) = code == 429 || code == 403

/**
 * Anonymous-first GitHub call with token fallback. Executes anonymously;
 * on 403/429 retries ONCE with the shipped token (if usable) and tracks
 * its state (invalid / exhausted-until-reset). Caller must consume/close
 * the returned response (e.g. `.use { }`) exactly like a normal call.
 */
internal fun OkHttpClient.executeGithub(request: Request): okhttp3.Response {
    if (GithubAuth.forceToken && GithubAuth.usable()) {
        val authed = request.newBuilder()
            .header("Authorization", "Bearer ${GithubAuth.token()}")
            .build()
        val forced = newCall(authed).execute()
        if (forced.code != 401) {
            if (isGithubRateLimited(forced.code)) {
                GithubAuth.markExhausted(forced.header("X-RateLimit-Reset")?.toLongOrNull() ?: 0L)
            }
            return forced
        }
        GithubAuth.markInvalid()
        forced.close()
    }
    var resp = newCall(request).execute()
    if (isGithubRateLimited(resp.code) && GithubAuth.usable()) {
        resp.close()
        val authed = request.newBuilder()
            .header("Authorization", "Bearer ${GithubAuth.token()}")
            .build()
        resp = newCall(authed).execute()
        if (resp.code == 401) {
            GithubAuth.markInvalid()
        } else if (isGithubRateLimited(resp.code)) {
            GithubAuth.markExhausted(resp.header("X-RateLimit-Reset")?.toLongOrNull() ?: 0L)
        }
    }
    return resp
}
