package com.nendo.argosy.ui.components.recap

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.repository.MIN_DISPLAY_MS
import com.nendo.argosy.domain.model.RecapLastSession
import com.nendo.argosy.util.formatMonthDay
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatRelativeTime
import java.time.Duration
import java.time.Instant

object RecapMetaLine {
    /**
     * The pre-launch recap's line about [session]: when it ended relative to [now], its length when
     * at least [MIN_DISPLAY_MS], and [unlockCount] when given and positive.
     */
    fun of(
        context: Context,
        session: RecapLastSession,
        now: Instant,
        unlockCount: Int? = null,
        absolute: (Instant) -> String = ::formatMonthDay
    ): String {
        val relative = formatRelativeTime(context, session.end, now, absolute)
        val line = if (session.activePlayMs >= MIN_DISPLAY_MS) {
            context.getString(
                R.string.ui_launch_recap_meta_duration,
                relative,
                formatPlayTime(context, Duration.ofMillis(session.activePlayMs).toMinutes().toInt())
            )
        } else {
            context.getString(R.string.ui_launch_recap_meta, relative)
        }
        if (unlockCount == null || unlockCount <= 0) return line
        return context.resources.getQuantityString(R.plurals.ui_launch_recap_meta_unlocked, unlockCount, line, unlockCount)
    }
}
