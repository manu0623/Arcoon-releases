package com.nendo.argosy.ui.common

import android.content.Context
import androidx.annotation.StringRes
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.RaTileContent
import com.nendo.argosy.ui.components.CustomGridTileContent
import com.nendo.argosy.ui.components.RaTileLabels
import com.nendo.argosy.ui.components.RaTileUi
import com.nendo.argosy.ui.components.TilePickerEntry
import com.nendo.argosy.ui.components.tileStatsFor
import com.nendo.argosy.ui.screens.home.HomeGameUi
import java.util.concurrent.TimeUnit

/**
 * How long an unlock wears the new badge on the tile.
 */
private val RA_NEW_UNLOCK_WINDOW_MS = TimeUnit.HOURS.toMillis(24)

/**
 * The words the feature tiles draw, as the resource ids of the surface composing them. Each home
 * surface keeps its own keys; the mapping from a tile's target to what it shows is written once.
 */
data class FeatureTileStrings(
    @StringRes val randomLabel: Int,
    @StringRes val randomEmpty: Int,
    @StringRes val continueLabel: Int,
    @StringRes val continueEmpty: Int,
    @StringRes val raLabel: Int,
    @StringRes val libraryLinkLabel: Int,
    @StringRes val libraryLinkAll: Int,
    @StringRes val libraryLinkCount: Int,
    @StringRes val libraryLinkMore: Int,
    val ra: RaTileLabels
)

fun libraryLinkLabel(
    link: com.nendo.argosy.ui.screens.home.LibraryLinkTileUi?,
    context: Context,
    strings: FeatureTileStrings
): String {
    if (link == null) return context.getString(strings.libraryLinkAll)
    val parts = buildList {
        if (link.source != com.nendo.argosy.data.model.SourceFilter.ALL) {
            add(context.getString(link.source.labelRes))
        }
        addAll(link.platformNames)
        addAll(link.genres)
        addAll(link.series)
        link.players?.let { add(context.getString(it.labelRes)) }
    }
    if (parts.isEmpty()) return context.getString(strings.libraryLinkAll)
    val shown = parts.take(LIBRARY_LINK_MAX_PARTS).joinToString(LIBRARY_LINK_SEPARATOR)
    val hidden = parts.size - LIBRARY_LINK_MAX_PARTS
    if (hidden <= 0) return shown
    return shown + LIBRARY_LINK_SEPARATOR + context.getString(strings.libraryLinkMore, hidden)
}

private const val LIBRARY_LINK_SEPARATOR = " • "
private const val LIBRARY_LINK_MAX_PARTS = 3

/**
 * What a feature tile draws, from what the surface has resolved: the games the page points at,
 * the game the continue tile resumes, and what the RetroAchievements tile shows.
 */
fun featureTileContentFor(
    target: HomeTileTargetRef.Feature,
    tileGames: Map<Long, HomeGameUi>,
    continueGameId: Long?,
    raSummary: RaTileContent?,
    context: Context,
    strings: FeatureTileStrings,
    libraryLink: com.nendo.argosy.ui.screens.home.LibraryLinkTileUi? = null,
    now: Long = System.currentTimeMillis()
): CustomGridTileContent = when (target.kind) {
    FeatureTileKind.RANDOM_GAME -> {
        val game = target.pickedGameId?.let { tileGames[it] }
        CustomGridTileContent(
            game = game,
            label = game?.title ?: context.getString(strings.randomEmpty),
            subtitle = context.getString(strings.randomLabel),
            stats = game?.let { tileStatsFor(it, context) }.orEmpty(),
            isRandom = true
        )
    }
    FeatureTileKind.CONTINUE -> {
        val game = continueGameId?.let { tileGames[it] }
        CustomGridTileContent(
            game = game,
            label = game?.title ?: context.getString(strings.continueEmpty),
            subtitle = context.getString(strings.continueLabel),
            stats = game?.let { tileStatsFor(it, context) }.orEmpty(),
            isContinue = true
        )
    }
    FeatureTileKind.LIBRARY_LINK -> CustomGridTileContent(
        game = libraryLink?.coverGameId?.let { tileGames[it] },
        label = libraryLinkLabel(libraryLink, context, strings),
        subtitle = context.getString(strings.libraryLinkLabel),
        stats = listOf(
            com.nendo.argosy.ui.components.TileStat(
                context.getString(strings.libraryLinkCount),
                (libraryLink?.gameCount ?: 0).toString()
            )
        ),
        isLibraryLink = true
    )
    FeatureTileKind.RA_SUMMARY -> CustomGridTileContent(
        game = null,
        label = raSummary?.username ?: context.getString(strings.raLabel),
        ra = RaTileUi(
            content = raSummary,
            groundGame = raSummary?.groundGameId?.let { tileGames[it] },
            labels = strings.ra,
            entries = raSummary.browseEntries,
            newSince = now - RA_NEW_UNLOCK_WINDOW_MS
        )
    )
}

data class FeatureTilePickerStrings(
    @StringRes val randomTitle: Int,
    @StringRes val randomSubtitle: Int,
    @StringRes val continueTitle: Int,
    @StringRes val continueSubtitle: Int,
    @StringRes val raTitle: Int,
    @StringRes val raSubtitle: Int,
    @StringRes val libraryLinkTitle: Int,
    @StringRes val libraryLinkSubtitle: Int
)

/**
 * The rows of the picker's widgets tab: one per feature tile kind, in the order the kinds are
 * declared.
 */
fun featureTilePickerEntries(
    context: Context,
    strings: FeatureTilePickerStrings
): List<TilePickerEntry> = listOf(
    TilePickerEntry(
        target = HomeTileTargetRef.Feature(FeatureTileKind.RANDOM_GAME),
        title = context.getString(strings.randomTitle),
        subtitle = context.getString(strings.randomSubtitle)
    ),
    TilePickerEntry(
        target = HomeTileTargetRef.Feature(FeatureTileKind.CONTINUE),
        title = context.getString(strings.continueTitle),
        subtitle = context.getString(strings.continueSubtitle)
    ),
    TilePickerEntry(
        target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY),
        title = context.getString(strings.raTitle),
        subtitle = context.getString(strings.raSubtitle)
    ),
    TilePickerEntry(
        target = HomeTileTargetRef.Feature(
            kind = FeatureTileKind.LIBRARY_LINK,
            libraryLink = com.nendo.argosy.domain.model.LibraryLinkFilters()
        ),
        title = context.getString(strings.libraryLinkTitle),
        subtitle = context.getString(strings.libraryLinkSubtitle)
    )
)
