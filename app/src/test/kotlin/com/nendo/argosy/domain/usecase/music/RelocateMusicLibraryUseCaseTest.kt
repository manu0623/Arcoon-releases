package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.util.TreeRelocator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RelocateMusicLibraryUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val musicDirectoryManager = mockk<MusicDirectoryManager>(relaxed = true) {
        every { defaultMusicDir() } returns File("/storage/emulated/0/Music/RomM")
    }
    private val storagePreferences = mockk<StoragePreferencesRepository>(relaxed = true)
    private val gameFileDao = mockk<GameFileDao>(relaxed = true)

    private val useCase = RelocateMusicLibraryUseCase(
        musicDirectoryManager = musicDirectoryManager,
        bgmPlaylistRepository = mockk(relaxed = true),
        gameFileDao = gameFileDao,
        controlsPreferencesRepository = mockk(relaxed = true),
        storagePreferences = storagePreferences,
        attributionRepository = mockk(relaxed = true)
    )

    @Test
    fun `a track that failed to move keeps its exact path`() = runTest {
        val source = temp.newFolder("old")
        val target = File(temp.root, "new")
        val stayed = File(source, "Album/track.mp3").absolutePath
        coEvery { musicDirectoryManager.relocate(any(), any()) } returns TreeRelocator.Result(emptyList(), listOf(stayed))

        useCase(source.absolutePath, target.absolutePath, moveFiles = true)

        coVerify { gameFileDao.updateLocalPathByOldPath(File(target, "Album/track.mp3").absolutePath, stayed) }
    }

    @Test
    fun `files under a folder that stayed behind keep their paths`() = runTest {
        val source = temp.newFolder("old-root")
        val target = File(temp.root, "new-root")
        val stayedFolder = File(source, "Locked").absolutePath
        coEvery { musicDirectoryManager.relocate(any(), any()) } returns TreeRelocator.Result(emptyList(), listOf(stayedFolder))

        useCase(source.absolutePath, target.absolutePath, moveFiles = true)

        coVerify { gameFileDao.rewriteLocalPathPrefix(File(target, "Locked").absolutePath, stayedFolder) }
    }

    @Test
    fun `choosing another folder stores it as the override`() = runTest {
        useCase("/storage/emulated/0/Music/RomM", "/storage/75D7-DC5F/Music", moveFiles = false)

        coVerify { storagePreferences.setMusicStoragePath("/storage/75D7-DC5F/Music") }
    }

    @Test
    fun `returning to the default folder clears the override`() = runTest {
        useCase("/storage/75D7-DC5F/Music", "/storage/emulated/0/Music/RomM", moveFiles = false)

        coVerify { storagePreferences.setMusicStoragePath(null) }
    }
}
