package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.SessionPreferencesRepository
import com.nendo.argosy.data.remote.ra.RAWebUserSummary
import com.nendo.argosy.data.repository.RaMetadataSource
import com.nendo.argosy.data.repository.RetroAchievementsRepository.GameUnlocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SessionRecapFactsRecorderTest {

    private val start = Instant.parse("2026-10-01T10:00:00Z")
    private val end = Instant.parse("2026-10-01T12:00:00Z")
    private val external = "org.ppsspp.ppsspp"

    private fun summary(
        message: String? = "Exploring Hyrule",
        date: String? = "2026-10-01T11:30:00+00:00",
        gameId: Long? = 55L
    ) = RAWebUserSummary(richPresenceMsg = message, richPresenceMsgDate = date, lastGameId = gameId)

    @Test
    fun `a line for this game dated inside the session is accepted`() {
        val accepted = SessionRecapFactsRecorder.acceptSummary(summary(), 55L, start, end)
        assertEquals("Exploring Hyrule", accepted!!.message)
        assertEquals(Instant.parse("2026-10-01T11:30:00Z"), accepted.at)
    }

    @Test
    fun `a line for another game is rejected`() {
        assertNull(SessionRecapFactsRecorder.acceptSummary(summary(gameId = 56L), 55L, start, end))
    }

    @Test
    fun `a line with no game is rejected`() {
        assertNull(SessionRecapFactsRecorder.acceptSummary(summary(gameId = null), 55L, start, end))
    }

    @Test
    fun `a line dated before the session is rejected`() {
        assertNull(SessionRecapFactsRecorder.acceptSummary(summary(date = "2026-10-01T09:59:59Z"), 55L, start, end))
    }

    @Test
    fun `a line dated after the session is rejected`() {
        assertNull(SessionRecapFactsRecorder.acceptSummary(summary(date = "2026-10-01T12:00:01Z"), 55L, start, end))
    }

    @Test
    fun `a line dated at either end of the session is accepted`() {
        assertEquals(start, SessionRecapFactsRecorder.acceptSummary(summary(date = "2026-10-01T10:00:00Z"), 55L, start, end)!!.at)
        assertEquals(end, SessionRecapFactsRecorder.acceptSummary(summary(date = "2026-10-01T12:00:00Z"), 55L, start, end)!!.at)
    }

    @Test
    fun `a line with no date or an unreadable date is rejected`() {
        assertNull(SessionRecapFactsRecorder.acceptSummary(summary(date = null), 55L, start, end))
        assertNull(SessionRecapFactsRecorder.acceptSummary(summary(date = "whenever"), 55L, start, end))
    }

    @Test
    fun `a blank line is rejected`() {
        assertNull(SessionRecapFactsRecorder.acceptSummary(summary(message = "  "), 55L, start, end))
        assertNull(SessionRecapFactsRecorder.acceptSummary(summary(message = null), 55L, start, end))
    }

    @Test
    fun `the capture hands back a line recorded inside the session once`() {
        val capture = RichPresenceCapture()
        capture.record(55L, "Level 1", start.plusSeconds(60))
        assertEquals("Level 1", capture.take(start, end)!!.message)
        assertNull(capture.take(start, end))
    }

    @Test
    fun `the capture drops a line recorded before the session`() {
        val capture = RichPresenceCapture()
        capture.record(55L, "Old", start.minusSeconds(60))
        assertNull(capture.take(start, end))
    }

    @Test
    fun `clearing the capture at session start forgets the previous session's line`() {
        val capture = RichPresenceCapture()
        capture.record(55L, "Old", start.plusSeconds(10))
        capture.clear()
        assertNull(capture.take(start, end))
    }

    @Test
    fun `the capture ignores a blank line`() {
        val capture = RichPresenceCapture()
        capture.record(55L, "Kept", start.plusSeconds(10))
        capture.record(55L, " ", start.plusSeconds(20))
        assertEquals("Kept", capture.take(start, end)!!.message)
    }

    private fun game(id: Long?): GameEntity = mockk {
        every { effectiveRaId } returns id
        every { raId } returns id
    }

    private class StoredSnapshot {
        var value: String? = null
        val prefs: SessionPreferencesRepository = mockk {
            coEvery { setRaLaunchSnapshot(any()) } answers { this@StoredSnapshot.value = firstArg() }
            coEvery { getRaLaunchSnapshot() } answers { this@StoredSnapshot.value }
        }
    }

    private fun recorder(
        capture: RichPresenceCapture = RichPresenceCapture(),
        source: RaMetadataSource = mockk(relaxed = true),
        dao: PlaySessionDao = mockk(relaxed = true),
        gameDao: GameDao = mockk(),
        stored: StoredSnapshot = StoredSnapshot()
    ) = SessionRecapFactsRecorder(capture, dagger.Lazy { source }, dao, gameDao, stored.prefs)

    private fun unlocks(softcore: Set<Long>, hardcore: Set<Long>) =
        GameUnlocks(unlockedIds = softcore, hardcoreUnlockedIds = hardcore)

    @Test
    fun `a built-in session carries the captured line`() {
        val capture = RichPresenceCapture()
        val recorder = recorder(capture = capture)
        capture.record(55L, "Boss fight", start.plusSeconds(600))
        val facts = recorder.factsFor(game(55L), EmulatorRegistry.BUILTIN_PACKAGE, start, end)
        assertEquals(55L, facts.raGameId)
        assertEquals("Boss fight", facts.richPresence)
        assertEquals(start.plusSeconds(600), facts.richPresenceAt)
    }

    @Test
    fun `a built-in session drops a captured line for another game`() {
        val capture = RichPresenceCapture()
        val recorder = recorder(capture = capture)
        capture.record(99L, "Elsewhere", start.plusSeconds(600))
        val facts = recorder.factsFor(game(55L), EmulatorRegistry.BUILTIN_PACKAGE, start, end)
        assertEquals(55L, facts.raGameId)
        assertNull(facts.richPresence)
    }

    @Test
    fun `an external session carries the ra id and no captured line`() {
        val capture = RichPresenceCapture()
        val recorder = recorder(capture = capture)
        capture.record(55L, "Built-in line", start.plusSeconds(600))
        val facts = recorder.factsFor(game(55L), external, start, end)
        assertEquals(55L, facts.raGameId)
        assertNull(facts.richPresence)
    }

    @Test
    fun `an external session stores the source's line when it describes the session`() = runTest {
        val source = mockk<RaMetadataSource>(relaxed = true) { coEvery { userSummary() } returns summary() }
        val dao = mockk<PlaySessionDao>(relaxed = true)
        val scope = TestScope(testScheduler)

        recorder(source = source, dao = dao)
            .onSessionRecorded(scope, 3L, 1L, external, SessionRecapFacts(55L, null, null), start, end)
        scope.advanceUntilIdle()

        coVerify(exactly = 1) { dao.setRichPresence(3L, "Exploring Hyrule", Instant.parse("2026-10-01T11:30:00Z")) }
    }

    @Test
    fun `an external session leaves the row alone when the source has nothing`() = runTest {
        val source = mockk<RaMetadataSource>(relaxed = true) { coEvery { userSummary() } returns null }
        val dao = mockk<PlaySessionDao>(relaxed = true)
        val scope = TestScope(testScheduler)

        recorder(source = source, dao = dao)
            .onSessionRecorded(scope, 3L, 1L, external, SessionRecapFacts(55L, null, null), start, end)
        scope.advanceUntilIdle()

        coVerify(exactly = 0) { dao.setRichPresence(any(), any(), any()) }
    }

    @Test
    fun `an external session leaves the row alone when the source throws`() = runTest {
        val source = mockk<RaMetadataSource>(relaxed = true) { coEvery { userSummary() } throws IllegalStateException("boom") }
        val dao = mockk<PlaySessionDao>(relaxed = true)
        val scope = TestScope(testScheduler)

        recorder(source = source, dao = dao)
            .onSessionRecorded(scope, 3L, 1L, external, SessionRecapFacts(55L, null, null), start, end)
        scope.advanceUntilIdle()

        coVerify(exactly = 0) { dao.setRichPresence(any(), any(), any()) }
    }

    @Test
    fun `a built-in session never asks for rich presence but still warms the web cache`() = runTest {
        val source = mockk<RaMetadataSource>(relaxed = true)
        val scope = TestScope(testScheduler)

        recorder(source = source)
            .onSessionRecorded(scope, 3L, 1L, EmulatorRegistry.BUILTIN_PACKAGE, SessionRecapFacts(55L, null, null), start, end)
        scope.advanceUntilIdle()

        coVerify(exactly = 0) { source.userSummary() }
        coVerify(exactly = 1) { source.warmGameProgress(55L, end) }
    }

    @Test
    fun `an external session with no dated source stores the launch-to-end snapshot difference`() = runTest {
        val source = mockk<RaMetadataSource>(relaxed = true)
        coEvery { source.needsUnlockSnapshots(55L) } returns true
        coEvery { source.unlockSnapshot(55L) } returnsMany listOf(
            unlocks(softcore = setOf(1L), hardcore = emptySet()),
            unlocks(softcore = setOf(1L, 2L, 3L), hardcore = setOf(3L))
        )
        val gameDao = mockk<GameDao> { coEvery { getById(1L) } returns game(55L) }
        val dao = mockk<PlaySessionDao>(relaxed = true)
        val scope = TestScope(testScheduler)
        val recorder = recorder(source = source, dao = dao, gameDao = gameDao)

        recorder.onSessionStarted(scope, 1L, external, start)
        scope.advanceUntilIdle()
        recorder.onSessionRecorded(scope, 3L, 1L, external, SessionRecapFacts(55L, null, null), start, end)
        scope.advanceUntilIdle()

        coVerify(exactly = 1) { dao.setRaSnapshotUnlocks(3L, "s:2,h:3") }
    }

    @Test
    fun `an external session takes no snapshots when a dated source covers the game`() = runTest {
        val source = mockk<RaMetadataSource>(relaxed = true)
        coEvery { source.needsUnlockSnapshots(55L) } returns false
        val gameDao = mockk<GameDao> { coEvery { getById(1L) } returns game(55L) }
        val dao = mockk<PlaySessionDao>(relaxed = true)
        val scope = TestScope(testScheduler)
        val recorder = recorder(source = source, dao = dao, gameDao = gameDao)

        recorder.onSessionStarted(scope, 1L, external, start)
        scope.advanceUntilIdle()
        recorder.onSessionRecorded(scope, 3L, 1L, external, SessionRecapFacts(55L, null, null), start, end)
        scope.advanceUntilIdle()

        coVerify(exactly = 0) { source.unlockSnapshot(any()) }
        coVerify(exactly = 0) { dao.setRaSnapshotUnlocks(any(), any()) }
    }

    @Test
    fun `a session whose launch snapshot failed stores no unlocks rather than everything`() = runTest {
        val source = mockk<RaMetadataSource>(relaxed = true)
        coEvery { source.needsUnlockSnapshots(55L) } returns true
        coEvery { source.unlockSnapshot(55L) } returnsMany listOf(
            null,
            unlocks(softcore = setOf(1L, 2L), hardcore = setOf(2L))
        )
        val gameDao = mockk<GameDao> { coEvery { getById(1L) } returns game(55L) }
        val dao = mockk<PlaySessionDao>(relaxed = true)
        val scope = TestScope(testScheduler)
        val recorder = recorder(source = source, dao = dao, gameDao = gameDao)

        recorder.onSessionStarted(scope, 1L, external, start)
        scope.advanceUntilIdle()
        recorder.onSessionRecorded(scope, 3L, 1L, external, SessionRecapFacts(55L, null, null), start, end)
        scope.advanceUntilIdle()

        coVerify(exactly = 0) { dao.setRaSnapshotUnlocks(any(), any()) }
    }

    @Test
    fun `a session recorded with no launch snapshot never takes an end snapshot`() = runTest {
        val source = mockk<RaMetadataSource>(relaxed = true)
        val dao = mockk<PlaySessionDao>(relaxed = true)
        val scope = TestScope(testScheduler)

        recorder(source = source, dao = dao)
            .onSessionRecorded(scope, 3L, 1L, external, SessionRecapFacts(55L, null, null), start, end)
        scope.advanceUntilIdle()

        coVerify(exactly = 0) { source.unlockSnapshot(any()) }
        coVerify(exactly = 0) { dao.setRaSnapshotUnlocks(any(), any()) }
    }

    @Test
    fun `the launch snapshot is stored and survives a new recorder after process death`() = runTest {
        val source = mockk<RaMetadataSource>(relaxed = true)
        coEvery { source.needsUnlockSnapshots(55L) } returns true
        coEvery { source.unlockSnapshot(55L) } returnsMany listOf(
            unlocks(softcore = setOf(1L), hardcore = emptySet()),
            unlocks(softcore = setOf(1L, 2L, 3L), hardcore = setOf(3L))
        )
        val gameDao = mockk<GameDao> { coEvery { getById(1L) } returns game(55L) }
        val dao = mockk<PlaySessionDao>(relaxed = true)
        val stored = StoredSnapshot()
        val scope = TestScope(testScheduler)

        recorder(source = source, dao = dao, gameDao = gameDao, stored = stored).onSessionStarted(scope, 1L, external, start)
        scope.advanceUntilIdle()
        assertEquals(
            LaunchUnlockSnapshot(1L, start, 55L, unlocks(softcore = setOf(1L), hardcore = emptySet())),
            RaUnlockSnapshots.decodeLaunch(stored.value)
        )

        recorder(source = source, dao = dao, gameDao = gameDao, stored = stored)
            .onSessionRecorded(scope, 3L, 1L, external, SessionRecapFacts(55L, null, null), start, end)
        scope.advanceUntilIdle()

        coVerify(exactly = 1) { dao.setRaSnapshotUnlocks(3L, "s:2,h:3") }
        assertNull(stored.value)
    }

    @Test
    fun `a stored launch snapshot from another session is never used`() = runTest {
        val source = mockk<RaMetadataSource>(relaxed = true)
        val dao = mockk<PlaySessionDao>(relaxed = true)
        val stored = StoredSnapshot().apply {
            value = RaUnlockSnapshots.encodeLaunch(
                LaunchUnlockSnapshot(1L, start.minusSeconds(3600), 55L, unlocks(softcore = emptySet(), hardcore = emptySet()))
            )
        }
        val scope = TestScope(testScheduler)

        recorder(source = source, dao = dao, stored = stored)
            .onSessionRecorded(scope, 3L, 1L, external, SessionRecapFacts(55L, null, null), start, end)
        scope.advanceUntilIdle()

        coVerify(exactly = 0) { source.unlockSnapshot(any()) }
        coVerify(exactly = 0) { dao.setRaSnapshotUnlocks(any(), any()) }
    }

    @Test
    fun `starting a session drops the previous session's stored snapshot`() = runTest {
        val stored = StoredSnapshot().apply { value = "1|2026-10-01T10:00:00Z|55|1|" }
        val scope = TestScope(testScheduler)

        recorder(stored = stored).onSessionStarted(scope, 2L, EmulatorRegistry.BUILTIN_PACKAGE, end)
        scope.advanceUntilIdle()

        assertNull(stored.value)
    }

    @Test
    fun `the launch snapshot round-trips through its encoding`() {
        val snapshot = LaunchUnlockSnapshot(
            gameId = 7L,
            startTime = Instant.parse("2026-10-01T10:00:00.123456789Z"),
            raGameId = 55L,
            unlocks = unlocks(softcore = setOf(3L, 1L), hardcore = setOf(3L))
        )
        assertEquals(snapshot, RaUnlockSnapshots.decodeLaunch(RaUnlockSnapshots.encodeLaunch(snapshot)))
        assertEquals(
            snapshot.copy(unlocks = unlocks(emptySet(), emptySet())),
            RaUnlockSnapshots.decodeLaunch(RaUnlockSnapshots.encodeLaunch(snapshot.copy(unlocks = unlocks(emptySet(), emptySet()))))
        )
    }

    @Test
    fun `a malformed stored snapshot decodes to nothing`() {
        assertNull(RaUnlockSnapshots.decodeLaunch(null))
        assertNull(RaUnlockSnapshots.decodeLaunch(""))
        assertNull(RaUnlockSnapshots.decodeLaunch("1|not-a-time|55||"))
        assertNull(RaUnlockSnapshots.decodeLaunch("1|2026-10-01T10:00:00Z|55|x|"))
    }

    @Test
    fun `a built-in session takes no launch snapshot`() = runTest {
        val source = mockk<RaMetadataSource>(relaxed = true)
        val scope = TestScope(testScheduler)

        recorder(source = source).onSessionStarted(scope, 1L, EmulatorRegistry.BUILTIN_PACKAGE, start)
        scope.advanceUntilIdle()

        coVerify(exactly = 0) { source.needsUnlockSnapshots(any()) }
        coVerify(exactly = 0) { source.unlockSnapshot(any()) }
    }
}
