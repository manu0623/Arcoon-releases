package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.domain.model.LaunchRecap

/**
 * A pending request to show a game's pre-launch recap on its next launch, regardless of the
 * setting and the seven-day rule. [consume] answers true at most once per request; [recapFor] then
 * builds what that launch shows, or null when there is nothing to show.
 */
interface PreLaunchRecapOverride {
    suspend fun consume(gameId: Long): Boolean
    suspend fun recapFor(gameId: Long, gameTitle: String): LaunchRecap?
}
