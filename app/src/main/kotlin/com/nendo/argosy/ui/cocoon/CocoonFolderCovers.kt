package com.nendo.argosy.ui.cocoon

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.CocoonSurface

/**
 * Cocoon's cover grid inside a folder: [rows] rows of square covers filled top to bottom and
 * growing to the right. With one row the covers sit low and the focused game's name card floats
 * above them. Changing [rows] slides every cover to its new place.
 */
@Composable
fun CocoonFolderCovers(
    keys: List<Long>,
    focusedIndex: Int,
    rows: Int,
    topPadding: Dp,
    focusedTitle: String?,
    focusedSubtitle: String?,
    modifier: Modifier = Modifier,
    wave: CocoonCoverWave? = null,
    cell: @Composable (index: Int, size: Dp) -> Unit,
) {
    val state = rememberLazyGridState()
    val count = keys.size
    // R3 changes the row count: for that one change, every cover slides to its new place (as in
    // Cocoon). Ordinary browsing runs with no placement animation, so it stays at full speed.
    val reflow = androidx.compose.runtime.remember { ReflowWindow(rows) }
    if (reflow.rows != rows) {
        reflow.rows = rows
        reflow.until = android.os.SystemClock.uptimeMillis() + 500
    }
    val animateReflow = android.os.SystemClock.uptimeMillis() < reflow.until
    BoxWithConstraints(modifier = modifier) {
        val gap = if (rows >= 5) 8.dp else 14.dp
        val oneRow = rows == 1
        val usable = maxHeight - topPadding
        val gridHeight = if (oneRow) usable * 0.46f else usable
        val cellSize = ((gridHeight - gap * (rows + 1)) / rows).coerceAtLeast(36.dp)
        val boxWidth = maxWidth
        val visibleColumns = ((boxWidth - gap) / (cellSize + gap)).toInt().coerceAtLeast(1)

        LaunchedEffect(focusedIndex, rows, visibleColumns) {
            if (count == 0) return@LaunchedEffect
            val column = focusedIndex / rows
            val first = state.firstVisibleItemIndex / rows
            val last = first + visibleColumns - 1
            when {
                column < first -> state.animateScrollToItem(column * rows)
                column > last -> state.animateScrollToItem((column - visibleColumns + 1).coerceAtLeast(0) * rows)
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
            if (oneRow) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (focusedTitle != null) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = boxWidth * 0.7f)
                                .background(CocoonSurface.Chip, RoundedCornerShape(16.dp))
                                .padding(horizontal = 22.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = focusedTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = CocoonSurface.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            if (focusedSubtitle != null) {
                                Text(
                                    text = focusedSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CocoonSurface.TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            LazyHorizontalGrid(
                rows = GridCells.Fixed(rows),
                state = state,
                contentPadding = PaddingValues(gap),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.fillMaxWidth().height(gridHeight)
            ) {
                // Keys come from the same list snapshot as the count, so a list that changes under
                // the grid (R2 to the next platform) can never be read past its end.
                items(count, key = { keys[it] }) { index ->
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .then(
                                if (wave != null) Modifier.graphicsLayer {
                                    // position in view: columns from the left, then rows downward
                                    val column = index / rows - state.firstVisibleItemIndex / rows
                                    val p = wave.progress(column.coerceAtLeast(0), index % rows)
                                    alpha = p
                                    val scale = 0.84f + 0.16f * p
                                    scaleX = scale
                                    scaleY = scale
                                    translationY = (1f - p) * 18.dp.toPx()
                                } else Modifier
                            )
                            .then(
                                if (animateReflow) {
                                    Modifier.animateItem(
                                        fadeInSpec = null,
                                        placementSpec = tween<IntOffset>(300, easing = com.nendo.argosy.ui.theme.Motion.argosyEase),
                                        fadeOutSpec = null
                                    )
                                } else Modifier
                            )
                            .then(
                                if (index == focusedIndex) {
                                    Modifier.onGloballyPositioned {
                                        CocoonSessionDialog.origin = it.boundsInRoot()
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            com.nendo.argosy.ui.components.LocalCoverRequestSize provides COCOON_COVER_DECODE_PX
                        ) {
                            cell(index, cellSize)
                        }
                    }
                }
            }
        }
    }
}


/**
 * Covers arriving in a wave when a folder opens (left to right, each column a beat after the one
 * before) and leaving in the reverse wave when it closes. [clock] runs in milliseconds.
 */
class CocoonCoverWave {
    val clock = androidx.compose.animation.core.Animatable(0f)
    var leaving by androidx.compose.runtime.mutableStateOf(false)
    var done by androidx.compose.runtime.mutableStateOf(false)
    /** Whether the entrance has started (it plays once per visit). */
    var played = false
        private set

    fun skipIn() {
        played = true
        done = true
    }

    fun progress(column: Int, row: Int): Float {
        if (done && !leaving) return 1f
        val t = clock.value
        return if (!leaving) {
            val start = column * COLUMN_STEP + row * ROW_STEP
            ((t - start) / IN_MS).coerceIn(0f, 1f).let { androidx.compose.animation.core.FastOutSlowInEasing.transform(it) }
        } else {
            // the right-most columns leave first, so the wave runs back toward the folder
            val start = (MAX_COLUMNS - 1 - column.coerceAtMost(MAX_COLUMNS - 1)) * OUT_STEP
            1f - ((t - start) / OUT_MS).coerceIn(0f, 1f)
        }
    }

    suspend fun playIn() {
        played = true
        leaving = false
        done = false
        clock.snapTo(0f)
        clock.animateTo(IN_TOTAL, androidx.compose.animation.core.tween(IN_TOTAL.toInt(), easing = androidx.compose.animation.core.LinearEasing))
        done = true
    }

    suspend fun playOut() {
        leaving = true
        clock.snapTo(0f)
        clock.animateTo(OUT_TOTAL, androidx.compose.animation.core.tween(OUT_TOTAL.toInt(), easing = androidx.compose.animation.core.LinearEasing))
    }

    companion object {
        private const val COLUMN_STEP = 34f
        private const val ROW_STEP = 14f
        private const val IN_MS = 240f
        private const val MAX_COLUMNS = 8
        private const val OUT_STEP = 14f
        private const val OUT_MS = 120f
        const val IN_TOTAL = MAX_COLUMNS * COLUMN_STEP + 6 * ROW_STEP + IN_MS
        const val OUT_TOTAL = MAX_COLUMNS * OUT_STEP + OUT_MS
    }
}

/** Folder covers and the Home prefetch decode at this size, so they share one cached picture. */
const val COCOON_COVER_DECODE_PX = 512

/** Remembers the last row count and until when a change of it should animate. */
private class ReflowWindow(var rows: Int) {
    var until: Long = 0L
}
