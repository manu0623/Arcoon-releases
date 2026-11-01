package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration217To218Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_217_218.migrate(db)
        return captured
    }

    @Test
    fun `adds one pending flag per watch state field and marks every queued row pending in all three`() {
        assertEquals(
            listOf(
                "ALTER TABLE `media_user_data` ADD COLUMN `pendingPosition` INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE `media_user_data` ADD COLUMN `pendingPlayed` INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE `media_user_data` ADD COLUMN `pendingFavorite` INTEGER NOT NULL DEFAULT 0",
                "UPDATE `media_user_data` SET `pendingPosition` = 1, `pendingPlayed` = 1, " +
                    "`pendingFavorite` = 1 WHERE `needsSync` = 1"
            ),
            statements()
        )
    }

    @Test
    fun `is registered and leaves no gap`() {
        assertEquals(Migration_217_218, MigrationRegistry.byKey(217, 218))
        MigrationRegistry.assertContiguous(MigrationRegistry.ALL.last().endVersion)
    }
}
