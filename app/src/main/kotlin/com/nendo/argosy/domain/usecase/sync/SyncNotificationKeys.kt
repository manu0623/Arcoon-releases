package com.nendo.argosy.domain.usecase.sync

/**
 * Keys the library sync notifications are raised under. Stored tokens, not labels: a screen mutes
 * them by value while it shows the same progress itself, so renaming one silently stops that.
 */
object SyncNotificationKeys {
    const val LIBRARY = "romm-sync"
    const val PLATFORM = "romm-platform-sync"

    val ALL: Set<String> = setOf(LIBRARY, PLATFORM)
}
