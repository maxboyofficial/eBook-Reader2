package com.example.ui.notes

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.annotations.AnnotationExporter
import com.example.data.annotations.AnnotationWithBook
import com.example.data.db.BookEntity
import com.example.data.db.ExportTemplateEntity
import com.example.theme.ContrastChecker
import com.example.ui.designsystem.MochiConnectedSegments
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMochiPalette
import kotlinx.coroutines.launch

enum class ExportFormatOption(val label: String, val ext: String, val mimeType: String) {
    MARKDOWN("Markdown", "md", "text/markdown"),
    JSON("JSON", "json", "application/json"),
    CSV("CSV", "csv", "text/csv")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportTemplatesSheet(
    items: List<AnnotationWithBook>,
    templates: List<ExportTemplateEntity>,
    scopedBook: BookEntity? = null,
    vaultFolderUri: String? = null,
    onSaveTemplate: (name: String, body: String, existingId: Long) -> Unit,
    onDeleteTemplate: (Long) -> Unit,
    onSaveVaultFolderUri: (String?) -> Unit,
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMochiPalette.current
    val scope = rememberCoroutineScope()

    var format by remember { mutableStateOf(ExportFormatOption.MARKDOWN) }
    var scopeSingleBook by remember(scopedBook) { mutableStateOf(scopedBook != null) }

    val activeItems = remember(items, scopeSingleBook, scopedBook) {
        if (scopeSingleBook && scopedBook != null) {
            items.filter { it.annotation.bookId == scopedBook.id }
        } else {
            items
        }
    }

    var selectedTemplateId by remember(templates) {
        mutableStateOf(templates.firstOrNull()?.id ?: 0L)
    }
    val selectedTemplate = templates.find { it.id == selectedTemplateId } ?: templates.firstOrNull()

    var templateName by remember(selectedTemplate?.id) {
        mutableStateOf(selectedTemplate?.name ?: "Obsidian / Logseq Note")
    }
    var templateBody by remember(selectedTemplate?.id) {
        mutableStateOf(
            selectedTemplate?.body ?: "### {{title}} — {{chapter}}\n> {{quote}}\n\n{{note}}\nTags: {{tags}}"
        )
    }

    val exportedPayload = remember(format, activeItems, templateBody, scopeSingleBook, scopedBook) {
        when (format) {
            ExportFormatOption.MARKDOWN -> {
                if (scopeSingleBook && scopedBook != null) {
                    AnnotationExporter.exportBookMarkdown(scopedBook, activeItems.map { it.annotation }, templateBody)
                } else {
                    AnnotationExporter.exportLibraryMarkdown(activeItems, templateBody)
                }
            }
            ExportFormatOption.JSON -> AnnotationExporter.exportJson(activeItems)
            ExportFormatOption.CSV -> AnnotationExporter.exportCsv(activeItems)
        }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = AnnotationExporter.writeTextToUri(context, uri, exportedPayload)
                onShowMessage(
                    res.fold(
                        onSuccess = { "Exported ${activeItems.size} annotations (${format.label})" },
                        onFailure = { "Export failed: ${it.localizedMessage}" }
                    )
                )
            }
        }
    }

    val vaultPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onSaveVaultFolderUri(uri.toString())
            scope.launch {
                val res = AnnotationExporter.writeLibraryToVaultFolder(context, uri, activeItems, templateBody)
                onShowMessage(
                    res.fold(
                        onSuccess = { count -> "Synced $count Markdown note file(s) to Vault" },
                        onFailure = { "Vault sync failed: ${it.localizedMessage}" }
                    )
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface),
        modifier = Modifier.testTag("export_templates_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Export & Markdown Templates",
                style = MaterialTheme.typography.titleLarge,
                color = Color(palette.text)
            )

            MochiConnectedSegments(
                items = ExportFormatOption.entries,
                selected = format,
                labelProvider = { it.label },
                testTagProvider = { "export_format_${it.name.lowercase()}" },
                onSelect = { format = it }
            )

            if (scopedBook != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = scopeSingleBook,
                        onClick = { scopeSingleBook = true },
                        label = { Text("This Book (${items.count { it.annotation.bookId == scopedBook.id }})") }
                    )
                    FilterChip(
                        selected = !scopeSingleBook,
                        onClick = { scopeSingleBook = false },
                        label = { Text("All Books (${items.size})") }
                    )
                }
            }

            if (format == ExportFormatOption.MARKDOWN) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Templates", style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                    Row {
                        OutlinedButton(
                            onClick = {
                                selectedTemplateId = 0L
                                templateName = "My Custom Template"
                            }
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New")
                        }
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.forEach { tpl ->
                        FilterChip(
                            selected = tpl.id == selectedTemplate?.id,
                            onClick = {
                                selectedTemplateId = tpl.id
                                templateName = tpl.name
                                templateBody = tpl.body
                            },
                            label = { Text(tpl.name) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("Template Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedTemplate != null && templates.size > 1) {
                        IconButton(onClick = { onDeleteTemplate(selectedTemplate.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete template", tint = Color(palette.textSecondary))
                        }
                    }
                }

                // Placeholder token chips
                val tokens = listOf(
                    "{{quote}}", "{{note}}", "{{page}}", "{{chapter}}",
                    "{{color}}", "{{tags}}", "{{date}}", "{{title}}", "{{author}}"
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tokens.forEach { tok ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(palette.surfaceVariant),
                            onClick = { templateBody += if (templateBody.endsWith(" ") || templateBody.endsWith("\n")) tok else " $tok" }
                        ) {
                            Text(
                                text = tok,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = Color(palette.link),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = templateBody,
                    onValueChange = { templateBody = it },
                    label = { Text("Template Body") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_template_body_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            onSaveTemplate(templateName, templateBody, selectedTemplate?.id ?: 0L)
                            onShowMessage("Saved template \"$templateName\"")
                        }
                    ) {
                        Text("Save Template")
                    }
                }
            }

            // Live Export Preview
            Text("Output Preview (${activeItems.size} entries)", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(palette.background),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 180.dp)
                    .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            ) {
                Text(
                    text = exportedPayload.take(1800),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = Color(palette.textSecondary),
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val slug = (scopedBook?.title ?: "mochi_notes").lowercase().replace(Regex("[^a-z0-9]+"), "_").take(24)
                        exportFileLauncher.launch("${slug}_notes.${format.ext}")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(palette.accent),
                        contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_save_file_button")
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export .${format.ext}")
                }

                OutlinedButton(
                    onClick = {
                        if (vaultFolderUri != null) {
                            scope.launch {
                                val res = AnnotationExporter.writeLibraryToVaultFolder(
                                    context,
                                    Uri.parse(vaultFolderUri),
                                    activeItems,
                                    templateBody
                                )
                                res.fold(
                                    onSuccess = { count -> onShowMessage("Synced $count file(s) to Obsidian Vault") },
                                    onFailure = { vaultPickerLauncher.launch(null) }
                                )
                            }
                        } else {
                            vaultPickerLauncher.launch(null)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sync_vault_folder_button")
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (vaultFolderUri != null) "Sync Vault" else "Vault Folder")
                }
            }
        }
    }
}
