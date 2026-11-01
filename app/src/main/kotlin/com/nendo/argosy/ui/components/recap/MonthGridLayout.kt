package com.nendo.argosy.ui.components.recap

import java.time.DayOfWeek
import java.time.YearMonth

object MonthGridLayout {
    const val DAYS_IN_WEEK = 7

    fun weekdays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> =
        List(DAYS_IN_WEEK) { firstDayOfWeek.plus(it.toLong()) }

    /**
     * Day-of-month numbers for [month] in reading order, weeks starting on [firstDayOfWeek], with
     * null for the slots before the first and after the last day. The size is a whole number of
     * weeks.
     */
    fun slots(month: YearMonth, firstDayOfWeek: DayOfWeek): List<Int?> {
        val length = month.lengthOfMonth()
        val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value).mod(DAYS_IN_WEEK)
        val weeks = (leading + length + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK
        return List(weeks * DAYS_IN_WEEK) { slot -> (slot - leading + 1).takeIf { it in 1..length } }
    }

    fun cellDp(availableDp: Float, gapDp: Float, minDp: Float, maxDp: Float): Float =
        ((availableDp - gapDp * (DAYS_IN_WEEK - 1)) / DAYS_IN_WEEK).coerceIn(minDp, maxDp)

    fun widthDp(cellDp: Float, gapDp: Float): Float = cellDp * DAYS_IN_WEEK + gapDp * (DAYS_IN_WEEK - 1)
}
