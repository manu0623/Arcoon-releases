package com.nendo.argosy.data.media

import java.util.Locale

private const val SMDH_SIZE = 0x36C0
private const val SMDH_TITLES_OFFSET = 0x8
private const val SMDH_TITLE_SIZE = 0x200
private const val SMDH_ICON_OFFSET = 0x2040
private const val SMDH_SMALL_ICON_BYTES = 0x480
private const val SMDH_LARGE_ICON_SIZE = 48
private const val SMDH_LARGE_ICON_BYTES = 0x1200

data class SmdhMedia(
    val title: String?,
    val publisher: String?,
    val iconWidth: Int,
    val iconHeight: Int,
    val iconArgb: IntArray
)

object SmdhParser {

    private val languageIndices = mapOf(
        "ja" to 0,
        "en" to 1,
        "fr" to 2,
        "de" to 3,
        "it" to 4,
        "es" to 5,
        "zh" to 6,
        "ko" to 7,
        "nl" to 8,
        "pt" to 9,
        "ru" to 10
    )

    fun parse(smdh: ByteArray, preferredLanguage: String = Locale.getDefault().toLanguageTag()): SmdhMedia? {
        if (smdh.size < SMDH_SIZE ||
            !smdh.copyOfRange(0, 4).contentEquals("SMDH".toByteArray(Charsets.US_ASCII))
        ) {
            return null
        }

        val languageIndex = preferredLanguageIndex(preferredLanguage)
        val selectedIndex = (listOf(languageIndex, 1) + (0 until 12))
            .distinct()
            .firstOrNull { index -> titleAt(smdh, index).isNotBlank() }
        val title = selectedIndex?.let { titleAt(smdh, it) }
        val publisher = selectedIndex?.let { publisherAt(smdh, it) }?.ifBlank { null }
        val iconData = SMDH_ICON_OFFSET + SMDH_SMALL_ICON_BYTES
        val iconArgb = TiledPixelDecoder.decodeRgb565(
            smdh.copyOfRange(iconData, iconData + SMDH_LARGE_ICON_BYTES),
            SMDH_LARGE_ICON_SIZE,
            SMDH_LARGE_ICON_SIZE
        ) ?: return null

        return SmdhMedia(
            title = title?.ifBlank { null },
            publisher = publisher,
            iconWidth = SMDH_LARGE_ICON_SIZE,
            iconHeight = SMDH_LARGE_ICON_SIZE,
            iconArgb = iconArgb
        )
    }

    private fun preferredLanguageIndex(language: String): Int {
        val normalized = language.lowercase()
        if (normalized.startsWith("zh-tw") ||
            normalized.startsWith("zh-hk") ||
            normalized.startsWith("zh-hant")
        ) {
            return 11
        }
        return languageIndices.entries.firstOrNull { (code, _) ->
            normalized == code || normalized.startsWith("$code-")
        }?.value ?: 1
    }

    private fun titleAt(smdh: ByteArray, index: Int): String {
        val base = SMDH_TITLES_OFFSET + index * SMDH_TITLE_SIZE
        val shortTitle = decodeUtf16(smdh, base, 0x80)
        return shortTitle.ifBlank { decodeUtf16(smdh, base + 0x80, 0x100) }
    }

    private fun publisherAt(smdh: ByteArray, index: Int): String {
        val base = SMDH_TITLES_OFFSET + index * SMDH_TITLE_SIZE
        return decodeUtf16(smdh, base + 0x180, 0x80)
    }

    private fun decodeUtf16(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.UTF_16LE)
            .substringBefore('\u0000')
            .trim()

}
