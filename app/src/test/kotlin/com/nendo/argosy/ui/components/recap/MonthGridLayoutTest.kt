package com.nendo.argosy.ui.components.recap

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.YearMonth

class MonthGridLayoutTest {

    private fun leading(slots: List<Int?>): Int = slots.indexOfFirst { it != null }

    private fun trailing(slots: List<Int?>): Int = slots.size - 1 - slots.indexOfLast { it != null }

    private fun assertWholeMonth(month: YearMonth, slots: List<Int?>) {
        assertEquals((1..month.lengthOfMonth()).toList(), slots.filterNotNull())
        assertEquals(0, slots.size % MonthGridLayout.DAYS_IN_WEEK)
    }

    @Test
    fun `october 2026 monday first opens with three blanks over five rows`() {
        val october = YearMonth.of(2026, 10)
        val slots = MonthGridLayout.slots(october, DayOfWeek.MONDAY)

        assertWholeMonth(october, slots)
        assertEquals(3, leading(slots))
        assertEquals(1, trailing(slots))
        assertEquals(35, slots.size)
    }

    @Test
    fun `october 2026 sunday first opens with four blanks and fills five rows exactly`() {
        val october = YearMonth.of(2026, 10)
        val slots = MonthGridLayout.slots(october, DayOfWeek.SUNDAY)

        assertWholeMonth(october, slots)
        assertEquals(4, leading(slots))
        assertEquals(0, trailing(slots))
        assertEquals(35, slots.size)
    }

    @Test
    fun `february 2026 starting on a sunday takes five rows monday first`() {
        val february = YearMonth.of(2026, 2)
        val slots = MonthGridLayout.slots(february, DayOfWeek.MONDAY)

        assertWholeMonth(february, slots)
        assertEquals(6, leading(slots))
        assertEquals(1, trailing(slots))
        assertEquals(35, slots.size)
    }

    @Test
    fun `february 2026 sunday first fills exactly four rows`() {
        val february = YearMonth.of(2026, 2)
        val slots = MonthGridLayout.slots(february, DayOfWeek.SUNDAY)

        assertWholeMonth(february, slots)
        assertEquals(0, leading(slots))
        assertEquals(28, slots.size)
    }

    @Test
    fun `march 2026 starting on a sunday spills into a sixth row monday first`() {
        val march = YearMonth.of(2026, 3)
        val slots = MonthGridLayout.slots(march, DayOfWeek.MONDAY)

        assertWholeMonth(march, slots)
        assertEquals(6, leading(slots))
        assertEquals(42, slots.size)
    }

    @Test
    fun `leap february 2028 holds twenty nine days from a tuesday`() {
        val february = YearMonth.of(2028, 2)
        val mondayFirst = MonthGridLayout.slots(february, DayOfWeek.MONDAY)
        val sundayFirst = MonthGridLayout.slots(february, DayOfWeek.SUNDAY)

        assertWholeMonth(february, mondayFirst)
        assertEquals(1, leading(mondayFirst))
        assertEquals(35, mondayFirst.size)
        assertWholeMonth(february, sundayFirst)
        assertEquals(2, leading(sundayFirst))
        assertEquals(35, sundayFirst.size)
    }

    @Test
    fun `weekdays run seven days from the first day and wrap past sunday`() {
        assertEquals(
            listOf(
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            ),
            MonthGridLayout.weekdays(DayOfWeek.SATURDAY)
        )
    }

    @Test
    fun `cells divide the width between seven columns and six gaps`() {
        assertEquals(28f, MonthGridLayout.cellDp(availableDp = 220f, gapDp = 4f, minDp = 22f, maxDp = 32f), 0.001f)
    }

    @Test
    fun `cells stop at the maximum on wide columns and the minimum on narrow ones`() {
        assertEquals(32f, MonthGridLayout.cellDp(availableDp = 600f, gapDp = 4f, minDp = 22f, maxDp = 32f), 0.001f)
        assertEquals(22f, MonthGridLayout.cellDp(availableDp = 100f, gapDp = 4f, minDp = 22f, maxDp = 32f), 0.001f)
    }

    @Test
    fun `grid width is seven cells and six gaps`() {
        assertEquals(248f, MonthGridLayout.widthDp(cellDp = 32f, gapDp = 4f), 0.001f)
    }
}
