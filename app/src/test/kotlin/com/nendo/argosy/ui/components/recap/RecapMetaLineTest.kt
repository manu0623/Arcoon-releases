package com.nendo.argosy.ui.components.recap

import android.content.Context
import android.content.res.Resources
import com.nendo.argosy.R
import com.nendo.argosy.data.repository.MIN_DISPLAY_MS
import com.nendo.argosy.domain.model.RecapLastSession
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class RecapMetaLineTest {

    private val now: Instant = Instant.parse("2026-10-13T12:00:00Z")
    private val absolute: (Instant) -> String = { "ABSOLUTE" }

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk()
        val resources: Resources = mockk()
        every { context.resources } returns resources
        every { context.getString(any<Int>()) } answers { "res:${firstArg<Int>()}" }
        every { context.getString(any<Int>(), *anyVararg()) } answers {
            val rest = call.invocation.args.drop(1).flatten()
            if (rest.isEmpty()) "res:${firstArg<Int>()}" else "res:${firstArg<Int>()}:" + rest.joinToString(",")
        }
        every { resources.getQuantityString(any<Int>(), any<Int>(), *anyVararg()) } answers {
            val rest = call.invocation.args.drop(2).flatten()
            "plural:${firstArg<Int>()}:${secondArg<Int>()}:" + rest.joinToString(",")
        }
    }

    private fun List<Any?>.flatten(): List<Any?> =
        flatMap { if (it is Array<*>) it.toList() else listOf(it) }

    private fun res(id: Int, vararg args: Any): String =
        if (args.isEmpty()) "res:$id" else "res:$id:" + args.joinToString(",")

    private fun plural(id: Int, count: Int, vararg args: Any): String =
        "plural:$id:$count:" + args.joinToString(",")

    private fun endedAgo(days: Long = 0, hours: Long = 0, activeMs: Long = 0L): RecapLastSession {
        val end = now.minusSeconds(days * 86_400 + hours * 3_600)
        return RecapLastSession(
            start = end.minusMillis(activeMs),
            end = end,
            activePlayMs = activeMs,
            richPresence = null,
            daysSince = days
        )
    }

    private val today = res(R.string.util_formatters_relative_today)
    private val yesterday = res(R.string.util_formatters_relative_yesterday)

    @Test
    fun `a session that ended today reads as today with its length`() {
        val line = RecapMetaLine.of(context, endedAgo(hours = 2, activeMs = 45 * 60_000L), now, absolute = absolute)

        assertEquals(
            res(R.string.ui_launch_recap_meta_duration, today, res(R.string.util_formatters_playtime_minutes, 45)),
            line
        )
    }

    @Test
    fun `a session that ended yesterday reads as yesterday`() {
        val line = RecapMetaLine.of(context, endedAgo(days = 1, activeMs = 90 * 60_000L), now, absolute = absolute)

        assertEquals(
            res(R.string.ui_launch_recap_meta_duration, yesterday, res(R.string.util_formatters_playtime_hours_minutes, 1, 30)),
            line
        )
    }

    @Test
    fun `a session weeks ago reads in weeks`() {
        val line = RecapMetaLine.of(context, endedAgo(days = 15, activeMs = 0L), now, absolute = absolute)

        assertEquals(
            res(R.string.ui_launch_recap_meta, plural(R.plurals.util_formatters_relative_weeks_ago, 2, 2)),
            line
        )
    }

    @Test
    fun `a session a month or more ago reads as a date`() {
        val line = RecapMetaLine.of(context, endedAgo(days = 40, activeMs = 0L), now, absolute = absolute)

        assertEquals(res(R.string.ui_launch_recap_meta, "ABSOLUTE"), line)
    }

    @Test
    fun `a session shorter than the display minimum hides its length`() {
        val line = RecapMetaLine.of(context, endedAgo(days = 1, activeMs = MIN_DISPLAY_MS - 1), now, absolute = absolute)

        assertEquals(res(R.string.ui_launch_recap_meta, yesterday), line)
    }

    @Test
    fun `a session exactly at the display minimum shows its length`() {
        val line = RecapMetaLine.of(context, endedAgo(days = 1, activeMs = MIN_DISPLAY_MS), now, absolute = absolute)

        assertEquals(
            res(R.string.ui_launch_recap_meta_duration, yesterday, res(R.string.util_formatters_playtime_minutes, 1)),
            line
        )
    }

    @Test
    fun `the short-screen line folds in the unlock count`() {
        val session = endedAgo(days = 1, activeMs = 0L)
        val base = res(R.string.ui_launch_recap_meta, yesterday)

        assertEquals(
            plural(R.plurals.ui_launch_recap_meta_unlocked, 4, base, 4),
            RecapMetaLine.of(context, session, now, unlockCount = 4, absolute = absolute)
        )
    }

    @Test
    fun `no unlocks or no count leaves the line without a count`() {
        val session = endedAgo(days = 1, activeMs = 0L)
        val base = res(R.string.ui_launch_recap_meta, yesterday)

        assertEquals(base, RecapMetaLine.of(context, session, now, unlockCount = 0, absolute = absolute))
        assertEquals(base, RecapMetaLine.of(context, session, now, unlockCount = null, absolute = absolute))
    }
}
