package com.nendo.argosy.ui.dualscreen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.PresentationLayout
import com.nendo.argosy.domain.model.PresentationStat
import com.nendo.argosy.domain.model.PresentationStyle
import com.nendo.argosy.util.formatTimeToBeat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameShowcaseProgressTest {
    @get:Rule
    val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val completionist get() = context.getString(
        R.string.dual_showcase_completionist, formatTimeToBeat(context, 7200)
    )
    private val finished get() = context.getString(R.string.completion_status_finished)

    @Test
    fun cinematicShowsCompletionistAlongsideJourneyAndHonorsTimeVisibility() {
        val style = mutableStateOf(PresentationStyle())
        compose.setContent {
            MaterialTheme {
                GameShowcase(
                    detail = CompanionDetail(
                        title = "Game",
                        stats = CompanionGameStats(
                            playTimeMinutes = 30,
                            timeToBeatMainSec = 3600,
                            timeToBeatCompletionistSec = 7200,
                            status = "finished"
                        )
                    ),
                    style = style.value,
                    bottomInset = 0.dp
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithText(completionist).assertIsDisplayed()
        compose.onNodeWithText(finished).assertIsDisplayed()
        compose.runOnIdle {
            style.value = style.value.copy(hiddenStats = setOf(PresentationStat.TIME_TO_BEAT))
        }
        compose.onNodeWithText(completionist).assertDoesNotExist()
        compose.onNodeWithText(finished).assertIsDisplayed()
    }

    @Test
    fun journalShowsCompletionistAndStatusWithoutMainStoryOrPlayTime() {
        val stats = mutableStateOf(CompanionGameStats(timeToBeatCompletionistSec = 7200))
        compose.setContent {
            MaterialTheme {
                GameShowcase(
                    detail = CompanionDetail(title = "Game", stats = stats.value),
                    style = PresentationStyle(layout = PresentationLayout.JOURNAL),
                    bottomInset = 0.dp
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithText(completionist).assertIsDisplayed()
        compose.runOnIdle { stats.value = CompanionGameStats(status = "finished") }
        compose.onNodeWithText(finished).assertIsDisplayed()
        compose.onNodeWithText(completionist).assertDoesNotExist()
        compose.runOnIdle { stats.value = CompanionGameStats(status = "unknown") }
        compose.onNodeWithText(context.getString(R.string.dual_showcase_journey_header).uppercase())
            .assertDoesNotExist()
    }
}
