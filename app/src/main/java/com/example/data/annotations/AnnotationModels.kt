package com.example.data.annotations

import androidx.compose.ui.graphics.Color
import com.example.data.db.AnnotationEntity
import com.example.data.db.BookEntity
import com.example.theme.MochiThemePalette

enum class AnnotationType(val label: String) {
    HIGHLIGHT("Highlight"),
    NOTE("Note"),
    BOOKMARK("Bookmark");

    companion object {
        fun fromRaw(raw: String): AnnotationType =
            entries.find { it.name.equals(raw, ignoreCase = true) } ?: HIGHLIGHT
    }
}

enum class HighlightStyle(val label: String) {
    HIGHLIGHT("Highlight"),
    UNDERLINE("Underline"),
    SQUIGGLY("Squiggly"),
    STRIKETHROUGH("Strikethrough"),
    WASHI_TAPE("Washi Tape");

    companion object {
        fun fromRaw(raw: String?): HighlightStyle =
            entries.find { it.name.equals(raw, ignoreCase = true) } ?: HIGHLIGHT
    }
}

enum class CozySticker(val label: String, val badgeText: String) {
    NONE("None", ""),
    SPARKLE("Sparkle", "✦"),
    HEART("Heart", "♥"),
    STAR("Star", "★"),
    FLOWER("Sakura", "✿"),
    BOW("Ribbon", "୨ৎ"),
    MOCHI("Mochi", "◡̈");

    companion object {
        fun fromRaw(raw: String?): CozySticker =
            entries.find { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

data class AnnotationWithBook(
    val annotation: AnnotationEntity,
    val book: BookEntity?
)

object HighlightColorHelper {
    val colorNames = listOf(
        "Sakura",
        "Sunbeam",
        "Matcha",
        "Sky",
        "Lavender",
        "Peach",
        "Rose",
        "Mint"
    )

    // Okabe-Ito-inspired accessible 8-color palette for Light & Dark paper
    val colorBlindLight = listOf(
        0xFFF9D89C, // Warm Amber
        0xFFB9E2FA, // Sky Blue
        0xFFA7E8D0, // Bluish Green
        0xFFFFF0A6, // Soft Yellow
        0xFFC8D7F7, // Periwinkle
        0xFFF6C6AD, // Vermilion Tint
        0xFFEAC2DE, // Reddish Purple
        0xFFDCDCDC  // Neutral Mist
    )

    val colorBlindDark = listOf(
        0xFF704D18,
        0xFF1F4E6E,
        0xFF1C5A46,
        0xFF635A1B,
        0xFF2B3F6B,
        0xFF6B3620,
        0xFF5D2D50,
        0xFF434852
    )

    fun resolveColors(palette: MochiThemePalette, colorBlindSafe: Boolean): List<Long> {
        if (colorBlindSafe) {
            return if (palette.isDark) colorBlindDark else colorBlindLight
        }
        val base = palette.highlightColors
        return if (base.size >= 8) {
            base.take(8)
        } else if (base.isEmpty()) {
            if (palette.isDark) colorBlindDark else colorBlindLight
        } else {
            List(8) { idx -> base[idx % base.size] }
        }
    }

    fun colorForRole(palette: MochiThemePalette, role: Int, colorBlindSafe: Boolean): Color {
        val list = resolveColors(palette, colorBlindSafe)
        val safeIdx = ((role % list.size) + list.size) % list.size
        return Color(list[safeIdx])
    }

    fun nameForRole(role: Int): String {
        val safeIdx = ((role % colorNames.size) + colorNames.size) % colorNames.size
        return colorNames[safeIdx]
    }
}
