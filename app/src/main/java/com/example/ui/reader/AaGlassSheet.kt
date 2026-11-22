package com.example.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.AutoScrollMode
import com.example.data.preferences.CurlGrabMode
import com.example.data.preferences.OrientationLock
import com.example.data.preferences.PageSpreadMode
import com.example.data.preferences.PageTurnMode
import com.example.data.preferences.PdfFitMode
import com.example.data.preferences.ReaderAction
import com.example.data.preferences.ReaderGesture
import com.example.data.preferences.ReaderSettings
import com.example.data.preferences.ReaderTextAlignment
import com.example.data.preferences.TapZonePreset
import com.example.data.reader.ReaderFontResolver
import com.example.theme.ContrastChecker
import com.example.theme.MochiThemePalette
import com.example.ui.designsystem.LiquidGlassSlider
import com.example.ui.designsystem.MochiConnectedSegments
import com.example.ui.theme.LocalMochiPalette
import kotlin.math.roundToInt

private enum class AaTab(val label: String) {
    THEME("Theme"),
    TEXT("Text"),
    PAGE("Page")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AaGlassSheet(
    settings: ReaderSettings,
    themes: List<MochiThemePalette>,
    activeTheme: MochiThemePalette,
    bookThemeOverrideId: String?,
    warmLightEnabled: Boolean,
    onDismiss: () -> Unit,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onSelectGlobalTheme: (MochiThemePalette) -> Unit,
    onSetBookThemeOverride: (String?) -> Unit,
    onToggleWarmLight: (Boolean) -> Unit,
    onImportFont: () -> Unit,
    isPdfDocument: Boolean = false
) {
    val palette = LocalMochiPalette.current
    var activeTab by remember { mutableStateOf(AaTab.TEXT) }
    var showMoreText by remember { mutableStateOf(false) }
    var showAdvancedControls by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface).copy(alpha = 0.96f),
        modifier = Modifier.testTag("aa_glass_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MochiConnectedSegments(
                items = AaTab.entries,
                selected = activeTab,
                labelProvider = { it.label },
                testTagProvider = { "aa_tab_${it.name.lowercase()}" },
                onSelect = { activeTab = it }
            )

