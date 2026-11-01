package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration216To217Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_216_217.migrate(db)
        return captured
    }

    @Test
    fun `drops the steam download queue and depot tracking tables`() {
        assertEquals(
            listOf(
                "DROP TABLE IF EXISTS `steam_download_queue`",
                "DROP TABLE IF EXISTS `steam_completed_files`",
                "DROP TABLE IF EXISTS `steam_completed_depots`"
            ),
            statements()
        )
    }

    @Test
    fun `is registered and leaves no gap`() {
        assertEquals(Migration_216_217, MigrationRegistry.byKey(216, 217))
        MigrationRegistry.assertContiguous(MigrationRegistry.ALL.last().endVersion)
    }
}
