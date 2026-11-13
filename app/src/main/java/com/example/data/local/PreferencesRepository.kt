package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.clonixDataStore: DataStore<Preferences> by preferencesDataStore(name = "clonix_prefs")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultRegionCode: String = "US",
    val strictAntiDetect: Boolean = true,
    val autoLockOnExit: Boolean = true,
    val hasSkippedCloudAuth: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null
) {
    val isPinConfigured: Boolean get() = !pinHash.isNullOrBlank() && !pinSalt.isNullOrBlank()
}

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_REGION = stringPreferencesKey("default_region")
        val STRICT_ANTI_DETECT = booleanPreferencesKey("strict_anti_detect")
        val AUTO_LOCK_ON_EXIT = booleanPreferencesKey("auto_lock_on_exit")
        val SKIPPED_CLOUD_AUTH = booleanPreferencesKey("skipped_cloud_auth")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
    }

    val preferencesFlow: Flow<UserPreferences> = context.clonixDataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        UserPreferences(
            themeMode = themeMode,
            defaultRegionCode = prefs[Keys.DEFAULT_REGION] ?: "US",
            strictAntiDetect = prefs[Keys.STRICT_ANTI_DETECT] ?: true,
            autoLockOnExit = prefs[Keys.AUTO_LOCK_ON_EXIT] ?: true,
            hasSkippedCloudAuth = prefs[Keys.SKIPPED_CLOUD_AUTH] ?: false,
            pinHash = prefs[Keys.PIN_HASH],
            pinSalt = prefs[Keys.PIN_SALT]
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.clonixDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultRegion(regionCode: String) {
        context.clonixDataStore.edit { it[Keys.DEFAULT_REGION] = regionCode }
    }

    suspend fun setStrictAntiDetect(enabled: Boolean) {
        context.clonixDataStore.edit { it[Keys.STRICT_ANTI_DETECT] = enabled }
    }

    suspend fun setAutoLockOnExit(enabled: Boolean) {
        context.clonixDataStore.edit { it[Keys.AUTO_LOCK_ON_EXIT] = enabled }
    }

    suspend fun setHasSkippedCloudAuth(skipped: Boolean) {
        context.clonixDataStore.edit { it[Keys.SKIPPED_CLOUD_AUTH] = skipped }
    }

    suspend fun setPinCredential(hash: String?, salt: String?) {
        context.clonixDataStore.edit { prefs ->
            if (hash == null || salt == null) {
                prefs.remove(Keys.PIN_HASH)
                prefs.remove(Keys.PIN_SALT)
            } else {
                prefs[Keys.PIN_HASH] = hash
                prefs[Keys.PIN_SALT] = salt
            }
        }
    }
}
