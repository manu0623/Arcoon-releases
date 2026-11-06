package com.nendo.argosy.domain.usecase.journey

import com.nendo.argosy.domain.model.JourneyCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementClassifierTest {

    @Test
    fun `type token decides over wording`() {
        val category = AchievementClassifier.classify(
            type = "win_condition",
            title = "Collect every gem",
            description = null
        )
        assertEquals(JourneyCategory.COMPLETION, category)
    }

    @Test
    fun `progression token marks the main path`() {
        val category = AchievementClassifier.classify(
            type = "progression",
            title = "A quiet start",
            description = null
        )
        assertEquals(JourneyCategory.PROGRESSION, category)
    }

    @Test
    fun `boss defeat counts as combat`() {
        val category = AchievementClassifier.classify(
            type = null,
            title = "Iron Sentinel",
            description = "Defeat the guardian of the eastern gate."
        )
        assertEquals(JourneyCategory.COMBAT, category)
    }

    @Test
    fun `damage free boss counts as mastery`() {
        val category = AchievementClassifier.classify(
            type = null,
            title = "Untouchable",
            description = "Defeat the guardian without taking damage."
        )
        assertEquals(JourneyCategory.MASTERY, category)
    }

    @Test
    fun `secret room counts as exploration`() {
        val category = AchievementClassifier.classify(
            type = null,
            title = "Behind the waterfall",
            description = "Find the hidden room north of the village."
        )
        assertEquals(JourneyCategory.EXPLORATION, category)
    }

    @Test
    fun `item set counts as collection`() {
        val category = AchievementClassifier.classify(
            type = null,
            title = "Hoarder",
            description = "Collect every item in the armory."
        )
        assertEquals(JourneyCategory.COLLECTION, category)
    }

    @Test
    fun `credits roll marks completion`() {
        val category = AchievementClassifier.classify(
            type = null,
            title = "The long road",
            description = "Reach the ending and watch the credits."
        )
        assertEquals(JourneyCategory.COMPLETION, category)
    }

    @Test
    fun `empty wording stays unclassified`() {
        val category = AchievementClassifier.classify(type = null, title = "Zzyzx", description = null)
        assertEquals(JourneyCategory.UNCLASSIFIED, category)
    }

    @Test
    fun `cue matches whole words only`() {
        val category = AchievementClassifier.classify(
            type = null,
            title = "Arena champion",
            description = null
        )
        assertEquals(JourneyCategory.UNCLASSIFIED, category)
    }
}
