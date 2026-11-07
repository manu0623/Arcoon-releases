package com.nendo.argosy.ui.components

import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.compositeOver
import com.nendo.argosy.ui.cocoon.cocoonPopulate
import com.nendo.argosy.ui.primitives.cocoonBrackets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.util.clickableNoFocus

internal const val DRAWER_FOCUS_WASH_ALPHA = 0.15f
private const val NAV_ROW_SELECTED_FILL_ALPHA = 0.5f
private const val NAV_ROW_DISABLED_ALPHA = 0.4f

/**
 * Navigation drawer row: a primary left indicator while focused, the icon, then the label.
 * Setting [labelVisible] to false animates the label and [trailing] out so the row narrows
 * to its icon. A disabled row keeps its focus visuals and dims its content.
 */
@Composable
fun NavDrawerRow(
    icon: ImageVector,
    label: String,
    isFocused: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelVisible: Boolean = true,
    isFirst: Boolean = true,
    isLast: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    // Cocoon drawer row: a dark rounded card per entry, white icon and label, corner brackets on
    // the focused row; the screen you are on reads slightly brighter.
    // Looks like the Y menu (CocoonMenuRow): the rows are one dark panel, round only at its outer
    // corners and split by hairlines; the focused row lifts a shade lighter, the screen you are
    // on a little less.
    val panel = com.nendo.argosy.ui.cocoon.CocoonMenuPanel
    val backgroundColor = when {
        isFocused -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f).compositeOver(panel)
        isSelected -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.04f).compositeOver(panel)
        else -> panel
    }
    val baseContentColor = com.nendo.argosy.ui.theme.CocoonSurface.TextPrimary
    val contentColor = if (enabled) baseContentColor else baseContentColor.copy(alpha = NAV_ROW_DISABLED_ALPHA)
    val shape = RoundedCornerShape(
        topStart = if (isFirst) 18.dp else 0.dp,
        topEnd = if (isFirst) 18.dp else 0.dp,
        bottomStart = if (isLast) 18.dp else 0.dp,
        bottomEnd = if (isLast) 18.dp else 0.dp
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingSm, top = if (isFirst) 3.dp else 0.dp, bottom = if (isLast) 3.dp else 0.dp)
            .zIndex(if (isFocused) 1f else 0f)
            .cocoonBrackets(focused = isFocused, outset = 3.dp)
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (!isLast) Modifier.drawBehind {
                    val inset = 14.dp.toPx()
                    drawRect(
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f),
                        topLeft = androidx.compose.ui.geometry.Offset(inset, size.height - 1.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - inset * 2, 1.dp.toPx())
                    )
                } else Modifier
            )
            // Drop-down entrance: the card is there at once, its content populates.
            .cocoonPopulate()
            .clickableNoFocus(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(26.dp)
        )
        AnimatedVisibility(
            visible = labelVisible,
            modifier = Modifier.weight(1f),
            enter = expandHorizontally(tween(Motion.durationSlide, easing = Motion.argosyEase)) +
                fadeIn(tween(Motion.durationContent)),
            exit = shrinkHorizontally(tween(Motion.durationSlide, easing = Motion.argosyEase)) +
                fadeOut(tween(Motion.durationContent))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                trailing()
            }
        }
    }
}
