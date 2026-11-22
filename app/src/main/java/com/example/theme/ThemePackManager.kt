package com.example.theme

import android.content.Context
import android.net.Uri
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ThemePackImportResult(
    val palette: MochiThemePalette,
    val creditsNote: String?
)

object ThemePackManager {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val paletteAdapter = moshi.adapter(MochiThemePalette::class.java).indent("  ")

    fun toJson(palette: MochiThemePalette): String = paletteAdapter.toJson(palette)

    fun fromJson(json: String): MochiThemePalette? = runCatching {
        paletteAdapter.fromJson(json)
    }.getOrNull()

    suspend fun exportThemePack(
        context: Context,
        destUri: Uri,
        palette: MochiThemePalette,
        creditsText: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val exportPalette = palette.copy(builtIn = false)
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("theme.json"))
                    zip.write(toJson(exportPalette).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry("credits.txt"))
                    val credits = creditsText ?: buildString {
                        appendLine("Mochi Reader Theme Pack")
                        appendLine("Theme: ${exportPalette.name}")
                        appendLine("Format version: 1")
                        appendLine("License: User-exported offline theme")
                    }
                    zip.write(credits.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            } ?: error("Unable to open destination for writing")
        }
    }

    suspend fun importThemePack(
        context: Context,
        sourceUri: Uri
    ): Result<ThemePackImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            var themeJson: String? = null
            var creditsNote: String? = null

            context.contentResolver.openInputStream(sourceUri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    var entryCount = 0
                    while (entry != null) {
                        entryCount++
                        require(entryCount <= 64) { "Theme pack contains too many files" }
                        val entryName = entry.name
                        // Zip-slip path traversal guard
                        require(!entryName.contains("..") && !entryName.startsWith("/")) {
                            "Invalid entry path in theme pack: $entryName"
                        }
                        if (!entry.isDirectory) {
                            when (entryName.lowercase()) {
                                "theme.json" -> {
                                    themeJson = readBoundedUtf8(zip, maxBytes = 512 * 1024)
                                }
                                "credits.txt", "license.txt" -> {
                                    creditsNote = readBoundedUtf8(zip, maxBytes = 64 * 1024)
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Unable to open theme pack")

            val rawJson = themeJson ?: error("Missing theme.json inside .mochitheme archive")
            val parsed = fromJson(rawJson) ?: error("Invalid theme.json structure")
            require(parsed.name.isNotBlank()) { "Theme name cannot be empty" }
            require(parsed.highlightColors.size >= 4) { "Theme must include highlight colors" }

            val normalizedHighlights = if (parsed.highlightColors.size >= 8) {
                parsed.highlightColors.take(8)
            } else {
                List(8) { idx -> parsed.highlightColors[idx % parsed.highlightColors.size] }
            }

            val imported = parsed.copy(
                id = "custom_${UUID.randomUUID().toString().take(8)}",
                builtIn = false,
                category = ThemeCategory.CUSTOM,
                highlightColors = normalizedHighlights,
                glassTintAlpha = parsed.glassTintAlpha.coerceIn(0.45f, 1.0f),
                blurRadiusDp = parsed.blurRadiusDp.coerceIn(0f, 32f)
            )
            ThemePackImportResult(palette = imported, creditsNote = creditsNote)
        }
    }

    private fun readBoundedUtf8(zip: ZipInputStream, maxBytes: Int): String {
        val buffer = ByteArray(4096)
        val out = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = zip.read(buffer)
            if (read <= 0) break
            total += read
            require(total <= maxBytes) { "Entry exceeds maximum allowed size" }
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
