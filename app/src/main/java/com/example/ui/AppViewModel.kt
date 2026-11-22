package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MochiApplication
import com.example.data.preferences.AutoDarkMode
import com.example.data.preferences.PastelIconOption
import com.example.data.preferences.ReaderPreset
import com.example.data.preferences.UserPreferencesState
import com.example.theme.BundledThemes
import com.example.theme.GlassIntensity
import com.example.theme.MochiThemePalette
import com.example.theme.ShelfStyle
import com.example.theme.ThemePackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class AppUiState(
    val prefs: UserPreferencesState = UserPreferencesState(),
    val themes: List<MochiThemePalette> = BundledThemes.all,
    val statusMessage: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MochiApplication).container
    private val preferences = container.preferences
    private val repository = container.libraryRepository

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val uiState: StateFlow<AppUiState> = combine(
        preferences.state,
        repository.themes,
        _statusMessage
    ) { prefs, themes, msg ->
        AppUiState(prefs = prefs, themes = themes, statusMessage = msg)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppUiState()
    )

    fun resolveActivePalette(state: AppUiState, systemDark: Boolean): MochiThemePalette {
        val prefs = state.prefs
        val targetId = when (prefs.autoDarkMode) {
            AutoDarkMode.MANUAL -> prefs.activeThemeId
            AutoDarkMode.FOLLOW_SYSTEM -> if (systemDark) prefs.activeDarkThemeId else prefs.activeLightThemeId
            AutoDarkMode.EVENING_SCHEDULE -> {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                if (hour >= 19 || hour < 7) prefs.activeDarkThemeId else prefs.activeLightThemeId
            }
        }
        return state.themes.find { it.id == targetId } ?: BundledThemes.byId(targetId)
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    fun showMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun selectTheme(palette: MochiThemePalette) = viewModelScope.launch {
        preferences.selectTheme(palette.id, palette.isDark)
    }

    fun setAutoDarkMode(mode: AutoDarkMode) = viewModelScope.launch {
        preferences.setAutoDarkMode(mode)
    }

    fun setMaterialYouDynamic(enabled: Boolean) = viewModelScope.launch {
        preferences.setMaterialYouDynamic(enabled)
    }

    fun setGlassIntensity(intensity: GlassIntensity) = viewModelScope.launch {
        preferences.setGlassIntensity(intensity)
    }

    fun setSolidChrome(enabled: Boolean) = viewModelScope.launch {
        preferences.setSolidChrome(enabled)
    }

    fun setReduceMotion(enabled: Boolean) = viewModelScope.launch {
        preferences.setReduceMotion(enabled)
    }

    fun setShelfStyle(style: ShelfStyle) = viewModelScope.launch {
        preferences.setShelfStyle(style)
    }

    fun setSkeuomorphicShelfEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setSkeuomorphicShelfEnabled(enabled)
    }

    fun setSpineThicknessByPages(enabled: Boolean) = viewModelScope.launch {
        preferences.setSpineThicknessByPages(enabled)
    }

    fun setMascotEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setMascotEnabled(enabled)
    }

    fun setShelfBackground(uri: String?, dim: Float? = null, blur: Float? = null) = viewModelScope.launch {
        preferences.setShelfBackground(uri, dim, blur)
    }

    fun setWarmLight(enabled: Boolean, intensity: Float? = null) = viewModelScope.launch {
        preferences.setWarmLight(enabled, intensity)
    }

    fun setOnboardingCompleted(completed: Boolean) = viewModelScope.launch {
        preferences.setOnboardingCompleted(completed)
    }

    fun setSelectedPastelIcon(option: PastelIconOption) = viewModelScope.launch {
        preferences.setSelectedPastelIcon(option)
    }

    fun setMatchSystemBars(enabled: Boolean) = viewModelScope.launch {
        preferences.setMatchSystemBars(enabled)
    }

    fun applyPreset(preset: ReaderPreset) = viewModelScope.launch {
        preferences.applyPreset(preset)
        _statusMessage.value = "Applied ${preset.label} preset"
    }

    fun resetSettings() = viewModelScope.launch {
        preferences.resetDefaults()
        preferences.setOnboardingCompleted(true)
        _statusMessage.value = "Settings reset to defaults"
    }

    fun saveCustomTheme(palette: MochiThemePalette, selectAfterSave: Boolean = true) = viewModelScope.launch {
        repository.saveCustomTheme(palette)
        if (selectAfterSave) {
            preferences.selectTheme(palette.id, palette.isDark)
        }
        _statusMessage.value = "Saved \"${palette.name}\""
    }

    fun duplicateTheme(palette: MochiThemePalette, onCreated: (MochiThemePalette) -> Unit) = viewModelScope.launch {
        val copy = repository.duplicateTheme(palette)
        onCreated(copy)
    }

    fun deleteCustomTheme(palette: MochiThemePalette) = viewModelScope.launch {
        if (palette.builtIn) return@launch
        repository.deleteCustomTheme(palette.id)
        if (uiState.value.prefs.activeThemeId == palette.id) {
            preferences.selectTheme(BundledThemes.DEFAULT_LIGHT_ID, isDark = false)
        }
        _statusMessage.value = "Deleted \"${palette.name}\""
    }

    fun exportThemePack(context: Context, destUri: Uri, palette: MochiThemePalette) = viewModelScope.launch {
        val result = ThemePackManager.exportThemePack(context, destUri, palette)
        _statusMessage.value = result.fold(
            onSuccess = { "Exported ${palette.name}.mochitheme" },
            onFailure = { "Export failed: ${it.localizedMessage}" }
        )
    }

    fun importThemePack(context: Context, sourceUri: Uri) = viewModelScope.launch {
        val result = ThemePackManager.importThemePack(context, sourceUri)
        result.fold(
            onSuccess = { imported ->
                repository.saveCustomTheme(imported.palette)
                preferences.selectTheme(imported.palette.id, imported.palette.isDark)
                _statusMessage.value = "Imported theme \"${imported.palette.name}\""
            },
            onFailure = {
                _statusMessage.value = "Invalid theme pack: ${it.localizedMessage}"
            }
        )
    }

    fun updateReaderSettings(transform: (com.example.data.preferences.ReaderSettings) -> com.example.data.preferences.ReaderSettings) =
        viewModelScope.launch {
            preferences.updateReaderSettings(transform)
        }

    fun importCustomFont(uri: Uri) = viewModelScope.launch {
        val info = container.readerRepository.importFontFromSaf(uri)
        if (info != null) {
            preferences.updateReaderSettings { s ->
                s.copy(
                    fontId = info.id,
                    importedFonts = s.importedFonts + info
                )
            }
            _statusMessage.value = "Imported font \"${info.displayName}\""
        } else {
            _statusMessage.value = "Could not import font file"
        }
    }

    fun handleIncomingFile(uri: Uri, onOpened: ((Long) -> Unit)? = null) = viewModelScope.launch {
        val book = repository.importSingleFile(uri)
        if (book != null) {
            _statusMessage.value = "Opened & added \"${book.title}\" to Library"
            onOpened?.invoke(book.id)
        } else {
            _statusMessage.value = "Unsupported file format (supported: EPUB, PDF, CBZ, MD, TXT)"
        }
    }
}
