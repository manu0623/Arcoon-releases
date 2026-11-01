package com.nendo.argosy.data.emulator

/**
 * The arguments of an external emulator's [PlaySessionTracker.startSession], held until its
 * window has been requested.
 */
data class ExternalSessionStart(
    val gameId: Long,
    val emulatorPackage: String,
    val coreName: String?,
    val variantFileId: Long?,
    val origin: LaunchOrigin
)

fun PlaySessionTracker.startExternal(session: ExternalSessionStart) {
    startSession(
        gameId = session.gameId,
        emulatorPackage = session.emulatorPackage,
        coreName = session.coreName,
        isNewGame = true,
        variantFileId = session.variantFileId,
        origin = session.origin
    )
}
