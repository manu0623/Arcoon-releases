package com.nendo.argosy.ui.cocoon

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.steamgriddb.SteamGridDbArtScraper
import com.nendo.argosy.data.remote.steamgriddb.SteamGridDbScrapeStage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object CocoonIntegrationsState {
    val steamGridDbOpen = mutableStateOf(false)
    val romMediaOpen = mutableStateOf(false)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SteamGridDbScraperEntryPoint {
    fun scraper(): SteamGridDbArtScraper
}

private fun parkedMinutes(untilMillis: Long?): Long {
    val remaining = (untilMillis ?: 0L) - System.currentTimeMillis()
    return (remaining / 60_000L).coerceAtLeast(1L)
}

@Composable
private fun ScrapeProgressBar(done: Int, total: Int) {
    val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(Dimens.spacingXs)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.spacingXs)
            .background(CocoonSurface.Chip, shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(CocoonSurface.TextPrimary, shape)
        )
    }
}

@Composable
private fun IntegrationPage(titleRes: Int, subtitleRes: Int, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickableNoFocus { }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
        ) {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = CocoonSurface.TextPrimary
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                stringResource(subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = CocoonSurface.TextSecondary
            )
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            content()
        }
    }
}

@Composable
private fun IntegrationRow(
    label: String,
    value: String?,
    focused: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spacingXs)
            .cocoonBrackets(focused = focused, outset = Dimens.spacingXs)
            .background(
                if (focused) CocoonSurface.Chip else Color.Transparent,
                RoundedCornerShape(Dimens.radiusMd)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = CocoonSurface.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = CocoonSurface.TextSecondary
            )
        }
    }
}

private enum class SteamGridDbRow(@StringRes val labelRes: Int) {
    ENABLED(R.string.cocoon_steamgriddb_toggle),
    KEY(R.string.cocoon_steamgriddb_key),
    SCRAPE(R.string.cocoon_steamgriddb_scrape),
    RESCAN(R.string.cocoon_steamgriddb_rescan),
}

