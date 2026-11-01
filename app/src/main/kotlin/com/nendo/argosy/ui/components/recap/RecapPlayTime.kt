package com.nendo.argosy.ui.components.recap

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.repository.MIN_DISPLAY_MS
import com.nendo.argosy.util.formatPlayTime
import java.time.Duration

object RecapPlayTime {
    /**
     * [activeMs] in letter units, with any positive time under [MIN_DISPLAY_MS] read as under a
     * minute.
     */
    fun of(context: Context, activeMs: Long): String =
        if (activeMs in 1 until MIN_DISPLAY_MS) {
            context.getString(R.string.ui_monthly_recap_playtime_under_minute)
        } else {
            formatPlayTime(context, Duration.ofMillis(activeMs).toMinutes().toInt())
        }
}
