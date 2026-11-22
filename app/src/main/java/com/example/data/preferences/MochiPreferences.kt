package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.theme.BundledThemes
import com.example.theme.GlassIntensity
import com.example.theme.ShelfStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mochiDataStore: DataStore<Preferences> by preferencesDataStore(name = "mochi_prefs")

enum class AutoDarkMode(val label: String) {
    MANUAL("Manual"),
    FOLLOW_SYSTEM("Follow System"),
    EVENING_SCHEDULE("Evening (7pm–7am)")
}

enum class PastelIconOption(val id: String, val label: String, val bgHex: Long, val ribbonHex: Long) {
    SAKURA("sakura", "Sakura Milk", 0xFFFFF5F7, 0xFFE0527B),
    MATCHA("matcha", "Matcha Latte", 0xFFF5F8F1, 0xFF5D9451),
    LAVENDER("lavender", "Lavender Fog", 0xFFF6F2FF, 0xFF8C6CE8),
    MIDNIGHT("midnight", "Midnight Sakura", 0xFF1F1A24, 0xFFF4A6C8)
}

enum class ReaderPreset(val label: String, val description: String) {
    NOVEL("Novel", "Warm paper tones, subtle glass, calm shelf"),
    STUDY("Study", "Crisp contrast, structured list, solid chrome"),
    NIGHT("Night", "Midnight Sakura dark palette + warm filter"),
    COZY("Cozy", "Sakura Milk, wooden bookshelf, Mochi mascot")
}

data class UserPreferencesState(
    val activeLightThemeId: String = BundledThemes.DEFAULT_LIGHT_ID,
    val activeDarkThemeId: String = BundledThemes.DEFAULT_DARK_ID,
    val activeThemeId: String = BundledThemes.DEFAULT_LIGHT_ID,
    val autoDarkMode: AutoDarkMode = AutoDarkMode.FOLLOW_SYSTEM,
    val useMaterialYouDynamic: Boolean = false,
    val glassIntensity: GlassIntensity = GlassIntensity.RICH,
    val solidChrome: Boolean = false,
    val reduceMotion: Boolean = false,
    val defaultShelfStyle: ShelfStyle = ShelfStyle.FLAT_GRID,
    val skeuomorphicShelfEnabled: Boolean = false,
    val spineThicknessByPages: Boolean = true,
    val mascotEnabled: Boolean = true,
    val shelfBackgroundUri: String? = null,
    val shelfBackgroundDim: Float = 0.28f,
    val shelfBackgroundBlur: Float = 8f,
    val warmLightEnabled: Boolean = false,
    val warmLightIntensity: Float = 0.25f,
    val onboardingCompleted: Boolean = false,
    val selectedPastelIcon: PastelIconOption = PastelIconOption.SAKURA,
    val matchSystemBars: Boolean = true,
    val readerSettings: ReaderSettings = ReaderSettings()
)

class MochiPreferences(private val context: Context) {

    private object Keys {
        val ACTIVE_THEME_ID = stringPreferencesKey("active_theme_id")
        val ACTIVE_LIGHT_THEME_ID = stringPreferencesKey("active_light_theme_id")
        val ACTIVE_DARK_THEME_ID = stringPreferencesKey("active_dark_theme_id")
        val AUTO_DARK_MODE = stringPreferencesKey("auto_dark_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val GLASS_INTENSITY = stringPreferencesKey("glass_intensity")
        val SOLID_CHROME = booleanPreferencesKey("solid_chrome")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val SHELF_STYLE = stringPreferencesKey("shelf_style")
        val SKEUOMORPHIC_SHELF = booleanPreferencesKey("skeuomorphic_shelf")
        val SPINE_THICKNESS = booleanPreferencesKey("spine_thickness")
        val MASCOT_ENABLED = booleanPreferencesKey("mascot_enabled")
        val SHELF_BG_URI = stringPreferencesKey("shelf_bg_uri")
        val SHELF_BG_DIM = floatPreferencesKey("shelf_bg_dim")
        val SHELF_BG_BLUR = floatPreferencesKey("shelf_bg_blur")
        val WARM_LIGHT_ENABLED = booleanPreferencesKey("warm_light_enabled")
        val WARM_LIGHT_INTENSITY = floatPreferencesKey("warm_light_intensity")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SELECTED_ICON = stringPreferencesKey("selected_icon")
        val MATCH_SYSTEM_BARS = booleanPreferencesKey("match_system_bars")
        val READER_SETTINGS_JSON = stringPreferencesKey("reader_settings_json")
    }

