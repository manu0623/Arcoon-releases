package com.nendo.argosy.ui.dualscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nendo.argosy.domain.model.multitasking.SuspendedGameSession
import com.nendo.argosy.domain.model.multitasking.TopScreenUiState

@Composable
fun TopScreenOverlay(
    uiState: TopScreenUiState,
    modifier: Modifier = Modifier,
    batteryPercentage: Int = 85,
    wifiConnected: Boolean = true,
    currentTimeText: String = "14:32"
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Dynamic Hero Wallpaper Background with Crossfade
        Crossfade(
            targetState = uiState.highlightedHeroUrl,
            label = "HeroWallpaperCrossfade"
        ) { heroUrl ->
            if (heroUrl != null) {
                AsyncImage(
                    model = heroUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121212))
                )
            }
        }

        // Gradient Scrim Overlay for Legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // 2. System Status Bar at Top
        TopSystemStatusBar(
            wifiConnected = wifiConnected,
            batteryPercentage = batteryPercentage,
            currentTimeText = currentTimeText,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )

        // 3. Upper-Screen Suspended Game Card State Machine
        uiState.suspendedSession?.let { session ->
            SuspendedGameCard(
                session = session,
                isCenteredStateA = uiState.isCardCentered,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SuspendedGameCard(
    session: SuspendedGameSession,
    isCenteredStateA: Boolean,
    modifier: Modifier = Modifier
) {
    val art = com.nendo.argosy.ui.common.rememberResolvedArt(session.gameId)
    val image = session.screenshotPath ?: art?.backgroundPath ?: art?.coverPath
    // Smooth Interpolation Specifications
    val cardWidth: Dp by animateDpAsState(
        targetValue = if (isCenteredStateA) 640.dp else 220.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "CardWidth"
    )
    val cardHeight: Dp by animateDpAsState(
        targetValue = if (isCenteredStateA) 360.dp else 135.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "CardHeight"
    )
    val offsetX: Dp by animateDpAsState(
        targetValue = if (isCenteredStateA) 0.dp else (-260).dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "OffsetX"
    )
    val offsetY: Dp by animateDpAsState(
        targetValue = if (isCenteredStateA) 20.dp else (-140).dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "OffsetY"
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = offsetX, y = offsetY)
                .size(width = cardWidth, height = cardHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E24).copy(alpha = 0.92f))
                .border(
                    width = 2.dp,
                    color = if (isCenteredStateA) Color(0xFF3D5AFE) else Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            if (isCenteredStateA) {
                // State A (Suspended Game Highlighted): Centered PIP Card
                Row(modifier = Modifier.fillMaxSize()) {
                    // Screenshot Preview Frame + Session Timer Pill
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = image,
                            contentDescription = "Emulator Snapshot",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Timer Pill (Bottom-Left)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = session.formattedElapsedTime,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Metadata & Achievements Section
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = session.platformName.uppercase(),
                                color = Color(0xFF9E9E9E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = session.title,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                            )
                        }

                        // Achievement Progress Ratio
                        if (session.achievementsTotal > 0) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Achievements",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "${session.achievementsUnlocked}/${session.achievementsTotal}",
                                        color = Color(0xFFFFD54F),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { session.achievementProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = Color(0xFFFFD54F),
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }
            } else {
                // State B (Moved to Another Game): Compact Thumbnail Card
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = session.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = session.formattedElapsedTime,
                            color = Color(0xFF4CAF50),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Floating Gamepad Action Hints (State A Only)
        AnimatedVisibility(
            visible = isCenteredStateA,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 32.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GamepadHintPill(buttonLabel = "X", actionLabel = "Resume", badgeColor = Color(0xFF29B6F6))
                GamepadHintPill(buttonLabel = "Y", actionLabel = "Close", badgeColor = Color(0xFFEF5350))
            }
        }
    }
}

@Composable
private fun GamepadHintPill(
    buttonLabel: String,
    actionLabel: String,
    badgeColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(badgeColor)
        ) {
            Text(
                text = buttonLabel,
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = actionLabel,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TopSystemStatusBar(
    wifiConnected: Boolean,
    batteryPercentage: Int,
    currentTimeText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "ARGOSY MULTITASK", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = if (wifiConnected) "Wi-Fi" else "Offline", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Text(text = "$batteryPercentage%", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Text(text = currentTimeText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
