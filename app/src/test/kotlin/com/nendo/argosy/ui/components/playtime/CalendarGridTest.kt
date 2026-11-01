package com.nendo.argosy.ui.components.playtime

import com.nendo.argosy.data.model.PlayDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarGridTest {

    private fun daysBetween(first: LocalDate, last: LocalDate, played: Set<LocalDate> = emptySet()): List<PlayDay> =
        generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(last) }
            .map { PlayDay(it, if (it in played) 60_000L else 0L) }
            .toList()

    @Test
    fun `a thirty one day month starting on a thursday fills five weeks with every day`() {
        val october = YearMonth.of(2026, 10)
        val days = daysBetween(
            october.atDay(1),
            october.atEndOfMonth(),
            setOf(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 13))
        )

        val grid = buildCalendarGrid(days)

        assertEquals(5, grid.weeks)
        assertEquals(31, grid.rows.flatten().count { it != null })
        assertEquals((0 until 31).toList(), grid.rows.flatten().filterNotNull().sorted())
        assertNull(grid.rows[0][0])
        assertNull(grid.rows[2][0])
        assertEquals(0, grid.rows[3][0])
        assertEquals(30, grid.rows[5][4])
        assertNull(grid.rows[6][4])
    }

    @Test
    fun `a single month is headed by that month alone`() {
        val october = YearMonth.of(2026, 10)
        val grid = buildCalendarGrid(daysBetween(october.atDay(1), october.atEndOfMonth()))

        assertEquals(mapOf(0 to october), grid.monthStarts)
    }

    @Test
    fun `a range ending mid week names no month that has no day in it`() {
        val grid = buildCalendarGrid(daysBetween(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 29)))

        assertEquals(mapOf(0 to YearMonth.of(2026, 9)), grid.monthStarts)
    }

    @Test
    fun `a range spanning months names each month at the first week it holds the sunday of`() {
        val grid = buildCalendarGrid(daysBetween(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 10, 13)))

        assertEquals(7, grid.weeks)
        assertEquals(mapOf(0 to YearMonth.of(2026, 9), 4 to YearMonth.of(2026, 10)), grid.monthStarts)
    }
}
