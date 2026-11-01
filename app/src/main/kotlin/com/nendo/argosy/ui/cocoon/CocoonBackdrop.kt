package com.nendo.argosy.ui.cocoon

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * A still of the screen a Cocoon sheet opens over (Y ▸ Edit / Achievements). The sheet draws it,
 * blurred, behind itself, so opening and closing the sheet doesn't flash to a different screen.
 */
object CocoonBackdrop {
    val image = mutableStateOf<ImageBitmap?>(null)
}
