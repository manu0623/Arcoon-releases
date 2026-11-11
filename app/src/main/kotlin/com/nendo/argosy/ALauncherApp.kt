package com.nendo.argosy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.launcher.GameNativeStoreSync
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.cheats.CheatsDownloadObserver
import com.nendo.argosy.data.emulator.TitleIdDownloadObserver
import com.nendo.argosy.data.sync.SaveSyncDownloadObserver
import com.nendo.argosy.data.update.ApkInstallManager
import android.os.SystemClock
import com.nendo.argosy.data.download.DownloadServiceController
import com.nendo.argosy.data.sync.SaveSyncWorker
import com.nendo.argosy.data.sync.SocialSyncWorker
import com.nendo.argosy.data.sync.SyncServiceController
import com.nendo.argosy.data.update.UpdateCheckWorker
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.preferences.BuiltinEmulatorPreferencesRepository
import com.nendo.argosy.libretro.CoreCrashController
import com.nendo.argosy.libretro.CoreUpdateCheckWorker
import com.nendo.argosy.libretro.CompatCoreCache
import com.nendo.argosy.libretro.LibretroBuildbot
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import com.nendo.argosy.ui.coil.AppIconFetcher
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import com.nendo.argosy.util.SafeCoroutineScope
import com.nendo.argosy.util.AppPaths
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

private const val NEVER_REFRESHED = -1L
private const val FOREGROUND_STEAM_REFRESH_INTERVAL_MS = 60 * 60 * 1000L
private const val EMULATOR_SCAN_DELAY_MS = 4_000L
private const val DEFERRED_SERVICES_FALLBACK_MS = 2_500L

@HiltAndroidApp
class ArgosyApp : Application(), Configuration.Provider, ImageLoaderFactory {

    companion object {
        lateinit var instance: ArgosyApp
            private set
    }

    init {
        instance = this
    }

    private val appScope = SafeCoroutineScope(Dispatchers.IO, "ArgosyApp")

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var lidSleepGuardLazy: dagger.Lazy<com.nendo.argosy.hardware.LidSleepGuard>
    private val lidSleepGuard: com.nendo.argosy.hardware.LidSleepGuard get() = lidSleepGuardLazy.get()

    @Inject
    lateinit var userCertStoreLazy: dagger.Lazy<com.nendo.argosy.data.remote.ssl.UserCertStore>
    private val userCertStore: com.nendo.argosy.data.remote.ssl.UserCertStore get() = userCertStoreLazy.get()

    @Inject
    lateinit var rommConnectionManagerLazy: dagger.Lazy<com.nendo.argosy.data.remote.romm.RomMConnectionManager>
    private val rommConnectionManager: com.nendo.argosy.data.remote.romm.RomMConnectionManager get() = rommConnectionManagerLazy.get()

    @Inject
    lateinit var socialSyncCoordinatorLazy: dagger.Lazy<com.nendo.argosy.data.sync.SocialSyncCoordinator>
    private val socialSyncCoordinator: com.nendo.argosy.data.sync.SocialSyncCoordinator get() = socialSyncCoordinatorLazy.get()

    @Inject
    lateinit var saveSyncDownloadObserverLazy: dagger.Lazy<SaveSyncDownloadObserver>
    private val saveSyncDownloadObserver: SaveSyncDownloadObserver get() = saveSyncDownloadObserverLazy.get()

    @Inject
    lateinit var cheatsDownloadObserverLazy: dagger.Lazy<CheatsDownloadObserver>
    private val cheatsDownloadObserver: CheatsDownloadObserver get() = cheatsDownloadObserverLazy.get()

    @Inject
    lateinit var titleIdDownloadObserverLazy: dagger.Lazy<TitleIdDownloadObserver>
    private val titleIdDownloadObserver: TitleIdDownloadObserver get() = titleIdDownloadObserverLazy.get()

    @Inject
    lateinit var platformDaoLazy: dagger.Lazy<PlatformDao>
    private val platformDao: PlatformDao get() = platformDaoLazy.get()

