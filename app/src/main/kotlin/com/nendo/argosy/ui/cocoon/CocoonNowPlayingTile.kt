package com.nendo.argosy.ui.cocoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.launch

/**
 * The game left running, drawn as one square among the folders on Home: its art, a play mark to go
 * back into it, and a small close badge to quit it.
 *
 * Temporary by construction: the board only puts this on screen while a session is actually
 * suspended, so the square appears when a game is left waiting and leaves with it.
 */
@Composable
fun CocoonNowPlayingTile(
    gameId: Long,
    title: String,
    size: Dp,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val art = com.nendo.argosy.ui.common.rememberResolvedArt(gameId)
    val image = art?.backgroundPath ?: art?.coverPath
    val label = stringResource(R.string.cocoon_now_playing)
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .size(size)
            .cocoonBrackets(focused = focused, outset = 1.dp)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(CocoonSurface.Chip),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            AsyncImage(
                model = com.nendo.argosy.ui.common.rememberFileImageModel(image),
                contentDescription = "$label $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .size(size * 0.34f)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.22f)
            )
        }
        // On the corner, so one target goes back and the other quits without a second press.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(size * 0.05f)
                .size(size * 0.24f)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                .clickableNoFocus(onClick = {
                    scope.launch {
                        com.nendo.argosy.DualScreenManagerHolder.instance
                            ?.gameSessionManager?.closeAndWait()
                    }
                }),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cocoon_quit_game),
                tint = Color.White,
                modifier = Modifier.size(size * 0.14f)
            )
        }
    }
}
