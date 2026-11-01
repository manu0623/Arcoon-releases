package com.nendo.argosy.data.launcher

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.preferences.StoragePreferences
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SteamInstallMarkingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val gameNative = GameNativeLauncher.packageName
    private val droidDeck = DroidDeckLauncher.packageName

    private val rows = linkedMapOf<Long, GameEntity>()
    private var installedPackages = setOf(gameNative, droidDeck)
    private var currentOwnerUserId = 1L
    private var beforeCompareAndSet: (() -> Unit)? = null

    private val gameDao = mockk<GameDao>(relaxed = true).also { dao ->
        coEvery { dao.getBySourcesForOwner(listOf(GameSource.STEAM), LocalPlatformIds.STEAM, any()) } answers {
            rows.values.toList()
        }
        coEvery { dao.setSteamLauncher(any(), any()) } answers {
            val id = firstArg<Long>()
            rows[id] = rows.getValue(id).copy(steamLauncher = secondArg())
        }
        coEvery { dao.getById(any()) } answers { rows[firstArg()] }
        coEvery { dao.compareAndSetSteamLauncher(any(), any(), any(), any(), any(), any()) } answers {
            beforeCompareAndSet?.also { beforeCompareAndSet = null }?.invoke()
            val id = firstArg<Long>()
            val row = rows[id]
            if (row == null || row.steamLauncher != arg<String?>(1) || row.launcherSetManually != arg<Boolean>(2)) {
                0
            } else {
                rows[id] = row.copy(steamLauncher = arg(3), launcherSetManually = arg(4))
                1
            }
        }
    }

    private fun steamRow(id: Long, appId: Long, launcher: String? = null, pinned: Boolean = false) {
        rows[id] = GameEntity(
            id = id,
            platformId = LocalPlatformIds.STEAM,
            platformSlug = "steam",
            title = "Game $appId",
            sortTitle = "game $appId",
            localPath = null,
            rommId = null,
            igdbId = null,
            steamAppId = appId,
            steamLauncher = launcher,
            launcherSetManually = pinned,
            source = GameSource.STEAM
        )
    }

    private fun markerFolder(vararg appIds: Long): String {
        val dir = tempFolder.newFolder()
        appIds.forEach { File(dir, "Game $it.steam").writeText(it.toString()) }
        return dir.absolutePath
    }

    private suspend fun scan(dirs: Map<GameNativeSyncFolder, String>): GameNativeStoreSync.ScanSummary {
        val storagePrefs = mockk<StoragePreferencesRepository>(relaxed = true)
        every { storagePrefs.preferences } returns flowOf(StoragePreferences(gameNativeSyncDirs = dirs))
        val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
        every { userPrefs.userPreferences } returns flowOf(UserPreferences())
        val syncPrefs = mockk<SyncPreferencesRepository>(relaxed = true)
        coEvery { syncPrefs.getRommUserId() } answers { currentOwnerUserId }
        val context = mockk<Context>(relaxed = true)
        every { context.packageManager.getPackageInfo(any<String>(), any<Int>()) } answers {
            if (firstArg<String>() !in installedPackages) throw PackageManager.NameNotFoundException()
            mockk<PackageInfo>()
        }
        val apps = mockk<AppsRepository>(relaxed = true)
        every { apps.isPackageMissing(any()) } answers { firstArg<String>() !in installedPackages }
        return GameNativeStoreSync(
            context = context,
            appsRepository = apps,
            gameDao = gameDao,
            platformDao = mockk(relaxed = true),
            imageCacheManager = mockk(relaxed = true),
            storagePrefs = storagePrefs,
            preferencesRepository = userPrefs,
            syncPreferencesRepository = syncPrefs,
            steamLibraryRepair = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            gameScreenshotDao = mockk(relaxed = true)
        ).scan()
    }

    @Test
    fun `a droiddeck marker marks the owned game installed for droiddeck`() = runTest {
        steamRow(id = 1, appId = 620)
        steamRow(id = 2, appId = 400, launcher = GameEntity.LAUNCHER_UNSPECIFIED)

        scan(mapOf(GameNativeSyncFolder.DROIDDECK to markerFolder(620, 400)))

        assertEquals(droidDeck, rows.getValue(1).steamLauncher)
        assertEquals(droidDeck, rows.getValue(2).steamLauncher)
    }

    @Test
    fun `a gamenative marker still marks gamenative`() = runTest {
        steamRow(id = 1, appId = 620)

        scan(mapOf(GameNativeSyncFolder.STEAM to markerFolder(620)))

        assertEquals(gameNative, rows.getValue(1).steamLauncher)
    }

    @Test
    fun `a game both folders report is claimed by gamenative and stays put on rescans`() = runTest {
        steamRow(id = 1, appId = 620)
        val dirs = mapOf(
            GameNativeSyncFolder.STEAM to markerFolder(620),
            GameNativeSyncFolder.DROIDDECK to markerFolder(620)
        )

        scan(dirs)
        scan(dirs)

        assertEquals(gameNative, rows.getValue(1).steamLauncher)
    }

    @Test
    fun `a game droiddeck already marked stays droiddeck when gamenative starts reporting it`() = runTest {
        steamRow(id = 1, appId = 620, launcher = droidDeck)

        scan(
            mapOf(
                GameNativeSyncFolder.STEAM to markerFolder(620),
                GameNativeSyncFolder.DROIDDECK to markerFolder(620)
            )
        )

        assertEquals(droidDeck, rows.getValue(1).steamLauncher)
    }

    @Test
    fun `when gamenative drops a game droiddeck still reports it moves to droiddeck in one scan`() = runTest {
        steamRow(id = 1, appId = 620, launcher = gameNative)

        scan(
            mapOf(
                GameNativeSyncFolder.STEAM to markerFolder(),
                GameNativeSyncFolder.DROIDDECK to markerFolder(620)
            )
        )

        assertEquals(droidDeck, rows.getValue(1).steamLauncher)
    }

    @Test
    fun `when droiddeck drops a game gamenative replaces it in one scan`() = runTest {
        steamRow(id = 1, appId = 620, launcher = droidDeck)

        scan(mapOf(GameNativeSyncFolder.STEAM to markerFolder(620), GameNativeSyncFolder.DROIDDECK to markerFolder()))

        assertEquals(gameNative, rows.getValue(1).steamLauncher)
    }

    @Test
    fun `uninstalled gamenative exports cannot retain a missing manual pin`() = runTest {
        installedPackages = setOf(droidDeck)
        steamRow(id = 1, appId = 620, launcher = gameNative, pinned = true)

        scan(mapOf(GameNativeSyncFolder.STEAM to markerFolder(620), GameNativeSyncFolder.DROIDDECK to markerFolder(620)))

        assertEquals(droidDeck, rows.getValue(1).steamLauncher)
        assertEquals(false, rows.getValue(1).launcherSetManually)
    }

    @Test
    fun `a missing owner without a replacement keeps its association but reinstall does not restore its pin`() = runTest {
        installedPackages = setOf(droidDeck)
        steamRow(id = 1, appId = 620, launcher = gameNative, pinned = true)
        val dirs = mapOf(
            GameNativeSyncFolder.STEAM to markerFolder(620),
            GameNativeSyncFolder.DROIDDECK to markerFolder()
        )

        scan(dirs)

        assertEquals(gameNative, rows.getValue(1).steamLauncher)
        assertEquals(false, rows.getValue(1).launcherSetManually)

        installedPackages = setOf(gameNative, droidDeck)
        scan(dirs)

        assertEquals(gameNative, rows.getValue(1).steamLauncher)
        assertEquals(false, rows.getValue(1).launcherSetManually)
    }

    @Test
    fun `an automatic missing owner moves to the unique installed reporter in either direction`() = runTest {
        val dirs = mapOf(
            GameNativeSyncFolder.STEAM to markerFolder(620),
            GameNativeSyncFolder.DROIDDECK to markerFolder(620)
        )
        for ((missing, replacement) in listOf(gameNative to droidDeck, droidDeck to gameNative)) {
            installedPackages = setOf(replacement)
            steamRow(id = 1, appId = 620, launcher = missing)

            scan(dirs)
            scan(dirs)

            assertEquals(missing, replacement, rows.getValue(1).steamLauncher)
            assertEquals(false, rows.getValue(1).launcherSetManually)
        }
    }

    @Test
    fun `two installed reporters do not guess a replacement for a missing owner`() = runTest {
        val missing = "com.previous.launcher"
        steamRow(id = 1, appId = 620, launcher = missing, pinned = true)

        scan(mapOf(GameNativeSyncFolder.STEAM to markerFolder(620), GameNativeSyncFolder.DROIDDECK to markerFolder(620)))

        assertEquals(missing, rows.getValue(1).steamLauncher)
        assertEquals(false, rows.getValue(1).launcherSetManually)
    }

    @Test
    fun `an installed manual pin remains pinned when only the other launcher reports the game`() = runTest {
        steamRow(id = 1, appId = 620, launcher = gameNative, pinned = true)
        val original = rows.getValue(1)

        scan(mapOf(GameNativeSyncFolder.STEAM to markerFolder(), GameNativeSyncFolder.DROIDDECK to markerFolder(620)))

        assertEquals(original, rows.getValue(1))
        coVerify(exactly = 0) { gameDao.compareAndSetSteamLauncher(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a missing configured inventory preserves its installed owner`() = runTest {
        steamRow(id = 1, appId = 620, launcher = droidDeck)
        val original = rows.getValue(1)
        val missing = File(tempFolder.root, "missing").absolutePath

        val summary = scan(mapOf(GameNativeSyncFolder.DROIDDECK to missing))

        assertEquals(original, rows.getValue(1))
        assertEquals(StoreScanResult.FolderMissing(missing), summary.results[GameNativeSyncFolder.DROIDDECK])
    }

    @Test
    fun `an unreadable inventory preserves its installed owner`() = runTest {
        steamRow(id = 1, appId = 620, launcher = droidDeck)
        val original = rows.getValue(1)
        val dir = markerFolder(620)
        mockkObject(FrontendSyncMarkers)
        try {
            every { FrontendSyncMarkers.read(any(), GameNativeSyncFolder.DROIDDECK) } returns null

            val summary = scan(mapOf(GameNativeSyncFolder.DROIDDECK to dir))

            assertEquals(original, rows.getValue(1))
            assertEquals(StoreScanResult.FolderMissing(dir), summary.results[GameNativeSyncFolder.DROIDDECK])
        } finally {
            unmockkObject(FrontendSyncMarkers)
        }
    }

    @Test
    fun `an account switch after the inventory query prevents recovery writes`() = runTest {
        installedPackages = setOf(droidDeck)
        steamRow(id = 1, appId = 620, launcher = gameNative, pinned = true)
        val original = rows.getValue(1)
        coEvery { gameDao.getBySourcesForOwner(listOf(GameSource.STEAM), LocalPlatformIds.STEAM, 1L) } answers {
            currentOwnerUserId = 2L
            rows.values.toList()
        }

        scan(mapOf(GameNativeSyncFolder.DROIDDECK to markerFolder(620)))

        assertEquals(original, rows.getValue(1))
        coVerify(exactly = 0) { gameDao.compareAndSetSteamLauncher(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a newer manual choice wins when recovery loses its compare and set`() = runTest {
        installedPackages = setOf(droidDeck)
        steamRow(id = 1, appId = 620, launcher = gameNative)
        beforeCompareAndSet = {
            rows[1] = rows.getValue(1).copy(steamLauncher = droidDeck, launcherSetManually = true)
        }

        scan(mapOf(GameNativeSyncFolder.DROIDDECK to markerFolder(620)))

        assertEquals(droidDeck, rows.getValue(1).steamLauncher)
        assertEquals(true, rows.getValue(1).launcherSetManually)
        coVerify(exactly = 1) {
            gameDao.compareAndSetSteamLauncher(1L, gameNative, false, droidDeck, false, 1L)
        }
    }

    @Test
    fun `a newer manual choice wins while a missing pin is being cleared`() = runTest {
        installedPackages = setOf(droidDeck)
        steamRow(id = 1, appId = 620, launcher = gameNative, pinned = true)
        beforeCompareAndSet = {
            rows[1] = rows.getValue(1).copy(steamLauncher = droidDeck, launcherSetManually = true)
        }

        scan(mapOf(GameNativeSyncFolder.DROIDDECK to markerFolder(620)))

        assertEquals(droidDeck, rows.getValue(1).steamLauncher)
        assertEquals(true, rows.getValue(1).launcherSetManually)
        coVerify(exactly = 1) {
            gameDao.compareAndSetSteamLauncher(1L, gameNative, true, gameNative, false, 1L)
        }
    }

    @Test
    fun `a droiddeck marker going away clears only droiddeck rows`() = runTest {
        steamRow(id = 1, appId = 620, launcher = droidDeck)
        steamRow(id = 2, appId = 400, launcher = gameNative)

        scan(mapOf(GameNativeSyncFolder.DROIDDECK to markerFolder()))

        assertNull(rows.getValue(1).steamLauncher)
        assertEquals(gameNative, rows.getValue(2).steamLauncher)
    }

    @Test
    fun `an unconfigured droiddeck folder leaves droiddeck rows alone`() = runTest {
        steamRow(id = 1, appId = 620, launcher = droidDeck)

        scan(mapOf(GameNativeSyncFolder.STEAM to markerFolder()))

        assertEquals(droidDeck, rows.getValue(1).steamLauncher)
    }

    @Test
    fun `a pinned row is never touched by either folder`() = runTest {
        steamRow(id = 1, appId = 620, launcher = null, pinned = true)
        steamRow(id = 2, appId = 400, launcher = droidDeck, pinned = true)

        scan(
            mapOf(
                GameNativeSyncFolder.STEAM to markerFolder(620),
                GameNativeSyncFolder.DROIDDECK to markerFolder(620)
            )
        )

        assertNull(rows.getValue(1).steamLauncher)
        assertEquals(droidDeck, rows.getValue(2).steamLauncher)
    }

    @Test
    fun `the droiddeck folder reports its own install counts`() = runTest {
        steamRow(id = 1, appId = 620)

        val summary = scan(mapOf(GameNativeSyncFolder.DROIDDECK to markerFolder(620, 999)))

        assertEquals(
            StoreScanResult.InstallState(markers = 2, matched = 1, notInLibrary = 1),
            summary.results[GameNativeSyncFolder.DROIDDECK]
        )
    }
}
