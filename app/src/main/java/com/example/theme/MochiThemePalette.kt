package com.example.theme

import com.squareup.moshi.JsonClass

enum class ThemeCategory(val label: String) {
    CUTE_COZY("Cute & Cozy"),
    DEVELOPER("Developer"),
    CLASSIC_PAPER("Reading Classics"),
    VIBRANT("Cool & Vibrant"),
    CUSTOM("Custom")
}

enum class GlassIntensity(val label: String, val alphaMultiplier: Float, val blurMultiplier: Float) {
    OFF("Off (Solid)", 1f, 0f),
    SUBTLE("Subtle", 0.88f, 0.7f),
    RICH("Rich", 0.76f, 1.0f)
}

enum class ShelfStyle(val label: String) {
    FLAT_GRID("Grid"),
    LIST("List"),
    GLASS_SHELF("Glass Shelf"),
    WOODEN_SHELF("Wood Shelf")
}

enum class DecorProgressStyle(val label: String) {
    PILL("Minimal Pill"),
    WAVY("Expressive Wave"),
    HEART("Fill Heart"),
    FLOWER("Blooming Flower"),
    MOCHI("Mochi Fill")
}

@JsonClass(generateAdapter = true)
data class MochiThemePalette(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val category: ThemeCategory = ThemeCategory.CUSTOM,
    val builtIn: Boolean = false,
    // Semantic reading & surface roles (ARGB 0xAARRGGBB stored as Long)
    val background: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val text: Long,
    val textSecondary: Long,
    val accent: Long,
    val accentContainer: Long,
    val link: Long,
    val selection: Long,
    val highlightColors: List<Long>, // 8 theme-harmonized highlight roles
    val paperTint: Long,
    // Liquid Glass tokens
    val glassTint: Long,
    val glassTintAlpha: Float = 0.78f,
    val glassBorder: Long,
    val glassHighlight: Long,
    val glassShadow: Long,
    val blurRadiusDp: Float = 18f,
    val prefersSolidChrome: Boolean = false,
    // Shelf tokens
    val shelfWoodTint: Long,
    val shelfPlankHighlight: Long,
    val shelfPlankShadow: Long,
    val shelfLedgeColor: Long,
    val shelfStyleDefault: ShelfStyle = ShelfStyle.FLAT_GRID,
    // Cute & Cozy / Decor tokens
    val stickerSet: String = "cozy_basics",
    val washiTapePattern: String = "pastel_dots",
    val frameStyle: String = "soft_rounded",
    val progressStyle: DecorProgressStyle = DecorProgressStyle.WAVY
)