    @Inject
    lateinit var biosRepositoryLazy: dagger.Lazy<com.nendo.argosy.data.repository.BiosRepository>
    private val biosRepository: com.nendo.argosy.data.repository.BiosRepository get() = biosRepositoryLazy.get()

    @Inject
    lateinit var appPreferencesRepositoryLazy: dagger.Lazy<com.nendo.argosy.data.preferences.AppPreferencesRepository>
    private val appPreferencesRepository: com.nendo.argosy.data.preferences.AppPreferencesRepository get() = appPreferencesRepositoryLazy.get()

    @Inject
    lateinit var gameDaoLazy: dagger.Lazy<GameDao>
    private val gameDao: GameDao get() = gameDaoLazy.get()

    @Inject
    lateinit var activeSaveRepositoryLazy: dagger.Lazy<com.nendo.argosy.data.repository.ActiveSaveRepository>
    private val activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository get() = activeSaveRepositoryLazy.get()

    @Inject
    lateinit var libraryDefaultPlatformMigrationLazy: dagger.Lazy<com.nendo.argosy.data.repository.LibraryDefaultPlatformMigration>
    private val libraryDefaultPlatformMigration: com.nendo.argosy.data.repository.LibraryDefaultPlatformMigration get() = libraryDefaultPlatformMigrationLazy.get()

    @Inject
    lateinit var apkInstallManagerLazy: dagger.Lazy<ApkInstallManager>
    private val apkInstallManager: ApkInstallManager get() = apkInstallManagerLazy.get()

    @Inject
    lateinit var downloadServiceControllerLazy: dagger.Lazy<DownloadServiceController>
    private val downloadServiceController: DownloadServiceController get() = downloadServiceControllerLazy.get()

    @Inject
    lateinit var syncServiceControllerLazy: dagger.Lazy<SyncServiceController>
    private val syncServiceController: SyncServiceController get() = syncServiceControllerLazy.get()

    @Inject
    lateinit var coreManagerLazy: dagger.Lazy<LibretroCoreManager>
    private val coreManager: LibretroCoreManager get() = coreManagerLazy.get()

    @Inject
    lateinit var compatCoreCacheLazy: dagger.Lazy<CompatCoreCache>
    private val compatCoreCache: CompatCoreCache get() = compatCoreCacheLazy.get()

    @Inject
    lateinit var playSessionTrackerLazy: dagger.Lazy<PlaySessionTracker>
    private val playSessionTracker: PlaySessionTracker get() = playSessionTrackerLazy.get()

    @Inject
    lateinit var coverArtScraperLazy: dagger.Lazy<com.nendo.argosy.data.cache.CoverArtScraper>
    private val coverArtScraper: com.nendo.argosy.data.cache.CoverArtScraper get() = coverArtScraperLazy.get()

    @Inject
    lateinit var coreCrashControllerLazy: dagger.Lazy<CoreCrashController>
    private val coreCrashController: CoreCrashController get() = coreCrashControllerLazy.get()

    @Inject
    lateinit var builtinPrefsLazy: dagger.Lazy<BuiltinEmulatorPreferencesRepository>
    private val builtinPrefs: BuiltinEmulatorPreferencesRepository get() = builtinPrefsLazy.get()

    @Inject
    lateinit var quayPassServiceLazy: dagger.Lazy<com.nendo.argosy.data.quaypass.QuayPassService>
    private val quayPassService: com.nendo.argosy.data.quaypass.QuayPassService get() = quayPassServiceLazy.get()

    @Inject
    lateinit var quayPassCredentialManagerLazy: dagger.Lazy<com.nendo.argosy.data.quaypass.QuayPassCredentialManager>
    private val quayPassCredentialManager: com.nendo.argosy.data.quaypass.QuayPassCredentialManager get() = quayPassCredentialManagerLazy.get()

    @Inject
    lateinit var quayPassAvatarSyncCoordinatorLazy: dagger.Lazy<com.nendo.argosy.data.quaypass.QuayPassAvatarSyncCoordinator>
    private val quayPassAvatarSyncCoordinator: com.nendo.argosy.data.quaypass.QuayPassAvatarSyncCoordinator get() = quayPassAvatarSyncCoordinatorLazy.get()