            when (activeTab) {
                AaTab.THEME -> {
                    // Brightness
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.BrightnessMedium, contentDescription = "Brightness", tint = Color(palette.textSecondary))
                        val sliderVal = if (settings.brightness < 0f) 0.7f else settings.brightness
                        LiquidGlassSlider(
                            value = sliderVal,
                            onValueChange = { v -> onUpdateSettings { it.copy(brightness = v) } },
                            valueRange = 0.05f..1.0f,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onUpdateSettings { it.copy(brightness = -1f) } }) {
                            Text(if (settings.brightness < 0f) "Auto" else "Reset")
                        }
                    }

                    // Circular theme swatches (all 38+ bundled + custom themes)
                    var themeFilterDark by remember { mutableStateOf<Boolean?>(null) }
                    val sheetThemes = remember(themes, themeFilterDark) {
                        when (themeFilterDark) {
                            null -> themes
                            true -> themes.filter { it.isDark }
                            false -> themes.filter { !it.isDark }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reader Theme (${activeTheme.name})", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(null to "All", false to "Light", true to "Dark").forEach { (flag, label) ->
                                FilterChip(
                                    selected = themeFilterDark == flag,
                                    onClick = { themeFilterDark = flag },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sheetThemes, key = { it.id }) { item ->
                            val selected = item.id == activeTheme.id
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (bookThemeOverrideId != null) {
                                            onSetBookThemeOverride(item.id)
                                        } else {
                                            onSelectGlobalTheme(item)
                                        }
                                    }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(item.background))
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) Color(item.accent) else Color(item.textSecondary).copy(alpha = 0.3f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color(item.accent))
                                    } else {
                                        Text("Aa", style = MaterialTheme.typography.labelLarge, color = Color(item.text))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(item.name.take(11), style = MaterialTheme.typography.labelSmall, color = Color(palette.textSecondary))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Per-Book Theme Override", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                            Text(
                                text = if (bookThemeOverrideId != null) "Locked to ${activeTheme.name} for this book" else "Following global theme",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(palette.textSecondary)
                            )
                        }
                        Switch(
                            checked = bookThemeOverrideId != null,
                            onCheckedChange = { on -> onSetBookThemeOverride(if (on) activeTheme.id else null) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Warm Light Filter", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                        Switch(checked = warmLightEnabled, onCheckedChange = onToggleWarmLight)
                    }
                }

                AaTab.TEXT -> {
                    // A- / A+ size controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(palette.surfaceVariant),
                            onClick = {
                                onUpdateSettings { it.copy(fontSizeSp = (it.fontSizeSp - 1.5f).coerceAtLeast(12f)) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("font_size_decrease")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("A-", style = MaterialTheme.typography.titleMedium, color = Color(palette.text))
                            }
                        }
                        Text(
                            text = "${settings.fontSizeSp.roundToInt()} sp",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(palette.textSecondary)
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color(palette.surfaceVariant),
                            onClick = {
                                onUpdateSettings { it.copy(fontSizeSp = (it.fontSizeSp + 1.5f).coerceAtMost(32f)) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("font_size_increase")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("A+", style = MaterialTheme.typography.titleLarge, color = Color(palette.text))
                            }
                        }
                    }

                    // Font family previews + SAF TTF/OTF import
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                        TextButton(onClick = onImportFont) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import TTF/OTF")
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderFontResolver.builtInFonts.forEach { opt ->
                            val selected = settings.fontId == opt.id
                            FilterChip(
                                selected = selected,
                                onClick = { onUpdateSettings { it.copy(fontId = opt.id) } },
                                label = { Text(opt.label, fontFamily = opt.family) }
                            )
                        }
                        settings.importedFonts.forEach { custom ->
                            val selected = settings.fontId == custom.id
                            FilterChip(
                                selected = selected,
                                onClick = { onUpdateSettings { it.copy(fontId = custom.id) } },
                                label = { Text(custom.displayName) }
                            )
                        }
                    }

                    // Line height & alignment quick row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Line Spacing: ${String.format("%.2f×", settings.lineHeightMultiplier)}", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                            LiquidGlassSlider(
                                value = settings.lineHeightMultiplier,
                                onValueChange = { v -> onUpdateSettings { it.copy(lineHeightMultiplier = v) } },
                                valueRange = 1.15f..2.15f
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = settings.alignment == ReaderTextAlignment.LEFT,
                                onClick = { onUpdateSettings { it.copy(alignment = ReaderTextAlignment.LEFT) } },
                                label = { Icon(Icons.Rounded.FormatAlignLeft, contentDescription = "Left align", modifier = Modifier.size(18.dp)) }
                            )
                            FilterChip(
                                selected = settings.alignment == ReaderTextAlignment.JUSTIFY,
                                onClick = { onUpdateSettings { it.copy(alignment = ReaderTextAlignment.JUSTIFY) } },
                                label = { Icon(Icons.Rounded.FormatAlignJustify, contentDescription = "Justify", modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }

                    // More text options (progressive disclosure)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showMoreText = !showMoreText }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("More options", style = MaterialTheme.typography.titleSmall, color = Color(palette.link))
                        Icon(
                            imageVector = if (showMoreText) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = Color(palette.link)
                        )
                    }

                    AnimatedVisibility(visible = showMoreText) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Margins: ${settings.marginHorizontalDp.roundToInt()} dp", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                            LiquidGlassSlider(
                                value = settings.marginHorizontalDp,
                                onValueChange = { v -> onUpdateSettings { it.copy(marginHorizontalDp = v) } },
                                valueRange = 12f..64f
                            )
                            Text("Paragraph Spacing: ${settings.paragraphSpacingDp.roundToInt()} dp", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                            LiquidGlassSlider(
                                value = settings.paragraphSpacingDp,
                                onValueChange = { v -> onUpdateSettings { it.copy(paragraphSpacingDp = v) } },
                                valueRange = 0f..28f
                            )
                            Text("First-Line Indent: ${settings.firstLineIndentDp.roundToInt()} dp", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                            LiquidGlassSlider(
                                value = settings.firstLineIndentDp,
                                onValueChange = { v -> onUpdateSettings { it.copy(firstLineIndentDp = v) } },
                                valueRange = 0f..28f
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Knuth-Liang Hyphenation", style = MaterialTheme.typography.bodyMedium, color = Color(palette.text))
                                Switch(
                                    checked = settings.hyphenationEnabled,
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(hyphenationEnabled = v) } }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Reading Ruler / Focus Band", style = MaterialTheme.typography.bodyMedium, color = Color(palette.text))
                                Switch(
                                    checked = settings.readingRulerEnabled,
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(readingRulerEnabled = v) } }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Calm Mini Status Bar", style = MaterialTheme.typography.bodyMedium, color = Color(palette.text))
                                Switch(
                                    checked = settings.miniStatusBarEnabled,
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(miniStatusBarEnabled = v) } }
                                )
                            }
                        }
                    }
                }

                AaTab.PAGE -> {
                    Text("Page Turn", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PageTurnMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.pageTurnMode == mode,
                                onClick = { onUpdateSettings { it.copy(pageTurnMode = mode) } },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("page_turn_${mode.name.lowercase()}")
                            )
                        }
                    }

                    AnimatedVisibility(visible = settings.pageTurnMode == PageTurnMode.CURL) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(palette.surfaceVariant).copy(alpha = 0.45f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Page Curl Tuning", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
                            Text("Curl Tightness: ${(settings.curlStrength * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                            LiquidGlassSlider(
                                value = settings.curlStrength,
                                onValueChange = { v -> onUpdateSettings { it.copy(curlStrength = v) } },
                                valueRange = 0.25f..1.0f
                            )
                            Text("Shadow Depth: ${(settings.curlShadowStrength * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                            LiquidGlassSlider(
                                value = settings.curlShadowStrength,
                                onValueChange = { v -> onUpdateSettings { it.copy(curlShadowStrength = v) } },
                                valueRange = 0.1f..1.0f
                            )
                            Text("Turn Speed: ${String.format("%.1f×", settings.curlSpeed)}", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                            LiquidGlassSlider(
                                value = settings.curlSpeed,
                                onValueChange = { v -> onUpdateSettings { it.copy(curlSpeed = v) } },
                                valueRange = 0.6f..1.8f
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Show Mirrored Page Back", style = MaterialTheme.typography.bodyMedium, color = Color(palette.text))
                                Switch(
                                    checked = settings.curlShowPageBack,
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(curlShowPageBack = v) } }
                                )
                            }
                            Text("Curl Grab Zone", style = MaterialTheme.typography.labelMedium, color = Color(palette.textSecondary))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CurlGrabMode.entries.forEach { gm ->
                                    FilterChip(
                                        selected = settings.curlGrabMode == gm,
                                        onClick = { onUpdateSettings { it.copy(curlGrabMode = gm) } },
                                        label = { Text(gm.label) }
                                    )
                                }
                            }
                        }
                    }

                    Text("PDF Display & Reflow", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Text Reflow Mode (PDF)", style = MaterialTheme.typography.bodyMedium, color = Color(palette.text))
                            Text(
                                text = if (isPdfDocument) "Reflow embedded PDF text with custom font, margins & dictionary" else "Reflow embedded text when reading PDFs",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(palette.textSecondary)
                            )
                        }
                        Switch(
                            checked = settings.pdfReflowEnabled,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(pdfReflowEnabled = v) } },
                            modifier = Modifier.testTag("pdf_reflow_switch")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PdfFitMode.entries.forEach { fm ->
                            FilterChip(
                                selected = settings.pdfFitMode == fm,
                                onClick = { onUpdateSettings { it.copy(pdfFitMode = fm) } },
                                label = { Text(fm.label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto Margin Crop (PDF)", style = MaterialTheme.typography.bodyMedium, color = Color(palette.text))
                        Switch(
                            checked = settings.pdfAutoMarginCrop,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(pdfAutoMarginCrop = v) } }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Smart Dark Invert (PDF)", style = MaterialTheme.typography.bodyMedium, color = Color(palette.text))
                        Switch(
                            checked = settings.pdfSmartDarkInvert,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(pdfSmartDarkInvert = v) } }
                        )
                    }

                    Text("Layout Spread", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PageSpreadMode.entries.forEach { sp ->
                            FilterChip(
                                selected = settings.pageSpreadMode == sp,
                                onClick = { onUpdateSettings { it.copy(pageSpreadMode = sp) } },
                                label = { Text(sp.label) }
                            )
                        }
                    }

                    Text("Orientation Lock", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrientationLock.entries.forEach { ol ->
                            FilterChip(
                                selected = settings.orientationLock == ol,
                                onClick = { onUpdateSettings { it.copy(orientationLock = ol) } },
                                label = { Text(ol.label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Keep Screen Awake", style = MaterialTheme.typography.bodyMedium, color = Color(palette.text))
                        Switch(
                            checked = settings.keepScreenAwake,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(keepScreenAwake = v) } }
                        )
                    }

                    Text("Highlights & Annotations", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quick-Highlight Mode", style = MaterialTheme.typography.bodyMedium, color = Color(palette.text))
                            Text("Long-press immediately saves highlight with active color", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                        }
                        Switch(
                            checked = settings.quickHighlightEnabled,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(quickHighlightEnabled = v) } },
                            modifier = Modifier.testTag("quick_highlight_switch")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Color-Blind-Safe Palette", style = MaterialTheme.typography.bodyMedium, color = Color(palette.text))
                            Text("Use high-distinction Okabe-Ito highlight colors", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                        }
                        Switch(
                            checked = settings.colorBlindPaletteEnabled,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(colorBlindPaletteEnabled = v) } },
                            modifier = Modifier.testTag("color_blind_palette_switch")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Copy with Citation", style = MaterialTheme.typography.bodyMedium, color = Color(palette.text))
                            Text("Include book title, author & chapter when copying", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                        }
                        Switch(
                            checked = settings.copyWithCitationEnabled,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(copyWithCitationEnabled = v) } }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tap Word to Define", style = MaterialTheme.typography.bodyMedium, color = Color(palette.text))
                            Text("Single-tap words in center text to open offline dictionary", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                        }
                        Switch(
                            checked = settings.tapToDefineEnabled,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(tapToDefineEnabled = v) } },
                            modifier = Modifier.testTag("tap_word_define_switch")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Highlight Unknown Words", style = MaterialTheme.typography.bodyMedium, color = Color(palette.text))
                            Text("Underline unmastered vocabulary words while reading", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                        }
                        Switch(
                            checked = settings.highlightUnknownWordsEnabled,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(highlightUnknownWordsEnabled = v) } },
                            modifier = Modifier.testTag("highlight_unknown_words_switch")
                        )
                    }

                    // Advanced Controls: Tap Zones, Action Map, 5 Auto-Scroll modes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showAdvancedControls = !showAdvancedControls }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Advanced Controls (Tap Zones & Auto-Scroll)", style = MaterialTheme.typography.titleSmall, color = Color(palette.link))
                        Icon(
                            imageVector = if (showAdvancedControls) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = Color(palette.link)
                        )
                    }

                    AnimatedVisibility(visible = showAdvancedControls) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Tap Zone Preset", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TapZonePreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = settings.tapZonePreset == preset,
                                        onClick = { onUpdateSettings { it.withTapPreset(preset) } },
                                        label = { Text(preset.label) }
                                    )
                                }
                            }

                            Text("Auto-Scroll Mode", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AutoScrollMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = settings.autoScrollMode == mode,
                                        onClick = { onUpdateSettings { it.copy(autoScrollMode = mode) } },
                                        label = { Text(mode.label) }
                                    )
                                }
                            }
                            if (settings.autoScrollMode != AutoScrollMode.OFF) {
                                Text("Speed: ${String.format("%.1f×", settings.autoScrollSpeed)}", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                                LiquidGlassSlider(
                                    value = settings.autoScrollSpeed,
                                    onValueChange = { v -> onUpdateSettings { it.copy(autoScrollSpeed = v) } },
                                    valueRange = 0.3f..3.0f
                                )
                            }

                            Text("Gesture Action Map", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
                            ReaderGesture.entries.forEach { gesture ->
                                val currentAction = settings.actionFor(gesture)
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(palette.surfaceVariant).copy(alpha = 0.5f))
                                            .clickable { expanded = true }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(gesture.label, style = MaterialTheme.typography.bodySmall, color = Color(palette.text))
                                        Text(currentAction.label, style = MaterialTheme.typography.labelMedium, color = Color(palette.link))
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        ReaderAction.entries.forEach { action ->
                                            DropdownMenuItem(
                                                text = { Text(action.label) },
                                                onClick = {
                                                    onUpdateSettings { s ->
                                                        val updated = s.actionMap.toMutableMap()
                                                        updated[gesture.name] = action.name
                                                        s.copy(actionMap = updated)
                                                    }
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // One-tap layout reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = { onUpdateSettings { ReaderSettings(importedFonts = it.importedFonts) } }) {
                    Text("Reset Layout Defaults")
                }
            }
        }
    }
}
