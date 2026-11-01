package com.nendo.argosy.ui.common.savechannel

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens

@Composable
internal fun Modifier.saveRowFocus(selected: Boolean): Modifier {
    val shape = RoundedCornerShape(Dimens.radiusMd)
    return clip(shape).argosyFocusIndicators(
        focused = selected,
        indicators = FocusIndicators.ListRow,
        shape = shape
    )
}
