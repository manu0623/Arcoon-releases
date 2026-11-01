package com.nendo.argosy.libretro

import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.local.entity.HotkeyScopeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyScopeResolverTest {

    private val parseCombo: (HotkeyEntity) -> List<Int> = { entity ->
        entity.buttonComboJson.trim('[', ']')
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
    }

    private fun hotkey(
        action: HotkeyAction,
        combo: List<Int>,
        scopeType: HotkeyScopeType = HotkeyScopeType.GLOBAL,
        scopeKey: String? = null,
        id: Long = 0
    ) = HotkeyEntity(
        id = id,
        action = action,
        buttonComboJson = combo.joinToString(prefix = "[", postfix = "]"),
        scopeType = scopeType,
        scopeKey = scopeKey
    )

    private fun resolve(all: List<HotkeyEntity>, platformSlug: String?, coreId: String? = null) =
        HotkeyScopeResolver.resolve(all, platformSlug, coreId, parseCombo)

    private val L2 = 104
    private val R2 = 105
    private val L1 = 102

    @Test
    fun `platform binding on a new combo replaces the global binding for that action`() {
        val global = hotkey(HotkeyAction.FAST_FORWARD, listOf(R2), id = 1)
        val platform = hotkey(
            HotkeyAction.FAST_FORWARD, listOf(L1), HotkeyScopeType.PLATFORM, "gba", id = 2
        )

        val onGba = resolve(listOf(global, platform), platformSlug = "gba")

        assertEquals(listOf(L1), onGba.single().let(parseCombo))
    }

    @Test
    fun `disabled platform binding suppresses the global binding for that action`() {
        val global = hotkey(HotkeyAction.FAST_FORWARD, listOf(R2), id = 1)
        val tombstone = hotkey(
            HotkeyAction.FAST_FORWARD, listOf(R2), HotkeyScopeType.PLATFORM, "gba", id = 2
        ).copy(isEnabled = false)

        val onGba = resolve(listOf(global, tombstone), platformSlug = "gba")
        val onSnes = resolve(listOf(global, tombstone), platformSlug = "snes")

        assertTrue(onGba.none { it.isEnabled })
        assertEquals(HotkeyAction.FAST_FORWARD, onSnes.single().action)
        assertTrue(onSnes.single().isEnabled)
    }

    @Test
    fun `platform binding shadows global on the same combo for its own platform`() {
        val global = hotkey(HotkeyAction.REWIND, listOf(L2), id = 1)
        val platform = hotkey(HotkeyAction.FAST_FORWARD, listOf(L2), HotkeyScopeType.PLATFORM, "genesis", id = 2)

        val onGenesis = resolve(listOf(global, platform), platformSlug = "genesis")

        assertEquals(1, onGenesis.size)
        assertEquals(HotkeyAction.FAST_FORWARD, onGenesis.first().action)
    }

    @Test
    fun `global applies on a platform that has no override`() {
        val global = hotkey(HotkeyAction.REWIND, listOf(L2), id = 1)
        val platform = hotkey(HotkeyAction.FAST_FORWARD, listOf(L2), HotkeyScopeType.PLATFORM, "genesis", id = 2)

        val onSnes = resolve(listOf(global, platform), platformSlug = "snes")

        assertEquals(1, onSnes.size)
        assertEquals(HotkeyAction.REWIND, onSnes.first().action)
    }

    @Test
    fun `additive platform binding on a free combo appears only on its platform`() {
        val platform = hotkey(HotkeyAction.QUICK_SAVE, listOf(L2), HotkeyScopeType.PLATFORM, "genesis", id = 1)

        val onGenesis = resolve(listOf(platform), platformSlug = "genesis")
        val onSnes = resolve(listOf(platform), platformSlug = "snes")

        assertEquals(listOf(HotkeyAction.QUICK_SAVE), onGenesis.map { it.action })
        assertTrue(onSnes.isEmpty())
    }

    @Test
    fun `a platform override does not leak into the global-only settings view`() {
        val global = hotkey(HotkeyAction.REWIND, listOf(L2), id = 1)
        val platform = hotkey(HotkeyAction.FAST_FORWARD, listOf(L2), HotkeyScopeType.PLATFORM, "genesis", id = 2)

        val globalScope = resolve(listOf(global, platform), platformSlug = null)

        assertEquals(1, globalScope.size)
        assertEquals(HotkeyAction.REWIND, globalScope.first().action)
    }
}
