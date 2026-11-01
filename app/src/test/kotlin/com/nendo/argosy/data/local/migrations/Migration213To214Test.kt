package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration213To214Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_213_214.migrate(db)
        return captured
    }

    @Test
    fun `creates the patch cache keyed by game and owner, with an owner index for account removal`() {
        assertEquals(
            listOf(
                "CREATE TABLE IF NOT EXISTS `ra_patch_cache` (" +
                    "`raGameId` INTEGER NOT NULL, `ownerUserId` INTEGER NOT NULL, " +
                    "`patchJson` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`raGameId`, `ownerUserId`))",
                "CREATE INDEX IF NOT EXISTS `index_ra_patch_cache_ownerUserId` ON `ra_patch_cache` (`ownerUserId`)"
            ),
            statements()
        )
    }

    @Test
    fun `is registered`() {
        assertTrue(Migration_213_214 in MigrationRegistry.ALL)
    }
}
