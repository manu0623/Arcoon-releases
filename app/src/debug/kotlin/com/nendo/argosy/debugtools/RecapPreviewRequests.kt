package com.nendo.argosy.debugtools

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-only set of games whose every launch shows the pre-launch recap regardless of the setting
 * and the seven-day rule, until [clear] removes them. Persisted in its own preferences file.
 */
@Singleton
class RecapPreviewRequests @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    suspend fun request(gameId: Long): Boolean = withContext(Dispatchers.IO) {
        synchronized(this@RecapPreviewRequests) {
            prefs.edit().putStringSet(KEY_GAME_IDS, pending() + gameId.toString()).commit()
        }
    }

    suspend fun consume(gameId: Long): Boolean = withContext(Dispatchers.IO) {
        synchronized(this@RecapPreviewRequests) { gameId.toString() in pending() }
    }

    suspend fun clear(gameId: Long?): Boolean = withContext(Dispatchers.IO) {
        synchronized(this@RecapPreviewRequests) {
            val remaining = if (gameId == null) emptySet() else pending() - gameId.toString()
            prefs.edit().putStringSet(KEY_GAME_IDS, remaining).commit()
        }
    }

    private fun pending(): Set<String> = prefs.getStringSet(KEY_GAME_IDS, emptySet()).orEmpty().toSet()

    private companion object {
        const val PREFS_NAME = "debug_recap_preview"
        const val KEY_GAME_IDS = "game_ids"
    }
}