@Composable
fun CocoonSteamGridDbSettings(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val prefs by viewModel.preferencesRepository.preferences.collectAsState(initial = null)
    val enabled = prefs?.steamGridDbEnabled ?: false
    val savedKey = prefs?.steamGridDbApiKey
    val scope = rememberCoroutineScope()
    var focus by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val dismiss by rememberUpdatedState(onDismiss)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val scraper = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SteamGridDbScraperEntryPoint::class.java
        ).scraper()
    }
    val scrapeState by scraper.state.collectAsState()
    val scrapeBusy = scrapeState.stage == SteamGridDbScrapeStage.RUNNING ||
        scrapeState.stage == SteamGridDbScrapeStage.PARKED
    val scrapeStatus = when (scrapeState.stage) {
        SteamGridDbScrapeStage.IDLE -> null
        SteamGridDbScrapeStage.RUNNING -> stringResource(
            R.string.cocoon_steamgriddb_scrape_running,
            scrapeState.scanned,
            scrapeState.total
        )
        SteamGridDbScrapeStage.PARKED -> stringResource(
            R.string.cocoon_steamgriddb_scrape_parked,
            parkedMinutes(scrapeState.parkedUntilMillis)
        )
        SteamGridDbScrapeStage.FINISHED -> stringResource(
            R.string.cocoon_steamgriddb_scrape_done,
            scrapeState.filled
        )
        SteamGridDbScrapeStage.NEEDS_KEY -> stringResource(R.string.cocoon_steamgriddb_scrape_needs_key)
        SteamGridDbScrapeStage.RESTORING -> stringResource(
            R.string.cocoon_steamgriddb_restore_running,
            scrapeState.scanned,
            scrapeState.total
        )
        SteamGridDbScrapeStage.RESTORED -> stringResource(
            R.string.cocoon_steamgriddb_restore_done,
            scrapeState.filled
        )
    }
    fun toggle() {
        scope.launch {
            val current = viewModel.preferencesRepository.userPreferences.first().steamGridDbEnabled
            val next = !current
            viewModel.preferencesRepository.setSteamGridDbEnabled(next)
            if (next) scraper.scrapeLibrary() else scraper.restoreRomMCovers()
        }
    }

    fun scrapeAction() {
        if (scrapeBusy) {
            scraper.stop()
        } else {
            scope.launch { scraper.scrapeLibrary() }
        }
    }

    fun saveKey() {
        scope.launch { viewModel.preferencesRepository.setSteamGridDbApiKey(draft) }
        editing = false
        keyboard?.hide()
    }

    fun rowCount(): Int = SteamGridDbRow.entries.size

    LaunchedEffect(rowCount()) {
        val rows = rowCount()
        if (rows > 0 && focus >= rows) focus = rows - 1
    }

    fun stepRow(delta: Int): InputResult {
        if (editing) return InputResult.HANDLED
        val next = focus + delta
        if (next !in 0 until rowCount()) return InputResult.handled(SoundType.BOUNDARY)
        focus = next
        return InputResult.HANDLED
    }

    fun cycle(delta: Int): InputResult {
        if (editing) return InputResult.HANDLED
        if (SteamGridDbRow.entries.getOrNull(focus) != SteamGridDbRow.ENABLED) return InputResult.HANDLED
        toggle()
        return InputResult.handled(SoundType.TOGGLE)
    }

    val handler = remember {
        object : InputHandler {
            override fun onUp(): InputResult = stepRow(-1)
            override fun onDown(): InputResult = stepRow(1)
            override fun onLeft(): InputResult = cycle(-1)
            override fun onRight(): InputResult = cycle(1)

            override fun onConfirm(): InputResult {
                if (editing) {
                    saveKey()
                    return InputResult.handled(SoundType.TOGGLE)
                }
                return when (SteamGridDbRow.entries[focus]) {
                    SteamGridDbRow.ENABLED -> {
                        toggle()
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    SteamGridDbRow.KEY -> {
                        draft = savedKey.orEmpty()
                        editing = true
                        InputResult.handled(SoundType.OPEN_MODAL)
                    }
                    SteamGridDbRow.SCRAPE -> {
                        scrapeAction()
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    SteamGridDbRow.RESCAN -> {
                        scope.launch { scraper.restartFromScratch() }
                        InputResult.handled(SoundType.TOGGLE)
                    }
                }
            }

            override fun onBack(): InputResult {
                if (editing) {
                    editing = false
                    keyboard?.hide()
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
    ModalInputEffect(active = true, handler = handler)

    IntegrationPage(
        titleRes = R.string.cocoon_steamgriddb_title,
        subtitleRes = R.string.cocoon_steamgriddb_subtitle
    ) {
        SteamGridDbRow.entries.forEachIndexed { index, row ->
            val focused = !editing && focus == index
            IntegrationRow(
                label = when (row) {
                    SteamGridDbRow.SCRAPE -> stringResource(
                        if (scrapeBusy) {
                            R.string.cocoon_steamgriddb_scrape_stop
                        } else {
                            R.string.cocoon_steamgriddb_scrape
                        }
                    )
                    else -> stringResource(row.labelRes)
                },
                value = when (row) {
                    SteamGridDbRow.ENABLED -> stringResource(
                        if (enabled) R.string.cocoon_on else R.string.cocoon_off
                    )
                    SteamGridDbRow.KEY -> stringResource(
                        if (savedKey.isNullOrBlank()) {
                            R.string.cocoon_steamgriddb_key_unset
                        } else {
                            R.string.cocoon_steamgriddb_key_set
                        }
                    )
                    SteamGridDbRow.SCRAPE -> null
                    SteamGridDbRow.RESCAN -> null
                },
                focused = focused,
                onClick = {
                    focus = index
                    when (row) {
                        SteamGridDbRow.ENABLED -> toggle()
                        SteamGridDbRow.KEY -> {
                            draft = savedKey.orEmpty()
                            editing = true
                        }
                        SteamGridDbRow.SCRAPE -> scrapeAction()
                        SteamGridDbRow.RESCAN -> scope.launch { scraper.restartFromScratch() }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            stringResource(R.string.cocoon_steamgriddb_toggle_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = CocoonSurface.TextSecondary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        Text(
            stringResource(R.string.cocoon_steamgriddb_scrape_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = CocoonSurface.TextSecondary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        Text(
            stringResource(R.string.cocoon_steamgriddb_rescan_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = CocoonSurface.TextSecondary
        )
        if (scrapeBusy) {
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            ScrapeProgressBar(done = scrapeState.scanned, total = scrapeState.total)
        }
        scrapeStatus?.let { status ->
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = CocoonSurface.TextSecondary
            )
        }
        if (editing) {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                label = { Text(stringResource(R.string.cocoon_steamgriddb_key_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            IntegrationRow(
                label = stringResource(R.string.cocoon_steamgriddb_key_save),
                value = null,
                focused = true,
                onClick = { saveKey() }
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
        }
    }
}

private enum class RomMediaRow(@StringRes val labelRes: Int) {
    EXTRACT(R.string.cocoon_rom_media_extract),
    BANNER(R.string.cocoon_rom_media_banner),
}

@Composable
fun CocoonRomMediaSettings(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val prefs by viewModel.preferencesRepository.preferences.collectAsState(initial = null)
    val extraction = prefs?.embeddedMediaExtractionEnabled ?: true
    val banner = prefs?.useEmbedded3dsBannerForHero ?: false
    var focus by remember { mutableIntStateOf(0) }
    val dismiss by rememberUpdatedState(onDismiss)

    fun valueOf(row: RomMediaRow): Boolean = when (row) {
        RomMediaRow.EXTRACT -> extraction
        RomMediaRow.BANNER -> banner
    }

    fun toggle(row: RomMediaRow) {
        when (row) {
            RomMediaRow.EXTRACT -> viewModel.setEmbeddedMediaExtractionEnabled(!extraction)
            RomMediaRow.BANNER -> viewModel.setUseEmbedded3dsBannerForHero(!banner)
        }
    }

    fun stepRow(delta: Int): InputResult {
        val next = focus + delta
        if (next !in RomMediaRow.entries.indices) return InputResult.handled(SoundType.BOUNDARY)
        focus = next
        return InputResult.HANDLED
    }

    fun adjust(direction: Int): InputResult {
        val row = RomMediaRow.entries[focus]
        val next = direction > 0
        if (valueOf(row) == next) return InputResult.HANDLED
        toggle(row)
        return InputResult.handled(SoundType.TOGGLE)
    }

    val handler = remember {
        object : InputHandler {
            override fun onUp(): InputResult = stepRow(-1)
            override fun onDown(): InputResult = stepRow(1)
            override fun onLeft(): InputResult = adjust(-1)
            override fun onRight(): InputResult = adjust(1)
            override fun onConfirm(): InputResult {
                toggle(RomMediaRow.entries[focus])
                return InputResult.handled(SoundType.TOGGLE)
            }

            override fun onBack(): InputResult {
                dismiss()
                return InputResult.HANDLED
            }

            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = true, handler = handler)

    IntegrationPage(
        titleRes = R.string.cocoon_rom_media_title,
        subtitleRes = R.string.cocoon_rom_media_subtitle
    ) {
        RomMediaRow.entries.forEachIndexed { index, row ->
            IntegrationRow(
                label = stringResource(row.labelRes),
                value = stringResource(if (valueOf(row)) R.string.cocoon_on else R.string.cocoon_off),
                focused = focus == index,
                onClick = {
                    focus = index
                    toggle(row)
                }
            )
        }
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            stringResource(R.string.cocoon_rom_media_extract_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = CocoonSurface.TextSecondary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        Text(
            stringResource(R.string.cocoon_rom_media_banner_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = CocoonSurface.TextSecondary
        )
    }
}
