package com.example.ui.dictionary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.example.data.db.DictionaryEntity
import com.example.ui.theme.LocalMochiPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryManagerSheet(
    dictionaries: List<DictionaryEntity>,
    perLanguageDefaults: Map<String, Long>,
    isImporting: Boolean,
    onToggleEnabled: (DictionaryEntity, Boolean) -> Unit,
    onMoveOrder: (Long, Int) -> Unit,
    onUpdateLanguage: (DictionaryEntity, String) -> Unit,
    onDeleteCustom: (Long) -> Unit,
    onSetPerLanguageDefault: (String, Long?) -> Unit,
    onImportSafTree: (Uri) -> Unit,
    onImportSafFile: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) onImportSafTree(uri) }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportSafFile(uri) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface),
        modifier = Modifier.testTag("dictionary_manager_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 660.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Offline Dictionaries", style = MaterialTheme.typography.titleLarge, color = Color(palette.text))
                    Text(
                        text = "StarDict (.ifo/.idx/.dict), dictd (.index/.dict), Wiktionary JSONL & TSV",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(palette.textSecondary)
                    )
                }
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(palette.accent))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { folderLauncher.launch(null) },
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f).testTag("import_dict_folder_button")
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Folder")
                }
                OutlinedButton(
                    onClick = {
                        fileLauncher.launch(arrayOf("application/zip", "application/gzip", "text/*", "application/octet-stream", "*/*"))
                    },
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f).testTag("import_dict_file_button")
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Pack/Zip")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 310.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(dictionaries, key = { _, d -> d.id }) { idx, dict ->
                    var langExpanded by remember { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(palette.surfaceVariant).copy(alpha = 0.42f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = Color(palette.accent), modifier = Modifier.size(18.dp))
                                    Column {
                                        Text(
                                            text = dict.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color(palette.text)
                                        )
                                        Text(
                                            text = "${dict.format} · ${dict.entryCount} entries",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(palette.textSecondary)
                                        )
                                    }
                                }
                                Switch(
                                    checked = dict.enabled,
                                    onCheckedChange = { onToggleEnabled(dict, it) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(palette.accentContainer).copy(alpha = 0.65f),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { langExpanded = true }
                                    ) {
                                        Text(
                                            text = "Lang: ${dict.language.uppercase()}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(palette.text),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                    DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                                        listOf("en", "es", "fr", "de", "it", "ja", "zh").forEach { code ->
                                            DropdownMenuItem(
                                                text = { Text(code.uppercase()) },
                                                onClick = {
                                                    onUpdateLanguage(dict, code)
                                                    langExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onMoveOrder(dict.id, -1) },
                                        enabled = idx > 0,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move priority up", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { onMoveOrder(dict.id, 1) },
                                        enabled = idx < dictionaries.lastIndex,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move priority down", modifier = Modifier.size(18.dp))
                                    }
                                    if (!dict.isBundled) {
                                        IconButton(
                                            onClick = { onDeleteCustom(dict.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove dictionary", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (dictionaries.isNotEmpty()) {
                val languages = dictionaries.map { it.language.lowercase() }.plus("en").distinct()
                Text("Per-Book-Language Default", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    languages.forEach { lang ->
                        var pickExpanded by remember { mutableStateOf(false) }
                        val assigned = dictionaries.find { it.id == perLanguageDefaults[lang] }
                        Box {
                            OutlinedButton(onClick = { pickExpanded = true }) {
                                Text("${lang.uppercase()}: ${assigned?.name?.take(14) ?: "Auto Priority"}")
                            }
                            DropdownMenu(expanded = pickExpanded, onDismissRequest = { pickExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Auto Priority") },
                                    onClick = {
                                        onSetPerLanguageDefault(lang, null)
                                        pickExpanded = false
                                    }
                                )
                                dictionaries.forEach { d ->
                                    DropdownMenuItem(
                                        text = { Text(d.name) },
                                        onClick = {
                                            onSetPerLanguageDefault(lang, d.id)
                                            pickExpanded = false
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
}
