package com.nendo.argosy.ui.components.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.model.PlayDay
import com.nendo.argosy.ui.common.ChartPalette
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * [month] as a weekday-headed grid, one square per day shaded by its play in [days], with a ring
 * on the day at [busiestIndex] into [days]. Slots outside the month draw near the background.
 * Weeks start on the default locale's first day.
 */
@Composable
fun MonthPlayGrid(
    month: YearMonth,
    days: List<PlayDay>,
    busiestIndex: Int?,
    cellSize: Dp,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val locale = Locale.getDefault()
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weekdayLabels = remember(locale, firstDayOfWeek) {
        MonthGridLayout.weekdays(firstDayOfWeek).map { it.getDisplayName(TextStyle.NARROW_STANDALONE, locale) }
    }
    val weeks = remember(month, firstDayOfWeek) {
        MonthGridLayout.slots(month, firstDayOfWeek).chunked(MonthGridLayout.DAYS_IN_WEEK)
    }
    val levels = ComponentDefaults.PlayTimeChart.calendarLevels
    val indexByDate = remember(days) { days.indices.associateBy { days[it].date } }
    val dayLevels = remember(days) {
        val max = days.maxOfOrNull { it.activeMs } ?: 0L
        days.map { ChartPalette.levelOf(it.activeMs, max, levels) }
    }
    val accent = remember(theme.isDark) { ChartPalette.series(theme.isDark).first() }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = ComponentDefaults.UnlockRecap.calendarEmptyFillAlpha)
    val outside = MaterialTheme.colorScheme.onSurface.copy(alpha = ComponentDefaults.UnlockRecap.calendarOutsideFillAlpha)
    val gap = ComponentDefaults.UnlockRecap.calendarGapDp.dp
    val shape = RoundedCornerShape(Dimens.radiusSm)
    val textStyle = MaterialTheme.typography.labelLarge

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = textStyle,
                    color = theme.textDim,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.width(cellSize)
                )
            }
        }
        weeks.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                week.forEach { day ->
                    val index = day?.let { indexByDate[month.atDay(it)] }
                    val level = index?.let { dayLevels[it] } ?: 0
                    DayCell(
                        fill = if (day == null) {
                            outside
                        } else {
                            ChartPalette.sequential(track, accent, level, levels, ComponentDefaults.PlayTimeChart.calendarRampFloor)
                        },
                        ringColor = accent.takeIf { index != null && index == busiestIndex },
                        size = cellSize,
                        shape = shape
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(fill: Color, ringColor: Color?, size: Dp, shape: Shape) {
    Box(
        modifier = Modifier
            .size(size)
            .then(if (ringColor != null) Modifier.border(Dimens.borderMedium, ringColor, shape) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(if (ringColor != null) ComponentDefaults.UnlockRecap.calendarRingInsetDp.dp else 0.dp)
                .clip(shape)
                .background(fill)
        )
    }
}
