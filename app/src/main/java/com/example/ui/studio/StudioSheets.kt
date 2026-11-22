package com.example.ui.studio

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.data.db.AnnotationLayerEntity
import com.example.data.db.MarkupObjectEntity
import com.example.data.studio.ArrowHeadStyle
import com.example.data.studio.DashStyle
import com.example.data.studio.MeasurementUnit
import com.example.data.studio.StampKind
import com.example.data.studio.StudioGeometryJson
import com.example.data.studio.StudioPresets
import com.example.data.studio.StudioStyleJson
import com.example.data.studio.StudioTool
import com.example.theme.ContrastChecker
import com.example.ui.designsystem.LiquidGlassSlider
import com.example.ui.theme.LocalMochiPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioPropertiesSheet(
    selectedMarkup: MarkupObjectEntity?,
    activeStyle: StudioStyleJson,
    onUpdateActiveStyle: (StudioStyleJson) -> Unit,
    onUpdateSelectedMarkup: (MarkupObjectEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    val initialStyle = selectedMarkup?.let { StudioStyleJson.fromJson(it.styleJson) } ?: activeStyle
    var style by remember(selectedMarkup?.id, activeStyle) { mutableStateOf(initialStyle) }
    var geo by remember(selectedMarkup?.id) {
        mutableStateOf(selectedMarkup?.let { StudioGeometryJson.fromJson(it.geometryJson) })
    }

    fun applyStyle(next: StudioStyleJson) {
        style = next
        if (selectedMarkup != null) {
            onUpdateSelectedMarkup(selectedMarkup.copy(styleJson = StudioStyleJson.toJson(next)))
        } else {
            onUpdateActiveStyle(next)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface),
        modifier = Modifier.testTag("studio_properties_sheet")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (selectedMarkup != null) "Object Properties (${selectedMarkup.kind})" else "Tool Style & Inspector",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(palette.text)
            )

            Text("Style Presets", style = MaterialTheme.typography.labelLarge, color = Color(palette.textSecondary))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioPresets.all.forEach { preset ->
                    FilterChip(
                        selected = style.strokeColorHex == preset.style.strokeColorHex && style.strokeWidthPt == preset.style.strokeWidthPt,
                        onClick = { applyStyle(preset.style) },
                        label = { Text(preset.name) }
                    )
                }
            }

            Text("Stroke Color", style = MaterialTheme.typography.labelLarge, color = Color(palette.textSecondary))
            val swatches = listOf("#E03A3E", "#2F7CF6", "#2E8B57", "#F4C542", "#9B51E0", "#E6739F", "#1F2430", "#FFFFFF")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                swatches.forEach { hex ->
                    val col = Color(android.graphics.Color.parseColor(hex))
                    val selected = style.strokeColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(if (selected) 3.dp else 1.dp, if (selected) Color(palette.accent) else Color.Gray.copy(alpha = 0.4f), CircleShape)
                            .clickable { applyStyle(style.copy(strokeColorHex = hex)) }
                    )
                }
            }

            Text("Stroke Width: ${String.format("%.1f pt", style.strokeWidthPt)}", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
            LiquidGlassSlider(
                value = style.strokeWidthPt,
                onValueChange = { applyStyle(style.copy(strokeWidthPt = it)) },
                valueRange = 0.8f..16f
            )

            Text("Opacity: ${(style.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
            LiquidGlassSlider(
                value = style.opacity,
                onValueChange = { applyStyle(style.copy(opacity = it)) },
                valueRange = 0.1f..1f
            )

            Text("Dash Style", style = MaterialTheme.typography.labelLarge, color = Color(palette.textSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashStyle.entries.forEach { ds ->
                    FilterChip(
                        selected = style.dashStyle == ds.name,
                        onClick = { applyStyle(style.copy(dashStyle = ds.name)) },
                        label = { Text(ds.label) }
                    )
                }
            }

            Text("End Arrowhead", style = MaterialTheme.typography.labelLarge, color = Color(palette.textSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArrowHeadStyle.entries.forEach { ah ->
                    FilterChip(
                        selected = style.endArrow == ah.name,
                        onClick = { applyStyle(style.copy(endArrow = ah.name)) },
                        label = { Text(ah.label) }
                    )
                }
            }

            if (selectedMarkup != null && geo != null) {
                val currentGeo = geo!!
                Text("Numeric Bounds (Normalized 0–100%)", style = MaterialTheme.typography.labelLarge, color = Color(palette.textSecondary))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var x1Txt by remember(selectedMarkup.id) { mutableStateOf(String.format("%.1f", currentGeo.x1 * 100f)) }
                    var y1Txt by remember(selectedMarkup.id) { mutableStateOf(String.format("%.1f", currentGeo.y1 * 100f)) }
                    OutlinedTextField(
                        value = x1Txt,
                        onValueChange = {
                            x1Txt = it
                            it.toFloatOrNull()?.let { v ->
                                val next = currentGeo.copy(x1 = (v / 100f).coerceIn(0f, 1f))
                                geo = next
                                onUpdateSelectedMarkup(selectedMarkup.copy(geometryJson = StudioGeometryJson.toJson(next)))
                            }
                        },
                        label = { Text("X1 %") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = y1Txt,
                        onValueChange = {
                            y1Txt = it
                            it.toFloatOrNull()?.let { v ->
                                val next = currentGeo.copy(y1 = (v / 100f).coerceIn(0f, 1f))
                                geo = next
                                onUpdateSelectedMarkup(selectedMarkup.copy(geometryJson = StudioGeometryJson.toJson(next)))
                            }
                        },
                        label = { Text("Y1 %") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (selectedMarkup.kind == StudioTool.TEXT_NOTE.name || selectedMarkup.kind == StudioTool.CALLOUT.name) {
                    var noteTxt by remember(selectedMarkup.id) { mutableStateOf(currentGeo.text) }
                    OutlinedTextField(
                        value = noteTxt,
                        onValueChange = {
                            noteTxt = it
                            val next = currentGeo.copy(text = it)
                            geo = next
                            onUpdateSelectedMarkup(selectedMarkup.copy(geometryJson = StudioGeometryJson.toJson(next), label = it))
                        },
                        label = { Text("Annotation Text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioLayersSheet(
    layers: List<AnnotationLayerEntity>,
    activeLayerId: Long,
    onSelectLayer: (Long) -> Unit,
    onCreateLayer: (String) -> Unit,
    onUpdateLayer: (AnnotationLayerEntity) -> Unit,
    onDeleteLayer: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    var newLayerName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface),
        modifier = Modifier.testTag("studio_layers_sheet")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Annotation Layers", style = MaterialTheme.typography.headlineSmall, color = Color(palette.text))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newLayerName,
                    onValueChange = { newLayerName = it },
                    placeholder = { Text("New layer name…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onCreateLayer(newLayerName)
                        newLayerName = ""
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(palette.accent),
                        contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                    )
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add layer")
                }
            }

            layers.forEach { layer ->
                val isActive = layer.id == activeLayerId
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isActive) Color(palette.accentContainer) else Color(palette.surfaceVariant).copy(alpha = 0.5f),
                    onClick = { onSelectLayer(layer.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = layer.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(palette.text),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onUpdateLayer(layer.copy(visible = !layer.visible)) }) {
                                Icon(
                                    imageVector = if (layer.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = "Toggle layer visibility",
                                    tint = Color(palette.text)
                                )
                            }
                            IconButton(onClick = { onUpdateLayer(layer.copy(locked = !layer.locked)) }) {
                                Icon(
                                    imageVector = if (layer.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                    contentDescription = "Toggle layer lock",
                                    tint = if (layer.locked) Color(palette.accent) else Color(palette.textSecondary)
                                )
                            }
                            if (layers.size > 1) {
                                IconButton(onClick = { onDeleteLayer(layer.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete layer", tint = Color(palette.textSecondary))
                                }
                            }
                        }
                        Text("Layer Opacity: ${(layer.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                        LiquidGlassSlider(
                            value = layer.opacity,
                            onValueChange = { onUpdateLayer(layer.copy(opacity = it)) },
                            valueRange = 0.1f..1f
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalibrateScaleDialog(
    measuredSegmentPts: Float,
    currentUnit: MeasurementUnit,
    onConfirm: (Float, MeasurementUnit) -> Unit,
    onDismiss: () -> Unit
) {
    var knownDistance by remember { mutableStateOf("100") }
    var unit by remember { mutableStateOf(currentUnit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calibrate Page Scale") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Set the real-world length of the line you just drew (${measuredSegmentPts.roundToInt()} PDF pts):",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = knownDistance,
                    onValueChange = { knownDistance = it },
                    label = { Text("Known length") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MeasurementUnit.entries.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(u.suffix) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val realUnits = knownDistance.toFloatOrNull()?.coerceAtLeast(0.01f) ?: 100f
                val ptsPerUnit = measuredSegmentPts.coerceAtLeast(1f) / realUnits
                onConfirm(ptsPerUnit, unit)
            }) {
                Text("Save Scale")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioExportSheet(
    onExportAnnotatedPdf: () -> Unit,
    onExportSvg: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Export Studio Annotations", style = MaterialTheme.typography.headlineSmall, color = Color(palette.text))
            Text("Original PDF files stay untouched. Exports write a new copy via Android's document picker.", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
            Button(
                onClick = { onDismiss(); onExportAnnotatedPdf() },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(palette.accent), contentColor = ContrastChecker.bestOnColor(Color(palette.accent))),
                modifier = Modifier.fillMaxWidth().testTag("export_annotated_pdf_button")
            ) {
                Text("Export Annotated PDF Copy (.pdf)")
            }
            OutlinedButton(
                onClick = { onDismiss(); onExportSvg() },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().testTag("export_svg_button")
            ) {
                Text("Export Layered Vector Graphics (.svg)")
            }
            OutlinedButton(
                onClick = { onDismiss(); onExportJson() },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().testTag("export_studio_json_button")
            ) {
                Text("Export Portable Project Backup (.json)")
            }
            OutlinedButton(
                onClick = { onDismiss(); onImportJson() },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Project Backup (.json)")
            }
        }
    }
}

@Composable
fun StudioTextPromptDialog(
    title: String,
    initialText: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Label or note") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.ifBlank { "Note" }) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