    @Inject
    lateinit var quayPassMessageSyncCoordinatorLazy: dagger.Lazy<com.nendo.argosy.data.quaypass.QuayPassMessageSyncCoordinator>
    private val quayPassMessageSyncCoordinator: com.nendo.argosy.data.quaypass.QuayPassMessageSyncCoordinator get() = quayPassMessageSyncCoordinatorLazy.get()

    @Inject
    lateinit var quayPassFriendRequestQueueCoordinatorLazy: dagger.Lazy<com.nendo.argosy.data.quaypass.QuayPassFriendRequestQueueCoordinator>
    private val quayPassFriendRequestQueueCoordinator: com.nendo.argosy.data.quaypass.QuayPassFriendRequestQueueCoordinator get() = quayPassFriendRequestQueueCoordinatorLazy.get()

    @Inject
    lateinit var quayPassEncounterReporterLazy: dagger.Lazy<com.nendo.argosy.data.quaypass.QuayPassEncounterReporter>
    private val quayPassEncounterReporter: com.nendo.argosy.data.quaypass.QuayPassEncounterReporter get() = quayPassEncounterReporterLazy.get()

    @Inject
    lateinit var quayPassWalletCoordinatorLazy: dagger.Lazy<com.nendo.argosy.data.quaypass.QuayPassWalletCoordinator>
    private val quayPassWalletCoordinator: com.nendo.argosy.data.quaypass.QuayPassWalletCoordinator get() = quayPassWalletCoordinatorLazy.get()

    @Inject
    lateinit var accountSwitchCoordinatorLazy: dagger.Lazy<com.nendo.argosy.data.sync.AccountSwitchCoordinator>
    private val accountSwitchCoordinator: com.nendo.argosy.data.sync.AccountSwitchCoordinator get() = accountSwitchCoordinatorLazy.get()

    @Inject
    lateinit var pushInstallCoordinatorLazy: dagger.Lazy<com.nendo.argosy.data.install.PushInstallCoordinator>
    private val pushInstallCoordinator: com.nendo.argosy.data.install.PushInstallCoordinator get() = pushInstallCoordinatorLazy.get()

    @Inject
    lateinit var siblingGroupRepositoryLazy: dagger.Lazy<com.nendo.argosy.data.repository.SiblingGroupRepository>
    private val siblingGroupRepository: com.nendo.argosy.data.repository.SiblingGroupRepository get() = siblingGroupRepositoryLazy.get()

    @Inject
    lateinit var siblingGroupDownloadObserverLazy: dagger.Lazy<com.nendo.argosy.data.repository.SiblingGroupDownloadObserver>
    private val siblingGroupDownloadObserver: com.nendo.argosy.data.repository.SiblingGroupDownloadObserver get() = siblingGroupDownloadObserverLazy.get()

    @Inject
    lateinit var raPatchDownloadObserverLazy: dagger.Lazy<com.nendo.argosy.data.repository.RAPatchDownloadObserver>
    private val raPatchDownloadObserver: com.nendo.argosy.data.repository.RAPatchDownloadObserver get() = raPatchDownloadObserverLazy.get()

    @Inject
    lateinit var raRepositoryLazy: dagger.Lazy<com.nendo.argosy.data.repository.RetroAchievementsRepository>
    private val raRepository: com.nendo.argosy.data.repository.RetroAchievementsRepository get() = raRepositoryLazy.get()

    @Inject
    lateinit var variantFileCleanupLazy: dagger.Lazy<com.nendo.argosy.data.emulator.VariantFileCleanup>
    private val variantFileCleanup: com.nendo.argosy.data.emulator.VariantFileCleanup get() = variantFileCleanupLazy.get()

    @Inject
    lateinit var lockScreenArtManagerLazy: dagger.Lazy<com.nendo.argosy.data.wallpaper.LockScreenArtManager>
    private val lockScreenArtManager: com.nendo.argosy.data.wallpaper.LockScreenArtManager get() = lockScreenArtManagerLazy.get()

