package com.example.ui.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.example.ui.designsystem.LiquidGlassSlider
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
import com.example.theme.ContrastChecker
import com.example.theme.MochiThemePalette
import com.example.theme.ThemeCategory
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiterataFontFamily
import java.util.UUID

@Composable
fun ThemeEditorScreen(
    initialPalette: MochiThemePalette,
    onBack: () -> Unit,
    onSave: (MochiThemePalette) -> Unit
) {
    // If editing a built-in theme, automatically edit as a new custom copy
    var draft by remember(initialPalette.id) {
        mutableStateOf(
            if (initialPalette.builtIn) {
                initialPalette.copy(
                    id = "custom_${UUID.randomUUID().toString().take(8)}",
                    name = "${initialPalette.name} Custom",
                    builtIn = false,
                    category = ThemeCategory.CUSTOM
                )
            } else {
                initialPalette
            }
        )
    }

    val report = remember(draft) { ContrastChecker.evaluate(draft) }
    var editingRole by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var onColorPicked by remember { mutableStateOf<(Long) -> Unit>({}) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(draft.background))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color(draft.text))
                    }
                    Text("Theme Studio", style = MaterialTheme.typography.headlineMedium, color = Color(draft.text))
                }
                Button(
                    onClick = { onSave(draft) },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(draft.accent),
                        contentColor = ContrastChecker.bestOnColor(Color(draft.accent))
                    ),
                    modifier = Modifier.testTag("save_custom_theme_button")
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save")
                }
            }

            // Live Page & Glass Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(draft.background))
                    .border(1.dp, Color(draft.textSecondary).copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Chapter I · Live Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(draft.textSecondary)
                    )
                    Text(
                        text = "Morning light fell softly across the open pages, warming the paper with quiet clarity.",
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = LiterataFontFamily),
                        color = Color(draft.text)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Highlighted passage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(draft.text),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(draft.highlightColors.first()))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Text(
                            text = "Footnote link",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(draft.link)
                        )
                    }
                    // Simulated floating glass pill preview
                    val glassFill = ContrastChecker.adaptiveGlassFill(
                        glassTint = Color(draft.glassTint),
                        glassTintAlpha = draft.glassTintAlpha,
                        underlyingColor = Color(draft.background),
                        textColor = Color(draft.text),
                        solidChrome = draft.prefersSolidChrome
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(glassFill)
                            .border(1.dp, Color(draft.glassBorder), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Page 42 of 310", style = MaterialTheme.typography.labelLarge, color = Color(draft.text))
                            Text("Aa", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(draft.accent))
                        }
                    }
                }
            }

            // Automated WCAG Contrast Checker Banner
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (report.passesAll) Color(draft.surfaceVariant) else MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contrast_report_banner")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (report.passesAll) Icons.Rounded.Check else Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = if (report.passesAll) Color(draft.link) else MaterialTheme.colorScheme.error
                    )
                    Column {
                        Text(
                            text = if (report.passesAll) "WCAG AA Contrast Passed" else "Contrast Warning (< 4.5:1 AA)",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(draft.text)
                        )
                        Text(
                            text = "Body ${String.format("%.1f:1", report.bodyOnBackground)} · Secondary ${String.format("%.1f:1", report.secondaryOnBackground)} · Glass ${String.format("%.1f:1", report.bodyOnGlass)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(draft.textSecondary)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text("Theme name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark theme", style = MaterialTheme.typography.titleSmall, color = Color(draft.text))
                Switch(checked = draft.isDark, onCheckedChange = { draft = draft.copy(isDark = it) })
            }

            Text("Semantic Roles", style = MaterialTheme.typography.titleMedium, color = Color(draft.text))
            val roles = listOf(
                Triple("Background", draft.background) { c: Long -> draft = draft.copy(background = c, paperTint = c) },
                Triple("Surface", draft.surface) { c: Long -> draft = draft.copy(surface = c) },
                Triple("Surface Variant", draft.surfaceVariant) { c: Long -> draft = draft.copy(surfaceVariant = c) },
                Triple("Body Text", draft.text) { c: Long -> draft = draft.copy(text = c) },
                Triple("Secondary Text", draft.textSecondary) { c: Long -> draft = draft.copy(textSecondary = c) },
                Triple("Accent", draft.accent) { c: Long -> draft = draft.copy(accent = c) },
                Triple("Accent Container", draft.accentContainer) { c: Long -> draft = draft.copy(accentContainer = c) },
                Triple("Link", draft.link) { c: Long -> draft = draft.copy(link = c) },
                Triple("Glass Tint", draft.glassTint) { c: Long -> draft = draft.copy(glassTint = c) },
                Triple("Shelf Wood Tint", draft.shelfWoodTint) { c: Long -> draft = draft.copy(shelfWoodTint = c) }
            )
            roles.forEach { (label, value, updater) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(draft.surface))
                        .clickable {
                            editingRole = label to value
                            onColorPicked = updater
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = Color(draft.text))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = ContrastChecker.formatHex(value),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = Color(draft.textSecondary)
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(value))
                                .border(1.dp, Color(draft.textSecondary).copy(alpha = 0.4f), CircleShape)
                        )
                    }
                }
            }

            Text("Glass Opacity: ${(draft.glassTintAlpha * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = Color(draft.text))
            LiquidGlassSlider(
                value = draft.glassTintAlpha,
                onValueChange = { draft = draft.copy(glassTintAlpha = it) },
                valueRange = 0.55f..1.0f
            )

            Text("Highlight Palette (tap to edit)", style = MaterialTheme.typography.titleMedium, color = Color(draft.text))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                draft.highlightColors.forEachIndexed { idx, colorLong ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(colorLong))
                            .border(1.5.dp, Color(draft.textSecondary).copy(alpha = 0.35f), CircleShape)
                            .clickable {
                                editingRole = "Highlight ${idx + 1}" to colorLong
                                onColorPicked = { nextColor ->
                                    val updated = draft.highlightColors.toMutableList()
                                    updated[idx] = nextColor
                                    draft = draft.copy(highlightColors = updated)
                                }
                            }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (editingRole != null) {
        val (roleName, initialColor) = editingRole!!
        ColorPickerDialog(
            title = roleName,
            initialArgb = initialColor,
            onDismiss = { editingRole = null },
            onConfirm = { chosen ->
                onColorPicked(chosen)
                editingRole = null
            }
        )
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    initialArgb: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var hexInput by remember(initialArgb) { mutableStateOf(ContrastChecker.formatHex(initialArgb)) }
    val parsedColor = ContrastChecker.parseHexOrNull(hexInput) ?: initialArgb

    val swatches = listOf(
        0xFFFFF5F7, 0xFFFF8FB1, 0xFF2D1B22, 0xFF1F1A24, 0xFFF4A6C8,
        0xFFFAF9F6, 0xFFF4ECD8, 0xFF1E1E2E, 0xFFCBA6F7, 0xFF2E3440,
        0xFF88C0D0, 0xFF282A36, 0xFFBD93F9, 0xFFF5F8F1, 0xFF84B878,
        0xFFF6F2FF, 0xFFB79CFF, 0xFF000000, 0xFFFFFFFF, 0xFF4A6FA5
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit $title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(parsedColor))
                            .border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
                    )
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { hexInput = it },
                        label = { Text("Hex (#RRGGBB)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    swatches.forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(swatch))
                                .border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
                                .clickable { hexInput = ContrastChecker.formatHex(swatch) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parsedColor) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
