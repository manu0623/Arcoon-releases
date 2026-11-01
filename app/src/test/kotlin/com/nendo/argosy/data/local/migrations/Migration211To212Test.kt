package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration211To212Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_211_212.migrate(db)
        return captured
    }

    @Test
    fun `adds the session recap facts as nullable columns, so every recorded session keeps its row`() {
        assertEquals(
            listOf(
                "ALTER TABLE `play_sessions` ADD COLUMN `raGameId` INTEGER",
                "ALTER TABLE `play_sessions` ADD COLUMN `richPresence` TEXT",
                "ALTER TABLE `play_sessions` ADD COLUMN `richPresenceAt` INTEGER"
            ),
            statements()
        )
    }

    @Test
    fun `is registered`() {
        assertTrue(Migration_211_212 in MigrationRegistry.ALL)
    }
}