    @Inject
    lateinit var iconPackRepositoryLazy: dagger.Lazy<com.nendo.argosy.data.iconpack.IconPackRepository>
    private val iconPackRepository: com.nendo.argosy.data.iconpack.IconPackRepository get() = iconPackRepositoryLazy.get()

    @Inject
    lateinit var homeAppAvailabilityLazy: dagger.Lazy<com.nendo.argosy.util.HomeAppAvailability>
    private val homeAppAvailability: com.nendo.argosy.util.HomeAppAvailability get() = homeAppAvailabilityLazy.get()

    @Inject
    lateinit var appsRepositoryLazy: dagger.Lazy<AppsRepository>
    private val appsRepository: AppsRepository get() = appsRepositoryLazy.get()

    @Inject
    lateinit var gameNativeStoreSyncLazy: dagger.Lazy<GameNativeStoreSync>
    private val gameNativeStoreSync: GameNativeStoreSync get() = gameNativeStoreSyncLazy.get()

    @Inject
    lateinit var emulatorDetectorLazy: dagger.Lazy<com.nendo.argosy.data.emulator.EmulatorDetector>
    private val emulatorDetector: com.nendo.argosy.data.emulator.EmulatorDetector get() = emulatorDetectorLazy.get()

    private val lastForegroundSteamRefreshAt = AtomicLong(NEVER_REFRESHED)

