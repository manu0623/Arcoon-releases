package com.nendo.argosy.ui.cocoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.CocoonCursorHost
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.launch

/**
 * The jingle picker for one game: "Choose a file…", "None", then the sounds from the added
 * jingle repositories for this game's console (best matches first). A chooses, X previews, B goes back.
 */
@Composable
fun CocoonJinglePicker(
    gameId: Long,
    platformSlug: String?,
    title: String,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<CocoonJingleRepos.Entry>>(emptyList()) }
    var addingRepo by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    val loading = stringResource(R.string.cocoon_jingle_loading)
    val noRepos = stringResource(R.string.cocoon_jingle_no_repos)
    LaunchedEffect(gameId, reload) {
        if (CocoonJingleRepos.repos(context).isEmpty()) { status = noRepos; return@LaunchedEffect }
        status = loading
        CocoonJingleRepos.entriesFor(context, platformSlug, title)
            .onSuccess { entries = it; status = null }
            .onFailure { status = it.message }
    }
    var focus by remember { mutableIntStateOf(0) }
    // rows: Choose a file…, None, Add a jingle collection…, then the collection's sounds
    val rowCount = 3 + entries.size
    val dismiss by rememberUpdatedState(onDismiss)
    val pickFile by rememberUpdatedState(onPickFile)
    val entriesNow by rememberUpdatedState(entries)

    fun choose(index: Int) {
        when (index) {
            0 -> pickFile()
            1 -> { CocoonJingles.setGameJingle(context, gameId, null); dismiss() }
            2 -> addingRepo = true
            else -> {
                val entry = entriesNow.getOrNull(index - 3) ?: return
                scope.launch {
                    runCatching { CocoonJingleRepos.download(context, entry) }
                        .onSuccess { path ->
                            CocoonJingles.setGameJingle(context, gameId, path)
                            CocoonJingles.play(context, path)
                            dismiss()
                        }
                        .onFailure { status = it.message }
                }
            }
        }
    }

    fun preview(index: Int) {
        val entry = entriesNow.getOrNull(index - 3) ?: return
        scope.launch {
            runCatching { CocoonJingleRepos.download(context, entry) }
                .onSuccess { CocoonJingles.play(context, it) }
                .onFailure { status = it.message }
        }
    }

    val handler = remember {
        object : InputHandler {
            fun step(delta: Int): InputResult {
                val next = focus + delta
                if (next !in 0 until (3 + entriesNow.size)) return InputResult.handled(SoundType.BOUNDARY)
                focus = next
                return InputResult.HANDLED
            }
            override fun onUp() = step(-1)
            override fun onDown() = step(1)
            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onConfirm(): InputResult { choose(focus); return InputResult.handled(SoundType.SELECT) }
            override fun onContextMenu(): InputResult { preview(focus); return InputResult.HANDLED }
            override fun onBack(): InputResult { CocoonJingles.stop(context); dismiss(); return InputResult.HANDLED }
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = !addingRepo, handler = handler)
    val list = rememberLazyListState()
    LaunchedEffect(focus) { runCatching { list.animateScrollToItem((focus - 2).coerceAtLeast(0)) } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickableNoFocus { }) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Text(stringResource(R.string.cocoon_jingle) + " · " + title, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold, color = CocoonSurface.TextPrimary, maxLines = 1)
            status?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = CocoonSurface.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            CocoonCursorHost(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                    items(rowCount) { index ->
                        val (label, sub) = when (index) {
                            0 -> stringResource(R.string.cocoon_jingle_from_file) to ""
                            1 -> stringResource(R.string.cocoon_jingle_none) to ""
                            2 -> stringResource(R.string.cocoon_jingle_add_collection) to ""
                            else -> entries[index - 3].let { it.name to it.repo }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .cocoonBrackets(focused = index == focus, outset = 2.dp)
                                .background(CocoonMenuPanel, RoundedCornerShape(14.dp))
                                .clickableNoFocus { focus = index; choose(index) }
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = CocoonSurface.TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                            if (sub.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Text(sub, color = CocoonSurface.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.cocoon_jingle_hints), color = CocoonSurface.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (addingRepo) {
        com.nendo.argosy.ui.screens.collections.dialogs.CollectionNameDialog(
            title = stringResource(R.string.cocoon_jingle_add_collection),
            label = "owner/repository",
            confirmLabel = stringResource(R.string.cocoon_themes_apply),
            initialName = "",
            gamepadInput = true,
            onDismiss = { addingRepo = false },
            onSubmit = { name ->
                addingRepo = false
                CocoonJingleRepos.addRepo(context, name)
                reload++
            }
        )
    }
}
