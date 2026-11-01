package com.nendo.argosy.ui.cocoon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.primitives.cocoonBrackets
import androidx.compose.ui.draw.blur

/** The wordmark shown on the intro: the app's name in a soft moving gradient. */
@Composable
private fun CocoonWordmark(size: Int) {
    val shimmer = androidx.compose.animation.core.rememberInfiniteTransition(label = "wordmark")
    val shift by shimmer.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(2600), androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "wordmarkShift"
    )
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(700)) }
    Text(
        text = stringResource(R.string.cocoon_opt_wordmark),
        fontSize = size.sp,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.displayMedium.copy(
            brush = Brush.linearGradient(
                listOf(Color(0xFFA27BFF), Color(0xFF6DB8FF), Color(0xFFFF8798)),
                start = androidx.compose.ui.geometry.Offset(-400f + 800f * shift, 0f),
                end = androidx.compose.ui.geometry.Offset(400f + 800f * shift, 200f)
            )
        ),
        modifier = Modifier.graphicsLayer {
            alpha = appear.value
            translationY = (1f - appear.value) * 24.dp.toPx()
        }
    )
}

/**
 * Top screen during optimization: the wordmark, then the library's platform folders, each popping
 * in once its covers are ready.
 */
@Composable
fun CocoonOptimizeTop(modifier: Modifier = Modifier) {
    val state by CocoonOptimizer.state.collectAsState()
    AnimatedVisibility(
        visible = state.introVisible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(450)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).clickableNoFocus { },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CocoonWordmark(64)
                Spacer(Modifier.height(36.dp))
                val perRow = 6
                state.platforms.chunked(perRow).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        row.forEachIndexed { i, slug ->
                            val ready = slug in state.readyPlatforms
                            val pop by animateFloatAsState(
                                if (ready) 1f else 0f,
                                spring(dampingRatio = 0.5f, stiffness = 420f),
                                label = "platformPop"
                            )
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                                // where the folder will land
                                Box(
                                    Modifier.size(72.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                                )
                                CocoonFolderIcon(
                                    colorIndex = rowIndex * perRow + i,
                                    glyph = CocoonGlyph.PLATFORM,
                                    platformSlug = slug,
                                    size = 72.dp,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = pop
                                        scaleY = pop
                                        alpha = pop.coerceIn(0f, 1f)
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

/**
 * Launcher screen during optimization: what is happening in plain words, a progress bar, the
 * steps with check marks, and Skip (B). On a single screen it carries the wordmark too.
 */
@Composable
fun CocoonOptimizeControls(showWordmark: Boolean, modifier: Modifier = Modifier) {
    val state by CocoonOptimizer.state.collectAsState()
    com.nendo.argosy.ui.input.ModalInputEffect(
        active = state.introVisible,
        handler = remember {
            object : com.nendo.argosy.ui.input.CapturingInputHandler {
                override fun onBack(): com.nendo.argosy.ui.input.InputResult {
                    CocoonOptimizer.skip()
                    return com.nendo.argosy.ui.input.InputResult.handled(com.nendo.argosy.core.input.SoundType.BACK)
                }
            }
        }
    )
    AnimatedVisibility(
        visible = state.introVisible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(450)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).clickableNoFocus { }.padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            // Warm-up: the app's effects drawn once, practically invisible, so Android prepares
            // their graphics now rather than on your first visit to each screen.
            if (state.warmingUp) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.01f }) { CocoonWarmUpScene() }
            }
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (showWordmark) {
                    CocoonWordmark(44)
                    Spacer(Modifier.height(24.dp))
                }
                Text(
                    stringResource(if (state.finished) R.string.cocoon_opt_done else R.string.cocoon_opt_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = CocoonSurface.TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                val stepLine = when {
                    state.finished -> stringResource(R.string.cocoon_opt_done_detail)
                    state.step == null -> stringResource(R.string.cocoon_opt_starting)
                    state.step == CocoonOptimizeStep.COMPILE -> stringResource(R.string.cocoon_opt_compile_detail)
                    state.total > 0 -> stringResource(state.step!!.labelRes) + " · " +
                        stringResource(R.string.cocoon_opt_count, state.current, state.total)
                    else -> stringResource(state.step!!.labelRes)
                }
                Text(stepLine, style = MaterialTheme.typography.bodyMedium, color = CocoonSurface.TextSecondary)
                Spacer(Modifier.height(20.dp))
                // progress bar and percentage
                val shown by animateFloatAsState(state.progress, tween(300), label = "optProgress")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(0.85f)) {
                    Box(
                        Modifier.weight(1f).height(8.dp)
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    ) {
                        Box(
                            Modifier.fillMaxWidth(shown).fillMaxHeight().background(
                                Brush.horizontalGradient(listOf(Color(0xFFA27BFF), Color(0xFF6DB8FF))),
                                RoundedCornerShape(50)
                            )
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("${(shown * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = CocoonSurface.TextPrimary)
                }
                Spacer(Modifier.height(22.dp))
                // the steps, ticked off as they finish
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(0.85f)) {
                    state.steps.forEach { step ->
                        val done = step in state.done
                        val active = step == state.step && !done
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(20.dp).background(
                                    when {
                                        done -> Color(0xFF6DD58C)
                                        active -> Color.White.copy(alpha = 0.25f)
                                        else -> Color.White.copy(alpha = 0.08f)
                                    },
                                    CircleShape
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (done) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(step.labelRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (done || active) CocoonSurface.TextPrimary else CocoonSurface.TextSecondary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
                if (!state.finished) {
                    Row(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                            .clickableNoFocus { CocoonOptimizer.skip() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.nendo.argosy.ui.primitives.InputGlyph(
                            button = com.nendo.argosy.ui.components.InputButton.B,
                            size = 18.dp,
                            tint = CocoonSurface.TextSecondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cocoon_opt_skip), style = MaterialTheme.typography.labelLarge, color = CocoonSurface.TextPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.cocoon_opt_skip_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = CocoonSurface.TextSecondary
                    )
                }
            }
        }
    }
}


/** One of each effect the app uses: blur, glass, gradients, folders, cursor corners, text. */
@Composable
private fun CocoonWarmUpScene() {
    androidx.compose.runtime.CompositionLocalProvider(com.nendo.argosy.ui.primitives.LocalCocoonCursor provides null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { i ->
                    CocoonFolderIcon(
                        colorIndex = i, glyph = CocoonGlyph.PLATFORM, platformSlug = listOf("snes", "gba", "psx", "n64")[i],
                        size = 64.dp,
                        modifier = Modifier.cocoonBrackets(focused = i == 0, outset = 2.dp)
                    )
                }
            }
            Box(
                Modifier.size(220.dp, 90.dp)
                    .blur(20.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFFA27BFF), Color(0xFF6DB8FF))), RoundedCornerShape(16.dp))
            )
            Box(Modifier.size(220.dp, 60.dp).background(CocoonSurface.Panel, RoundedCornerShape(24.dp)))
            Box(Modifier.size(220.dp, 60.dp).background(Brush.radialGradient(listOf(Color.Transparent, Color.Black))))
            Text("Warm", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("Warm", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("Warm", style = MaterialTheme.typography.bodySmall, color = Color.White)
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}
