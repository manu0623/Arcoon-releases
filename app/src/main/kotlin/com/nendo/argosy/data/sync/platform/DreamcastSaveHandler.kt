package com.nendo.argosy.data.sync.platform

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dreamcast saves are VMU images. Flycast never implements RETRO_MEMORY_SAVE_RAM, so there is no
 * `.srm` for the platform at all, and with `reicast_per_content_vmus` set to `VMU A1` the core
 * writes the port A1 card as `<product number>.A1.bin` in the libretro save directory. The product
 * number is the disc's IP.BIN id that sigil reports, sanitized the way flycast sanitizes it before
 * building the name.
 *
 * The bytes are one file, so upload and extraction are the plain file handling; only the name is
 * platform-specific.
 */
@Singleton
class DreamcastSaveHandler @Inject constructor(
    delegate: DefaultSaveHandler
) : PlatformSaveHandler by delegate {

    override val namesSavesBySaveId: Boolean = true

    override fun saveFileBaseNames(saveId: String): List<String> =
        vmuNames(saveId, RESERVED_CHARS, PORT_A1_SUFFIX)

    /**
     * Standalone Flycast's per-game card: `<product number>_vmu_save_A1.bin`, the name
     * `hostfs::getVmuPath` gives port A1 while `PerGameVmu` is on.
     */
    val standalone: PlatformSaveHandler = object : PlatformSaveHandler by delegate {
        override val namesSavesBySaveId: Boolean = true

        override fun saveFileBaseNames(saveId: String): List<String> =
            vmuNames(saveId, STANDALONE_RESERVED_CHARS, STANDALONE_PORT_A1_SUFFIX)
    }

    companion object {
        const val PORT_A1_SUFFIX = ".A1"
        const val STANDALONE_PORT_A1_SUFFIX = "_vmu_save_A1"
        const val CARD_EXTENSION = "bin"
        private val RESERVED_CHARS = setOf(' ', '/', '\\', ':', '*', '?', '|', '<', '>')
        private val STANDALONE_RESERVED_CHARS = RESERVED_CHARS + '"'

        private fun vmuNames(saveId: String, reserved: Set<Char>, suffix: String): List<String> {
            if (saveId.isBlank()) return emptyList()
            val sanitized = saveId.trim().map { if (it in reserved) '_' else it }.joinToString("")
            return listOf("$sanitized$suffix")
        }
    }
}
