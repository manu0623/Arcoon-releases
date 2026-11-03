package com.nendo.argosy.data.emulator

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import com.nendo.argosy.domain.model.multitasking.ProcessState
import com.nendo.argosy.domain.model.multitasking.SuspendedGameSession
import com.nendo.argosy.libretro.LibretroActivity
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GameSessionManager"
private const val CLOSE_WAIT_MS = 4_000L
private const val CLOSE_POLL_MS = 50L
private const val VEIL_AFTER_GROW_MS = 400L
private const val RESUME_REQUEST_TIMEOUT_MS = 5_000L

/**
 * Cocoon-style game suspension for Argosy's built-in emulator.
 *
 * The built-in emulator runs inside Argosy's own process, so it is never frozen with a signal:
 * stopping that process would stop the launcher with it. Instead Home leaves the game's activity
 * paused in its own task (the core is already paused and its save written by the activity's
 * onPause), this manager records the session and a snapshot for the top-screen card, and resume
 * brings that task back to the front. Close finishes the task through the activity's normal
 * shutdown, so the play session ends and save sync runs exactly as when quitting from the menu.
 */
@Singleton
open class GameSessionManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "GameSessionManager")

    private val _sessionState = MutableStateFlow<SuspendedGameSession?>(null)
    val sessionState: StateFlow<SuspendedGameSession?> = _sessionState.asStateFlow()

    private val _processState = MutableStateFlow(ProcessState.IDLE)
    val processState: StateFlow<ProcessState> = _processState.asStateFlow()

    private val _resumeRequest =
        MutableStateFlow<com.nendo.argosy.ui.dualscreen.BuiltInResumeRequest?>(null)
    val resumeRequest: StateFlow<com.nendo.argosy.ui.dualscreen.BuiltInResumeRequest?> =
        _resumeRequest.asStateFlow()

    private var timerJob: Job? = null
    @Volatile private var suspendedDisplayId: Int? = null

    /** Called by the built-in emulator when the user leaves it with Home. */
    fun suspendBuiltIn(
        gameId: Long,
        title: String,
        platformName: String,
        snapshot: Bitmap?,
        gameDisplayId: Int? = null
    ) {
        val now = System.currentTimeMillis()
        suspendedDisplayId = gameDisplayId
        _resumeRequest.value = null
        _sessionState.value = SuspendedGameSession(
            gameId = gameId,
            title = title,
            platformName = platformName,
            packageName = context.packageName,
            processPid = 0,
            startTimeMillis = now,
            elapsedTimeSeconds = 0,
            screenshotPath = null,
            builtIn = true,
            emulatorName = "Argosy"
        )
        lastSuspendedAt = now
        _processState.value = ProcessState.SUSPENDED
        startTimer(now)
        if (snapshot != null) {
            scope.launch {
                val path = runCatching { saveSnapshotToDisk(gameId, snapshot) }.getOrNull()
                _sessionState.update { current -> if (current?.gameId == gameId) current.copy(screenshotPath = path) else current }
            }
        }
        Logger.info(TAG, "Suspended built-in game '$title'")
    }

    /** Called by the built-in emulator when it is back in front, however it got there. */
    fun onBuiltInResumed(gameId: Long) {
        val session = _sessionState.value ?: return
        if (!session.builtIn || session.gameId != gameId) return
        stopTimer()
        _sessionState.value = null
        _processState.value = ProcessState.RUNNING
    }

    /** Called by the built-in emulator when it finishes, so no stale card is left behind. */
    fun onBuiltInFinished(gameId: Long) {
        val session = _sessionState.value ?: return
        if (session.builtIn && session.gameId == gameId) clear(session)
    }

    /** When the last game was suspended; Home arriving right after it is the same press. */
    @Volatile
    var lastSuspendedAt: Long = 0L
        private set

    /** Whether a game was suspended within the last [windowMs]. */
    fun recentlySuspended(windowMs: Long = 2_500L): Boolean =
        System.currentTimeMillis() - lastSuspendedAt < windowMs

    /**
     * Ends an external emulator's session after that emulator is stopped: set by the dual-screen
     * manager, which owns the session end and its save sync.
     */
    @Volatile
    var externalCloser: (suspend (packageName: String) -> Unit)? = null

    /**
     * An external emulator (RetroArch and the like) left the screen while its session is still
     * open: like Cocoon, the game stays running in that app and the launcher shows it suspended.
     * The play session stays open, so resuming returns to it and closing runs the save sync.
     */
    fun suspendExternal(gameId: Long, title: String, platformName: String, packageName: String, emulatorName: String?) {
        val current = _sessionState.value
        if (current != null && current.gameId == gameId && !current.builtIn) return
        val now = System.currentTimeMillis()
        _sessionState.value = SuspendedGameSession(
            gameId = gameId,
            title = title,
            platformName = platformName,
            packageName = packageName,
            processPid = 0,
            startTimeMillis = now,
            elapsedTimeSeconds = 0,
            screenshotPath = null,
            builtIn = false,
            emulatorName = emulatorName
        )
        lastSuspendedAt = now
        _processState.value = ProcessState.SUSPENDED
        startTimer(now)
        Logger.info(TAG, "Suspended '$title' in $packageName")
    }

    /** The external game was brought back to the front. */
    fun onExternalResumed(gameId: Long) {
        val session = _sessionState.value ?: return
        if (session.builtIn || session.gameId != gameId) return
        stopTimer()
        _sessionState.value = null
        _processState.value = ProcessState.RUNNING
    }

    /** The play session ended (any way at all): an external card no longer has a game behind it. */
    fun onSessionEnded() {
        val session = _sessionState.value ?: return
        if (!session.builtIn) clear(session)
    }

    /**
     * Quits the suspended game, built-in or external, and returns once it is gone and its session
     * has ended (save sync included), so another game can start straight after.
     */
    suspend fun closeAndWait() {
        val session = _sessionState.value ?: return
        if (session.builtIn) {
            closeBuiltInAndWait()
            return
        }
        clear(session)
        externalCloser?.invoke(session.packageName)
    }

    /** Brings the suspended game back to the front, exactly where it was left. */
    open fun resumeSession() {
        val session = _sessionState.value ?: return
        if (!session.builtIn) return
        val resumeRequest = suspendedDisplayId?.let { displayId ->
            com.nendo.argosy.ui.dualscreen.BuiltInResumeRequest(
                gameId = session.gameId,
                displayId = displayId,
                requestedAtMillis = System.currentTimeMillis()
            )
        }
        if (resumeRequest != null) {
            _resumeRequest.value = resumeRequest
            scope.launch {
                delay(RESUME_REQUEST_TIMEOUT_MS)
                _resumeRequest.update { current ->
                    if (current == resumeRequest) null else current
                }
            }
        }
        val test = com.nendo.argosy.ui.cocoon.CocoonSuspendTest
        if (test.suspendedGameId == session.gameId && test.cardShown && test.picture(session.gameId) != null) {
            test.resumeInProgress.value = true
            // TEST: the card grows back to the game's size first, then the game comes forward.
            if (test.growThen.value == null) {
                com.nendo.argosy.util.ArcoonTrace.log("test resume: asking the card to grow back first")
                val once = java.util.concurrent.atomic.AtomicBoolean(false)
                test.growThen.value = {
                    if (once.compareAndSet(false, true)) bringBuiltInToFront(session, armVeilAfter = true)
                }
            }
            return
        }
        if (test.suspendedGameId == session.gameId) {
            com.nendo.argosy.util.ArcoonTrace.log(
                "test resume: no grow (card shown=${test.cardShown}, picture=${test.picture(session.gameId) != null})"
            )
        }
        bringBuiltInToFront(session, armVeilAfter = false)
    }

    private fun bringBuiltInToFront(session: SuspendedGameSession, armVeilAfter: Boolean) {
        val task = builtInTask()
        if (task == null) {
            Logger.warn(TAG, "Suspended game task is gone; clearing '${session.title}'")
            clear(session)
            return
        }
        // Like a launch, the game comes back with Argosy's own transition rather than the
        // system's sideways slide: it grows back out of its card. AppTask.moveToFront() cannot
        // carry animation options, so the task is moved through the activity manager instead.
        // Brought back the way a launch brings a game up: a start with NO_ANIMATION, here into
        // the game's own task, so the paused activity is reordered to the front (onNewIntent with
        // no action, which the game ignores) instead of the system sliding its task in.
        // After the test grow the launcher must not go black while still in view, so the veil
        // (which hides a later Home slide) is armed once the game already covers it.
        if (!armVeilAfter) com.nendo.argosy.ui.cocoon.CocoonVeil.arm()
        val moved = runCatching {
            val intent = android.content.Intent(context, LibretroActivity::class.java).addFlags(
                android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            val options = android.app.ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
            task.startActivity(context, intent, options)
        }.isSuccess
        if (!moved) {
            runCatching { task.moveToFront() }
                .onFailure { Logger.error(TAG, "Could not bring '${session.title}' to the front", it) }
        }
        if (armVeilAfter) {
            scope.launch(Dispatchers.Main) {
                delay(VEIL_AFTER_GROW_MS)
                com.nendo.argosy.ui.cocoon.CocoonVeil.arm()
            }
        }
    }

    /** Quits the suspended game through its normal shutdown (save written, session ended). */
    open fun closeSession() {
        val session = _sessionState.value ?: return
        if (session.builtIn) {
            builtInTask()?.let { runCatching { it.finishAndRemoveTask() } }
        }
        clear(session)
    }

    /**
     * Before another game starts in the built-in emulator: only one core can be loaded at a time,
     * so a suspended game is closed and its activity given time to tear down first.
     */
    suspend fun closeBuiltInAndWait() {
        val session = _sessionState.value?.takeIf { it.builtIn } ?: return
        withContext(Dispatchers.Main) {
            builtInTask()?.let { runCatching { it.finishAndRemoveTask() } }
        }
        clear(session)
        var waited = 0L
        while (builtInTask() != null && waited < CLOSE_WAIT_MS) {
            delay(CLOSE_POLL_MS)
            waited += CLOSE_POLL_MS
        }
    }

    private fun clear(session: SuspendedGameSession) {
        stopTimer()
        _resumeRequest.update { current -> current?.takeIf { it.gameId != session.gameId } }
        session.screenshotPath?.let { path -> runCatching { File(path).delete() } }
        _sessionState.value = null
        _processState.value = ProcessState.IDLE
    }

    fun acknowledgeResumeRequest(request: com.nendo.argosy.ui.dualscreen.BuiltInResumeRequest) {
        _resumeRequest.update { current -> if (current == request) null else current }
    }

    private fun builtInTask(): ActivityManager.AppTask? {
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        val gameActivity = LibretroActivity::class.java.name
        return am.appTasks.firstOrNull { task ->
            val info = runCatching { task.taskInfo }.getOrNull() ?: return@firstOrNull false
            info.baseActivity?.className == gameActivity || info.topActivity?.className == gameActivity
        }
    }

    private fun startTimer(startTimeMillis: Long) {
        stopTimer()
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                val elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000
                _sessionState.update { current -> current?.copy(elapsedTimeSeconds = elapsed) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private suspend fun saveSnapshotToDisk(gameId: Long, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "snapshots").apply { mkdirs() }
        val snapshotFile = File(cacheDir, "suspended_${gameId}.jpg")
        FileOutputStream(snapshotFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        snapshotFile.absolutePath
    }
}
