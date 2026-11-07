package com.nendo.argosy.ui.cocoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * TEST: suspending a game from a button Arcoon owns, instead of Android's Home press.
 *
 * Because Arcoon starts the switch itself, it can ask for no system animation at all: the game
 * cuts straight to the launcher, which already shows the game's last frame at the same size, and
 * that picture then shrinks into the suspended card. Android's sideways slide never plays, so no
 * black cover is needed. The Home button keeps its current behaviour.
 */
object CocoonSuspendTest {
    /** True while a one-screen built-in game is in front and can be suspended. */
    val available = MutableStateFlow(false)

    /** A press of the test button; the game screen answers it. */
    val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * The game id the launcher is being brought back to, or -1 while none is pending.
     */
    val launcherReturn = MutableStateFlow(-1L)

    /**
     * The picture handed from the game to the launcher, for one suspend. [picture] is what the top
     * screen showed; [lower] is a two-screen console's other screen, null for one-screen games.
     */
    class Handoff(
        val gameId: Long,
        val picture: ImageBitmap,
        val aspect: Float,
        val at: Long,
        val lower: ImageBitmap? = null
    )

    val handoff = mutableStateOf<Handoff?>(null)

    /**
     * When a game last started or resumed. A handoff from before that is over: a quick resume
     * used to still count as "just suspended", so the launcher skipped its black cover and a
     * later Home press showed Android's slide.
     */
    @Volatile var lastGameShownAt: Long = 0L

    /** A handoff only counts for the moment right after the press, and only until the game is back. */
    fun fresh(gameId: Long): Handoff? =
        handoff.value?.takeIf { it.gameId == gameId && isFresh(it) }

    fun freshAny(): Boolean = handoff.value?.let { isFresh(it) } == true

    private fun isFresh(h: Handoff): Boolean =
        System.currentTimeMillis() - h.at < HANDOFF_FRESH_MS && h.at > lastGameShownAt

    /** The handed-over picture for as long as that game stays suspended, fresh or not. */
    fun picture(gameId: Long): Handoff? = handoff.value?.takeIf { it.gameId == gameId }

    /** The game suspended with the test button; only it gets the grow-back on resume. */
    @Volatile var suspendedGameId: Long = -1L

    /**
     * How many suspended cards are on screen. A count, not a flag: a second copy of the card
     * (drawn for a moment on the lower screen) used to switch the flag off as it went away while
     * the real card was still up, so the grow-back was skipped.
     */
    private val cardsShown = java.util.concurrent.atomic.AtomicInteger(0)
    val cardShown: Boolean get() = cardsShown.get() > 0
    fun cardAppeared() { cardsShown.incrementAndGet() }
    fun cardGone() { cardsShown.updateAndGet { (it - 1).coerceAtLeast(0) } }

    /**
     * Resume asked for: the card grows back to the game's size, then runs this to bring the game
     * to the front. Null when no grow is pending.
     */
    val growThen = mutableStateOf<(() -> Unit)?>(null)

    val resumeInProgress = mutableStateOf(false)

    fun finishResumeHandoff() {
        handoff.value = null
        suspendedGameId = -1L
        growThen.value = null
        resumeInProgress.value = false
    }

    /** Length of the grow back into the game. */
    const val GROW_MS = 600

    fun clear() {
        if (!resumeInProgress.value) handoff.value = null
        suspendedGameId = -1L
        growThen.value = null
    }

    const val HANDOFF_FRESH_MS = 2_500L

    /** A moment for the launcher's first frame (the picture at full size) to settle before shrinking. */
    const val GAME_HOLD_MS = 40L
}

/**
 * A two-screen game's lower screen on the launcher's lower window. On suspend it covers the
 * launcher with the game's last lower frame and fades away while the top picture shrinks; on
 * resume it fades back in while the card grows, so the game's own window takes over a matching
 * picture instead of the launcher.
 */
@Composable
fun CocoonSuspendLowerPicture(modifier: Modifier = Modifier) {
    val handoff = CocoonSuspendTest.handoff.value ?: return
    val lower = handoff.lower ?: return
    val resuming = CocoonSuspendTest.resumeInProgress.value
    val leaving = androidx.compose.runtime.remember(handoff) { CocoonSuspendTest.fresh(handoff.gameId) != null }
    val alpha = androidx.compose.runtime.remember(handoff) {
        androidx.compose.animation.core.Animatable(if (leaving) 1f else 0f)
    }
    androidx.compose.runtime.LaunchedEffect(handoff, resuming) {
        if (resuming) {
            alpha.animateTo(
                1f,
                androidx.compose.animation.core.tween(
                    CocoonSuspendTest.GROW_MS,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            )
        } else if (alpha.value > 0f) {
            kotlinx.coroutines.delay(CocoonSuspendTest.GAME_HOLD_MS + LOWER_FADE_DELAY_MS)
            alpha.animateTo(
                0f,
                androidx.compose.animation.core.tween(
                    LOWER_FADE_MS,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            )
        }
    }
    if (alpha.value <= 0f) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(Color.Black)
    ) {
        androidx.compose.foundation.Image(
            bitmap = lower,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private const val LOWER_FADE_DELAY_MS = 60L
private const val LOWER_FADE_MS = 440

@Composable
fun CocoonSuspendTestButton(modifier: Modifier = Modifier) {
    val available by CocoonSuspendTest.available.collectAsState()
    if (!available) return
    val size = com.nendo.argosy.ui.cocoon.cocoonHandleTargetDp().dp
    val glyph = com.nendo.argosy.ui.cocoon.cocoonHandleIconDp().dp
    val x = CocoonHomeStore.handleX
    val y = CocoonHomeStore.handleY
    val clarity = CocoonHomeStore.handleClarity
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(x = (maxWidth - size) * x, y = (maxHeight - size) * y)
                .size(size)
                .clickableNoFocus { CocoonSuspendTest.requests.tryEmit(Unit) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Home,
                contentDescription = stringResource(R.string.ingame_suspend_handle),
                tint = Color.White.copy(alpha = clarity),
                modifier = Modifier.size(glyph)
            )
        }
    }
}

