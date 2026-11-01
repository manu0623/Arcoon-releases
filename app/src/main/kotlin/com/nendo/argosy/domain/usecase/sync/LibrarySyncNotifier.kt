package com.nendo.argosy.domain.usecase.sync

/**
 * Tells the screens that show the library that a sync pass finished, so they reload what the
 * pass changed.
 */
interface LibrarySyncNotifier {

    suspend fun emitSyncCompleted()
}
