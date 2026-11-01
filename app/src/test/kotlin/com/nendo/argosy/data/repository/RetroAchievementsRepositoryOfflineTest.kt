package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncStatus
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.ra.RAAchievementPatch
import com.nendo.argosy.data.remote.ra.RAApi
import com.nendo.argosy.data.remote.ra.RAApiProvider
import com.nendo.argosy.data.remote.ra.RAAwardResponse
import com.nendo.argosy.data.remote.ra.RAGameInfoResponse
import com.nendo.argosy.data.remote.ra.RAPatchData
import com.nendo.argosy.data.remote.ra.RAStartSessionResponse
import com.nendo.argosy.data.sync.AchievementPayload
import com.nendo.argosy.data.sync.SyncPayloadCodec
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Instant

class RetroAchievementsRepositoryOfflineTest {

    private val pendingSyncQueueDao = mockk<PendingSyncQueueDao>(relaxed = true)
    private val prefsRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val api = mockk<RAApi>()
    private val apiProvider = mockk<RAApiProvider>()
    private val patchCacheStore = mockk<RAPatchCacheStore>(relaxed = true)
    private val payloadCodec = SyncPayloadCodec(Moshi.Builder().build())

    private lateinit var repository: RetroAchievementsRepository

    private val patch = RAPatchData(
        id = 4242L,
        consoleId = 5,
        achievements = listOf(RAAchievementPatch(id = 1L, memAddr = "0xH0001=1", title = "First", points = 5))
    )

    @Before
    fun setUp() {
        val prefs = UserPreferences(rommUserId = 7L, raUsername = "player", raToken = "token")
        every { prefsRepository.userPreferences } returns flowOf(prefs)
        every { prefsRepository.preferences } returns flowOf(prefs)
        coEvery { apiProvider.api() } returns api
        repository = RetroAchievementsRepository(
            context = mockk(relaxed = true),
            pendingSyncQueueDao = pendingSyncQueueDao,
            achievementDao = mockk(relaxed = true),
            gameDao = mockk(relaxed = true),
            prefsRepository = prefsRepository,
            payloadCodec = payloadCodec,
            retroArchConfigParser = mockk(relaxed = true),
            richPresenceCapture = mockk(relaxed = true),
            apiProvider = apiProvider,
            patchCacheStore = patchCacheStore
        )
    }

    private fun stubPatchResponse(response: Response<RAGameInfoResponse>) {
        coEvery { api.getGameInfo(any(), any(), any(), any(), any()) } returns response
    }

    private fun httpError(code: Int, json: String = "") =
        json.toResponseBody("application/json".toMediaType())
            .let { Response.error<RAAwardResponse>(code, it) }

    private fun exhaustedRow(id: Long = 5L) = PendingSyncQueueEntity(
        id = id,
        gameId = 1L,
        rommId = 0L,
        syncType = SyncType.ACHIEVEMENT,
        priority = SyncPriority.PROPERTY,
        payloadJson = payloadCodec.encode(AchievementPayload(achievementRaId = 9L, forHardcoreMode = false, earnedAt = 1L)),
        status = SyncStatus.FAILED,
        retryCount = 5,
        maxRetries = 3,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        ownerUserId = 7L
    )

    @Test
    fun `startsession sends the hardcore flag with the verified hash, as rcheevos does`() = runTest {
        coEvery {
            api.startSession(any(), any(), any(), any(), any(), any(), any())
        } returns Response.success(RAStartSessionResponse(success = true))

        repository.startSession(4242L, hardcore = true, gameHash = "abc123")

        coVerify {
            api.startSession(
                request = "startsession",
                username = "player",
                token = "token",
                gameId = 4242L,
                hardcore = 1,
                gameHash = "abc123",
                clientVersion = RAApi.RCHEEVOS_VERSION
            )
        }
    }

    @Test
    fun `startsession without a verified hash sends neither h nor m`() = runTest {
        coEvery {
            api.startSession(any(), any(), any(), any(), any(), any(), any())
        } returns Response.success(RAStartSessionResponse(success = true))

        repository.startSession(4242L, hardcore = true, gameHash = null)

        coVerify {
            api.startSession(
                request = "startsession",
                username = "player",
                token = "token",
                gameId = 4242L,
                hardcore = null,
                gameHash = null,
                clientVersion = RAApi.RCHEEVOS_VERSION
            )
        }
    }

    private fun stubAward(response: Response<RAAwardResponse>) {
        coEvery { pendingSyncQueueDao.getUnsubmittedBySyncType(SyncType.ACHIEVEMENT) } returns listOf(exhaustedRow())
        coEvery { api.awardAchievement(any(), any(), any(), any(), any(), any()) } returns response
    }

