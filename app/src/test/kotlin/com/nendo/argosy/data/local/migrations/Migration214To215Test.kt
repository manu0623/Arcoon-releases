package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration214To215Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_214_215.migrate(db)
        return captured
    }

    @Test
    fun `adds the full sync skip flag off, so every platform stays in the full pass`() {
        assertEquals(
            listOf("ALTER TABLE `platforms` ADD COLUMN `skipFullSync` INTEGER NOT NULL DEFAULT 0"),
            statements()
        )
    }

    @Test
    fun `is registered`() {
        assertTrue(Migration_214_215 in MigrationRegistry.ALL)
    }
}
