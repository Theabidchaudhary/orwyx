package com.orwyx.player.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.orwyx.player.domain.model.LibraryLayout
import com.orwyx.player.domain.model.SortBy
import com.orwyx.player.domain.model.SortDirection
import com.orwyx.player.domain.model.VideoCardField
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "orwyx_settings")

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class DecoderMode { AUTO, HARDWARE, SOFTWARE }
enum class BatteryMode { SAVER, BALANCED, PERFORMANCE }
enum class ResumeMode { ALWAYS, ASK, NEVER }
enum class ZoomMode { FIT, FILL, STRETCH, ORIGINAL }

/** Everything user-configurable, exposed as one cold snapshot flow. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Playback
    val defaultSpeed: Float = 1.0f,
    val resumeMode: ResumeMode = ResumeMode.ALWAYS,
    val autoRotate: Boolean = true,
    val gestureSensitivity: Float = 1.0f,
    val seekStepSeconds: Int = 10,
    val decoderMode: DecoderMode = DecoderMode.AUTO,
    val backgroundPlayback: Boolean = false,
    val rememberZoom: Boolean = true,
    val zoomMode: ZoomMode = ZoomMode.FIT,
    // Library
    val autoScan: Boolean = true,
    val hasScannedOnce: Boolean = false,
    val ignoredFolders: Set<String> = emptySet(),
    val hiddenFolders: Set<String> = emptySet(),
    val safFolders: Set<String> = emptySet(),
    // Display (global: shared by the folder list and every folder's video list)
    val librarySortBy: SortBy = SortBy.DATE,
    val libraryDirection: SortDirection = SortDirection.DESCENDING,
    val libraryLayout: LibraryLayout = LibraryLayout.LIST,
    val videoCardFields: Set<String> = VideoCardField.DEFAULT_ENABLED,
    // Subtitles
    val subtitleAutoLoad: Boolean = true,
    val subtitlePreferredLanguage: String = "en",
    val subtitleTextScale: Float = 1.0f,
    val subtitleColor: Long = 0xFFFFFFFF,
    val subtitleOutline: Boolean = true,
    val subtitleShadow: Boolean = true,
    val subtitleBackground: Boolean = false,
    val subtitleBottomOffsetFraction: Float = 0.08f,
    val subtitleOpacity: Float = 1.0f,
    val subtitleEncoding: String = "UTF-8",
    // Battery
    val batteryMode: BatteryMode = BatteryMode.BALANCED,
    // Private folder (PBKDF2 hash + salt, never the PIN itself)
    val pinHash: String = "",
    val pinSalt: String = "",
    val biometricUnlock: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val RESUME_MODE = stringPreferencesKey("resume_mode")
        val AUTO_ROTATE = booleanPreferencesKey("auto_rotate")
        val GESTURE_SENSITIVITY = floatPreferencesKey("gesture_sensitivity")
        val SEEK_STEP = intPreferencesKey("seek_step")
        val DECODER = stringPreferencesKey("decoder")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val REMEMBER_ZOOM = booleanPreferencesKey("remember_zoom")
        val ZOOM_MODE = stringPreferencesKey("zoom_mode")
        val AUTO_SCAN = booleanPreferencesKey("auto_scan")
        val HAS_SCANNED_ONCE = booleanPreferencesKey("has_scanned_once")
        val IGNORED_FOLDERS = stringSetPreferencesKey("ignored_folders")
        val HIDDEN_FOLDERS = stringSetPreferencesKey("hidden_folders")
        val SAF_FOLDERS = stringSetPreferencesKey("saf_folders")
        val LIBRARY_SORT_BY = stringPreferencesKey("library_sort_by")
        val LIBRARY_DIRECTION = stringPreferencesKey("library_direction")
        val LIBRARY_LAYOUT = stringPreferencesKey("library_layout")
        val VIDEO_CARD_FIELDS = stringSetPreferencesKey("video_card_fields")
        val SUB_AUTO_LOAD = booleanPreferencesKey("sub_auto_load")
        val SUB_LANGUAGE = stringPreferencesKey("sub_language")
        val SUB_SCALE = floatPreferencesKey("sub_scale")
        val SUB_COLOR = stringPreferencesKey("sub_color")
        val SUB_OUTLINE = booleanPreferencesKey("sub_outline")
        val SUB_SHADOW = booleanPreferencesKey("sub_shadow")
        val SUB_BACKGROUND = booleanPreferencesKey("sub_background")
        val SUB_OFFSET = floatPreferencesKey("sub_offset")
        val SUB_OPACITY = floatPreferencesKey("sub_opacity")
        val SUB_ENCODING = stringPreferencesKey("sub_encoding")
        val BATTERY_MODE = stringPreferencesKey("battery_mode")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC = booleanPreferencesKey("biometric")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .map { p ->
            AppSettings(
                themeMode = p.enum(Keys.THEME, ThemeMode.SYSTEM),
                defaultSpeed = p[Keys.DEFAULT_SPEED] ?: 1.0f,
                resumeMode = p.enum(Keys.RESUME_MODE, ResumeMode.ALWAYS),
                autoRotate = p[Keys.AUTO_ROTATE] ?: true,
                gestureSensitivity = p[Keys.GESTURE_SENSITIVITY] ?: 1.0f,
                seekStepSeconds = p[Keys.SEEK_STEP] ?: 10,
                decoderMode = p.enum(Keys.DECODER, DecoderMode.AUTO),
                backgroundPlayback = p[Keys.BACKGROUND_PLAYBACK] ?: false,
                rememberZoom = p[Keys.REMEMBER_ZOOM] ?: true,
                zoomMode = p.enum(Keys.ZOOM_MODE, ZoomMode.FIT),
                autoScan = p[Keys.AUTO_SCAN] ?: true,
                hasScannedOnce = p[Keys.HAS_SCANNED_ONCE] ?: false,
                ignoredFolders = p[Keys.IGNORED_FOLDERS] ?: emptySet(),
                hiddenFolders = p[Keys.HIDDEN_FOLDERS] ?: emptySet(),
                safFolders = p[Keys.SAF_FOLDERS] ?: emptySet(),
                librarySortBy = p.enum(Keys.LIBRARY_SORT_BY, SortBy.DATE),
                libraryDirection = p.enum(Keys.LIBRARY_DIRECTION, SortDirection.DESCENDING),
                libraryLayout = p.enum(Keys.LIBRARY_LAYOUT, LibraryLayout.LIST),
                videoCardFields = p[Keys.VIDEO_CARD_FIELDS] ?: VideoCardField.DEFAULT_ENABLED,
                subtitleAutoLoad = p[Keys.SUB_AUTO_LOAD] ?: true,
                subtitlePreferredLanguage = p[Keys.SUB_LANGUAGE] ?: "en",
                subtitleTextScale = p[Keys.SUB_SCALE] ?: 1.0f,
                subtitleColor = p[Keys.SUB_COLOR]?.toLongOrNull(16) ?: 0xFFFFFFFF,
                subtitleOutline = p[Keys.SUB_OUTLINE] ?: true,
                subtitleShadow = p[Keys.SUB_SHADOW] ?: true,
                subtitleBackground = p[Keys.SUB_BACKGROUND] ?: false,
                subtitleBottomOffsetFraction = p[Keys.SUB_OFFSET] ?: 0.08f,
                subtitleOpacity = p[Keys.SUB_OPACITY] ?: 1.0f,
                subtitleEncoding = p[Keys.SUB_ENCODING] ?: "UTF-8",
                batteryMode = p.enum(Keys.BATTERY_MODE, BatteryMode.BALANCED),
                pinHash = p[Keys.PIN_HASH] ?: "",
                pinSalt = p[Keys.PIN_SALT] ?: "",
                biometricUnlock = p[Keys.BIOMETRIC] ?: true,
            )
        }
        .distinctUntilChanged()

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME] = value.name }
    suspend fun setDefaultSpeed(value: Float) = edit { it[Keys.DEFAULT_SPEED] = value }
    suspend fun setResumeMode(value: ResumeMode) = edit { it[Keys.RESUME_MODE] = value.name }
    suspend fun setAutoRotate(value: Boolean) = edit { it[Keys.AUTO_ROTATE] = value }
    suspend fun setGestureSensitivity(value: Float) = edit { it[Keys.GESTURE_SENSITIVITY] = value }
    suspend fun setSeekStepSeconds(value: Int) = edit { it[Keys.SEEK_STEP] = value }
    suspend fun setDecoderMode(value: DecoderMode) = edit { it[Keys.DECODER] = value.name }
    suspend fun setBackgroundPlayback(value: Boolean) = edit { it[Keys.BACKGROUND_PLAYBACK] = value }
    suspend fun setRememberZoom(value: Boolean) = edit { it[Keys.REMEMBER_ZOOM] = value }
    suspend fun setZoomMode(value: ZoomMode) = edit { it[Keys.ZOOM_MODE] = value.name }
    suspend fun setAutoScan(value: Boolean) = edit { it[Keys.AUTO_SCAN] = value }
    suspend fun setHasScannedOnce(value: Boolean) = edit { it[Keys.HAS_SCANNED_ONCE] = value }
    suspend fun setLibrarySortBy(value: SortBy) = edit { it[Keys.LIBRARY_SORT_BY] = value.name }
    suspend fun setLibraryDirection(value: SortDirection) = edit { it[Keys.LIBRARY_DIRECTION] = value.name }
    suspend fun setLibraryLayout(value: LibraryLayout) = edit { it[Keys.LIBRARY_LAYOUT] = value.name }
    suspend fun toggleVideoCardField(key: String) = edit {
        val current = it[Keys.VIDEO_CARD_FIELDS] ?: VideoCardField.DEFAULT_ENABLED
        it[Keys.VIDEO_CARD_FIELDS] = if (key in current) current - key else current + key
    }
    suspend fun setBatteryMode(value: BatteryMode) = edit { it[Keys.BATTERY_MODE] = value.name }
    suspend fun setSubtitleAutoLoad(value: Boolean) = edit { it[Keys.SUB_AUTO_LOAD] = value }
    suspend fun setSubtitlePreferredLanguage(value: String) = edit { it[Keys.SUB_LANGUAGE] = value }
    suspend fun setSubtitleTextScale(value: Float) = edit { it[Keys.SUB_SCALE] = value }
    suspend fun setSubtitleColor(value: Long) = edit { it[Keys.SUB_COLOR] = value.toString(16) }
    suspend fun setSubtitleOutline(value: Boolean) = edit { it[Keys.SUB_OUTLINE] = value }
    suspend fun setSubtitleShadow(value: Boolean) = edit { it[Keys.SUB_SHADOW] = value }
    suspend fun setSubtitleBackground(value: Boolean) = edit { it[Keys.SUB_BACKGROUND] = value }
    suspend fun setSubtitleBottomOffset(value: Float) = edit { it[Keys.SUB_OFFSET] = value }
    suspend fun setSubtitleOpacity(value: Float) = edit { it[Keys.SUB_OPACITY] = value }
    suspend fun setSubtitleEncoding(value: String) = edit { it[Keys.SUB_ENCODING] = value }
    suspend fun setBiometricUnlock(value: Boolean) = edit { it[Keys.BIOMETRIC] = value }

    suspend fun setPin(hash: String, salt: String) = edit {
        it[Keys.PIN_HASH] = hash
        it[Keys.PIN_SALT] = salt
    }

    suspend fun toggleHiddenFolder(path: String) = edit {
        val current = it[Keys.HIDDEN_FOLDERS] ?: emptySet()
        it[Keys.HIDDEN_FOLDERS] = if (path in current) current - path else current + path
    }

    suspend fun toggleIgnoredFolder(path: String) = edit {
        val current = it[Keys.IGNORED_FOLDERS] ?: emptySet()
        it[Keys.IGNORED_FOLDERS] = if (path in current) current - path else current + path
    }

    suspend fun addSafFolder(treeUri: String) = edit {
        it[Keys.SAF_FOLDERS] = (it[Keys.SAF_FOLDERS] ?: emptySet()) + treeUri
    }

    suspend fun removeSafFolder(treeUri: String) = edit {
        it[Keys.SAF_FOLDERS] = (it[Keys.SAF_FOLDERS] ?: emptySet()) - treeUri
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private inline fun <reified T : Enum<T>> Preferences.enum(
        key: Preferences.Key<String>,
        default: T,
    ): T = this[key]?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: default
}
