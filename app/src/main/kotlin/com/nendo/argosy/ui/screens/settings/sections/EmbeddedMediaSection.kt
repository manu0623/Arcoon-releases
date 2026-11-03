package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsViewModel

@Composable
fun EmbeddedMediaSection(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false
) {
    val prefs by viewModel.preferencesRepository.preferences.collectAsState(initial = null)
    val enabled = prefs?.embeddedMediaExtractionEnabled ?: true

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SwitchPreference(
            title = "Extract ROM Media",
            subtitle = "Extract embedded icons and banners directly from decrypted NDS/3DS ROMs",
            isEnabled = true,
            isFocused = isFocused,
            onToggle = {
                viewModel.setEmbeddedMediaExtractionEnabled(!enabled)
            }
        )
    }
}
