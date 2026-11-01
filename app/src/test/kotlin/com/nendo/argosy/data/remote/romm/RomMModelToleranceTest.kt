package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomMModelToleranceTest {

    private val moshi = Moshi.Builder().build()

    @Test
    fun `a rom missing optional keys still parses`() {
        val json = """{"id":7,"platform_id":3,"platform_slug":"snes","name":"ActRaiser"}"""

        val rom = moshi.adapter(RomMRom::class.java).fromJson(json)!!

        assertEquals(7L, rom.id)
        assertNull(rom.slug)
        assertNull(rom.filePath)
        assertNull(rom.igdbId)
        assertNull(rom.coverLarge)
    }

    @Test
    fun `a save missing its emulator still parses`() {
        val json = """{"id":1,"rom_id":7,"user_id":2,"file_name":"a.srm","updated_at":"2026-10-27T00:00:00Z"}"""

        val save = moshi.adapter(RomMSave::class.java).fromJson(json)!!

        assertNull(save.emulator)
    }
}
