package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration215To216Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_215_216.migrate(db)
        return captured
    }

    @Test
    fun `creates the play record cutoffs keyed by game and owner, with an owner index for account removal`() {
        assertEquals(
            listOf(
                "CREATE TABLE IF NOT EXISTS `play_record_deletions` (" +
                    "`gameId` INTEGER NOT NULL, `ownerUserId` INTEGER NOT NULL, `deletedBefore` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`gameId`, `ownerUserId`))",
                "CREATE INDEX IF NOT EXISTS `index_play_record_deletions_ownerUserId` " +
                    "ON `play_record_deletions` (`ownerUserId`)"
            ),
            statements()
        )
    }

    @Test
    fun `is registered`() {
        assertEquals(Migration_215_216, MigrationRegistry.byKey(215, 216))
    }
}
