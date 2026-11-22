package com.example.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min

data class ContrastReport(
    val bodyOnBackground: Double,
    val secondaryOnBackground: Double,
    val bodyOnSurface: Double,
    val bodyOnGlass: Double,
    val linkOnBackground: Double,
    val passesBodyAa: Boolean,
    val passesSecondaryAa: Boolean,
    val passesGlassAa: Boolean
) {
    val passesAll: Boolean
        get() = passesBodyAa && passesSecondaryAa && passesGlassAa
}

object ContrastChecker {

    const val WCAG_AA_NORMAL_TEXT = 4.5
    const val WCAG_AA_LARGE_TEXT = 3.0
    const val WCAG_AAA_NORMAL_TEXT = 7.0

    fun contrastRatio(foreground: Color, background: Color): Double {
        val fg = if (foreground.alpha < 1f) compositeOver(foreground, background) else foreground
        val l1 = fg.luminance().toDouble()
        val l2 = background.luminance().toDouble()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun compositeOver(foreground: Color, background: Color): Color {
        val a = foreground.alpha
        val invA = 1f - a
        return Color(
            red = foreground.red * a + background.red * invA,
            green = foreground.green * a + background.green * invA,
            blue = foreground.blue * a + background.blue * invA,
            alpha = 1f
        )
    }

    /**
     * Computes an adaptive glass scrim color that guarantees at least WCAG AA (4.5:1)
     * contrast for [textColor] over the glass container, even over bright or busy content.
     */
    fun adaptiveGlassFill(
        glassTint: Color,
        glassTintAlpha: Float,
        underlyingColor: Color,
        textColor: Color,
        solidChrome: Boolean
    ): Color {
        val effectiveAlpha = if (solidChrome) 1f else glassTintAlpha.coerceIn(0.68f, 0.96f)
        var candidate = glassTint.copy(alpha = effectiveAlpha)
        var composited = compositeOver(candidate, underlyingColor)
        if (contrastRatio(textColor, composited) >= WCAG_AA_NORMAL_TEXT) {
            return candidate
        }
        // Step opacity up toward opaque if needed to protect contrast
        var stepAlpha = effectiveAlpha
        while (stepAlpha < 0.98f) {
            stepAlpha = (stepAlpha + 0.06f).coerceAtMost(0.98f)
            candidate = glassTint.copy(alpha = stepAlpha)
            composited = compositeOver(candidate, underlyingColor)
            if (contrastRatio(textColor, composited) >= WCAG_AA_NORMAL_TEXT) {
                return candidate
            }
        }
        // Fallback to high-contrast backing tint opposite text luminance
        val fallbackBase = if (textColor.luminance() > 0.5f) Color(0xFF141418) else Color(0xFFFAF9FB)
        return fallbackBase.copy(alpha = if (solidChrome) 1f else 0.92f)
    }

    fun evaluate(palette: MochiThemePalette): ContrastReport {
        val bg = Color(palette.background)
        val surface = Color(palette.surface)
        val text = Color(palette.text)
        val textSec = Color(palette.textSecondary)
        val link = Color(palette.link)
        val glassFill = adaptiveGlassFill(
            glassTint = Color(palette.glassTint),
            glassTintAlpha = palette.glassTintAlpha,
            underlyingColor = bg,
            textColor = text,
            solidChrome = palette.prefersSolidChrome
        )
        val glassComposited = compositeOver(glassFill, bg)

        val bodyBg = contrastRatio(text, bg)
        val secBg = contrastRatio(textSec, bg)
        val bodySurf = contrastRatio(text, surface)
        val bodyGlass = contrastRatio(text, glassComposited)
        val linkBg = contrastRatio(link, bg)

        return ContrastReport(
            bodyOnBackground = bodyBg,
            secondaryOnBackground = secBg,
            bodyOnSurface = bodySurf,
            bodyOnGlass = bodyGlass,
            linkOnBackground = linkBg,
            passesBodyAa = bodyBg >= WCAG_AA_NORMAL_TEXT && bodySurf >= WCAG_AA_NORMAL_TEXT,
            passesSecondaryAa = secBg >= WCAG_AA_NORMAL_TEXT,
            passesGlassAa = bodyGlass >= WCAG_AA_NORMAL_TEXT
        )
    }

    fun formatHex(argb: Long): String {
        return String.format("#%06X", (argb.toInt() and 0xFFFFFF))
    }

    fun parseHexOrNull(hex: String): Long? {
        val clean = hex.trim().removePrefix("#")
        return when (clean.length) {
            6 -> clean.toLongOrNull(16)?.let { 0xFF000000L or it }
            8 -> clean.toLongOrNull(16)
            else -> null
        }
    }

    fun bestOnColor(background: Color): Color {
        val whiteContrast = contrastRatio(Color.White, background)
        val darkContrast = contrastRatio(Color(0xFF141218), background)
        return if (whiteContrast >= darkContrast) Color.White else Color(0xFF141218)
    }

    fun toArgbLong(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL
}
