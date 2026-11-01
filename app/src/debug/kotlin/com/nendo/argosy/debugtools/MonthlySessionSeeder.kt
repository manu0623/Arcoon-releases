package com.nendo.argosy.debugtools

import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Debug-only play sessions for one month, written as if from another device so stats surfaces
 * have data to draw. Every row carries [SEED_DEVICE_ID] and a negative RomM session id, so the
 * uploader never sends them and [clear] finds them all.
 */
@Singleton
class MonthlySessionSeeder @Inject constructor(
    private val database: ALauncherDatabase,
    private val preferencesRepository: UserPreferencesRepository
) {

    suspend fun seed(month: YearMonth): Int = withContext(Dispatchers.IO) {
        val owner = preferencesRepository.userPreferences.first().rommUserId
        val games = database.openHelper.readableDatabase.query(
            "SELECT id, title, platformSlug, igdbId FROM games WHERE localPath IS NOT NULL ORDER BY lastPlayed DESC LIMIT $GAME_COUNT"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        SeedGame(
                            id = cursor.getLong(0),
                            title = cursor.getString(1),
                            platformSlug = cursor.getString(2),
                            igdbId = if (cursor.isNull(3)) null else cursor.getLong(3)
                        )
                    )
                }
            }
        }
        if (games.isEmpty()) return@withContext 0

        val random = Random(month.hashCode())
        val zone = ZoneId.systemDefault()
        val dao = database.playSessionDao()
        var nextRommId = -(System.currentTimeMillis() / 1000)
        var inserted = 0
        for (day in 1..month.lengthOfMonth()) {
            if (random.nextFloat() > PLAY_CHANCE) continue
            repeat(1 + random.nextInt(MAX_SESSIONS_PER_DAY)) {
                val game = games[minOf(games.lastIndex, (random.nextFloat() * random.nextFloat() * games.size).toInt())]
                val start = month.atDay(day).atTime(SESSION_START_HOURS.random(random), random.nextInt(60)).atZone(zone).toInstant()
                val minutes = MIN_MINUTES + random.nextInt(MAX_MINUTES - MIN_MINUTES)
                dao.insert(
                    PlaySessionEntity(
                        userId = null,
                        gameId = game.id,
                        igdbId = game.igdbId,
                        gameTitle = game.title,
                        platformSlug = game.platformSlug,
                        startTime = start,
                        endTime = start.plusSeconds(minutes * 60L),
                        deviceId = SEED_DEVICE_ID,
                        deviceManufacturer = SEED_DEVICE_NAME,
                        deviceModel = SEED_DEVICE_NAME,
                        activePlayMs = minutes * 60_000L,
                        ownerUserId = owner,
                        rommSessionId = nextRommId--
                    )
                )
                inserted++
            }
        }
        inserted
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        database.openHelper.writableDatabase.execSQL("DELETE FROM play_sessions WHERE deviceId = '$SEED_DEVICE_ID'")
    }

    private data class SeedGame(val id: Long, val title: String, val platformSlug: String, val igdbId: Long?)

    private companion object {
        const val SEED_DEVICE_ID = "debug-seed"
        const val SEED_DEVICE_NAME = "Debug Seed"
        const val GAME_COUNT = 5
        const val PLAY_CHANCE = 0.55f
        const val MAX_SESSIONS_PER_DAY = 3
        const val MIN_MINUTES = 8
        const val MAX_MINUTES = 140
        val SESSION_START_HOURS = listOf(8, 12, 18, 19, 20, 21, 22)
    }
}
