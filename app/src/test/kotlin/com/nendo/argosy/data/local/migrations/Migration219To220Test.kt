package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration219To220Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_219_220.migrate(db)
        return captured
    }

    @Test
    fun `drops only the auto-detected hakuX save path`() {
        assertEquals(
            listOf(
                "DELETE FROM `emulator_save_config` WHERE `emulatorId` = 'hakux' " +
                    "AND `isAutoDetected` = 1 AND `isUserOverride` = 0"
            ),
            statements()
        )
    }
}
