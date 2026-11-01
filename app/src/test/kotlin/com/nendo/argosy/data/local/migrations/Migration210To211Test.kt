package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration210To211Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_210_211.migrate(db)
        return captured
    }

    @Test
    fun `adds the per-game backend as nullable text, so every existing game inherits`() {
        assertEquals(
            listOf("ALTER TABLE `emulator_configs` ADD COLUMN `graphicsBackend` TEXT"),
            statements()
        )
    }

    @Test
    fun `is registered`() {
        assert(Migration_210_211 in MigrationRegistry.ALL)
    }
}
