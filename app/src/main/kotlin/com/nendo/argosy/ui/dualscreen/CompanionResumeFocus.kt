package com.nendo.argosy.ui.dualscreen

fun companionTakesFocusOnFirstResume(sessionActive: Boolean, gameActive: Boolean, launchLive: Boolean): Boolean =
    !sessionActive && !gameActive && !launchLive
