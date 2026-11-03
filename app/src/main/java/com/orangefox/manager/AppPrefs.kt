package com.orangefox.manager

import android.content.Context
import androidx.annotation.StringRes

class AppPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("ofox_prefs", Context.MODE_PRIVATE)

    var blurRadius: Float
        get() = sp.getFloat(KEY_BLUR, DEFAULT_BLUR)
        set(v) { sp.edit().putFloat(KEY_BLUR, v).apply() }

    var themeMode: Int
        get() = sp.getInt(KEY_THEME, ThemeMode.System.ordinal)
        set(v) { sp.edit().putInt(KEY_THEME, v).apply() }

    var showBannerInCollapsed: Boolean
        get() = sp.getBoolean(KEY_BANNER_COLLAPSED, true)
        set(v) { sp.edit().putBoolean(KEY_BANNER_COLLAPSED, v).apply() }

    var collapseH2AsSpoilers: Boolean
        get() = sp.getBoolean(KEY_COLLAPSE_H2, true)
        set(v) { sp.edit().putBoolean(KEY_COLLAPSE_H2, v).apply() }

    var collapseH2Home: Boolean
        get() = sp.getBoolean(KEY_COLLAPSE_H2_HOME, true)
        set(v) { sp.edit().putBoolean(KEY_COLLAPSE_H2_HOME, v).apply() }

    var docsLastRefresh: Long
        get() = sp.getLong(KEY_DOCS_REFRESH, 0L)
        set(v) { sp.edit().putLong(KEY_DOCS_REFRESH, v).apply() }

    var releaseFilterMode: Int
        get() = sp.getInt(KEY_RELEASE_FILTER, ReleaseFilterMode.Main.ordinal)
        set(v) { sp.edit().putInt(KEY_RELEASE_FILTER, v).apply() }

    var releaseNotifyMode: Int
        get() = sp.getInt(KEY_RELEASE_NOTIFY, ReleaseNotifyMode.None.ordinal)
        set(v) { sp.edit().putInt(KEY_RELEASE_NOTIFY, v).apply() }

    var backupVendorBootOnFlash: Boolean
        get() = sp.getBoolean(KEY_BACKUP_ON_FLASH, true)
        set(v) { sp.edit().putBoolean(KEY_BACKUP_ON_FLASH, v).apply() }

    var spaceErrorSeen: Boolean
        get() = sp.getBoolean(KEY_SPACE_ERROR_SEEN, false)
        set(v) { sp.edit().putBoolean(KEY_SPACE_ERROR_SEEN, v).apply() }

    var lastNotifiedReleaseTag: String
        get() = sp.getString(KEY_LAST_NOTIFIED_TAG, "") ?: ""
        set(v) { sp.edit().putString(KEY_LAST_NOTIFIED_TAG, v).apply() }

    companion object {
        const val DEFAULT_BLUR = 40f
        private const val KEY_BLUR = "blur_radius"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_BANNER_COLLAPSED = "show_banner_collapsed"
        private const val KEY_COLLAPSE_H2 = "collapse_h2_spoilers"
        private const val KEY_COLLAPSE_H2_HOME = "collapse_h2_home"
        private const val KEY_DOCS_REFRESH = "docs_last_refresh"
        private const val KEY_RELEASE_FILTER = "release_filter_mode"
        private const val KEY_RELEASE_NOTIFY = "release_notify_mode"
        private const val KEY_BACKUP_ON_FLASH = "backup_vendor_boot_on_flash"
        private const val KEY_SPACE_ERROR_SEEN = "space_error_seen"
        private const val KEY_LAST_NOTIFIED_TAG = "last_notified_release_tag"
    }
}

enum class ThemeMode(@StringRes val labelRes: Int) {
    System(R.string.theme_system),
    Light(R.string.theme_light),
    Dark(R.string.theme_dark);

    companion object {
        fun fromOrdinal(o: Int): ThemeMode = entries.getOrElse(o) { System }
    }
}

enum class ReleaseFilterMode(@StringRes val labelRes: Int) {
    Main(R.string.filter_releases_main),
    Prerelease(R.string.filter_releases_prerelease),
    All(R.string.filter_releases_all);

    companion object {
        fun fromOrdinal(o: Int): ReleaseFilterMode = entries.getOrElse(o) { Main }
    }
}

enum class ReleaseNotifyMode(@StringRes val labelRes: Int) {
    None(R.string.notify_releases_none),
    All(R.string.notify_releases_all),
    Main(R.string.notify_releases_main);

    companion object {
        fun fromOrdinal(o: Int): ReleaseNotifyMode = entries.getOrElse(o) { None }
    }
}
