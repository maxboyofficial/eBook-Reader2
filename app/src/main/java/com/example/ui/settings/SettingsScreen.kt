package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AutoDarkMode
import com.example.data.preferences.PastelIconOption
import com.example.data.preferences.ReaderPreset
import com.example.data.preferences.UserPreferencesState
import com.example.theme.ContrastChecker
import com.example.theme.GlassIntensity
import com.example.theme.MochiThemePalette
import com.example.theme.ShelfStyle
import com.example.ui.designsystem.LiquidGlassSlider
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.designsystem.LocalMochiWidthClass
import com.example.ui.designsystem.MochiWidthClass
import com.example.ui.theme.LocalMochiPalette

@Composable
fun SettingsScreen(
    prefs: UserPreferencesState,
    activePalette: MochiThemePalette,
    onOpenThemes: () -> Unit,
    onOpenThemeStudio: () -> Unit,
    onOpenDictionaries: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenPrivacy: () -> Unit,
    onOpenCredits: () -> Unit,
    onApplyPreset: (ReaderPreset) -> Unit,
    onSetAutoDarkMode: (AutoDarkMode) -> Unit,
    onSetShelfStyle: (ShelfStyle) -> Unit,
    onSetSkeuomorphicShelf: (Boolean) -> Unit = {},
    onSetGlassIntensity: (GlassIntensity) -> Unit,
    onSetSolidChrome: (Boolean) -> Unit,
    onSetReduceMotion: (Boolean) -> Unit,
    onSetSpineThickness: (Boolean) -> Unit,
    onSetMascotEnabled: (Boolean) -> Unit,
    onPickShelfBackground: () -> Unit,
    onClearShelfBackground: () -> Unit,
    onUpdateShelfBgDimBlur: (Float, Float) -> Unit,
    onSetWarmLight: (Boolean, Float?) -> Unit,
    onSetMatchSystemBars: (Boolean) -> Unit,
    onSelectPastelIcon: (PastelIconOption) -> Unit,
    onReplayOnboarding: () -> Unit,
    onResetDefaults: () -> Unit
) {
    val palette = LocalMochiPalette.current
    val widthClass = LocalMochiWidthClass.current
    val isExpanded = widthClass == MochiWidthClass.EXPANDED
    var searchQuery by remember { mutableStateOf("") }
    var showAdvancedDisplay by remember { mutableStateOf(false) }
    var showAdvancedCozy by remember { mutableStateOf(false) }

    val q = searchQuery.trim().lowercase()
    fun matches(vararg keywords: String): Boolean =
        q.isEmpty() || keywords.any { it.lowercase().contains(q) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(palette.background))
            .statusBarsPadding()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = if (widthClass == MochiWidthClass.COMPACT) 120.dp else 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1120.dp)
                .align(Alignment.TopCenter)
        ) {
            item(key = "settings_title") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.displayLarge,
                        color = Color(palette.text)
                    )
                    LiquidGlassSurface(
                        shape = CircleShape,
                        elevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(palette.textSecondary), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text("Search settings…", style = MaterialTheme.typography.bodyMedium, color = Color(palette.textSecondary))
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(palette.text)),
                                    cursorBrush = SolidColor(Color(palette.accent)),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Presets row (Novel, Study, Night, Cozy)
            if (matches("preset", "novel", "study", "night", "cozy")) {
                item(key = "presets") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Quick Presets", style = MaterialTheme.typography.titleMedium, color = Color(palette.text))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaderPreset.entries.forEach { preset ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(palette.surface),
                                    onClick = { onApplyPreset(preset) },
                                    modifier = Modifier
                                        .width(148.dp)
                                        .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                                        .testTag("preset_${preset.name.lowercase()}")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(preset.label, style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(preset.description, style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary), maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val showAppearance = matches("theme", "appearance", "glass", "solid", "dark", "warm", "motion")
            val showShelfCozy = matches("shelf", "wood", "grid", "list", "cute", "cozy", "mascot", "icon", "background")
            val showDictionaries = matches("dictionary", "dictionaries", "stardict", "dictd", "wiktionary", "vocabulary", "language", "study", "tts")
            val showAbout = matches("privacy", "credits", "license", "reset", "onboarding", "about")

            @Composable
            fun AppearanceSectionCard() {
                SettingsSectionCard(title = "Appearance & Liquid Glass") {
                    SettingsNavRow(
                        icon = Icons.Rounded.Palette,
                        title = "Themes",
                        subtitle = "${activePalette.name} (${if (activePalette.isDark) "Dark" else "Light"})",
                        onClick = onOpenThemes,
                        testTag = "settings_open_themes"
                    )
                    SettingsNavRow(
                        icon = Icons.Rounded.ColorLens,
                        title = "Theme Studio",
                        subtitle = "Customize colors, glass & WCAG AA contrast",
                        onClick = onOpenThemeStudio,
                        testTag = "settings_open_theme_studio"
                    )
                    Text("Auto Light / Dark", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AutoDarkMode.entries.forEach { mode ->
                            FilterChip(
                                selected = prefs.autoDarkMode == mode,
                                onClick = { onSetAutoDarkMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    SettingsSwitchRow(
                        title = "Solid Chrome",
                        subtitle = "Disable blur and transparency",
                        checked = prefs.solidChrome,
                        onCheckedChange = onSetSolidChrome,
                        testTag = "settings_solid_chrome"
                    )
                    SettingsSwitchRow(
                        title = "Reduce Motion",
                        subtitle = "Replace springs with instant fades",
                        checked = prefs.reduceMotion,
                        onCheckedChange = onSetReduceMotion,
                        testTag = "settings_reduce_motion"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showAdvancedDisplay = !showAdvancedDisplay }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, tint = Color(palette.link), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Advanced Display", style = MaterialTheme.typography.titleSmall, color = Color(palette.link))
                        }
                        Icon(
                            imageVector = if (showAdvancedDisplay) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = Color(palette.link)
                        )
                    }
                    AnimatedVisibility(visible = showAdvancedDisplay) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Glass Intensity", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassIntensity.entries.forEach { gi ->
                                    FilterChip(
                                        selected = prefs.glassIntensity == gi,
                                        onClick = { onSetGlassIntensity(gi) },
                                        label = { Text(gi.label) }
                                    )
                                }
                            }
                            SettingsSwitchRow(
                                title = "Warm Light Filter",
                                subtitle = "Software amber eye-comfort overlay",
                                checked = prefs.warmLightEnabled,
                                onCheckedChange = { onSetWarmLight(it, null) }
                            )
                            if (prefs.warmLightEnabled) {
                                LiquidGlassSlider(
                                    value = prefs.warmLightIntensity,
                                    onValueChange = { onSetWarmLight(true, it) },
                                    valueRange = 0.05f..0.70f
                                )
                            }
                            SettingsSwitchRow(
                                title = "Match Status & Nav Bars",
                                subtitle = "Sync system bar icon luminance to theme",
                                checked = prefs.matchSystemBars,
                                onCheckedChange = onSetMatchSystemBars
                            )
                        }
                    }
                }
            }

            @Composable
            fun ShelfCozySectionCard() {
                SettingsSectionCard(title = "Bookshelf & Cute & Cozy Pack") {
                    Text("Default Shelf Style", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
                    val availableStyles = remember(prefs.skeuomorphicShelfEnabled) {
                        if (prefs.skeuomorphicShelfEnabled) {
                            ShelfStyle.entries
                        } else {
                            listOf(ShelfStyle.FLAT_GRID, ShelfStyle.LIST, ShelfStyle.GLASS_SHELF)
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableStyles.forEach { style ->
                            FilterChip(
                                selected = prefs.defaultShelfStyle == style,
                                onClick = { onSetShelfStyle(style) },
                                label = { Text(style.label) },
                                modifier = Modifier.testTag("settings_shelf_${style.name.lowercase()}")
                            )
                        }
                    }
                    SettingsSwitchRow(
                        title = "Skeuomorphic Wood Bookshelf",
                        subtitle = "Off uses translucent Liquid Glass ledges; on enables textured wood planks",
                        checked = prefs.skeuomorphicShelfEnabled,
                        onCheckedChange = onSetSkeuomorphicShelf,
                        testTag = "settings_skeuomorphic_shelf"
                    )
                    SettingsSwitchRow(
                        title = "Mochi Mascot",
                        subtitle = "Show Mochi in empty states & onboarding",
                        checked = prefs.mascotEnabled,
                        onCheckedChange = onSetMascotEnabled
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showAdvancedCozy = !showAdvancedCozy }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(palette.link), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Advanced Shelf & Decor", style = MaterialTheme.typography.titleSmall, color = Color(palette.link))
                        }
                        Icon(
                            imageVector = if (showAdvancedCozy) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = Color(palette.link)
                        )
                    }
                    AnimatedVisibility(visible = showAdvancedCozy) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsSwitchRow(
                                title = "Spine Thickness by Page Count",
                                subtitle = "Visible on Wooden Shelf view",
                                checked = prefs.spineThicknessByPages,
                                onCheckedChange = onSetSpineThickness
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = onPickShelfBackground, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Shelf Wallpaper")
                                }
                                if (prefs.shelfBackgroundUri != null) {
                                    TextButton(onClick = onClearShelfBackground) {
                                        Text("Clear")
                                    }
                                }
                            }
                            if (prefs.shelfBackgroundUri != null) {
                                Text("Wallpaper Dim: ${(prefs.shelfBackgroundDim * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                                LiquidGlassSlider(
                                    value = prefs.shelfBackgroundDim,
                                    onValueChange = { onUpdateShelfBgDimBlur(it, prefs.shelfBackgroundBlur) },
                                    valueRange = 0f..0.85f
                                )
                                Text("Wallpaper Blur: ${prefs.shelfBackgroundBlur.toInt()} dp", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                                LiquidGlassSlider(
                                    value = prefs.shelfBackgroundBlur,
                                    onValueChange = { onUpdateShelfBgDimBlur(prefs.shelfBackgroundDim, it) },
                                    valueRange = 0f..24f
                                )
                            }

                            Text("Pastel Badge & Icon Style", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PastelIconOption.entries.forEach { opt ->
                                    val selected = prefs.selectedPastelIcon == opt
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) Color(palette.accent) else Color(palette.textSecondary).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .clickable { onSelectPastelIcon(opt) }
                                            .padding(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(opt.bgHex))
                                                .border(1.dp, Color(opt.ribbonHex).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Bookmark,
                                                contentDescription = opt.label,
                                                tint = Color(opt.ribbonHex)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(opt.label, style = MaterialTheme.typography.labelSmall, color = Color(palette.text))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            @Composable
            fun DictionarySectionCard() {
                SettingsSectionCard(title = "Reading Insights & Dictionaries") {
                    SettingsNavRow(
                        icon = Icons.Rounded.BarChart,
                        title = "Reading Statistics & Streaks",
                        subtitle = "Local daily minutes, 7-day pace & pages turned",
                        onClick = onOpenStats,
                        testTag = "settings_open_stats"
                    )
                    SettingsNavRow(
                        icon = Icons.Rounded.MenuBook,
                        title = "Offline Dictionaries",
                        subtitle = "Import StarDict, dictd, Wiktionary & set language defaults",
                        onClick = onOpenDictionaries,
                        testTag = "settings_open_dictionaries"
                    )
                }
            }

            @Composable
            fun AboutSectionCard() {
                SettingsSectionCard(title = "Privacy & About Mochi") {
                    SettingsNavRow(
                        icon = Icons.Rounded.Lock,
                        title = "Privacy & Access",
                        subtitle = "100% offline · View exact permissions",
                        onClick = onOpenPrivacy,
                        testTag = "settings_open_privacy"
                    )
                    SettingsNavRow(
                        icon = Icons.Rounded.Info,
                        title = "Credits & Licenses",
                        subtitle = "Bundled fonts, palettes & open-source libraries",
                        onClick = onOpenCredits,
                        testTag = "settings_open_credits"
                    )
                    SettingsNavRow(
                        icon = Icons.Rounded.NightlightRound,
                        title = "Replay Onboarding",
                        subtitle = "View the 3-step welcome guide",
                        onClick = onReplayOnboarding
                    )
                    SettingsNavRow(
                        icon = Icons.Rounded.Restore,
                        title = "Reset Settings to Defaults",
                        subtitle = "Keep your books & custom themes intact",
                        onClick = onResetDefaults
                    )
                }
            }

            if (isExpanded) {
                item(key = "tablet_two_col_settings") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (showAppearance) AppearanceSectionCard()
                            if (showDictionaries) DictionarySectionCard()
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (showShelfCozy) ShelfCozySectionCard()
                            if (showAbout) AboutSectionCard()
                        }
                    }
                }
            } else {
                if (showAppearance) {
                    item(key = "appearance_group") { AppearanceSectionCard() }
                }
                if (showDictionaries) {
                    item(key = "dictionary_group") { DictionarySectionCard() }
                }
                if (showShelfCozy) {
                    item(key = "shelf_cozy_group") { ShelfCozySectionCard() }
                }
                if (showAbout) {
                    item(key = "about_group") { AboutSectionCard() }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val palette = LocalMochiPalette.current
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(palette.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(palette.text)
            )
            content()
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    val palette = LocalMochiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(palette.accentContainer)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ContrastChecker.bestOnColor(Color(palette.accentContainer)), modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color(palette.textSecondary))
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    val palette = LocalMochiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}
