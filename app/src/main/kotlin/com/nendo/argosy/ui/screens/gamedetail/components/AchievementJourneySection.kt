package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.AchievementJourney
import com.nendo.argosy.domain.model.JourneyCategory
import com.nendo.argosy.domain.model.JourneyEntry
import com.nendo.argosy.domain.model.JourneyPhase
import com.nendo.argosy.domain.model.JourneyState
import com.nendo.argosy.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val TITLE_SEPARATOR = " · "
private const val FOCUSED_ALPHA = 0.24f
private const val CARD_ALPHA = 0.5f
private const val CONTEXT_LIMIT = 3
private const val SUBJECT_LIMIT = 6

@Composable
fun JourneyTabRow(
    journeyTab: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        JourneyTabLabel(
            text = stringResource(R.string.gamedetail_journey_tab_achievements),
            isActive = !journeyTab
        )
        JourneyTabLabel(
            text = stringResource(R.string.gamedetail_journey_tab_journey),
            isActive = journeyTab
        )
    }
}

@Composable
private fun JourneyTabLabel(text: String, isActive: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The written journey of [journey], one row per phase between the opening and the closing summary.
 * [focusIndex] walks those rows with the controller and scrolls the focused one into view.
 */
@Composable
fun JourneyBody(
    journey: AchievementJourney?,
    focusIndex: Int,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val phases = journey?.phases.orEmpty()
    val rows = journey?.rowCount ?: 1

    LaunchedEffect(focusIndex, rows) {
        listState.animateScrollToItem(focusIndex.coerceIn(0, rows - 1))
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = Dimens.spacingXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        if (journey == null || journey.isEmpty) {
            item { JourneyEmptyCard(isFocused = true) }
            return@LazyColumn
        }
        item { JourneyOpeningCard(journey, isFocused = focusIndex == 0) }
        itemsIndexed(phases) { index, phase ->
            JourneyPhaseCard(phase, isFocused = focusIndex == index + 1)
        }
        item { JourneyMilestoneCard(journey, isFocused = focusIndex == phases.size + 1) }
        item { JourneySummaryCard(journey, isFocused = focusIndex == phases.size + 2) }
        item { Spacer(modifier = Modifier.height(Dimens.spacingLg)) }
    }
}

@Composable
private fun JourneyCard(
    isFocused: Boolean,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = FOCUSED_ALPHA)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CARD_ALPHA)
            )
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        content()
    }
}

@Composable
private fun JourneyEmptyCard(isFocused: Boolean) {
    JourneyCard(isFocused) {
        Text(
            text = stringResource(R.string.gamedetail_journey_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun JourneyOpeningCard(journey: AchievementJourney, isFocused: Boolean) {
    JourneyCard(isFocused) {
        Text(
            text = stringResource(R.string.gamedetail_journey_intro_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = journey.gameTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        journey.platformName?.takeIf { it.isNotBlank() }?.let { platform ->
            Text(
                text = platform,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(
                R.string.gamedetail_journey_record,
                journey.progress.unlocked,
                journey.progress.total,
                journey.progress.unlockedPoints,
                journey.progress.totalPoints
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(journey.progress.state.messageRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun JourneyPhaseCard(phase: JourneyPhase, isFocused: Boolean) {
    JourneyCard(isFocused) {
        Text(
            text = stringResource(
                phase.category.leadRes(),
                phase.entries.joinToString(TITLE_SEPARATOR) { it.title }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(
                R.string.gamedetail_journey_phase_meta,
                journeyDate(phase.firstUnlock),
                journeyDate(phase.lastUnlock),
                phase.points
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        phase.entries
            .mapNotNull { entry -> entry.context?.let { context -> entry.title to context } }
            .take(CONTEXT_LIMIT)
            .forEach { (title, context) ->
                Text(
                    text = stringResource(R.string.gamedetail_journey_context, title, context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
    }
}

@Composable
private fun JourneyMilestoneCard(journey: AchievementJourney, isFocused: Boolean) {
    JourneyCard(isFocused) {
        Text(
            text = stringResource(R.string.gamedetail_journey_milestones_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (journey.milestones.isEmpty()) {
            Text(
                text = stringResource(R.string.gamedetail_journey_milestones_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            journey.milestones.forEach { milestone ->
                JourneyMilestoneRow(milestone)
            }
        }
    }
}

@Composable
private fun JourneyMilestoneRow(entry: JourneyEntry) {
    Text(
        text = stringResource(R.string.gamedetail_journey_milestone_row, entry.title, entry.points),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun JourneySummaryCard(journey: AchievementJourney, isFocused: Boolean) {
    JourneyCard(isFocused) {
        Text(
            text = stringResource(R.string.gamedetail_journey_summary_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(
                R.string.gamedetail_journey_summary_body,
                journey.entries.size,
                journey.phases.size,
                journeyDate(journey.entries.first().unlockedAt),
                journeyDate(journey.entries.last().unlockedAt)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        val subjects = journey.namedSubjects.take(SUBJECT_LIMIT)
        if (subjects.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.gamedetail_journey_subjects,
                    subjects.joinToString(TITLE_SEPARATOR)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(
                R.string.gamedetail_journey_updated,
                journeyDate(journey.generatedAt)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@StringRes
private fun JourneyState.messageRes(): Int = when (this) {
    JourneyState.EARLY -> R.string.gamedetail_journey_state_early
    JourneyState.IN_PROGRESS -> R.string.gamedetail_journey_state_progress
    JourneyState.MAIN_COMPLETE -> R.string.gamedetail_journey_state_main
    JourneyState.MASTERED -> R.string.gamedetail_journey_state_mastered
}

@StringRes
internal fun JourneyCategory.leadRes(): Int = when (this) {
    JourneyCategory.PROGRESSION -> R.string.gamedetail_journey_lead_progression
    JourneyCategory.EXPLORATION -> R.string.gamedetail_journey_lead_exploration
    JourneyCategory.COMBAT -> R.string.gamedetail_journey_lead_combat
    JourneyCategory.COLLECTION -> R.string.gamedetail_journey_lead_collection
    JourneyCategory.MASTERY -> R.string.gamedetail_journey_lead_mastery
    JourneyCategory.COMPLETION -> R.string.gamedetail_journey_lead_completion
    JourneyCategory.UNCLASSIFIED -> R.string.gamedetail_journey_lead_unclassified
}

private fun journeyDate(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(instant)
