package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.data.repository.AchievementJourneyRepository
import com.nendo.argosy.domain.model.AchievementJourney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the journey of the game whose achievements are open. [rowCount] is the number of scroll
 * rows the journey view renders, which is what the achievements focus index walks in journey mode.
 */
class JourneyDelegate @Inject constructor(
    private val repository: AchievementJourneyRepository
) {
    private val _journey = MutableStateFlow<AchievementJourney?>(null)
    val journey: StateFlow<AchievementJourney?> = _journey.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load(scope: CoroutineScope, gameId: Long, platformName: String?) {
        scope.launch {
            _isLoading.value = true
            _journey.value = repository.load(gameId, platformName)
            _isLoading.value = false
        }
    }

    fun rowCount(): Int = _journey.value?.rowCount ?: EMPTY_ROWS

    private companion object {
        const val EMPTY_ROWS = 1
    }
}
