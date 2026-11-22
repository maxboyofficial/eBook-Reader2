package com.example.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.stats.DailyReadingStat
import com.example.data.stats.ReadingInsights
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.theme.LocalMochiPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatsSheet(
    insights: ReadingInsights,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(palette.surface).copy(alpha = 0.96f),
        modifier = Modifier.testTag("reading_stats_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(palette.accentContainer)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.BarChart,
                            contentDescription = "Reading Statistics",
                            tint = Color(palette.accent)
                        )
                    }
                    Column {
                        Text(
                            text = "Reading Insights",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(palette.text)
                        )
                        Text(
                            text = "100% local · Stored privately on device",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(palette.textSecondary)
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_reading_stats_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close stats", tint = Color(palette.text))
                }
            }

            // 2x2 Summary KPI Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatKpiCard(
                    icon = Icons.Rounded.LocalFireDepartment,
                    label = "Current Streak",
                    value = "${insights.currentStreakDays} day${if (insights.currentStreakDays == 1) "" else "s"}",
                    sublabel = "Best: ${insights.longestStreakDays}d",
                    modifier = Modifier.weight(1f)
                )
                StatKpiCard(
                    icon = Icons.Rounded.Schedule,
                    label = "Total Time",
                    value = formatDurationBrief(insights.totalSeconds),
                    sublabel = "Today: ${formatDurationBrief(insights.todaySeconds)}",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatKpiCard(
                    icon = Icons.Rounded.AutoStories,
                    label = "Pages Turned",
                    value = insights.totalPagesTurned.toString(),
                    sublabel = "${insights.topBooks.size} active title${if (insights.topBooks.size == 1) "" else "s"}",
                    modifier = Modifier.weight(1f)
                )
                val weeklyMinutes = insights.last7Days.sumOf { it.minutes }
                StatKpiCard(
                    icon = Icons.Rounded.BarChart,
                    label = "7-Day Pace",
                    value = "${weeklyMinutes}m",
                    sublabel = "${(weeklyMinutes / 7f).toInt()}m/day avg",
                    modifier = Modifier.weight(1f)
                )
            }

            // Weekly 7-Day Activity Chart
            LiquidGlassSurface(
                shape = RoundedCornerShape(22.dp),
                elevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Last 7 Days",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(palette.text)
                    )
                    WeeklyReadingBarChart(
                        days = insights.last7Days,
                        accentColor = Color(palette.accent),
                        trackColor = Color(palette.surfaceVariant),
                        labelColor = Color(palette.textSecondary)
                    )
                }
            }

            // Top Books Breakdown
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Time by Book",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(palette.text)
                )
                if (insights.topBooks.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(palette.surfaceVariant).copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Open a book and turn a few pages to start building your local reading timeline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(palette.textSecondary),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    val maxBookSec = insights.topBooks.maxOf { it.durationSeconds }.coerceAtLeast(1L)
                    insights.topBooks.forEach { stat ->
                        val ratio = (stat.durationSeconds.toFloat() / maxBookSec.toFloat()).coerceIn(0.06f, 1f)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(palette.surfaceVariant).copy(alpha = 0.42f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    Color(palette.textSecondary).copy(alpha = 0.12f),
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stat.bookTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(palette.text),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${formatDurationBrief(stat.durationSeconds)} · ${stat.pagesTurned}p",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(palette.accent)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(palette.surface))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(ratio)
                                            .height(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(palette.accent))
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

@Composable
private fun StatKpiCard(
    icon: ImageVector,
    label: String,
    value: String,
    sublabel: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalMochiPalette.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(palette.surfaceVariant).copy(alpha = 0.48f),
        modifier = modifier.border(
            1.dp,
            Color(palette.textSecondary).copy(alpha = 0.14f),
            RoundedCornerShape(18.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = Color(palette.accent), modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = Color(palette.textSecondary))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(palette.text)
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color(palette.textSecondary)
            )
        }
    }
}

@Composable
private fun WeeklyReadingBarChart(
    days: List<DailyReadingStat>,
    accentColor: Color,
    trackColor: Color,
    labelColor: Color
) {
    val safeDays = days.ifEmpty { emptyList() }
    val maxSec = safeDays.maxOfOrNull { it.durationSeconds }?.coerceAtLeast(60L) ?: 60L

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .testTag("weekly_reading_chart")
        ) {
            if (safeDays.isEmpty()) return@Canvas
            val count = safeDays.size
            val slotW = size.width / count.toFloat()
            val barW = (slotW * 0.44f).coerceAtLeast(12f)
            val corner = CornerRadius(barW / 2f, barW / 2f)

            safeDays.forEachIndexed { idx, stat ->
                val centerX = slotW * idx + slotW / 2f
                val left = centerX - barW / 2f

                // Track background
                drawRoundRect(
                    color = trackColor.copy(alpha = 0.45f),
                    topLeft = Offset(left, 6f),
                    size = Size(barW, size.height - 12f),
                    cornerRadius = corner
                )

                val frac = (stat.durationSeconds.toFloat() / maxSec.toFloat()).coerceIn(0f, 1f)
                val barH = if (stat.durationSeconds > 0L) {
                    ((size.height - 12f) * frac).coerceAtLeast(12f)
                } else 0f

                if (barH > 0f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(accentColor, accentColor.copy(alpha = 0.72f))
                        ),
                        topLeft = Offset(left, size.height - 6f - barH),
                        size = Size(barW, barH),
                        cornerRadius = corner
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            safeDays.forEach { stat ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (stat.durationSeconds in 1L..59L) "<1m" else "${stat.minutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (stat.durationSeconds > 0L) accentColor else labelColor.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stat.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor
                    )
                }
            }
        }
    }
}

private fun formatDurationBrief(totalSeconds: Long): String {
    val mins = (totalSeconds / 60L).toInt()
    val hours = mins / 60
    val remMins = mins % 60
    return when {
        totalSeconds in 1L..59L -> "${totalSeconds}s"
        hours > 0 -> "${hours}h ${remMins}m"
        else -> "${mins}m"
    }
}
