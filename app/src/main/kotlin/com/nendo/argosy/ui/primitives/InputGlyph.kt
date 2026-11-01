package com.nendo.argosy.ui.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.toPainter
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
fun InputGlyph(
    button: InputButton,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.iconSm,
    tint: Color? = null,
) {
    val effectiveTint = tint ?: LocalArgosyTheme.current.textDim
    // Cocoon draws face buttons as a small round keycap with the button's letter.
    val letter = when (button) {
        InputButton.A -> "A"
        InputButton.B -> "B"
        InputButton.X -> "X"
        InputButton.Y -> "Y"
        else -> null
    }
    if (letter != null) {
        val fontSize = with(LocalDensity.current) { (size * 0.58f).toSp() }
        Box(
            modifier = modifier
                .size(size)
                .background(effectiveTint.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                color = effectiveTint,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                lineHeight = fontSize
            )
        }
        return
    }
    val painter = button.toPainter() ?: return
    Icon(
        painter = painter,
        contentDescription = button.name,
        tint = effectiveTint,
        modifier = modifier.size(size),
    )
}
