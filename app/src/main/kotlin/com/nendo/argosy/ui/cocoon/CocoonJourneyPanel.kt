package com.nendo.argosy.ui.cocoon

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.JourneyEntry
import com.nendo.argosy.domain.model.JourneyPhase
import com.nendo.argosy.domain.usecase.journey.JourneyClosing
import com.nendo.argosy.domain.usecase.journey.JourneyStory
import com.nendo.argosy.domain.usecase.journey.LoadJourneyStoryUseCase
import com.nendo.argosy.ui.screens.gamedetail.components.leadRes
import com.nendo.argosy.ui.theme.CocoonSurface
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@EntryPoint
@InstallIn(SingletonComponent::class)
interface JourneyStoryEntryPoint {
    fun loadJourneyStory(): LoadJourneyStoryUseCase
}

private sealed interface JourneyLoad {
    data object Loading : JourneyLoad
    data object Failed : JourneyLoad
    data class Ready(val story: JourneyStory?) : JourneyLoad
}

private const val SCROLL_STEP_DP = 120
private const val CHAPTER_TITLES_SHOWN = 3

/**
 * The journey page of the X panel: the game's story told from its recorded unlocks, scrolled with
 * the right stick. Every sentence names only what the record holds.
 */
@Composable
fun CocoonJourneyPanel(gameId: Long, platformName: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val loader = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, JourneyStoryEntryPoint::class.java)
            .loadJourneyStory()
    }
    val load by produceState<JourneyLoad>(JourneyLoad.Loading, gameId) {
        value = try {
            JourneyLoad.Ready(loader(gameId, platformName))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.nendo.argosy.util.Logger.warn("CocoonJourney", "Journey for game $gameId failed: ${e.message}")
            JourneyLoad.Failed
        }
    }
    val scroll = rememberScrollState()
    val stepPx = with(LocalDensity.current) { SCROLL_STEP_DP.dp.toPx() }
    LaunchedEffect(scroll) {
        CocoonHeroInfo.scroll.collect { step -> scroll.animateScrollBy(step * stepPx) }
    }
    Column(modifier = modifier.fillMaxSize().verticalScroll(scroll)) {
        Text(
            text = stringResource(R.string.cocoon_journey_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = CocoonSurface.TextPrimary
        )
        when (val state = load) {
            JourneyLoad.Loading -> Note(stringResource(R.string.cocoon_journey_loading))
            JourneyLoad.Failed -> Note(stringResource(R.string.cocoon_journey_error))
            is JourneyLoad.Ready -> state.story?.let { StoryBody(it) }
                ?: Note(stringResource(R.string.cocoon_journey_empty))
        }
    }
}

@Composable
private fun StoryBody(story: JourneyStory) {
    val progress = story.journey.progress
    Text(
        text = stringResource(
            R.string.cocoon_journey_progress,
            progress.unlocked,
            progress.total,
            progress.unlockedPoints,
            progress.totalPoints
        ),
        style = MaterialTheme.typography.labelMedium,
        color = CocoonSurface.TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Body(stringResource(R.string.cocoon_journey_opening, date(story.opening.unlockedAt), story.opening.title))
    story.closing?.let { closing ->
        val res = when (closing) {
            is JourneyClosing.Mastered -> R.string.cocoon_journey_closing_mastered
            is JourneyClosing.MainComplete -> R.string.cocoon_journey_closing_main
            is JourneyClosing.Latest -> R.string.cocoon_journey_closing_latest
        }
        Body(stringResource(res, date(closing.entry.unlockedAt), closing.entry.title))
    }

    story.leftOff?.let { entry ->
        Header(stringResource(R.string.cocoon_journey_left_off))
        Body(entry.context.orEmpty())
        Meta(stringResource(R.string.cocoon_journey_last_known, date(entry.unlockedAt), entry.title))
    }

    if (story.highlights.isNotEmpty()) {
        Header(stringResource(R.string.cocoon_journey_highlights))
        story.highlights.forEach { EntryRow(it) }
    }

    Header(stringResource(R.string.cocoon_journey_full))
    story.chapters.forEachIndexed { index, chapter -> ChapterBlock(index + 1, chapter) }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun ChapterBlock(number: Int, chapter: JourneyPhase) {
    val span = if (date(chapter.firstUnlock) == date(chapter.lastUnlock)) date(chapter.firstUnlock)
        else "${date(chapter.firstUnlock)} – ${date(chapter.lastUnlock)}"
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.cocoon_journey_chapter, number, span),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = CocoonSurface.TextPrimary
    )
    val titles = chapter.entries.map { "“${it.title}”" }
    val named = titles.take(CHAPTER_TITLES_SHOWN).joinToString(", ")
    val list = if (titles.size > CHAPTER_TITLES_SHOWN) {
        stringResource(R.string.cocoon_journey_and_more, named, titles.size - CHAPTER_TITLES_SHOWN)
    } else {
        named
    }
    Body(stringResource(chapter.category.leadRes(), list))
}

@Composable
private fun EntryRow(entry: JourneyEntry) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = CocoonSurface.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        entry.description?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = CocoonSurface.TextPrimary.copy(alpha = 0.85f))
        }
        Meta(stringResource(R.string.cocoon_journey_entry_meta, date(entry.unlockedAt), entry.points))
    }
}

@Composable
private fun Header(text: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = CocoonSurface.TextSecondary
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = CocoonSurface.TextPrimary.copy(alpha = 0.92f),
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun Meta(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = CocoonSurface.TextSecondary)
}

@Composable
private fun Note(text: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = CocoonSurface.TextSecondary)
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun date(at: Instant): String = DATE_FORMAT.format(at.atZone(ZoneId.systemDefault()))
