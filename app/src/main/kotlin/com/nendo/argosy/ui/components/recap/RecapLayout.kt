package com.nendo.argosy.ui.components.recap

import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlin.math.roundToInt

object RecapLayout {
    fun isShort(heightDp: Float): Boolean = heightDp < ComponentDefaults.UnlockRecap.shortHeightDp

    fun unlockRowFits(count: Int, tileDp: Float, gapDp: Float, widthDp: Float): Boolean {
        if (count <= 0) return true
        return count * tileDp + (count - 1) * gapDp <= widthDp
    }

    fun bodyScrollTarget(from: Int, direction: Int, steps: Int, stepPx: Float, maxValue: Int): Int =
        (from + direction * steps * stepPx).roundToInt().coerceIn(0, maxValue.coerceAtLeast(0))
}
