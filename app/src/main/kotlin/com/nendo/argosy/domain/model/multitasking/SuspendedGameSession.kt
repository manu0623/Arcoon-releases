package com.nendo.argosy.domain.model.multitasking

/**
 * Lifecycle states of an emulator background process session.
 */
enum class ProcessState {
    IDLE,
    RUNNING,
    SUSPENDED
}

/**
 * Snapshot data model for a suspended background game session.
 * Complies with Argosy's domain rules (pure Kotlin, Compose-free, all val fields).
 */
data class SuspendedGameSession(
    val gameId: Long,
    val title: String,
    val platformName: String,
    val packageName: String,
    val processPid: Int,
    val startTimeMillis: Long,
    val elapsedTimeSeconds: Long,
    val screenshotPath: String? = null,
    val achievementsUnlocked: Int = 0,
    val achievementsTotal: Int = 0,
    /** True for Argosy's built-in emulator, which pauses inside Argosy's own process. */
    val builtIn: Boolean = false,
    /** The app the game runs in, as Cocoon names it ("RetroArch (AArch64)"). */
    val emulatorName: String? = null
) {
    val achievementProgress: Float
        get() = if (achievementsTotal > 0) achievementsUnlocked.toFloat() / achievementsTotal else 0f

    val formattedElapsedTime: String
        get() {
            val minutes = elapsedTimeSeconds / 60
            val seconds = elapsedTimeSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}

/**
 * UI State representation for the upper screen secondary display.
 */
data class TopScreenUiState(
    val suspendedSession: SuspendedGameSession? = null,
    val processState: ProcessState = ProcessState.IDLE,
    val highlightedGameId: Long? = null,
    val highlightedHeroUrl: String? = null,
    val isSuspendedGameFocused: Boolean = false
) {
    val isCardCentered: Boolean
        get() = suspendedSession != null && isSuspendedGameFocused
}
