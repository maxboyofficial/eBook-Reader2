package com.example.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.theme.ContrastChecker
import com.example.ui.theme.LocalMochiPalette

enum class MochiWidthClass {
    COMPACT,   // < 600.dp (phones portrait)
    MEDIUM,    // 600.dp..839.dp (unfolded foldables, small tablets, landscape phones)
    EXPANDED   // >= 840.dp (tablets, ChromeOS, large foldables landscape)
}

val LocalMochiWidthClass = staticCompositionLocalOf { MochiWidthClass.COMPACT }

@Composable
fun MochiAdaptiveContainer(
    modifier: Modifier = Modifier,
    content: @Composable (widthClass: MochiWidthClass, availableWidth: Dp, availableHeight: Dp) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthClass = when {
            maxWidth >= 840.dp -> MochiWidthClass.EXPANDED
            maxWidth >= 600.dp -> MochiWidthClass.MEDIUM
            else -> MochiWidthClass.COMPACT
        }
        CompositionLocalProvider(LocalMochiWidthClass provides widthClass) {
            content(widthClass, maxWidth, maxHeight)
        }
    }
}

@Composable
fun LiquidGlassNavigationRail(
    destinations: List<GlassNavDestination>,
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMochiPalette.current

    Box(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp)
    ) {
        LiquidGlassSurface(
            shape = RoundedCornerShape(32.dp),
            elevation = 12.dp,
            modifier = Modifier
                .fillMaxHeight()
                .width(84.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Compact Mochi emblem at top of rail
                MochiBlobMascot(size = 42.dp)
                Spacer(modifier = Modifier.height(8.dp))

                destinations.forEach { dest ->
                    val selected = currentRoute == dest.route
                    val interactionSource = remember { MutableInteractionSource() }
                    val pillColor = if (selected) Color(palette.accentContainer) else Color.Transparent
                    val tint = if (selected) {
                        ContrastChecker.bestOnColor(Color(palette.accentContainer))
                    } else {
                        Color(palette.textSecondary)
                    }

                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 64.dp, minHeight = 56.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(pillColor)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(bounded = true),
                                role = Role.Tab,
                                onClick = { onSelect(dest.route) }
                            )
                            .testTag("${dest.testTag}_rail")
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                contentDescription = dest.label,
                                tint = tint,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = dest.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = tint,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
