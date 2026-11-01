package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameUserOverlayEntity
import com.nendo.argosy.data.model.FileOrigin
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.platform.PlatformDefinitions
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameDaoSteamLauncherSqlTest {
    private lateinit var db: ALauncherDatabase
    private val gameNative = "app.gamenative"
    private val droidDeck = "com.droiddeck.launcher"

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        db.platformDao().insert(
            PlatformDefinitions.toLocalPlatformEntity(PlatformDefinitions.getBySlug("steam")!!)!!
        )
        db.gameDao().insert(
            GameEntity(
                id = 1, platformId = LocalPlatformIds.STEAM, platformSlug = "steam",
                title = "UFO 50", sortTitle = "ufo 50", localPath = null,
                rommId = null, igdbId = null, source = GameSource.STEAM,
                steamAppId = 1147860, steamLauncher = gameNative, launcherSetManually = true
            )
        )
        Unit
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun retiring_pin_preserves_external_ownership_and_allows_automatic_replacement() = runBlocking {
        val dao = db.gameDao()
        assertEquals(1, dao.compareAndSetSteamLauncher(1, gameNative, true, gameNative, false, 10))
        val automatic = dao.getById(1)!!
        assertFalse(automatic.launcherSetManually)
        assertTrue(automatic.isExternallyManaged)
        assertEquals(gameNative, automatic.steamLauncher)

        assertEquals(1, dao.compareAndSetSteamLauncher(1, gameNative, false, droidDeck, false, 10))
        assertEquals(droidDeck, dao.getById(1)!!.steamLauncher)
    }

    @Test
    fun stale_automatic_scan_cannot_replace_a_new_manual_choice() = runBlocking {
        val dao = db.gameDao()
        dao.updateSteamLauncher(1, droidDeck, true)

        assertEquals(0, dao.compareAndSetSteamLauncher(1, gameNative, false, gameNative, false, 10))
        assertEquals(droidDeck, dao.getById(1)!!.steamLauncher)
        assertTrue(dao.getById(1)!!.launcherSetManually)
    }

    @Test
    fun explicit_auto_only_removes_pin_and_preserves_installation_metadata() = runBlocking {
        val before = db.gameDao().getById(1)!!

        db.gameDao().clearSteamLauncherPin(1)

        assertEquals(before.copy(launcherSetManually = false), db.gameDao().getById(1))
    }

    @Test
    fun account_mask_prevents_resetting_another_accounts_game() = runBlocking {
        db.gameUserOverlayDao().upsert(GameUserOverlayEntity(ownerUserId = 10, gameId = 1, isMember = false))

        assertEquals(0, db.gameDao().compareAndSetSteamLauncher(1, gameNative, true, gameNative, false, 10))
        assertTrue(db.gameDao().getById(1)!!.launcherSetManually)
        assertEquals(1, db.gameDao().compareAndSetSteamLauncher(1, gameNative, true, gameNative, false, 20))
    }

    @Test
    fun metadata_updates_preserve_recovered_launcher_and_current_game_state() = runBlocking {
        val dao = db.gameDao()
        assertEquals(1, dao.compareAndSetSteamLauncher(1, gameNative, true, droidDeck, false, 10))
        val recovered = dao.getById(1)!!.copy(
            localPath = "/current/install", playCount = 8, saveId = "current-save",
            igdbId = 123, storeEnrichStatus = GameEntity.STORE_FAILED
        )
        dao.update(recovered)

        dao.updateSteamStoreMetadata(1, "Store description", "Action", 91f)
        val enriched = recovered.copy(description = "Store description", genre = "Action", rating = 91f)
        assertEquals(enriched, dao.getById(1))

        dao.updateSteamInstallInfo(1, "Updated")
        assertEquals(enriched.copy(steamInstallDir = "Updated"), dao.getById(1))

        dao.updateSteamLibraryMetadata(1, "Updated Game", "updated game", "Developer", "Publisher", 2020, "Puzzle", "Forced")
        assertEquals(enriched.copy(
            title = "Updated Game", sortTitle = "updated game", developer = "Developer",
            publisher = "Publisher", releaseYear = 2020, genre = "Puzzle", steamInstallDir = "Forced",
            storeEnrichStatus = GameEntity.STORE_NOT_ATTEMPTED, igdbId = null
        ), dao.getById(1))
    }

    @Test
    fun missing_metadata_uses_current_columns_and_only_defaults_unassigned_launchers() = runBlocking {
        val dao = db.gameDao()
        val selected = dao.getById(1)!!.copy(
            steamLauncher = "com.xiaoji.egggame", description = "Current description", genre = "Current genre",
            rating = 80f, developer = "Current developer", publisher = "Current publisher",
            releaseYear = 2024, steamInstallDir = "Current"
        )
        dao.update(selected)

        dao.updateSteamStoreMetadata(1, null, null, null)
        dao.updateSteamInstallInfo(1, null)
        dao.updateSteamLibraryMetadata(1, null, null, null, null, null, null, null)
        assertEquals(selected, dao.getById(1))

        val unassigned = selected.copy(steamLauncher = null, launcherSetManually = false)
        dao.update(unassigned)
        dao.updateSteamInstallInfo(1, null)
        assertEquals(unassigned.copy(steamLauncher = "native"), dao.getById(1))
    }

    @Test
    fun download_path_updates_preserve_retired_preferences_and_game_state() = runBlocking {
        val dao = db.gameDao()
        assertEquals(1, dao.compareAndSetSteamLauncher(1, gameNative, true, droidDeck, false, 10))
        val retired = dao.getById(1)!!.copy(playCount = 8, saveId = "current-save")
        dao.update(retired)

        dao.updateLocalPath(1, "/download/install", GameSource.STEAM, FileOrigin.STEAM_DOWNLOAD, retired.addedAt)
        val completed = retired.copy(localPath = "/download/install", fileOrigin = FileOrigin.STEAM_DOWNLOAD)
        assertEquals(completed, dao.getById(1))

        dao.updateLocalPath(1, null, completed.source, completed.fileOrigin, completed.addedAt)
        assertEquals(completed.copy(localPath = null), dao.getById(1))
    }
}
