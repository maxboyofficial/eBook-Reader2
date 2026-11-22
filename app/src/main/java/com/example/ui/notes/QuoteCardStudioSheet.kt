package com.example.ui.notes

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.annotations.CozySticker
import com.example.data.annotations.QuoteCardGenerator
import com.example.data.db.AnnotationEntity
import com.example.data.db.BookEntity
import com.example.data.preferences.ImportedFontInfo
import com.example.data.reader.ReaderFontResolver
import com.example.data.reader.ReaderLocator
import com.example.theme.ContrastChecker
import com.example.theme.MochiThemePalette
import com.example.ui.theme.LocalMochiPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteCardStudioSheet(
    annotation: AnnotationEntity,
    book: BookEntity?,
    themes: List<MochiThemePalette>,
    initialTheme: MochiThemePalette,
    initialFontId: String,
    importedFonts: List<ImportedFontInfo>,
    colorBlindSafe: Boolean,
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMochiPalette.current
    val scope = rememberCoroutineScope()

    var selectedTheme by remember(initialTheme) { mutableStateOf(initialTheme) }
    var selectedFontId by remember(initialFontId) { mutableStateOf(initialFontId) }
    var selectedSticker by remember(annotation.sticker) {
        mutableStateOf(CozySticker.fromRaw(annotation.sticker).takeIf { it != CozySticker.NONE } ?: CozySticker.SPARKLE)
    }
    var selectedColorRole by remember(annotation.colorRole) { mutableIntStateOf(annotation.colorRole) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val locator = remember(annotation.locatorJson) { ReaderLocator.fromJson(annotation.locatorJson) }

    LaunchedEffect(
        annotation.quoteExact,
        annotation.noteMarkdown,
        selectedTheme.id,
        selectedFontId,
        selectedSticker,
        selectedColorRole,
        colorBlindSafe
    ) {
        previewBitmap = QuoteCardGenerator.generateQuoteCardBitmap(
            context = context,
            quote = annotation.quoteExact,
            note = annotation.noteMarkdown,
            bookTitle = book?.title ?: "Mochi Book",
            bookAuthor = book?.authors.orEmpty(),
            chapterTitle = locator?.chapterTitle.orEmpty(),
            palette = selectedTheme,
            fontId = selectedFontId,
            importedFonts = importedFonts,
            sticker = selectedSticker,
            colorRole = selectedColorRole,
            colorBlindSafe = colorBlindSafe
        )
    }

    val savePngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val bmp = previewBitmap
        if (uri != null && bmp != null) {
            scope.launch {
                val res = QuoteCardGenerator.saveBitmapToUri(context, bmp, uri)
                onShowMessage(
                    res.fold(
                        onSuccess = { "Saved Quote Card PNG" },
                        onFailure = { "Could not save PNG: ${it.localizedMessage}" }
                    )
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface),
        modifier = Modifier.testTag("quote_card_studio_sheet")
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
            Text("Quote Card Studio", style = MaterialTheme.typography.titleLarge, color = Color(palette.text))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .aspectRatio(1080f / 1350f)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(palette.surfaceVariant)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = previewBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Quote card preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    CircularProgressIndicator(color = Color(palette.accent))
                }
            }

            // Card Theme selector
            Text("Card Theme", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                themes.forEach { t ->
                    val selected = t.id == selectedTheme.id
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(t.background))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) Color(t.accent) else Color(t.textSecondary).copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable { selectedTheme = t },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aa", style = MaterialTheme.typography.labelSmall, color = Color(t.text))
                    }
                }
            }

            // Font selector
            Text("Card Font", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReaderFontResolver.builtInFonts.forEach { opt ->
                    FilterChip(
                        selected = selectedFontId == opt.id,
                        onClick = { selectedFontId = opt.id },
                        label = { Text(opt.label) }
                    )
                }
            }

            // Sticker selector
            Text("Sticker", style = MaterialTheme.typography.labelLarge, color = Color(palette.text))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CozySticker.entries.forEach { st ->
                    val label = if (st.badgeText.isNotEmpty()) "${st.badgeText} ${st.label}" else st.label
                    FilterChip(
                        selected = selectedSticker == st,
                        onClick = { selectedSticker = st },
                        label = { Text(label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val slug = (book?.title ?: "mochi_quote").lowercase().replace(Regex("[^a-z0-9]+"), "_").take(24)
                        savePngLauncher.launch("${slug}_quote.png")
                    },
                    enabled = previewBitmap != null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_quote_card_button")
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save PNG")
                }

                Button(
                    onClick = {
                        val bmp = previewBitmap ?: return@Button
                        scope.launch {
                            val res = QuoteCardGenerator.createShareablePngUri(context, bmp)
                            res.fold(
                                onSuccess = { uri ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Quote Card"))
                                },
                                onFailure = {
                                    onShowMessage("Share failed: ${it.localizedMessage}")
                                }
                            )
                        }
                    },
                    enabled = previewBitmap != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(palette.accent),
                        contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_quote_card_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share PNG")
                }
            }
        }
    }
}
