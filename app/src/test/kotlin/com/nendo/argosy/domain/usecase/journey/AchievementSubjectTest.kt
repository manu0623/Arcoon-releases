package com.nendo.argosy.domain.usecase.journey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AchievementSubjectTest {

    @Test
    fun `a named opponent is read from the description`() {
        assertEquals("Brock", AchievementSubject.of("Defeat Brock in Pewter City."))
    }

    @Test
    fun `a determiner is dropped before the name`() {
        assertEquals("guardian", AchievementSubject.of("Defeat the guardian of the eastern gate."))
    }

    @Test
    fun `a quantifier names nothing`() {
        assertNull(AchievementSubject.of("Defeat your first opposing Pokemon."))
    }

    @Test
    fun `a description without an action verb names nothing`() {
        assertNull(AchievementSubject.of("A quiet walk through the village."))
    }

    @Test
    fun `a missing description names nothing`() {
        assertNull(AchievementSubject.of(null))
    }

    @Test
    fun `a collected item is read`() {
        assertEquals("Silver Key", AchievementSubject.of("Collect the Silver Key from the cellar."))
    }
}