    val state: Flow<UserPreferencesState> = context.mochiDataStore.data.map { prefs ->
        UserPreferencesState(
            activeLightThemeId = prefs[Keys.ACTIVE_LIGHT_THEME_ID] ?: BundledThemes.DEFAULT_LIGHT_ID,
            activeDarkThemeId = prefs[Keys.ACTIVE_DARK_THEME_ID] ?: BundledThemes.DEFAULT_DARK_ID,
            activeThemeId = prefs[Keys.ACTIVE_THEME_ID] ?: BundledThemes.DEFAULT_LIGHT_ID,
            autoDarkMode = prefs[Keys.AUTO_DARK_MODE]?.let { name ->
                AutoDarkMode.entries.find { it.name == name }
            } ?: AutoDarkMode.FOLLOW_SYSTEM,
            useMaterialYouDynamic = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
            glassIntensity = prefs[Keys.GLASS_INTENSITY]?.let { name ->
                GlassIntensity.entries.find { it.name == name }
            } ?: GlassIntensity.RICH,
            solidChrome = prefs[Keys.SOLID_CHROME] ?: false,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
            defaultShelfStyle = prefs[Keys.SHELF_STYLE]?.let { name ->
                ShelfStyle.entries.find { it.name == name }
            } ?: ShelfStyle.FLAT_GRID,
            skeuomorphicShelfEnabled = prefs[Keys.SKEUOMORPHIC_SHELF] ?: false,
            spineThicknessByPages = prefs[Keys.SPINE_THICKNESS] ?: true,
            mascotEnabled = prefs[Keys.MASCOT_ENABLED] ?: true,
            shelfBackgroundUri = prefs[Keys.SHELF_BG_URI],
            shelfBackgroundDim = prefs[Keys.SHELF_BG_DIM] ?: 0.28f,
            shelfBackgroundBlur = prefs[Keys.SHELF_BG_BLUR] ?: 8f,
            warmLightEnabled = prefs[Keys.WARM_LIGHT_ENABLED] ?: false,
            warmLightIntensity = prefs[Keys.WARM_LIGHT_INTENSITY] ?: 0.25f,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            selectedPastelIcon = prefs[Keys.SELECTED_ICON]?.let { id ->
                PastelIconOption.entries.find { it.id == id }
            } ?: PastelIconOption.SAKURA,
            matchSystemBars = prefs[Keys.MATCH_SYSTEM_BARS] ?: true,
            readerSettings = ReaderSettings.fromJson(prefs[Keys.READER_SETTINGS_JSON]) ?: ReaderSettings()
        )
    }

