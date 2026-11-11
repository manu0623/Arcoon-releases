package com.nendo.argosy.ui.cocoon

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
 * The jingle picker for one game, laid out like the Cocoon reference: the source rail on the left
 * (GitHub collections, a file from the device), the sounds on the right, each with a preview and an
 * apply control. A chooses, X previews, B goes back; the pad walks the rail and the list.
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
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val loading = stringResource(R.string.cocoon_jingle_loading)
    val noRepos = stringResource(R.string.cocoon_jingle_no_repos)
    LaunchedEffect(gameId, reload) {
        if (CocoonJingleRepos.repos(context).isEmpty()) { status = noRepos; return@LaunchedEffect }
        status = loading
        CocoonJingleRepos.entriesFor(context, platformSlug, title)
            .onSuccess { entries = it; status = null }
            .onFailure { status = it.message }
    }
    val shown = remember(entries, query) {
        if (query.isBlank()) entries
        else entries.filter { it.name.contains(query, true) || it.file.contains(query, true) }
    }
    // 0 walks the source rail, 1 the sounds; the pad crosses between them with left/right.
    var column by remember { mutableIntStateOf(1) }
    var railIndex by remember { mutableIntStateOf(0) }
    var listIndex by remember { mutableIntStateOf(0) }
    // Row 0 clears the jingle; the rest are the collection's sounds.
    val listCount = 1 + shown.size
    val dismiss by rememberUpdatedState(onDismiss)
    val pickFile by rememberUpdatedState(onPickFile)
    val entriesNow by rememberUpdatedState(shown)

    fun choose(index: Int) {
        if (index == 0) {
            CocoonJingles.setGameJingle(context, gameId, null)
            dismiss()
            return
        }
        val entry = entriesNow.getOrNull(index - 1) ?: return
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

    fun preview(index: Int) {
        val entry = entriesNow.getOrNull(index - 1) ?: return
        scope.launch {
            runCatching { CocoonJingleRepos.download(context, entry) }
                .onSuccess { CocoonJingles.play(context, it) }
                .onFailure { status = it.message }
        }
    }

    fun activateRail() {
        if (railIndex == 0) addingRepo = true else pickFile()
    }

    val handler = remember {
        object : InputHandler {
            private fun moveRail(delta: Int): InputResult {
                val next = railIndex + delta
                if (next !in 0..1) return InputResult.handled(SoundType.BOUNDARY)
                railIndex = next
                return InputResult.HANDLED
            }
            private fun moveList(delta: Int): InputResult {
                val next = listIndex + delta
                if (next !in 0 until (1 + entriesNow.size)) return InputResult.handled(SoundType.BOUNDARY)
                listIndex = next
                return InputResult.HANDLED
            }
            override fun onUp(): InputResult = if (column == 0) moveRail(-1) else moveList(-1)
            override fun onDown(): InputResult = if (column == 0) moveRail(1) else moveList(1)
            override fun onLeft(): InputResult {
                if (column == 0) return InputResult.handled(SoundType.BOUNDARY)
                column = 0
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                if (column == 1) return InputResult.handled(SoundType.BOUNDARY)
                column = 1
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                if (column == 0) activateRail() else choose(listIndex)
                return InputResult.handled(SoundType.SELECT)
            }
            override fun onContextMenu(): InputResult {
                if (column == 1) preview(listIndex)
                return InputResult.HANDLED
            }
            override fun onSecondaryAction(): InputResult {
                if (column == 1) choose(listIndex) else activateRail()
                return InputResult.handled(SoundType.SELECT)
            }
            override fun onBack(): InputResult {
                if (searching) { searching = false; query = ""; return InputResult.HANDLED }
                CocoonJingles.stop(context)
                dismiss()
                return InputResult.HANDLED
            }
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
        }
    }
    ModalInputEffect(active = !addingRepo && !searching, handler = handler)
    val list = rememberLazyListState()
    LaunchedEffect(listIndex) { runCatching { list.animateScrollToItem((listIndex - 3).coerceAtLeast(0)) } }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searching) {
        if (searching) runCatching { searchFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickableNoFocus { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp)
                .background(CocoonMenuPanel, RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Pill(onClick = { CocoonJingles.stop(context); dismiss() }) {
                    Glyph("B")
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cocoon_jingle_back),
                        tint = CocoonSurface.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (searching) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it; listIndex = 0 },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = CocoonSurface.TextPrimary),
                        cursorBrush = SolidColor(CocoonSurface.TextPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .width(210.dp)
                            .focusRequester(searchFocus)
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill(onClick = { searching = false; query = "" }) {
                        Text(stringResource(R.string.cocoon_jingle_none), color = CocoonSurface.TextSecondary,
                            style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Pill(onClick = { searching = true }) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.cocoon_jingle_search),
                            tint = CocoonSurface.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Pill(onClick = { if (column == 1) preview(listIndex) }) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.cocoon_jingle_preview),
                        tint = CocoonSurface.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Pill(onClick = { if (column == 1) choose(listIndex) }) {
                    Glyph("Y")
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.cocoon_jingle_use),
                        tint = CocoonSurface.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier.width(84.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RailButton(
                        focused = column == 0 && railIndex == 0,
                        onClick = { addingRepo = true }
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_github_mark),
                            contentDescription = stringResource(R.string.cocoon_jingle_repos),
                            tint = CocoonSurface.TextPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    RailButton(
                        focused = column == 0 && railIndex == 1,
                        onClick = { pickFile() }
                    ) {
                        Icon(
                            Icons.Filled.FileUpload,
                            contentDescription = stringResource(R.string.cocoon_jingle_from_file),
                            tint = CocoonSurface.TextPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    status?.let {
                        Text(
                            it,
                            color = CocoonSurface.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                        )
                    }
                    CocoonCursorHost(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(
                            state = list,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(listCount) { index ->
                                val entry = shown.getOrNull(index - 1)
                                JingleRow(
                                    label = entry?.name ?: stringResource(R.string.cocoon_jingle_none),
                                    sub = entry?.file.orEmpty(),
                                    none = entry == null,
                                    focused = column == 1 && index == listIndex,
                                    onPreview = { preview(index) },
                                    onApply = { choose(index) },
                                    onClick = { listIndex = index; choose(index) }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.cocoon_jingle_hints),
                color = CocoonSurface.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
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

@Composable
private fun JingleRow(
    label: String,
    sub: String,
    none: Boolean,
    focused: Boolean,
    onPreview: () -> Unit,
    onApply: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cocoonBrackets(focused = focused, outset = 2.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .clickableNoFocus(onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (none) Icons.Filled.MusicOff else Icons.Filled.MusicNote,
            contentDescription = null,
            tint = CocoonSurface.TextSecondary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = CocoonSurface.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    color = CocoonSurface.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!none) {
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.cocoon_jingle_preview),
                tint = CocoonSurface.TextPrimary,
                modifier = Modifier.size(24.dp).clickableNoFocus(onPreview)
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.cocoon_jingle_use),
                tint = CocoonSurface.TextPrimary,
                modifier = Modifier.size(24.dp).clickableNoFocus(onApply)
            )
        }
    }
}

@Composable
private fun RailButton(
    focused: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .cocoonBrackets(focused = focused, outset = 3.dp)
            .background(CocoonSurface.TextPrimary.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .clickableNoFocus(onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun Pill(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .background(CocoonSurface.TextPrimary.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .clickableNoFocus(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
private fun Glyph(text: String) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(CocoonSurface.TextPrimary.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = CocoonSurface.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
