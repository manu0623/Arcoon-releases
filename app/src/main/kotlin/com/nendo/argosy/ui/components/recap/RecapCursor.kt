package com.nendo.argosy.ui.components.recap

import com.nendo.argosy.domain.model.SessionUnlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sign

/**
 * The recap card's input state. [bodyScrolls] counts body scroll requests and
 * [bodyScrollDirection] is the latest one's direction, -1 up or 1 down.
 */
data class RecapCursor(
    val currentIndex: Int = 0,
    val scrubs: Int = 0,
    val bodyScrolls: Int = 0,
    val bodyScrollDirection: Int = 0
) {
    fun centered(index: Int, count: Int): RecapCursor {
        if (count <= 0) return this
        return copy(currentIndex = index.coerceIn(0, count - 1))
    }

    fun scrubbed(delta: Int, count: Int): RecapCursor {
        if (count <= 0) return this
        return copy(currentIndex = (currentIndex + delta).coerceIn(0, count - 1), scrubs = scrubs + 1)
    }

    fun bodyScrolled(direction: Int): RecapCursor {
        if (direction == 0) return this
        return copy(bodyScrolls = bodyScrolls + 1, bodyScrollDirection = direction.sign)
    }
}

/**
 * The unlock the detail panel describes, with its 1-based [position] in the row.
 */
data class RecapSpotlight(
    val unlock: SessionUnlock,
    val position: Int
) {
    /**
     * The game line the detail panel draws above the unlock's title: its game's title when
     * [showGame] is set and one is known, else null.
     */
    fun gameLine(showGame: Boolean): String? =
        unlock.gameTitle?.takeIf { showGame && it.isNotBlank() }

    companion object {
        fun of(unlocks: List<SessionUnlock>, currentIndex: Int): RecapSpotlight? {
            if (unlocks.isEmpty()) return null
            val index = currentIndex.coerceIn(0, unlocks.lastIndex)
            return RecapSpotlight(unlocks[index], index + 1)
        }
    }
}

@Singleton
class LaunchRecapCursor @Inject constructor() {
    private val _state = MutableStateFlow(RecapCursor())
    val state: StateFlow<RecapCursor> = _state.asStateFlow()

    fun reset() {
        _state.value = RecapCursor()
    }

    fun centered(index: Int, count: Int) {
        _state.update { it.centered(index, count) }
    }

    /**
     * Moves the spotlight [delta] tiles, clamped to the row. True when the spotlight moved.
     */
    fun scrub(delta: Int, count: Int): Boolean {
        var moved = false
        _state.update { cursor ->
            cursor.scrubbed(delta, count).also { moved = it.currentIndex != cursor.currentIndex }
        }
        return moved
    }

    fun scrollBody(direction: Int) {
        _state.update { it.bodyScrolled(direction) }
    }
}
