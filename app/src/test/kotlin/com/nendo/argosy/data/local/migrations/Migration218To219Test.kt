package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration218To219Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_218_219.migrate(db)
        return captured
    }

    @Test
    fun `adds a nullable per-platform motion override`() {
        assertEquals(
            listOf("ALTER TABLE `platform_libretro_settings` ADD COLUMN `motionEnabled` INTEGER"),
            statements()
        )
    }
}
