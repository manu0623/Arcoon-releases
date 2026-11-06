package com.nendo.argosy.ui.input

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The app's haptic manager, for a composable that has to feel an animation of its own rather than
 * a key press. Null on surfaces that have no manager, where the cue is simply skipped.
 */
val LocalHaptics = staticCompositionLocalOf<HapticFeedbackManager?> { null }
