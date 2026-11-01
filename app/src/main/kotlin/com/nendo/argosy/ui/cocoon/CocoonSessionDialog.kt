package com.nendo.argosy.ui.cocoon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cocoon's game-session dialogs on the bottom screen: "Quit game" for the suspended game and
 * "Switch games" when another game is started while one is suspended. The panel grows out of the
 * focused cover while everything behind it blurs, then its content fades in.
 */
object CocoonSessionDialog {
    sealed interface Kind
    data class Quit(val gameId: Long, val title: String, val fallbackCover: String?) : Kind
    data class Switch(
        val fromGameId: Long,
        val fromTitle: String,
        val toGameId: Long,
        val toCover: String?,
        val emulatorName: String,
        val onLaunch: () -> Unit
    ) : Kind

    val kind = mutableStateOf<Kind?>(null)
    val visible = mutableStateOf(false)
    val closing = mutableStateOf(false)

    /** 0 = closed, 1 = fully open. Drives the panel, the scrim and the blur behind it. */
    val progress = Animatable(0f)

    /** Where the focused cover sits on screen, so the panel can grow out of it. */
    var origin: Rect? = null

    private val scope = SafeCoroutineScope(Dispatchers.Main.immediate, "CocoonSessionDialog")

    val isOpen: Boolean get() = kind.value != null

    /** How much of the blur behind the dialog to apply (0..1); it comes in first. */
    val blurFraction: Float get() = (progress.value / 0.25f).coerceIn(0f, 1f)

    fun show(next: Kind) {
        closing.value = false
        kind.value = next
        visible.value = true
    }

    fun dismiss() {
        if (closing.value) return
        visible.value = false
    }

    /** A: Force Quit, or Quit and launch. The button reads "Closing game…" until it is gone. */
    fun confirm() {
        val current = kind.value ?: return
        if (closing.value || !visible.value) return
        closing.value = true
        scope.launch {
            runCatching {
                com.nendo.argosy.DualScreenManagerHolder.instance?.gameSessionManager?.closeAndWait()
            }
            visible.value = false
            if (current is Switch) {
                delay(260)
                current.onLaunch()
            }
        }
    }

    /** Leaving the folder takes the sheet with it (a pending switch still launches). */
    fun reset() {
        if (closing.value) return
        kind.value = null
        visible.value = false
        scope.launch { progress.snapTo(0f) }
    }

    internal fun cleared() {
        kind.value = null
        closing.value = false
    }
}

private val PanelEarly = Color(0xFF26262B)
private val PanelLate = Color(0xFF3A3F43)
private val Mint = Color(0xFF2EDDA0)
private val MintDeep = Color(0xFF12C48C)

@Composable
fun CocoonSessionDialogHost(modifier: Modifier = Modifier) {
    val kind = CocoonSessionDialog.kind.value
    val visible = CocoonSessionDialog.visible.value
    val progress = CocoonSessionDialog.progress
    LaunchedEffect(visible, kind) {
        if (kind == null) return@LaunchedEffect
        if (visible) {
            progress.animateTo(1f, tween(((1f - progress.value) * 460).toInt().coerceAtLeast(1), easing = LinearEasing))
        } else {
            progress.animateTo(0f, tween((progress.value * 240).toInt().coerceAtLeast(1), easing = LinearEasing))
            CocoonSessionDialog.cleared()
        }
    }
    if (kind == null) return
    // The last kind stays on screen while the panel closes.
    val shown = remember { mutableStateOf(kind) }
    shown.value = kind

    var hostOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { hostOffset = it.positionInRoot() }
    ) {
        val p = progress.value
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val target = Rect(
            left = wPx * 0.1f,
            top = hPx * 0.13f,
            right = wPx * 0.9f,
            bottom = hPx * 0.97f
        )
        val start = CocoonSessionDialog.origin?.translate(-hostOffset.x, -hostOffset.y)
            ?: Rect(center = target.center, radius = wPx * 0.08f)
        val grow = FastOutSlowInEasing.transform(((p - 0.15f) / 0.5f).coerceIn(0f, 1f))
        val content = ((p - 0.55f) / 0.45f).coerceIn(0f, 1f)
        val rect = Rect(
            left = mix(start.left, target.left, grow),
            top = mix(start.top, target.top, grow),
            right = mix(start.right, target.right, grow),
            bottom = mix(start.bottom, target.bottom, grow)
        )
        val radius = androidx.compose.ui.unit.lerp(14.dp, 28.dp, grow)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f * CocoonSessionDialog.blurFraction))
        )
        if (p > 0.15f) {
            val shape = RoundedCornerShape(radius)
            with(density) {
                Box(
                    modifier = Modifier
                        .absoluteOffset(x = rect.left.toDp(), y = rect.top.toDp())
                        .size(width = rect.width.toDp(), height = rect.height.toDp())
                        .clip(shape)
                        .background(lerp(PanelEarly, PanelLate, content).copy(alpha = 0.96f))
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                1f to Mint.copy(alpha = 0.38f * content)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (content > 0f) {
                        Box(
                            modifier = Modifier
                                .requiredSize(width = target.width.toDp(), height = target.height.toDp())
                                .graphicsLayer {
                                    alpha = content
                                    val s = 0.94f + 0.06f * content
                                    scaleX = s
                                    scaleY = s
                                }
                        ) {
                            DialogContent(shown.value, CocoonSessionDialog.closing.value)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogContent(kind: CocoonSessionDialog.Kind, closing: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InputGlyph(button = InputButton.B, size = 18.dp, tint = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.cocoon_back),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(
                    if (kind is CocoonSessionDialog.Switch) R.string.cocoon_switch_games else R.string.cocoon_quit_game
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            // balances the Back pill so the title sits in the middle
            Spacer(modifier = Modifier.width(72.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (kind) {
                is CocoonSessionDialog.Quit -> {
                    DialogCover(kind.gameId, kind.fallbackCover, 116.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                is CocoonSessionDialog.Switch -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DialogCover(kind.fromGameId, null, 92.dp)
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp).size(30.dp)
                        )
                        DialogCover(kind.toGameId, kind.toCover, 92.dp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.cocoon_switch_body, kind.fromTitle, kind.emulatorName),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            Text(
                text = stringResource(R.string.cocoon_unsaved_lost),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Mint, MintDeep)))
                .graphicsLayer { alpha = if (closing) 0.85f else 1f },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!closing) {
                InputGlyph(button = InputButton.A, size = 20.dp, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = stringResource(
                    when {
                        closing -> R.string.cocoon_closing_game
                        kind is CocoonSessionDialog.Switch -> R.string.cocoon_quit_and_launch
                        else -> R.string.cocoon_force_quit
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun DialogCover(gameId: Long, fallback: String?, size: androidx.compose.ui.unit.Dp) {
    val path = com.nendo.argosy.ui.common.rememberResolvedCoverPath(gameId, fallback)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        if (path != null) {
            AsyncImage(
                model = com.nendo.argosy.ui.common.rememberFileImageModel(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t
