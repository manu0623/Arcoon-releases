package com.nendo.argosy.data.model

/**
 * A game's art after [resolveArtPath] has picked one path per slot. Gradient and aspect ratio come
 * from the cover row.
 */
data class ResolvedGameArt(
    val coverPath: String? = null,
    val backgroundPath: String? = null,
    val logoPath: String? = null,
    val box3dPath: String? = null,
    val boxSpinePath: String? = null,
    val boxBackPath: String? = null,
    val gradientColors: String? = null,
    val coverAspectRatio: Float? = null,
    val overriddenSlots: Set<ArtSlot> = emptySet(),
    /** True when the logo slot holds the animated banner extracted from the ROM. */
    val logoFromEmbeddedBanner: Boolean = false
) {
    fun path(slot: ArtSlot): String? = when (slot) {
        ArtSlot.COVER -> coverPath
        ArtSlot.BACKGROUND -> backgroundPath
        ArtSlot.LOGO -> logoPath
        ArtSlot.BOX_3D -> box3dPath
        ArtSlot.BOX_SPINE -> boxSpinePath
        ArtSlot.BOX_BACK -> boxBackPath
    }

    companion object {
        val EMPTY = ResolvedGameArt()
    }
}

/**
 * The one art resolution rule: a user override wins, then the locally cached file, then the remote
 * source url. `RESOLVED_ART_SQL` in the dao package is its SQL twin.
 */
fun resolveArtPath(overridePath: String?, cachedPath: String?, sourceUrl: String?): String? =
    overridePath ?: cachedPath ?: sourceUrl

/**
 * Puts an extracted 3DS banner in the logo slot, as Cocoon does: the banner is the game's animated
 * title art, so the hero keeps its own background behind it. A logo the user picked by hand wins.
 */
fun ResolvedGameArt.withEmbedded3dsBanner(path: String?): ResolvedGameArt {
    if (path.isNullOrBlank() || ArtSlot.LOGO in overriddenSlots) return this
    return copy(logoPath = path, logoFromEmbeddedBanner = true)
}
