package com.nendo.argosy.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * The shape of the presentation screen for a focused game. [CINEMATIC] lays the game's facts in one
 * rail over its art; [JOURNAL] stacks them in a column with the player's progress beneath; [LOGO]
 * shows the art and the game's name alone.
 */
enum class PresentationLayout { CINEMATIC, JOURNAL, LOGO }

enum class PresentationScrim { GRADIENT, SOLID, BLUR, NONE }

enum class PresentationArt { COVER, BOX_3D, TITLE }

/**
 * The facts the presentation screen can show about a focused game, in display order.
 */
enum class PresentationStat {
    DEVELOPER,
    RELEASE_YEAR,
    PLAYERS,
    GENRE,
    COMMUNITY_RATING,
    USER_RATING,
    DIFFICULTY,
    PLAY_TIME,
    TIME_TO_BEAT,
    ACHIEVEMENTS,
    FRIENDS
}

const val PRESENTATION_SCRIM_MIN = 0
const val PRESENTATION_SCRIM_MAX = 100
const val PRESENTATION_SCRIM_STEP = 10

/**
 * How the presentation screen draws a focused game.
 *
 * @param scrimStrength how opaque the scrim is at its darkest, in percent.
 * @param hiddenStats the stats turned off. Stored as the ones hidden so a stat added later shows
 *   by default.
 */
data class PresentationStyle(
    val layout: PresentationLayout = PresentationLayout.CINEMATIC,
    val scrim: PresentationScrim = PresentationScrim.GRADIENT,
    val scrimStrength: Int = DEFAULT_SCRIM_STRENGTH,
    val art: PresentationArt = PresentationArt.COVER,
    val hiddenStats: Set<PresentationStat> = DEFAULT_HIDDEN_STATS
) {
    fun shows(stat: PresentationStat): Boolean = stat !in hiddenStats

    fun withStat(stat: PresentationStat, shown: Boolean): PresentationStyle =
        copy(hiddenStats = if (shown) hiddenStats - stat else hiddenStats + stat)

    fun toJson(): String = JSONObject().apply {
        put(KEY_LAYOUT, layout.name)
        put(KEY_SCRIM, scrim.name)
        put(KEY_SCRIM_STRENGTH, scrimStrength)
        put(KEY_ART, art.name)
        put(KEY_HIDDEN_STATS, JSONArray(hiddenStats.map { it.name }))
    }.toString()

    companion object {
        const val DEFAULT_SCRIM_STRENGTH = 90
        val DEFAULT_HIDDEN_STATS: Set<PresentationStat> = setOf(PresentationStat.DEVELOPER)

        private const val KEY_LAYOUT = "layout"
        private const val KEY_SCRIM = "scrim"
        private const val KEY_SCRIM_STRENGTH = "scrimStrength"
        private const val KEY_ART = "art"
        private const val KEY_HIDDEN_STATS = "hiddenStats"

        /**
         * Reads each field independently and defaults the rest, dropping stat names this build does
         * not know.
         */
        fun fromJson(raw: String?): PresentationStyle {
            if (raw.isNullOrBlank()) return PresentationStyle()
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: return PresentationStyle()
            val defaults = PresentationStyle()
            val hidden = root.optJSONArray(KEY_HIDDEN_STATS)?.let { array ->
                (0 until array.length())
                    .mapNotNull { index -> enumOrNull<PresentationStat>(array.optString(index)) }
                    .toSet()
            }
            return PresentationStyle(
                layout = enumOrNull<PresentationLayout>(root.optString(KEY_LAYOUT)) ?: defaults.layout,
                scrim = enumOrNull<PresentationScrim>(root.optString(KEY_SCRIM)) ?: defaults.scrim,
                scrimStrength = root.optInt(KEY_SCRIM_STRENGTH, defaults.scrimStrength)
                    .coerceIn(PRESENTATION_SCRIM_MIN, PRESENTATION_SCRIM_MAX),
                art = enumOrNull<PresentationArt>(root.optString(KEY_ART)) ?: defaults.art,
                hiddenStats = hidden ?: defaults.hiddenStats
            )
        }

        private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? =
            raw?.takeIf { it.isNotBlank() }?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
    }
}
