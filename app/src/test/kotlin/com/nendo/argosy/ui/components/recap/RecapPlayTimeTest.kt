package com.nendo.argosy.ui.components.recap

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.repository.MIN_DISPLAY_MS
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RecapPlayTimeTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk()
        every { context.getString(any<Int>()) } answers { "res:${firstArg<Int>()}" }
        every { context.getString(any<Int>(), *anyVararg()) } answers {
            val rest = call.invocation.args.drop(1).flatMap { if (it is Array<*>) it.toList() else listOf(it) }
            if (rest.isEmpty()) "res:${firstArg<Int>()}" else "res:${firstArg<Int>()}:" + rest.joinToString(",")
        }
    }

    @Test
    fun `play under a minute reads as under a minute`() {
        val underMinute = "res:${R.string.ui_monthly_recap_playtime_under_minute}"

        assertEquals(underMinute, RecapPlayTime.of(context, 1L))
        assertEquals(underMinute, RecapPlayTime.of(context, MIN_DISPLAY_MS - 1))
    }

    @Test
    fun `a full minute reads in minutes`() {
        assertEquals(
            "res:${R.string.util_formatters_playtime_minutes}:1",
            RecapPlayTime.of(context, MIN_DISPLAY_MS)
        )
    }

    @Test
    fun `no play reads as zero minutes`() {
        assertEquals("res:${R.string.util_formatters_playtime_minutes}:0", RecapPlayTime.of(context, 0L))
    }
}
