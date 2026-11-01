package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesDockTest {
    private val key = stringPreferencesKey("secondary_home_apps")

    @Test
    fun `legacy CSV preserves order and removes blanks and duplicates`() = runBlocking {
        val repository = AppPreferencesRepository(Store(mutablePreferencesOf(key to "c,,a,c,b")))

        assertEquals(listOf("c", "a", "b"), repository.preferences.first().secondaryHomeApps)
    }

    @Test
    fun `pin changes preserve reordered survivors and concurrent additions`() = runBlocking {
        val repository = AppPreferencesRepository(Store(mutablePreferencesOf(key to "c,a,b")))
        listOf("d", "e").map { async { repository.toggleDockPin(it) } }.awaitAll()
        repository.toggleDockPin("a")

        val pins = repository.preferences.first().secondaryHomeApps
        assertEquals(listOf("c", "b"), pins.take(2))
        assertEquals(setOf("d", "e"), pins.drop(2).toSet())
        assertEquals(4, pins.size)
    }

    @Test
    fun `stale reorder cannot erase a newer pin and membership cannot change`() = runBlocking {
        val original = listOf("a", "b", "c")
        val repository = AppPreferencesRepository(Store(mutablePreferencesOf(key to original.joinToString(","))))
        repository.toggleDockPin("d")

        assertFalse(repository.reorderDockPins(original, original.reversed()))
        assertEquals(original + "d", repository.preferences.first().secondaryHomeApps)
        assertFalse(repository.reorderDockPins(original + "d", listOf("a")))
        assertTrue(repository.reorderDockPins(original + "d", listOf("c", "b", "a", "d")))
        assertEquals(listOf("c", "b", "a", "d"), repository.preferences.first().secondaryHomeApps)
    }

    private class Store(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        private val mutex = Mutex()

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(data.value).also { data.value = it } }
    }
}
