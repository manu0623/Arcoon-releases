package com.nendo.argosy.ui.cocoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus

private enum class HomeSymbolRow(val labelRes: Int) {
    POSITION(R.string.cocoon_home_symbol_position),
    SIZE(R.string.cocoon_home_symbol_size),
    CLARITY(R.string.cocoon_home_symbol_clarity),
}

/** How far one press of the stick moves the symbol, as a fraction of the screen. */
private const val MOVE_STEP = 0.02f
private const val CLARITY_STEP = 0.06f
private val HANDLE_SIZES = listOf("SMALL", "MEDIUM", "LARGE")

/**
 * Home Symbol: where the in-game handle that suspends a running game sits, how big it is and how
 * visible it is. The position is a point on the screen rather than a corner, because the bar a
 * game's picture leaves free is rarely a corner and a corner is often under the thumb holding the
 * console. The symbol is placed by moving it and pressing A, which is also the only way to know it
 * clears the game's touch panel.
 */
@Composable
fun CocoonHomeSymbolPage(onDismiss: () -> Unit): InputHandler {
    val context = LocalContext.current
    var focus by remember { mutableIntStateOf(0) }
    var placing by remember { mutableStateOf(false) }
    var placedX by remember { mutableFloatStateOf(CocoonHomeStore.handleX) }
    var placedY by remember { mutableFloatStateOf(CocoonHomeStore.handleY) }
    val dismiss by rememberUpdatedState(onDismiss)

    fun commit(x: Float = placedX, y: Float = placedY, size: String = CocoonHomeStore.handleSize, clarity: Float = CocoonHomeStore.handleClarity) {
        CocoonHomeStore.setHandle(context, size = size, x = x, y = y, clarity = clarity)
    }

    fun stepRow(delta: Int): InputResult {
        val next = focus + delta
        if (next !in HomeSymbolRow.entries.indices) return InputResult.handled(SoundType.BOUNDARY)
        focus = next
        return InputResult.HANDLED
    }

    fun moveBy(dx: Float, dy: Float): InputResult {
        placedX = (placedX + dx).coerceIn(0f, 1f)
        placedY = (placedY + dy).coerceIn(0f, 1f)
        return InputResult.HANDLED
    }

    fun cycle(delta: Int): InputResult = when (HomeSymbolRow.entries[focus]) {
        // The position is not a value to step through: A opens the placement.
        HomeSymbolRow.POSITION -> InputResult.HANDLED
        HomeSymbolRow.SIZE -> {
            val index = HANDLE_SIZES.indexOf(CocoonHomeStore.handleSize).coerceAtLeast(0)
            commit(size = HANDLE_SIZES[(index + delta).mod(HANDLE_SIZES.size)])
            InputResult.handled(SoundType.TOGGLE)
        }
        HomeSymbolRow.CLARITY -> {
            commit(clarity = (CocoonHomeStore.handleClarity + delta * CLARITY_STEP).coerceIn(0.05f, 0.6f))
            InputResult.handled(SoundType.TOGGLE)
        }
    }

    val handler = remember {
        object : InputHandler {
            override fun onUp(): InputResult = if (placing) moveBy(0f, -MOVE_STEP) else stepRow(-1)
            override fun onDown(): InputResult = if (placing) moveBy(0f, MOVE_STEP) else stepRow(1)
            override fun onLeft(): InputResult = if (placing) moveBy(-MOVE_STEP, 0f) else cycle(-1)
            override fun onRight(): InputResult = if (placing) moveBy(MOVE_STEP, 0f) else cycle(1)

            override fun onConfirm(): InputResult {
                if (placing) {
                    commit()
                    placing = false
                    return InputResult.handled(SoundType.TOGGLE)
                }
                return when (HomeSymbolRow.entries[focus]) {
                    HomeSymbolRow.POSITION -> {
                        placedX = CocoonHomeStore.handleX
                        placedY = CocoonHomeStore.handleY
                        placing = true
                        InputResult.handled(SoundType.OPEN_MODAL)
                    }
                    else -> cycle(1)
                }
            }

            override fun onBack(): InputResult {
                if (placing) {
                    placing = false
                    return InputResult.HANDLED
                }
                dismiss()
                return InputResult.HANDLED
            }

            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickableNoFocus { }) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Text(
                stringResource(R.string.cocoon_home_symbol_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = CocoonSurface.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.cocoon_home_symbol_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = CocoonSurface.TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            HomeSymbolRow.entries.forEachIndexed { index, row ->
                val focused = !placing && focus == index
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(
                            if (focused) CocoonSurface.Chip else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickableNoFocus {
                            focus = index
                            if (row == HomeSymbolRow.POSITION) {
                                placedX = CocoonHomeStore.handleX
                                placedY = CocoonHomeStore.handleY
                                placing = true
                            } else {
                                cycle(1)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(row.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = CocoonSurface.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = when (row) {
                            HomeSymbolRow.POSITION -> stringResource(
                                R.string.cocoon_home_symbol_value_position,
                                (CocoonHomeStore.handleX * 100).toInt(),
                                (CocoonHomeStore.handleY * 100).toInt()
                            )
                            HomeSymbolRow.SIZE -> stringResource(
                                when (CocoonHomeStore.handleSize) {
                                    "LARGE" -> R.string.cocoon_size_large
                                    "MEDIUM" -> R.string.cocoon_size_medium
                                    else -> R.string.cocoon_size_small
                                }
                            )
                            HomeSymbolRow.CLARITY -> stringResource(
                                when {
                                    CocoonHomeStore.handleClarity <= 0.12f -> R.string.cocoon_home_symbol_clarity_faint
                                    CocoonHomeStore.handleClarity >= 0.3f -> R.string.cocoon_home_symbol_clarity_clear
                                    else -> R.string.cocoon_home_symbol_clarity_normal
                                }
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = CocoonSurface.TextSecondary
                    )
                }
            }

            if (placing) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    stringResource(R.string.cocoon_home_symbol_place_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = CocoonSurface.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                // A stand-in for the screen the symbol is drawn on: the game's picture, with the
                // handle exactly where it will be.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.66f)
                        .height(180.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1B2A4A), Color(0xFF35607A), Color(0xFF7FA98C))
                            ),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
                        val box = cocoonHandleTargetDp().dp
                        val glyph = cocoonHandleIconDp().dp
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = (maxWidth - box) * placedX,
                                    y = (maxHeight - box) * placedY
                                )
                                .size(box),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Home,
                                contentDescription = stringResource(R.string.ingame_home_handle),
                                tint = Color.White.copy(alpha = CocoonHomeStore.handleClarity),
                                modifier = Modifier.size(glyph)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Home,
                    contentDescription = null,
                    tint = CocoonSurface.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.ingame_home_handle),
                    style = MaterialTheme.typography.bodySmall,
                    color = CocoonSurface.TextSecondary
                )
            }
        }
    }
    return handler
}
