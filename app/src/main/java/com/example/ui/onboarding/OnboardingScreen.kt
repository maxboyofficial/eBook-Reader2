package com.example.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.theme.BundledThemes
import com.example.theme.ContrastChecker
import com.example.theme.MochiThemePalette
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.designsystem.MochiBlobMascot
import com.example.ui.theme.LocalMochiPalette
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    themes: List<MochiThemePalette>,
    activeTheme: MochiThemePalette,
    onSelectTheme: (MochiThemePalette) -> Unit,
    onFinish: () -> Unit
) {
    val palette = LocalMochiPalette.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val featuredThemes = listOf(
        BundledThemes.DEFAULT_LIGHT_ID,
        BundledThemes.DEFAULT_DARK_ID,
        "matcha_latte",
        "lavender_fog",
        "paper",
        "catppuccin_mocha",
        "nord_dark",
        "sepia"
    ).mapNotNull { id -> themes.find { it.id == id } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(palette.background),
                        Color(palette.surfaceVariant).copy(alpha = 0.5f),
                        Color(palette.background)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier.testTag("onboarding_skip_button")
                ) {
                    Text("Skip", color = Color(palette.textSecondary))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> OnboardingPageWelcome(palette)
                    1 -> OnboardingPagePrivacy(palette)
                    else -> OnboardingPageThemePicker(
                        palette = palette,
                        featuredThemes = featuredThemes,
                        activeThemeId = activeTheme.id,
                        onSelectTheme = onSelectTheme
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Page indicator dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { idx ->
                    val selected = pagerState.currentPage == idx
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (selected) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) Color(palette.accent)
                                else Color(palette.textSecondary).copy(alpha = 0.28f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (pagerState.currentPage < 2) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onFinish()
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(palette.accent),
                    contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_next_button")
            ) {
                Text(
                    text = if (pagerState.currentPage < 2) "Continue" else "Start Reading",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageWelcome(palette: MochiThemePalette) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MochiBlobMascot(size = 148.dp)
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Calm reading, built for focus.",
            style = MaterialTheme.typography.headlineLarge,
            color = Color(palette.text),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Mochi keeps your books, PDFs, comics, and Markdown notes together in a serene, distraction-free shelf.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(palette.textSecondary),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardingPagePrivacy(palette: MochiThemePalette) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LiquidGlassSurface(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                PrivacyBullet(
                    icon = Icons.Rounded.Lock,
                    title = "100% Offline & Private",
                    subtitle = "No accounts, no ads, no analytics, and zero internet permission.",
                    palette = palette
                )
                PrivacyBullet(
                    icon = Icons.Rounded.FolderOpen,
                    title = "Your Files Stay Untouched",
                    subtitle = "Pick folders with Android's Storage Access Framework. Mochi never modifies your original files.",
                    palette = palette
                )
                PrivacyBullet(
                    icon = Icons.Rounded.AutoStories,
                    title = "Ready to Explore",
                    subtitle = "A starter shelf of sample EPUB, PDF, CBZ, Markdown, and TXT books is ready for you.",
                    palette = palette
                )
            }
        }
    }
}

@Composable
private fun PrivacyBullet(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    palette: MochiThemePalette
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(palette.accentContainer)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ContrastChecker.bestOnColor(Color(palette.accentContainer))
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color(palette.text)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(palette.textSecondary)
            )
        }
    }
}

@Composable
private fun OnboardingPageThemePicker(
    palette: MochiThemePalette,
    featuredThemes: List<MochiThemePalette>,
    activeThemeId: String,
    onSelectTheme: (MochiThemePalette) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Choose your first theme",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(palette.text),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "35+ themes and a full Theme Studio await in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(palette.textSecondary),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(featuredThemes, key = { it.id }) { item ->
                val selected = item.id == activeThemeId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(item.background))
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) Color(item.accent) else Color(item.textSecondary).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onSelectTheme(item) }
                        .testTag("onboarding_theme_${item.id}")
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(item.accent))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(item.surfaceVariant))
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = Color(item.accent),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(item.text),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (item.isDark) "Dark" else "Light",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(item.textSecondary)
                        )
                    }
                }
            }
        }
    }
}
