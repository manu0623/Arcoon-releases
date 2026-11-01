package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.libretro.frame.FrameManager
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.gripReserveBottomInset
import com.nendo.argosy.ui.util.clickableNoFocus

private const val NUDGE_STEP = 0.004f
private const val ZOOM_STEP = 1.02f

@Composable
fun InGameFrameScreen(
    manager: FrameManager,
    isOffline: Boolean,
    adjustable: Boolean,
    adjusting: Boolean,
    onToggleAdjust: () -> Unit,
    onAdjust: (Float, Float, Float) -> Unit,
    onResetAdjust: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
): InputHandler {
    val currentOnConfirm = rememberUpdatedState(onConfirm)
    val currentOnDismiss = rememberUpdatedState(onDismiss)
    val currentAdjusting = rememberUpdatedState(adjusting)
    val currentAdjustable = rememberUpdatedState(adjustable)
    val currentOnToggleAdjust = rememberUpdatedState(onToggleAdjust)
    val currentOnAdjust = rememberUpdatedState(onAdjust)
    val currentOnResetAdjust = rememberUpdatedState(onResetAdjust)

    val inputHandler = remember {
        object : InputHandler {
            override fun onLeft(): InputResult {
                if (currentAdjusting.value) {
                    currentOnAdjust.value(-NUDGE_STEP, 0f, 1f)
                } else {
                    manager.previousFrame(localOnly = isOffline)
                }
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                if (currentAdjusting.value) {
                    currentOnAdjust.value(NUDGE_STEP, 0f, 1f)
                } else {
                    manager.nextFrame(localOnly = isOffline)
                }
                return InputResult.HANDLED
            }

            override fun onUp(): InputResult {
                if (!currentAdjusting.value) return InputResult.UNHANDLED
                currentOnAdjust.value(0f, -NUDGE_STEP, 1f)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                if (!currentAdjusting.value) return InputResult.UNHANDLED
                currentOnAdjust.value(0f, NUDGE_STEP, 1f)
                return InputResult.HANDLED
            }

            override fun onPrevTrigger(): InputResult {
                if (!currentAdjusting.value) return InputResult.UNHANDLED
                currentOnAdjust.value(0f, 0f, 1f / ZOOM_STEP)
                return InputResult.HANDLED
            }

            override fun onNextTrigger(): InputResult {
                if (!currentAdjusting.value) return InputResult.UNHANDLED
                currentOnAdjust.value(0f, 0f, ZOOM_STEP)
                return InputResult.HANDLED
            }

            override fun onSecondaryAction(): InputResult {
                if (!currentAdjustable.value) return InputResult.UNHANDLED
                currentOnToggleAdjust.value()
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                currentOnConfirm.value()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                if (currentAdjusting.value) {
                    currentOnToggleAdjust.value()
                    return InputResult.HANDLED
                }
                currentOnDismiss.value()
                return InputResult.HANDLED
            }

            override fun onContextMenu(): InputResult {
                if (currentAdjusting.value) {
                    currentOnResetAdjust.value()
                } else if (!isOffline) {
                    manager.downloadSelectedFrame()
                }
                return InputResult.HANDLED
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = gripReserveBottomInset())
            .focusProperties { canFocus = false }
    ) {
        if (!adjusting) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArrowButton(
                    direction = -1,
                    onClick = { manager.previousFrame(localOnly = isOffline) },
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(Dimens.mediaMenuRailWidth)
                )

                Spacer(modifier = Modifier.weight(1f))

                ArrowButton(
                    direction = 1,
                    onClick = { manager.nextFrame(localOnly = isOffline) },
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(Dimens.mediaMenuRailWidth)
                )
            }
        }

        if (manager.isDownloading) {
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        if (adjusting) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Dimens.spacingMd)
                    .clip(RoundedCornerShape(Dimens.radiusSm)),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Text(
                    text = stringResource(R.string.ingame_frame_adjust_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        horizontal = Dimens.spacingMd,
                        vertical = Dimens.spacingSm
                    )
                )
            }
        } else {
            FrameInfoBar(
                manager = manager,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        top = Dimens.spacingMd,
                        start = Dimens.spacingLg,
                        end = Dimens.spacingLg
                    )
            )
        }

        FooterBar(
            hints = buildFrameFooterHints(
                canDownload = manager.installRefresh.let {
                    !isOffline && !manager.selectedFrameInstalled && !manager.isDownloading
                },
                adjustable = adjustable,
                adjusting = adjusting
            ),
            onHintClick = { button ->
                when (button) {
                    InputButton.A -> currentOnConfirm.value()
                    InputButton.B -> {
                        if (adjusting) currentOnToggleAdjust.value() else currentOnDismiss.value()
                    }
                    InputButton.X -> {
                        if (adjusting) {
                            currentOnResetAdjust.value()
                        } else {
                            manager.downloadSelectedFrame()
                        }
                    }
                    InputButton.Y -> currentOnToggleAdjust.value()
                    else -> {}
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    return inputHandler
}

@Composable
private fun ArrowButton(
    direction: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickableNoFocus(onClick = onClick)
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (direction < 0) {
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = if (direction < 0) {
                        stringResource(R.string.ingame_frame_previous_description)
                    } else {
                        stringResource(R.string.ingame_frame_next_description)
                    },
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun FrameInfoBar(
    manager: FrameManager,
    modifier: Modifier = Modifier
) {
    val frameId = manager.selectedFrameId
    val frameName = if (frameId != null) {
        manager.getFrameEntry(frameId)?.displayName ?: frameId
    } else {
        stringResource(R.string.ingame_frame_none)
    }

    val isInstalled = manager.installRefresh.let {
        frameId == null || manager.isFrameInstalled(frameId)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = frameName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (!isInstalled) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.ingame_frame_download_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun buildFrameFooterHints(
    canDownload: Boolean,
    adjustable: Boolean,
    adjusting: Boolean
): List<Pair<InputButton, String>> {
    val changeLabel = stringResource(R.string.ingame_frame_footer_change)
    val selectLabel = stringResource(R.string.ingame_frame_footer_select)
    val downloadLabel = stringResource(R.string.ingame_frame_footer_download)
    val cancelLabel = stringResource(R.string.ingame_frame_footer_cancel)
    val moveLabel = stringResource(R.string.ingame_frame_footer_move)
    val zoomLabel = stringResource(R.string.ingame_frame_footer_zoom)
    val adjustLabel = stringResource(R.string.ingame_frame_footer_adjust)
    val resetLabel = stringResource(R.string.ingame_frame_footer_reset)
    val doneLabel = stringResource(R.string.ingame_frame_footer_done)
    return buildList {
        if (adjusting) {
            add(InputButton.DPAD to moveLabel)
            add(InputButton.LT_RT to zoomLabel)
            add(InputButton.X to resetLabel)
            add(InputButton.A to selectLabel)
            add(InputButton.B to doneLabel)
        } else {
            add(InputButton.DPAD_HORIZONTAL to changeLabel)
            add(InputButton.A to selectLabel)
            if (canDownload) add(InputButton.X to downloadLabel)
            if (adjustable) add(InputButton.Y to adjustLabel)
            add(InputButton.B to cancelLabel)
        }
    }
}
