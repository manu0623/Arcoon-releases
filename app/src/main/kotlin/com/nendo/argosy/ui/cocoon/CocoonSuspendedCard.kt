package com.nendo.argosy.ui.cocoon

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.multitasking.SuspendedGameSession
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.InputGlyph

/**
 * Cocoon's suspended-game card on the top screen. It waits small in the top-left corner while
 * other games (or the board) are shown; when the suspended game itself is highlighted it glides to
 * the middle and grows, with the time pill bottom-left and the A Resume / X Close hints
 * bottom-right.
 */
@Composable
fun CocoonSuspendedCard(
    session: SuspendedGameSession,
    centered: Boolean,
    modifier: Modifier = Modifier
) {
    val p by animateFloatAsState(
        targetValue = if (centered) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "cocoon-suspended-card"
    )
    // Just suspended: the game's last frame still fills the screen, then settles into the card,
    // so leaving a game reads as one motion rather than a cut to black.
    val arrive = androidx.compose.runtime.remember(session.gameId, session.startTimeMillis) {
        androidx.compose.animation.core.Animatable(
            if (System.currentTimeMillis() - session.startTimeMillis < 2_500) 0f else 1f
        )
    }
    androidx.compose.runtime.LaunchedEffect(arrive) {
        // Wait out the black hold that hides Android's slide, then shrink into place.
        val age = System.currentTimeMillis() - session.startTimeMillis
        if (arrive.value < 1f) {
            kotlinx.coroutines.delay((com.nendo.argosy.ui.dualscreen.COCOON_LEAVE_HOLD_MS - age).coerceAtLeast(0))
        }
        arrive.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
    }
    val art = com.nendo.argosy.ui.common.rememberResolvedArt(session.gameId)
    val image = session.screenshotPath ?: art?.backgroundPath ?: art?.coverPath

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val cornerW = w * 0.21f
        val cornerH = cornerW * 0.56f
        val cornerX = w * 0.02f
        val cornerY = h * 0.15f
        val centerW = w * 0.6f
        val centerH = centerW * 0.5625f
        val centerX = (w - centerW) / 2
        val centerY = (h - centerH) / 2
        val a = arrive.value
        val cardW = lerp(w, lerp(cornerW, centerW, p), a)
        val cardH = lerp(h, lerp(cornerH, centerH, p), a)
        val radius = lerp(0.dp, lerp(14.dp, 20.dp, p), a)
        val shape = RoundedCornerShape(radius)

        Box(
            modifier = Modifier
                .absoluteOffset(x = lerp(0.dp, lerp(cornerX, centerX, p), a), y = lerp(0.dp, lerp(cornerY, centerY, p), a))
                .size(width = cardW, height = cardH)
                .shadow(14.dp, shape)
                .clip(shape)
                .background(Color(0xFF1C1C1F))
                .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                .padding(lerp(0.dp, lerp(6.dp, 0.dp, p), a))
        ) {
            if (image != null) {
                AsyncImage(
                    model = com.nendo.argosy.ui.common.rememberFileImageModel(image),
                    contentDescription = session.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(lerp(10.dp, 20.dp, p)))
                )
            }
            // Small card: Cocoon's home + play marks over the paused frame.
            if (p < 1f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { alpha = (1f - p) * a },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF1C1C1F), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        if (p > 0f) {
            // Time in the suspended session, bottom-left.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 18.dp)
                    .graphicsLayer { alpha = p }
                    .background(Color(0xFF4FA3F7), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = session.formattedElapsedTime,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            // A Resume, X Close, bottom-right.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 18.dp)
                    .graphicsLayer { alpha = p }
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InputGlyph(button = InputButton.A, size = 20.dp, tint = Color.White.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.cocoon_resume),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(16.dp))
                InputGlyph(button = InputButton.X, size = 20.dp, tint = Color.White.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.cocoon_close),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}
