package com.nendo.argosy.core.media

/**
 * Deletes the transcoded sound effect files. Samples already loaded stay playable and
 * re-transcode on their next load.
 */
interface SfxCacheCleaner {

    suspend fun clearSfxCache()
}
