package com.nendo.argosy.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ToggleOn = Color(0xFF8E8E93)
private val ToggleOff = Color(0xFF2C2C2E)
private val SliderEnd = Color(0xFF2C2C2E)
private val Track = Color(0xFF4A4A4E)
private val TrackFill = Color(0xFF7C7C80)
private val Knob = Color(0xFFA6A6AA)

private fun CocoonQuickToggle.icon(on: Boolean): ImageVector = when (this) {
    CocoonQuickToggle.WIFI -> if (on) Icons.Filled.Wifi else Icons.Filled.WifiOff
    CocoonQuickToggle.BLUETOOTH -> if (on) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled
    CocoonQuickToggle.SOUNDS -> if (on) Icons.Filled.MusicNote else Icons.Filled.MusicOff
    CocoonQuickToggle.VIBRATION -> Icons.Filled.Vibration
    CocoonQuickToggle.SWAP_SCREENS -> Icons.Filled.SwapVert
    CocoonQuickToggle.SETTINGS -> Icons.Filled.Settings
}

/** Cocoon's row of round quick toggles: light when on, dark when off, brackets on the focused one. */
@Composable
internal fun CocoonToggleRow(
    state: QuickSettingsState,
    rowFocused: Boolean,
    focusedToggle: Int,
    onTap: (index: Int, toggle: CocoonQuickToggle) -> Unit
) {
    val toggles = CocoonQuickToggle.visible(state)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        val gap = 10.dp
        val size = ((maxWidth - gap * (toggles.size - 1)) / toggles.size).coerceIn(40.dp, 58.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically
        ) {
            toggles.forEachIndexed { index, toggle ->
                val on = toggle.isOn(state)
                Box(
                    modifier = Modifier
                        .size(size)
                        .cocoonBrackets(focused = rowFocused && index == focusedToggle, outset = 5.dp)
                        .background(if (on) ToggleOn else ToggleOff, CircleShape)
                        .clickableNoFocus { onTap(index, toggle) },
                    contentAlignment = Alignment.Center
                ) {
                    val pending = (toggle == CocoonQuickToggle.WIFI && state.wifiPending) ||
                        (toggle == CocoonQuickToggle.BLUETOOTH && state.bluetoothPending)
                    Icon(
                        imageVector = toggle.icon(on),
                        contentDescription = toggle.name,
                        tint = Color.White.copy(alpha = if (pending) 0.6f else 1f),
                        modifier = Modifier.size(size * 0.44f)
                    )
                    // still switching: a thin ring spins round the circle until the radio is ready
                    if (pending) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.matchParentSize(),
                            color = Color.White.copy(alpha = 0.85f),
                            trackColor = Color.Transparent,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

enum class CocoonSliderKind { BRIGHTNESS, VOLUME }

/** Cocoon's slider: a round end-cap icon each side of a thin track with a round knob. */
@Composable
internal fun CocoonSliderRow(
    kind: CocoonSliderKind,
    fraction: Float,
    caption: String?,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onFractionChange: (Float) -> Unit
) {
    val (low, high) = when (kind) {
        CocoonSliderKind.BRIGHTNESS -> Icons.Filled.BrightnessLow to Icons.Filled.BrightnessHigh
        CocoonSliderKind.VOLUME -> Icons.AutoMirrored.Filled.VolumeDown to Icons.AutoMirrored.Filled.VolumeUp
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .cocoonBrackets(focused = isFocused, outset = 2.dp)
            .clickableNoFocus(onClick = onFocus)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 52.dp, bottom = 2.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SliderCap(low, onClick = { onFocus(); onFractionChange((fraction - 0.1f).coerceIn(0f, 1f)) })
            Spacer(modifier = Modifier.width(12.dp))
            BoxWithConstraints(
                modifier = Modifier.weight(1f).height(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val f = fraction.coerceIn(0f, 1f)
                val knob = 24.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Track, RoundedCornerShape(50))
                )
                Box(
                    modifier = Modifier
                        .width(maxWidth * f)
                        .height(4.dp)
                        .background(TrackFill, RoundedCornerShape(50))
                )
                Box(
                    modifier = Modifier
                        .offset(x = (maxWidth - knob) * f)
                        .size(knob)
                        .background(Knob, CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            SliderCap(high, onClick = { onFocus(); onFractionChange((fraction + 0.1f).coerceIn(0f, 1f)) })
        }
    }
}

@Composable
private fun SliderCap(icon: ImageVector, onClick: () -> Unit, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(SliderEnd, CircleShape)
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * Keeps the controller's Wi-Fi / Bluetooth status fresh while the panel is open and lets it open
 * Android's own panels for those toggles (apps cannot switch the radios themselves).
 */
@Composable
internal fun CocoonQuickSystemBridge(controller: QuickSettingsController, active: Boolean) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    DisposableEffect(controller, context) {
        controller.systemPanelLauncher = { toggle -> openSystemPanel(context, toggle) }
        val radio = runCatching {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext, CocoonRadioEntryPoint::class.java
            ).radioController()
        }.getOrNull()
        controller.radioSwitcher = if (radio != null) {
            { toggle, enabled ->
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    // The root-shell check can start a process, so it runs here, off the UI thread
                    val ok = runCatching {
                        if (!radio.canToggle) false
                        else if (toggle == CocoonQuickToggle.WIFI) radio.setWifiEnabled(enabled) else radio.setBluetoothEnabled(enabled)
                    }.getOrDefault(false)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (ok) controller.setRadios(readWifiOn(context), readBluetoothOn(context))
                        else {
                            controller.clearRadioPending(toggle)
                            openSystemPanel(context, toggle)
                        }
                    }
                }
            }
        } else null
        onDispose {
            if (controller.systemPanelLauncher != null) controller.systemPanelLauncher = null
            controller.radioSwitcher = null
        }
    }
    LaunchedEffect(active) {
        while (active) {
            controller.setRadios(readWifiOn(context), readBluetoothOn(context))
            // check often while a radio is still switching, so the spinner stops as soon as it's done
            delay(if (controller.hasPendingRadios) 400 else 1500)
        }
    }
}

private fun readWifiOn(context: Context): Boolean = runCatching {
    val wifi = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
    wifi?.isWifiEnabled == true
}.getOrDefault(false)

private fun readBluetoothOn(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
    manager?.adapter?.isEnabled == true
}.getOrDefault(false)

private fun openSystemPanel(context: Context, toggle: CocoonQuickToggle) {
    val intent = when (toggle) {
        CocoonQuickToggle.WIFI ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
        CocoonQuickToggle.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        else -> Intent(Settings.ACTION_SETTINGS)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}


@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface CocoonRadioEntryPoint {
    fun radioController(): com.nendo.argosy.hardware.RadioController
}