    private val foregroundObserver = object : androidx.lifecycle.DefaultLifecycleObserver {
        override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
            appScope.launch { quayPassCredentialManager.refreshIfNeeded() }
            appScope.launch { refreshSteamInstallationsIfStale(SystemClock.elapsedRealtime()) }
        }
    }

    override fun onCreate() {
        // Only what the first screen needs is built here. The background services (sync, social,
        // achievements, QuayPass, Steam, wallpaper...) are built off the main thread and started
        // right after the first screen is drawn: building all of them here took most of a second
        // before anything could appear.
        val started = SystemClock.uptimeMillis()
        super.onCreate()
        val injected = SystemClock.uptimeMillis()
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
        // Safety net when no screen opens (the app started for a background job): start anyway.
        android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed({ onFirstScreenDrawn() }, DEFERRED_SERVICES_FALLBACK_MS)
        val done = SystemClock.uptimeMillis()
        com.nendo.argosy.util.ArcoonTrace.log(
            "startup: app created in ${done - started} ms (injection ${injected - started} ms)"
        )
    }

    private val deferredServicesStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Every service built in the background after the first frame, by name (for the timing log). */
    private fun deferredServices(): List<Pair<String, dagger.Lazy<*>>> = listOf(
        "lidSleepGuard" to lidSleepGuardLazy,
        "userCertStore" to userCertStoreLazy,
        "rommConnectionManager" to rommConnectionManagerLazy,
        "socialSyncCoordinator" to socialSyncCoordinatorLazy,
        "saveSyncDownloadObserver" to saveSyncDownloadObserverLazy,
        "cheatsDownloadObserver" to cheatsDownloadObserverLazy,
        "titleIdDownloadObserver" to titleIdDownloadObserverLazy,
        "platformDao" to platformDaoLazy,
        "biosRepository" to biosRepositoryLazy,
        "appPreferencesRepository" to appPreferencesRepositoryLazy,
        "gameDao" to gameDaoLazy,
        "activeSaveRepository" to activeSaveRepositoryLazy,
        "libraryDefaultPlatformMigration" to libraryDefaultPlatformMigrationLazy,
        "apkInstallManager" to apkInstallManagerLazy,
        "downloadServiceController" to downloadServiceControllerLazy,
        "syncServiceController" to syncServiceControllerLazy,
        "coreManager" to coreManagerLazy,
        "compatCoreCache" to compatCoreCacheLazy,
        "playSessionTracker" to playSessionTrackerLazy,
        "coverArtScraper" to coverArtScraperLazy,
        "coreCrashController" to coreCrashControllerLazy,
        "builtinPrefs" to builtinPrefsLazy,
        "quayPassService" to quayPassServiceLazy,
        "quayPassCredentialManager" to quayPassCredentialManagerLazy,
        "quayPassAvatarSyncCoordinator" to quayPassAvatarSyncCoordinatorLazy,
        "quayPassMessageSyncCoordinator" to quayPassMessageSyncCoordinatorLazy,
        "quayPassFriendRequestQueueCoordinator" to quayPassFriendRequestQueueCoordinatorLazy,
        "quayPassEncounterReporter" to quayPassEncounterReporterLazy,
        "quayPassWalletCoordinator" to quayPassWalletCoordinatorLazy,
        "accountSwitchCoordinator" to accountSwitchCoordinatorLazy,
        "pushInstallCoordinator" to pushInstallCoordinatorLazy,
        "siblingGroupRepository" to siblingGroupRepositoryLazy,
        "siblingGroupDownloadObserver" to siblingGroupDownloadObserverLazy,
        "raPatchDownloadObserver" to raPatchDownloadObserverLazy,
        "raRepository" to raRepositoryLazy,
        "variantFileCleanup" to variantFileCleanupLazy,
        "lockScreenArtManager" to lockScreenArtManagerLazy,
        "iconPackRepository" to iconPackRepositoryLazy,
        "homeAppAvailability" to homeAppAvailabilityLazy,
        "appsRepository" to appsRepositoryLazy,
        "gameNativeStoreSync" to gameNativeStoreSyncLazy,
        "emulatorDetector" to emulatorDetectorLazy,
    )

    /**
     * Called once the main screen has drawn its first frame (or after a short safety delay):
     * builds the background services on a worker thread, then starts them on the main thread.
     */
    fun onFirstScreenDrawn() {
        if (!deferredServicesStarted.compareAndSet(false, true)) return
        appScope.launch {
            val buildStarted = SystemClock.uptimeMillis()
            val timings = deferredServices().map { (name, service) ->
                val at = SystemClock.uptimeMillis()
                // A service that insists on the main thread fails here and is built there instead.
                runCatching { service.get() }
                name to (SystemClock.uptimeMillis() - at)
            }
            val built = SystemClock.uptimeMillis()
            com.nendo.argosy.util.ArcoonTrace.log(
                "startup: background services built in ${built - buildStarted} ms off the main thread; slowest: " +
                    timings.sortedByDescending { it.second }.take(6).joinToString { "${it.first} ${it.second} ms" }
            )
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                val startAt = SystemClock.uptimeMillis()
                startServices()
                com.nendo.argosy.util.ArcoonTrace.log(
                    "startup: background services started in ${SystemClock.uptimeMillis() - startAt} ms"
                )
            }
        }
    }

    private fun startServices() {
        appScope.launch { userCertStore.initialize() }
        appScope.launch { com.nendo.argosy.util.RootShell.isAvailable }
        appScope.launch { socialSyncCoordinator.discardQueueWithoutSocialAccount() }
        appScope.launch { variantFileCleanup.runOnce() }
        appScope.launch { homeAppAvailability.applyAtStartup() }
        UpdateCheckWorker.schedule(this)
        SaveSyncWorker.schedule(this)
        SocialSyncWorker.schedule(this)
        CoreUpdateCheckWorker.schedule(this)
        com.nendo.argosy.data.quaypass.QuayPassCredentialRefreshWorker.schedule(this)
        appScope.launch { observeSteamPackageChanges() }
        saveSyncDownloadObserver.start()
        cheatsDownloadObserver.start()
        titleIdDownloadObserver.start()
        downloadServiceController.start()
        syncServiceController.start()
        pushInstallCoordinator.start()
        siblingGroupRepository.start()
        lidSleepGuard.start()
        siblingGroupDownloadObserver.start()
        raPatchDownloadObserver.start()
        appScope.launch {
            if (raRepository.isLoggedIn()) {
                com.nendo.argosy.data.sync.RAPatchBackfillWorker.schedule(this@ArgosyApp)
            }
        }
        lockScreenArtManager.start()
        coverArtScraper.resumeIfInterrupted()
        appScope.launch {
            val storedOverride = builtinPrefs.getArchitectureOverride().first()
            if (storedOverride != null) {
                if (LibretroBuildbot.isAbiCompatibleWithProcess(storedOverride)) {
                    LibretroBuildbot.abiOverride = storedOverride
                } else {
                    android.util.Log.w("ALauncherApp", "Stored ABI override '$storedOverride' is incompatible with process bitness; clearing")
                    builtinPrefs.setArchitectureOverride(null)
                }
            }
            coreManager.migrateAbiIfNeeded()
            compatCoreCache.evictStale()
        }
        appScope.launch {
            coreCrashController.runBootDetection()
            playSessionTracker.checkOrphanedSession()
        }
        appScope.launch { accountSwitchCoordinator.resumeIfInterrupted() }
        appScope.launch { activeSaveRepository.resetAllActiveSaveApplied() }
        appScope.launch { libraryDefaultPlatformMigration.run() }
        appScope.launch { deleteRetiredSteamDownloadDirs() }
        appScope.launch { biosRepository.purgeRetiredFirmware() }
        // The installed-emulator list, found once shortly after launch (after the first screens are
        // up), so the first game page opens without scanning for emulators first.
        appScope.launch {
            kotlinx.coroutines.delay(EMULATOR_SCAN_DELAY_MS)
            runCatching { emulatorDetector.detectEmulators() }
        }
        syncPlatformSortOrders()
    }

    private fun deleteRetiredSteamDownloadDirs() {
        AppPaths.retiredSteamDownloadDirs(filesDir)
            .filter { it.exists() }
            .forEach { dir ->
                if (!dir.deleteRecursively()) {
                    Logger.warn("ArgosyApp", "Could not fully remove retired Steam download dir ${dir.name}")
                }
            }
    }

    internal suspend fun refreshSteamInstallationsIfStale(now: Long) {
        val last = lastForegroundSteamRefreshAt.get()
        if (last != NEVER_REFRESHED && now - last < FOREGROUND_STEAM_REFRESH_INTERVAL_MS) return
        if (!lastForegroundSteamRefreshAt.compareAndSet(last, now)) return
        try {
            gameNativeStoreSync.refreshSteamInstallations()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error("ArgosyApp", "Failed to refresh Steam installations on foreground", e)
        }
    }

    internal suspend fun observeSteamPackageChanges() {
        appsRepository.packageChanges.collect { packageName ->
            if (SteamLaunchers.getByPackage(packageName) != null) {
                try {
                    gameNativeStoreSync.refreshSteamInstallations()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.error("ArgosyApp", "Failed to refresh Steam installations after package change", e)
                }
            }
        }
    }

    /**
     * Brings stored platform order back in line with the built-in one, unless the user has arranged
     * it themselves, in which case their order is what should survive a relaunch.
     */
    private fun syncPlatformSortOrders() {
        appScope.launch {
            if (appPreferencesRepository.isPlatformOrderCustomised()) return@launch
            PlatformDefinitions.getAll().forEach { def ->
                val platform = platformDao.getBySlug(def.slug) ?: return@forEach
                if (platform.sortOrder != def.sortOrder) {
                    platformDao.updateSortOrder(platform.id, def.sortOrder)
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                com.nendo.argosy.data.remote.romm.RomMImageAuthInterceptor(rommConnectionManager)
            )
            .withUserCertTrust(userCertStore)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .components {
                add(AppIconFetcher.Factory(packageManager, iconPackRepository))
                add(coil.decode.SvgDecoder.Factory())
                // Animated images (the 3DS banner animation is a GIF) play instead of freezing on
                // their first frame.
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .crossfade(true)
            // A bit more room for pictures than the default, so moving between Home and folders
            // doesn't reload them. Android can still ask for it back when memory runs low;
            // otherwise it is only emptied from Settings ▸ Free picture memory.
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.35)
                    .build()
            }
            .build()
    }
}