    @Test
    fun `a successful patch fetch overwrites the cached definitions for the active account`() = runTest {
        stubPatchResponse(Response.success(RAGameInfoResponse(success = true, patchData = patch)))

        val result = repository.fetchPatchData(4242L)

        assertEquals(RAPatchFetchResult.Fetched(patch), result)
        coVerify(exactly = 1) { patchCacheStore.write(4242L, 7L, patch, any()) }
    }

    @Test
    fun `throttling, auth refusals, server errors and no connection ask to retry and leave the cache alone`() = runTest {
        listOf(429, 401, 403, 500, 503).forEach { code ->
            stubPatchResponse(Response.error(code, "".toResponseBody(null)))
            assertEquals("HTTP $code", RAPatchFetchResult.RetryLater, repository.fetchPatchData(4242L))
        }
        coEvery { api.getGameInfo(any(), any(), any(), any(), any()) } throws IOException("offline")
        assertEquals(RAPatchFetchResult.RetryLater, repository.fetchPatchData(4242L))

        coVerify(exactly = 0) { patchCacheStore.write(any(), any(), any(), any()) }
    }

    @Test
    fun `a refused game is unavailable rather than retried`() = runTest {
        stubPatchResponse(Response.error(404, "".toResponseBody(null)))
        assertEquals(RAPatchFetchResult.Unavailable, repository.fetchPatchData(4242L))

        stubPatchResponse(Response.success(RAGameInfoResponse(success = false, error = "Unknown game")))
        assertEquals(RAPatchFetchResult.Unavailable, repository.fetchPatchData(4242L))
    }

    @Test
    fun `an unlock past its retry ceiling is still submitted and survives another network failure`() = runTest {
        coEvery { pendingSyncQueueDao.getUnsubmittedBySyncType(SyncType.ACHIEVEMENT) } returns listOf(exhaustedRow())
        coEvery { api.awardAchievement(any(), any(), any(), any(), any(), any()) } throws IOException("offline")

        repository.submitPendingAchievements()

        coVerify(exactly = 1) { api.awardAchievement(any(), "player", "token", 9L, 0, any()) }
        coVerify(exactly = 1) { pendingSyncQueueDao.recordRetryableFailure(5L, any(), any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.markFailed(any(), any(), any()) }
    }

    @Test
    fun `throttling and server errors keep the unlock queued`() = runTest {
        listOf(429, 502, 503).forEach { code ->
            stubAward(httpError(code))
            repository.submitPendingAchievements()
        }

        coVerify(exactly = 3) { pendingSyncQueueDao.recordRetryableFailure(5L, any(), any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(any()) }
    }

    @Test
    fun `an auth refusal keeps the unlock queued for the next login`() = runTest {
        stubAward(httpError(401, """{"Success":false,"Error":"Invalid token"}"""))

        repository.submitPendingAchievements()

        coVerify(exactly = 1) { pendingSyncQueueDao.recordRetryableFailure(5L, "Invalid token", any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(any()) }
    }

    @Test
    fun `an auth refusal in the body keeps the unlock queued`() = runTest {
        stubAward(
            Response.success(
                RAAwardResponse(success = false, error = "The access token has expired.", code = "expired_token")
            )
        )
        repository.submitPendingAchievements()

        stubAward(httpError(422, """{"Success":false,"Error":"Credentials invalid (0)","Code":"invalid_credentials"}"""))
        repository.submitPendingAchievements()

        coVerify(exactly = 2) { pendingSyncQueueDao.recordRetryableFailure(5L, any(), any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(any()) }
    }

    @Test
    fun `a definitive server rejection removes the unlock`() = runTest {
        stubAward(Response.success(RAAwardResponse(success = false, error = "Achievement not found")))
        repository.submitPendingAchievements()

        stubAward(httpError(422, """{"Success":false,"Error":"Achievement not found"}"""))
        repository.submitPendingAchievements()

        coVerify(exactly = 2) { pendingSyncQueueDao.deleteById(5L) }
        coVerify(exactly = 0) { pendingSyncQueueDao.recordRetryableFailure(any(), any(), any()) }
    }

    @Test
    fun `an accepted or already-awarded unlock leaves the queue`() = runTest {
        stubAward(Response.success(RAAwardResponse(success = true)))
        assertEquals(1, repository.submitPendingAchievements())

        stubAward(Response.success(RAAwardResponse(success = false, error = "User already has this achievement")))
        assertEquals(1, repository.submitPendingAchievements())

        coVerify(exactly = 2) { pendingSyncQueueDao.deleteById(5L) }
    }
}
