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
