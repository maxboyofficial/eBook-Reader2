package com.example.ui.themes

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.example.data.preferences.UserPreferencesState
import com.example.theme.ContrastChecker
import com.example.theme.GlassIntensity
import com.example.theme.MochiThemePalette
import com.example.theme.ThemeCategory
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.designsystem.LocalMochiWidthClass
import com.example.ui.designsystem.MochiWidthClass
import com.example.ui.theme.LocalMochiPalette

@Composable
fun ThemeGalleryScreen(
    themes: List<MochiThemePalette>,
    activeTheme: MochiThemePalette,
    prefs: UserPreferencesState,
    onBack: () -> Unit,
    onSelectTheme: (MochiThemePalette) -> Unit,
    onEditTheme: (MochiThemePalette) -> Unit,
    onDuplicateTheme: (MochiThemePalette) -> Unit,
    onDeleteTheme: (MochiThemePalette) -> Unit,
    onExportThemePack: (MochiThemePalette) -> Unit,
    onImportThemePack: () -> Unit,
    onSetGlassIntensity: (GlassIntensity) -> Unit,
    onSetSolidChrome: (Boolean) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit
) {
    val palette = LocalMochiPalette.current
    val widthClass = LocalMochiWidthClass.current
    val useTwoColumns = widthClass != MochiWidthClass.COMPACT
    var selectedCategory by remember { mutableStateOf<ThemeCategory?>(null) }

    val filteredThemes = remember(themes, selectedCategory) {
        if (selectedCategory == null) themes
        else themes.filter { it.category == selectedCategory }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(palette.background))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1120.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp)
        ) {
            item(key = "top_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("themes_back_button")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color(palette.text))
                        }
                        Text("Themes & Glass", style = MaterialTheme.typography.headlineMedium, color = Color(palette.text))
                    }
                    OutlinedButton(
                        onClick = onImportThemePack,
                        modifier = Modifier.testTag("import_theme_pack_button")
                    ) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import .mochitheme")
                    }
                }
            }

            // Glass & Dynamic Color quick controls
            item(key = "glass_controls") {
                LiquidGlassSurface(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Liquid Glass Chrome", style = MaterialTheme.typography.titleMedium, color = Color(palette.text))
                            Text(
                                text = "Active: ${activeTheme.name}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(palette.accent)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassIntensity.entries.forEach { gi ->
                                FilterChip(
                                    selected = prefs.glassIntensity == gi && !prefs.solidChrome,
                                    onClick = {
                                        onSetSolidChrome(gi == GlassIntensity.OFF)
                                        onSetGlassIntensity(gi)
                                    },
                                    label = { Text(gi.label) },
                                    modifier = Modifier.testTag("glass_intensity_${gi.name.lowercase()}")
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Solid Chrome", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                                Text("Disable blur & translucency across bars", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                            }
                            Switch(
                                checked = prefs.solidChrome,
                                onCheckedChange = onSetSolidChrome,
                                modifier = Modifier.testTag("solid_chrome_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Material You Dynamic Color", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                                Text("Blend wallpaper palette on Android 12+", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                            }
                            Switch(
                                checked = prefs.useMaterialYouDynamic,
                                onCheckedChange = onSetDynamicColor
                            )
                        }
                    }
                }
            }

            // Category filter chips
            item(key = "category_chips") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All (${themes.size})") }
                    )
                    ThemeCategory.entries.forEach { cat ->
                        val count = themes.count { it.category == cat }
                        if (count > 0) {
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                                label = { Text("${cat.label} ($count)") }
                            )
                        }
                    }
                }
            }

            if (useTwoColumns) {
                val rows = filteredThemes.chunked(2)
                items(rows, key = { pair -> pair.joinToString("_") { it.id } }) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { item ->
                            Box(modifier = Modifier.weight(1f)) {
                                ThemeGalleryCard(
                                    item = item,
                                    isSelected = item.id == activeTheme.id,
                                    onSelect = { onSelectTheme(item) },
                                    onDuplicate = { onDuplicateTheme(item) },
                                    onEdit = { onEditTheme(item) },
                                    onDelete = { onDeleteTheme(item) },
                                    onExport = { onExportThemePack(item) }
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(filteredThemes, key = { it.id }) { item ->
                    ThemeGalleryCard(
                        item = item,
                        isSelected = item.id == activeTheme.id,
                        onSelect = { onSelectTheme(item) },
                        onDuplicate = { onDuplicateTheme(item) },
                        onEdit = { onEditTheme(item) },
                        onDelete = { onDeleteTheme(item) },
                        onExport = { onExportThemePack(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeGalleryCard(
    item: MochiThemePalette,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val report = remember(item) { ContrastChecker.evaluate(item) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(item.background),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) Color(item.accent) else Color(item.textSecondary).copy(alpha = 0.22f),
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onSelect)
            .testTag("theme_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(item.surface))
                        .border(2.dp, Color(item.accent), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Active",
                            tint = Color(item.accent)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(item.accent))
                        )
                    }
                }
                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(item.text)
                    )
                    Text(
                        text = buildString {
                            append(if (item.isDark) "Dark" else "Light")
                            append(" · ")
                            append(item.category.label)
                            append(" · ")
                            append(String.format("%.1f:1", report.bodyOnBackground))
                            append(if (report.passesAll) " AA" else " Low")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(item.textSecondary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.highlightColors.take(6).forEach { hl ->
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(hl))
                            )
                        }
                    }
                }
            }

            Row {
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier.testTag("duplicate_theme_${item.id}")
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate & Edit", tint = Color(item.textSecondary))
                }
                if (!item.builtIn) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit theme", tint = Color(item.textSecondary))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete theme", tint = Color(item.textSecondary))
                    }
                } else {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Customize copy", tint = Color(item.textSecondary))
                    }
                }
                IconButton(onClick = onExport) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = "Export .mochitheme", tint = Color(item.textSecondary))
                }
            }
        }
    }
}
