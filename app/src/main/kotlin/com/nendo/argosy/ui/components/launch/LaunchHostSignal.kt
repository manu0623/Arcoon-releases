package com.nendo.argosy.ui.components.launch

/**
 * What the screen hosting the launch overlay just did: the overlay composed, the host started or
 * stopped, or the overlay left composition.
 */
enum class LaunchHostSignal { COMPOSED, STARTED, STOPPED, LEFT_COMPOSITION }

/**
 * Whether [signal] tells the launch tracker that its host stopped. A stop or a departure counts
 * only when the host activity is not being recreated; a return, the overlay composing or the host
 * starting again, counts only once a real stop was recorded, [stopRecorded].
 */
fun shouldReportHostStopped(
    isChangingConfigurations: Boolean,
    signal: LaunchHostSignal,
    stopRecorded: Boolean
): Boolean = when (signal) {
    LaunchHostSignal.STOPPED, LaunchHostSignal.LEFT_COMPOSITION -> !isChangingConfigurations
    LaunchHostSignal.COMPOSED, LaunchHostSignal.STARTED -> stopRecorded
}

/**
 * Whether a stop stays recorded after [signal] was reported: a stop or a departure records one,
 * and a return consumes it.
 */
fun recordsHostStop(signal: LaunchHostSignal): Boolean =
    signal == LaunchHostSignal.STOPPED || signal == LaunchHostSignal.LEFT_COMPOSITION
