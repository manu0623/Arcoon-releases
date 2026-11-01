package com.nendo.argosy.data.repository

import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.ra.RAWebClient
import com.nendo.argosy.data.remote.ra.RAWebCredentials
import com.nendo.argosy.data.remote.ra.RAWebResult
import com.nendo.argosy.data.remote.ra.RAWebUserSummary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RAWebApiKeyRepositoryTest {

    private val webClient: RAWebClient = mockk(relaxed = true)
    private val prefs: UserPreferencesRepository = mockk(relaxed = true) {
        every { userPreferences } returns flowOf(UserPreferences(raUsername = "Player"))
    }
    private val keyHealth = RAWebKeyHealth()
    private val repository = RAWebApiKeyRepository(prefs, webClient, keyHealth)

    @Test
    fun `an accepted key is checked once with the signed-in user and then stored`() = runTest {
        coEvery { webClient.userSummary(RAWebCredentials("Player", "abcd1234")) } returns
            RAWebResult.Success(RAWebUserSummary())

        assertEquals(RAWebApiKeyCheck.SAVED, repository.validateAndSave("  abcd1234 "))

        coVerify(exactly = 1) { webClient.userSummary(any()) }
        coVerify(exactly = 1) { prefs.setRAWebApiKey("abcd1234") }
    }

    @Test
    fun `a rejected key is not stored`() = runTest {
        coEvery { webClient.userSummary(any()) } returns RAWebResult.Rejected

        assertEquals(RAWebApiKeyCheck.REJECTED, repository.validateAndSave("abcd1234"))

        coVerify(exactly = 0) { prefs.setRAWebApiKey(any()) }
    }

    @Test
    fun `an unreachable check is reported apart from a rejection and stores nothing`() = runTest {
        coEvery { webClient.userSummary(any()) } returns RAWebResult.Failed("UnknownHostException")

        assertEquals(RAWebApiKeyCheck.UNREACHABLE, repository.validateAndSave("abcd1234"))

        coVerify(exactly = 0) { prefs.setRAWebApiKey(any()) }
    }

    @Test
    fun `a blank key is refused without a request`() = runTest {
        assertEquals(RAWebApiKeyCheck.EMPTY, repository.validateAndSave("   "))
        coVerify(exactly = 0) { webClient.userSummary(any()) }
    }

    @Test
    fun `without an RA user nothing is checked`() = runTest {
        every { prefs.userPreferences } returns flowOf(UserPreferences())
        assertEquals(RAWebApiKeyCheck.NOT_SIGNED_IN, repository.validateAndSave("abcd1234"))
        coVerify(exactly = 0) { webClient.userSummary(any()) }
    }

    @Test
    fun `clearing removes the stored key`() = runTest {
        repository.clear()
        coVerify(exactly = 1) { prefs.setRAWebApiKey(null) }
    }

    @Test
    fun `the masked value shows the last four characters only`() {
        assertEquals("1234", RAWebApiKeyRepository.lastCharsOf("abcdefgh1234"))
        assertNull(RAWebApiKeyRepository.lastCharsOf("1234"))
        assertNull(RAWebApiKeyRepository.lastCharsOf(null))
    }
}
