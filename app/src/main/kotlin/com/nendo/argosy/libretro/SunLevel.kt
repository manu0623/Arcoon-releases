package com.nendo.argosy.libretro

import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import kotlin.math.pow

/**
 * The sun sensor level that sun hotkeys step, reported to cores as lux.
 *
 * Each level's lux is the value whose `cbrt(lux) * 8`, the conversion mGBA and melonDS DS
 * apply to RETRO_SENSOR_ILLUMINANCE, lands on mGBA's matching manual solar level
 * (`0x16 + GBA_LUX_LEVELS[level - 1]`, src/gba/cart/gpio.c).
 */
object SunLevel {
    const val MIN = 0
    const val MAX = 10

    private const val DARK = 0x16
    private val GBA_LUX_LEVELS = intArrayOf(5, 11, 18, 27, 42, 62, 84, 109, 139, 183)

    private val HOTKEY_ACTIONS = setOf(HotkeyAction.SUN_BRIGHTER, HotkeyAction.SUN_DIMMER)

    fun coreLevel(level: Int): Int =
        DARK + if (level > MIN) GBA_LUX_LEVELS[level.coerceAtMost(MAX) - 1] else 0

    fun lux(level: Int): Float = ((coreLevel(level) + 0.5f) / 8f).pow(3)

    fun step(level: Int, direction: Int): Int = (level + direction).coerceIn(MIN, MAX)

    fun isBound(resolvedHotkeys: List<HotkeyEntity>): Boolean =
        resolvedHotkeys.any { it.action in HOTKEY_ACTIONS }
}
