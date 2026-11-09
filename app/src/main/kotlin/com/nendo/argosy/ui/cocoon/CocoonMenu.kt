package com.nendo.argosy.ui.cocoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus

/** The fill of Cocoon's pop-up menus: one dark panel the rows sit in, split by hairlines. */
val CocoonMenuPanel: Color
    get() = if (com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.settings.glass) Color(0x8C202024) else Color(0xFF181818)

/**
 * One row of a Cocoon pop-up menu (the Y menu on a folder or a game). Rows stack with no gap so
 * together they read as one rounded panel; the focused row lifts a shade lighter inside the big
 * corner brackets.
 */
@Composable
fun CocoonMenuRow(
    icon: ImageVector?,
    label: String,
    focused: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    color: Color = CocoonSurface.TextPrimary,
    trailing: (@Composable () -> Unit)? = null,
) {
    val r = 18.dp
    val shape = RoundedCornerShape(
        topStart = if (isFirst) r else 0.dp,
        topEnd = if (isFirst) r else 0.dp,
        bottomStart = if (isLast) r else 0.dp,
        bottomEnd = if (isLast) r else 0.dp
    )
    val lift = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            com.nendo.argosy.ui.primitives.CocoonBracketSpec.glideMs,
            easing = com.nendo.argosy.ui.primitives.CocoonBracketSpec.glideEasing
        ),
        label = "cocoon-menu-lift"
    ).value
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // The cursor is drawn outside the row. Without this the next row's card, painted
            // after it, covers the bottom arms and the brackets look cut off.
            .zIndex(if (focused) 1f else 0f)
            .cocoonBrackets(focused = focused, outset = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CocoonMenuPanel, shape)
                .background(Color.White.copy(alpha = 0.08f * lift), shape)
                // Drop-down entrance: the row's card is there at once, its content populates.
                .cocoonPopulate()
                .clickableNoFocus(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
        if (!isLast) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.07f))
            )
        }
    }
}
