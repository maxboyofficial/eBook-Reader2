package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.theme.BundledThemes
import com.example.theme.ContrastChecker
import com.example.theme.GlassIntensity
import com.example.theme.MochiThemePalette
import com.example.ui.designsystem.LocalMochiMotion
import com.example.ui.designsystem.MochiMotionTokens

@Immutable
data class GlassConfig(
    val intensity: GlassIntensity = GlassIntensity.RICH,
    val solidChrome: Boolean = false,
    val freezeBlur: Boolean = false
) {
    val isSolid: Boolean
        get() = solidChrome || intensity == GlassIntensity.OFF
}

val LocalMochiPalette = staticCompositionLocalOf { BundledThemes.byId(BundledThemes.DEFAULT_LIGHT_ID) }
val LocalGlassConfig = staticCompositionLocalOf { GlassConfig() }

private val MochiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

fun MochiThemePalette.toMaterialColorScheme(): ColorScheme {
    val bg = Color(background)
    val surf = Color(surface)
    val surfVar = Color(surfaceVariant)
    val txt = Color(text)
    val txtSec = Color(textSecondary)
    val pri = Color(accent)
    val priCont = Color(accentContainer)
    val lnk = Color(link)
    val onPri = ContrastChecker.bestOnColor(pri)
    val onPriCont = ContrastChecker.bestOnColor(priCont)

    return if (isDark) {
        darkColorScheme(
            primary = pri,
            onPrimary = onPri,
            primaryContainer = priCont,
            onPrimaryContainer = onPriCont,
            secondary = lnk,
            onSecondary = ContrastChecker.bestOnColor(lnk),
            secondaryContainer = surfVar,
            onSecondaryContainer = txt,
            tertiary = Color(highlightColors.firstOrNull() ?: accent),
            background = bg,
            onBackground = txt,
            surface = surf,
            onSurface = txt,
            surfaceVariant = surfVar,
            onSurfaceVariant = txtSec,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surf,
            surfaceContainer = surf,
            surfaceContainerHigh = surfVar,
            surfaceContainerHighest = surfVar,
            outline = Color(glassBorder),
            outlineVariant = txtSec.copy(alpha = 0.25f)
        )
    } else {
        lightColorScheme(
            primary = pri,
            onPrimary = onPri,
            primaryContainer = priCont,
            onPrimaryContainer = onPriCont,
            secondary = lnk,
            onSecondary = ContrastChecker.bestOnColor(lnk),
            secondaryContainer = surfVar,
            onSecondaryContainer = txt,
            tertiary = Color(highlightColors.firstOrNull() ?: accent),
            background = bg,
            onBackground = txt,
            surface = surf,
            onSurface = txt,
            surfaceVariant = surfVar,
            onSurfaceVariant = txtSec,
            surfaceContainerLowest = surf,
            surfaceContainerLow = bg,
            surfaceContainer = surf,
            surfaceContainerHigh = surfVar,
            surfaceContainerHighest = surfVar,
            outline = txtSec.copy(alpha = 0.35f),
            outlineVariant = txtSec.copy(alpha = 0.18f)
        )
    }
}

fun MochiThemePalette.withDynamicColorScheme(dynamic: ColorScheme): MochiThemePalette {
    return copy(
        background = ContrastChecker.toArgbLong(dynamic.background),
        surface = ContrastChecker.toArgbLong(dynamic.surfaceContainerLow),
        surfaceVariant = ContrastChecker.toArgbLong(dynamic.surfaceContainerHigh),
        text = ContrastChecker.toArgbLong(dynamic.onBackground),
        textSecondary = ContrastChecker.toArgbLong(dynamic.onSurfaceVariant),
        accent = ContrastChecker.toArgbLong(dynamic.primary),
        accentContainer = ContrastChecker.toArgbLong(dynamic.primaryContainer),
        link = ContrastChecker.toArgbLong(dynamic.secondary),
        paperTint = ContrastChecker.toArgbLong(dynamic.background),
        glassTint = ContrastChecker.toArgbLong(dynamic.surfaceContainer),
        selection = ContrastChecker.toArgbLong(dynamic.primary.copy(alpha = 0.28f))
    )
}

@Composable
fun MochiExpressiveTheme(
    palette: MochiThemePalette,
    glassIntensity: GlassIntensity = GlassIntensity.RICH,
    solidChrome: Boolean = false,
    reduceMotion: Boolean = false,
    useDynamicColor: Boolean = false,
    matchSystemBars: Boolean = true,
    warmLightEnabled: Boolean = false,
    warmLightIntensity: Float = 0.25f,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val resolvedPalette = remember(palette, useDynamicColor, context) {
        if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamic = if (palette.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            palette.withDynamicColorScheme(dynamic)
        } else {
            palette
        }
    }

    val colorScheme = remember(resolvedPalette) { resolvedPalette.toMaterialColorScheme() }
    val effectiveSolid = solidChrome || resolvedPalette.prefersSolidChrome || glassIntensity == GlassIntensity.OFF
    val effectiveReduceMotion = reduceMotion || resolvedPalette.id == "e_ink"

    val view = LocalView.current
    if (!view.isInEditMode && matchSystemBars) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !resolvedPalette.isDark
            insetsController.isAppearanceLightNavigationBars = !resolvedPalette.isDark
        }
    }

    val motion = remember(effectiveReduceMotion) { MochiMotionTokens(effectiveReduceMotion) }
    val glassConfig = remember(glassIntensity, effectiveSolid) {
        GlassConfig(intensity = glassIntensity, solidChrome = effectiveSolid)
    }

    CompositionLocalProvider(
        LocalMochiPalette provides resolvedPalette,
        LocalMochiMotion provides motion,
        LocalGlassConfig provides glassConfig
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MochiTypography,
            shapes = MochiShapes
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
                if (warmLightEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFF9E3D).copy(alpha = (warmLightIntensity * 0.38f).coerceIn(0.02f, 0.28f)))
                    )
                }
            }
        }
    }
}
