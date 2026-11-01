package com.nendo.argosy.domain.usecase.libretro

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class Ps2StandalonePinTest {

    private lateinit var preferences: UserPreferencesRepository
    private lateinit var platformDao: PlatformDao
    private lateinit var emulatorConfigDao: EmulatorConfigDao
    private lateinit var detector: EmulatorDetector
    private lateinit var useCase: LibretroMigrationUseCase
    private val pinDone = MutableStateFlow(false)

    private val nethersx2 = EmulatorRegistry.getById("nethersx2")!!

    @Before
    fun setUp() {
        preferences = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        emulatorConfigDao = mockk(relaxed = true)
        detector = mockk(relaxed = true)
        every { preferences.isPs2StandalonePinDone() } returns pinDone
        coEvery { preferences.setPs2StandalonePinDone() } answers { pinDone.value = true }
        every { detector.installedEmulators } returns MutableStateFlow(
            listOf(InstalledEmulator(nethersx2, "1.0", 1L))
        )
        coEvery { detector.detectEmulators() } returns listOf(InstalledEmulator(nethersx2, "1.0", 1L))
        every { detector.getPreferredEmulator("ps2", false) } returns InstalledEmulator(nethersx2, "1.0", 1L)
        coEvery { emulatorConfigDao.getDefaultForPlatform(any()) } returns null
        useCase = LibretroMigrationUseCase(preferences, platformDao, emulatorConfigDao, mockk(relaxed = true), detector)
    }

    private fun platform(id: Long, slug: String, games: Int) = PlatformEntity(
        id = id,
        slug = slug,
        name = slug,
        shortName = slug,
        sortOrder = 0,
        isVisible = true,
        logoPath = null,
        romExtensions = "iso",
        gameCount = games,
        syncEnabled = true,
        customRomPath = null
    )

    @Test
    fun `the built-in core leads the ps2 recommendations and the standalone order is unchanged`() {
        assertEquals(
            listOf(EmulatorRegistry.BUILTIN_ID, "nethersx2", "armsx2_refresh", "armsx2", "psx2", "pcsx2"),
            EmulatorRegistry.getRecommendedEmulators()["ps2"]
        )
    }

    @Test
    fun `an existing install with ps2 games keeps the emulator it resolved to`() = runTest {
        coEvery { platformDao.getAllPlatforms() } returns listOf(platform(4L, "ps2", games = 12), platform(5L, "snes", 3))
        val inserted = slot<EmulatorConfigEntity>()
        coEvery { emulatorConfigDao.insert(capture(inserted)) } returns 1L

        useCase.pinExistingPs2Emulator()

        assertEquals(4L, inserted.captured.platformId)
        assertEquals(nethersx2.packageName, inserted.captured.packageName)
        assertEquals(true, inserted.captured.isDefault)
        assertEquals(null, inserted.captured.gameId)
        coVerify(exactly = 1) { emulatorConfigDao.insert(any()) }
        coVerify { preferences.setPs2StandalonePinDone() }
    }

    @Test
    fun `a stored ps2 default is left alone`() = runTest {
        coEvery { platformDao.getAllPlatforms() } returns listOf(platform(4L, "ps2", games = 12))
        coEvery { emulatorConfigDao.getDefaultForPlatform(4L) } returns EmulatorConfigEntity(
            platformId = 4L, gameId = null, packageName = "com.armsx2", displayName = "ARMSX2",
            coreName = null, isDefault = true
        )

        useCase.pinExistingPs2Emulator()

        coVerify(exactly = 0) { emulatorConfigDao.insert(any()) }
    }

    @Test
    fun `a default row with no package keeps its other fields and gains the package`() = runTest {
        coEvery { platformDao.getAllPlatforms() } returns listOf(platform(4L, "ps2", games = 12))
        val existing = EmulatorConfigEntity(
            platformId = 4L, gameId = null, packageName = null, displayName = null,
            coreName = null, isDefault = true, displayTarget = "SECONDARY"
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(4L) } returns existing
        val inserted = slot<EmulatorConfigEntity>()
        coEvery { emulatorConfigDao.insert(capture(inserted)) } returns 1L

        useCase.pinExistingPs2Emulator()

        assertEquals(existing.copy(packageName = nethersx2.packageName, displayName = nethersx2.displayName), inserted.captured)
    }

    @Test
    fun `a fresh install with no ps2 games is not pinned and never will be`() = runTest {
        coEvery { platformDao.getAllPlatforms() } returns listOf(platform(4L, "ps2", games = 0))

        useCase.pinExistingPs2Emulator()
        coEvery { platformDao.getAllPlatforms() } returns listOf(platform(4L, "ps2", games = 12))
        useCase.pinExistingPs2Emulator()

        coVerify(exactly = 0) { emulatorConfigDao.insert(any()) }
    }

    @Test
    fun `a second run does nothing`() = runTest {
        coEvery { platformDao.getAllPlatforms() } returns listOf(platform(4L, "ps2", games = 12))

        useCase.pinExistingPs2Emulator()
        useCase.pinExistingPs2Emulator()

        coVerify(exactly = 1) { emulatorConfigDao.insert(any()) }
        coVerify(exactly = 1) { platformDao.getAllPlatforms() }
    }

    @Test
    fun `an emulator scan that returns nothing leaves the pin for the next start`() = runTest {
        coEvery { platformDao.getAllPlatforms() } returns listOf(platform(4L, "ps2", games = 12))
        coEvery { detector.detectEmulators() } returns emptyList()

        useCase.pinExistingPs2Emulator()

        coVerify(exactly = 0) { emulatorConfigDao.insert(any()) }
        coVerify(exactly = 0) { preferences.setPs2StandalonePinDone() }
    }

    @Test
    fun `nothing is pinned when no standalone ps2 emulator is installed`() = runTest {
        coEvery { platformDao.getAllPlatforms() } returns listOf(platform(4L, "ps2", games = 12))
        every { detector.getPreferredEmulator("ps2", false) } returns null

        useCase.pinExistingPs2Emulator()

        coVerify(exactly = 0) { emulatorConfigDao.insert(any()) }
        coVerify { preferences.setPs2StandalonePinDone() }
    }
}