    suspend fun selectTheme(themeId: String, isDark: Boolean) {
        context.mochiDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_THEME_ID] = themeId
            prefs[Keys.AUTO_DARK_MODE] = AutoDarkMode.MANUAL.name
            prefs[Keys.USE_DYNAMIC_COLOR] = false
            if (isDark) {
                prefs[Keys.ACTIVE_DARK_THEME_ID] = themeId
            } else {
                prefs[Keys.ACTIVE_LIGHT_THEME_ID] = themeId
            }
        }
    }

    suspend fun setAutoDarkMode(mode: AutoDarkMode) {
        context.mochiDataStore.edit { it[Keys.AUTO_DARK_MODE] = mode.name }
    }

    suspend fun setMaterialYouDynamic(enabled: Boolean) {
        context.mochiDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setGlassIntensity(intensity: GlassIntensity) {
        context.mochiDataStore.edit { it[Keys.GLASS_INTENSITY] = intensity.name }
    }

    suspend fun setSolidChrome(enabled: Boolean) {
        context.mochiDataStore.edit { it[Keys.SOLID_CHROME] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.mochiDataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    suspend fun setShelfStyle(style: ShelfStyle) {
        context.mochiDataStore.edit { it[Keys.SHELF_STYLE] = style.name }
    }

    suspend fun setSkeuomorphicShelfEnabled(enabled: Boolean) {
        context.mochiDataStore.edit { prefs ->
            prefs[Keys.SKEUOMORPHIC_SHELF] = enabled
            val currentStyle = prefs[Keys.SHELF_STYLE]?.let { name ->
                ShelfStyle.entries.find { it.name == name }
            }
            if (!enabled && currentStyle == ShelfStyle.WOODEN_SHELF) {
                prefs[Keys.SHELF_STYLE] = ShelfStyle.GLASS_SHELF.name
            } else if (enabled && currentStyle == ShelfStyle.GLASS_SHELF) {
                prefs[Keys.SHELF_STYLE] = ShelfStyle.WOODEN_SHELF.name
            }
        }
    }

    suspend fun setSpineThicknessByPages(enabled: Boolean) {
        context.mochiDataStore.edit { it[Keys.SPINE_THICKNESS] = enabled }
    }

    suspend fun setMascotEnabled(enabled: Boolean) {
        context.mochiDataStore.edit { it[Keys.MASCOT_ENABLED] = enabled }
    }

    suspend fun setShelfBackground(uri: String?, dim: Float? = null, blur: Float? = null) {
        context.mochiDataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.SHELF_BG_URI) else prefs[Keys.SHELF_BG_URI] = uri
            if (dim != null) prefs[Keys.SHELF_BG_DIM] = dim.coerceIn(0f, 0.85f)
            if (blur != null) prefs[Keys.SHELF_BG_BLUR] = blur.coerceIn(0f, 24f)
        }
    }

    suspend fun setWarmLight(enabled: Boolean, intensity: Float? = null) {
        context.mochiDataStore.edit { prefs ->
            prefs[Keys.WARM_LIGHT_ENABLED] = enabled
            if (intensity != null) prefs[Keys.WARM_LIGHT_INTENSITY] = intensity.coerceIn(0.05f, 0.7f)
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.mochiDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setSelectedPastelIcon(option: PastelIconOption) {
        context.mochiDataStore.edit { it[Keys.SELECTED_ICON] = option.id }
    }

    suspend fun setMatchSystemBars(enabled: Boolean) {
        context.mochiDataStore.edit { it[Keys.MATCH_SYSTEM_BARS] = enabled }
    }

    suspend fun applyPreset(preset: ReaderPreset) {
        context.mochiDataStore.edit { prefs ->
            prefs[Keys.USE_DYNAMIC_COLOR] = false
            when (preset) {
                ReaderPreset.NOVEL -> {
                    prefs[Keys.AUTO_DARK_MODE] = AutoDarkMode.MANUAL.name
                    prefs[Keys.ACTIVE_THEME_ID] = "paper"
                    prefs[Keys.ACTIVE_LIGHT_THEME_ID] = "paper"
                    prefs[Keys.GLASS_INTENSITY] = GlassIntensity.SUBTLE.name
                    prefs[Keys.SOLID_CHROME] = false
                    prefs[Keys.SHELF_STYLE] = ShelfStyle.FLAT_GRID.name
                    prefs[Keys.WARM_LIGHT_ENABLED] = false
                }
                ReaderPreset.STUDY -> {
                    prefs[Keys.AUTO_DARK_MODE] = AutoDarkMode.FOLLOW_SYSTEM.name
                    prefs[Keys.ACTIVE_THEME_ID] = "nord_light"
                    prefs[Keys.ACTIVE_LIGHT_THEME_ID] = "nord_light"
                    prefs[Keys.ACTIVE_DARK_THEME_ID] = "nord_dark"
                    prefs[Keys.GLASS_INTENSITY] = GlassIntensity.OFF.name
                    prefs[Keys.SOLID_CHROME] = true
                    prefs[Keys.SHELF_STYLE] = ShelfStyle.LIST.name
                    prefs[Keys.WARM_LIGHT_ENABLED] = false
                }
                ReaderPreset.NIGHT -> {
                    prefs[Keys.AUTO_DARK_MODE] = AutoDarkMode.MANUAL.name
                    prefs[Keys.ACTIVE_THEME_ID] = BundledThemes.DEFAULT_DARK_ID
                    prefs[Keys.ACTIVE_DARK_THEME_ID] = BundledThemes.DEFAULT_DARK_ID
                    prefs[Keys.GLASS_INTENSITY] = GlassIntensity.SUBTLE.name
                    prefs[Keys.SOLID_CHROME] = false
                    prefs[Keys.WARM_LIGHT_ENABLED] = true
                    prefs[Keys.WARM_LIGHT_INTENSITY] = 0.28f
                }
                ReaderPreset.COZY -> {
                    prefs[Keys.AUTO_DARK_MODE] = AutoDarkMode.FOLLOW_SYSTEM.name
                    prefs[Keys.ACTIVE_THEME_ID] = BundledThemes.DEFAULT_LIGHT_ID
                    prefs[Keys.ACTIVE_LIGHT_THEME_ID] = BundledThemes.DEFAULT_LIGHT_ID
                    prefs[Keys.ACTIVE_DARK_THEME_ID] = BundledThemes.DEFAULT_DARK_ID
                    prefs[Keys.GLASS_INTENSITY] = GlassIntensity.RICH.name
                    prefs[Keys.SOLID_CHROME] = false
                    prefs[Keys.SHELF_STYLE] = ShelfStyle.GLASS_SHELF.name
                    prefs[Keys.MASCOT_ENABLED] = true
                    prefs[Keys.WARM_LIGHT_ENABLED] = false
                }
            }
        }
    }

    suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) {
        context.mochiDataStore.edit { prefs ->
            val current = ReaderSettings.fromJson(prefs[Keys.READER_SETTINGS_JSON]) ?: ReaderSettings()
            prefs[Keys.READER_SETTINGS_JSON] = ReaderSettings.toJson(transform(current))
        }
    }

    suspend fun resetDefaults() {
        context.mochiDataStore.edit { it.clear() }
    }
}
