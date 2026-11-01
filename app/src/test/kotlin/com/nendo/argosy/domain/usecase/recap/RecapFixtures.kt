package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.local.entity.PlaySessionEntity
import java.time.Duration
import java.time.Instant

internal fun session(
    gameId: Long,
    start: Instant,
    activeMinutes: Long,
    id: Long = 0,
    title: String = "Game $gameId",
    end: Instant = start.plus(Duration.ofMinutes(activeMinutes)),
    raGameId: Long? = null,
    richPresence: String? = null,
    ownerUserId: Long? = 7L
) = PlaySessionEntity(
    id = id,
    userId = null,
    gameId = gameId,
    igdbId = null,
    gameTitle = title,
    platformSlug = "snes",
    startTime = start,
    endTime = end,
    deviceId = "device",
    deviceManufacturer = "maker",
    deviceModel = "model",
    activePlayMs = Duration.ofMinutes(activeMinutes).toMillis(),
    ownerUserId = ownerUserId,
    raGameId = raGameId,
    richPresence = richPresence,
    richPresenceAt = richPresence?.let { start }
)
