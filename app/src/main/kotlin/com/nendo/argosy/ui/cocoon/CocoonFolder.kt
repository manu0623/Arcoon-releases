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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.preferences.BoxArtInnerEffect
import com.nendo.argosy.data.preferences.BoxArtOuterEffect
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.theme.BoxArtStyleConfig
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus

/** Cocoon folders hold five columns of square covers. */
const val COCOON_FOLDER_COLUMNS = 5
const val COCOON_FOLDER_GAP_DP = 16

/**
 * Cover art as Cocoon draws it inside a folder: square, softly rounded, no border, no glow, no
 * platform badge. The selection is shown by the corner brackets alone.
 */
fun cocoonCoverStyle(base: BoxArtStyleConfig): BoxArtStyleConfig = base.copy(
    aspectRatio = 1f,
    nativeAspectRatio = false,
    cornerRadiusDp = 12.dp,
    borderThicknessDp = 0.dp,
    glowAlpha = 0f,
    outerEffect = BoxArtOuterEffect.OFF,
    innerEffect = BoxArtInnerEffect.OFF,
    // No platform tab or spine: the spine squeezed the cover into a tall strip beside a coloured
    // band. The platform shows in the name card instead.
    platformIndicatorStyle = com.nendo.argosy.data.preferences.PlatformIndicatorStyle.OFF,
)

/**
 * The header row inside a folder: a dark B/back pill, the folder name in large bold, and on the
 * right the sort chip and the list/grid toggle.
 */
@Composable
fun CocoonFolderHeader(
    title: String,
    sortLabel: String?,
    onBack: (() -> Unit)?,
    onSort: () -> Unit,
    onToggleLayout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CocoonSurface.Background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .background(CocoonSurface.Chip, RoundedCornerShape(50))
                .then(if (onBack != null) Modifier.clickableNoFocus(onClick = onBack) else Modifier)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InputGlyph(button = InputButton.B, size = 18.dp, tint = CocoonSurface.TextSecondary)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = CocoonSurface.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = CocoonSurface.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (sortLabel != null) {
            Row(
                modifier = Modifier
                    .background(CocoonSurface.Chip, RoundedCornerShape(50))
                    .clickableNoFocus(onClick = onSort)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    tint = CocoonSurface.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = sortLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = CocoonSurface.TextPrimary,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(CocoonSurface.Chip, CircleShape)
                .clickableNoFocus(onClick = onToggleLayout),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = null,
                tint = CocoonSurface.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Height the folder header takes, so the cover grid can start just under it. */
val COCOON_FOLDER_HEADER_HEIGHT = 60.dp

/**
 * Rows of covers inside a folder. Cocoon's folder grid runs sideways like the board; R3 cycles
 * 2 → 3 → 4 → 5 → 6 → 1 (one big row with the game's name above) → 2.
 */
object CocoonFolderGrid {
    private val sizes = listOf(2, 3, 4, 5, 6, 1)
    val rows = kotlinx.coroutines.flow.MutableStateFlow(3)
    fun cycle() {
        val i = sizes.indexOf(rows.value)
        rows.value = sizes[(i + 1).mod(sizes.size)]
    }

    /** The cover the d-pad lands on in a column-major grid of [count] covers, or null at an edge. */
    fun move(index: Int, count: Int, rows: Int, dRow: Int, dCol: Int): Int? {
        if (count == 0) return null
        val row = index % rows
        return when {
            // Up/Down step through the covers. Ending at the top of a column used to read as an
            // edge and lift the cursor onto the dock while games were still above-left of it.
            dRow < 0 -> if (index > 0) index - 1 else null
            dRow > 0 -> if (index + 1 < count) index + 1 else null
            dCol < 0 -> if (index - rows >= 0) index - rows else null
            dCol > 0 -> when {
                index + rows < count -> index + rows
                index / rows < (count - 1) / rows -> count - 1
                else -> null
            }
            else -> null
        }
    }
}

/** Lets the folder's input wrapper drive the sideways cover grid. */
interface CocoonGridNavigation {
    /** True while the cover grid (not a menu or list) has the d-pad. */
    fun active(): Boolean
    fun move(dRow: Int, dCol: Int): Boolean
}

/** Whether the top screen shows the game's info panel; off means art and logo only. */
/**
 * The top screen's X panel. X opens the game's info, a second X turns the panel to the game's
 * journey and a third closes it. [scroll] carries right-stick steps for the journey page.
 */
object CocoonHeroInfo {
    val visible = kotlinx.coroutines.flow.MutableStateFlow(false)
    val journey = kotlinx.coroutines.flow.MutableStateFlow(false)
    val scroll = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 8)

    fun toggle() {
        when {
            !visible.value -> {
                journey.value = false
                visible.value = true
            }
            !journey.value -> journey.value = true
            else -> {
                visible.value = false
                journey.value = false
            }
        }
    }

    /** Scrolls the journey page by [step] when it is the one showing. */
    fun scrollJourney(step: Int): Boolean {
        if (!visible.value || !journey.value) return false
        scroll.tryEmit(step)
        return true
    }
}

/** Height the dock row takes above a folder's header. */
val COCOON_DOCK_HEIGHT = 48.dp

/** Which dock slot holds the cursor inside a folder (0 = All apps), or null while on the covers. */
object CocoonFolderDock {
    val focus = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
}
