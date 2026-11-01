package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration209To210Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_209_210.migrate(db)
        return captured
    }

    @Test
    fun `adds the graphics columns as nullable text, so every existing row inherits`() {
        assertEquals(
            listOf(
                "ALTER TABLE `platform_libretro_settings` ADD COLUMN `graphicsApi` TEXT",
                "ALTER TABLE `platform_libretro_settings` ADD COLUMN `gpuDriver` TEXT",
                "ALTER TABLE `emulator_configs` ADD COLUMN `gpuDriver` TEXT"
            ),
            statements()
        )
    }

    @Test
    fun `is registered`() {
        assert(Migration_209_210 in MigrationRegistry.ALL)
    }
}
