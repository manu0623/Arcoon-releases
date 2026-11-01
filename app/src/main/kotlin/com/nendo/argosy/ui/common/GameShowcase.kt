package com.nendo.argosy.ui.common

import android.content.Context
import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.local.dao.PlatformShowcaseStats
import com.nendo.argosy.ui.dualscreen.CompanionFact
import com.nendo.argosy.ui.dualscreen.PresentationSlot

private const val MINUTES_PER_HOUR = 60

/**
 * A set of games described for the presentation screen: the covers it draws, the span of years
 * they cover and the counts worth reading at a glance.
 *
 * [fallbackCount] names how many games there are when [stats] has not been read, so a showcase
 * still says how large its set is.
 */
fun gameShowcase(
    context: Context,
    name: String,
    coverPaths: List<String>,
    stats: PlatformShowcaseStats?,
    fallbackCount: Int = 0
): PresentationSlot.PlatformShowcase = PresentationSlot.PlatformShowcase(
    name = name,
    yearSpan = showcaseYearSpan(stats?.earliestYear, stats?.latestYear),
    coverPaths = coverPaths,
    facts = buildList {
        add(
            fact(
                context,
                R.string.library_showcase_fact_games,
                (stats?.gameCount ?: fallbackCount).toString()
            )
        )
        stats ?: return@buildList
        add(fact(context, R.string.library_showcase_fact_installed, stats.installedCount.toString()))
        if (stats.achievementsTotal > 0) {
            add(
                fact(
                    context,
                    R.string.library_showcase_fact_achievements,
                    "${stats.achievementsEarned} / ${stats.achievementsTotal}"
                )
            )
        }
        if (stats.playTimeMinutes > 0) {
            add(
                fact(
                    context,
                    R.string.library_showcase_fact_play_time,
                    formatShowcasePlayTime(context, stats.playTimeMinutes)
                )
            )
        }
    }
)

fun showcaseYearSpan(from: Int?, to: Int?): String? = when {
    from == null || to == null -> null
    from == to -> from.toString()
    else -> "$from-$to"
}

fun formatShowcasePlayTime(context: Context, minutes: Int): String = when {
    minutes < MINUTES_PER_HOUR -> context.getString(
        R.string.library_showcase_play_time_minutes, minutes
    )
    else -> context.getString(
        R.string.library_showcase_play_time_hours, minutes / MINUTES_PER_HOUR
    )
}

private fun fact(context: Context, @StringRes label: Int, value: String) =
    CompanionFact(context.getString(label), value)
