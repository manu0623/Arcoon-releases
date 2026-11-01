package com.nendo.argosy.data.emulator

import android.content.Intent
import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinnedPastPathPatternTest {

    private val probeActivity = "info.cemu.cemu.emulation.EmulationActivity"

    private fun viewCommand(activityClass: String? = null, action: String = Intent.ACTION_VIEW) =
        EffectiveLaunchCommand(
            action = action,
            packageName = "info.cemu.cemu",
            activityClass = activityClass,
            dataUri = mockk<Uri>(relaxed = true),
            mimeType = "application/octet-stream",
            intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK
        )

    private val filterRejectsRealPath: (String?) -> String? = { path ->
        if (path == "/rom.wua") probeActivity else null
    }

    @Test
    fun `a path the filter rejects is pinned to the activity that takes the probe path`() {
        val pinned = viewCommand().pinnedPastPathPattern("wua", filterRejectsRealPath)

        assertEquals(probeActivity, pinned.activityClass)
    }

    @Test
    fun `pinning changes nothing but the activity`() {
        val command = viewCommand()

        val pinned = command.pinnedPastPathPattern("wua", filterRejectsRealPath)

        assertEquals(command.copy(activityClass = probeActivity), pinned)
    }

    @Test
    fun `a path the filter accepts stays a package-only launch`() {
        val pinned = viewCommand().pinnedPastPathPattern("wua") { probeActivity }

        assertNull(pinned.activityClass)
    }

    @Test
    fun `a path no activity takes even as a probe stays a package-only launch`() {
        val pinned = viewCommand().pinnedPastPathPattern("wua") { null }

        assertNull(pinned.activityClass)
    }

    @Test
    fun `an activity named by the registry is kept`() {
        val pinned = viewCommand(activityClass = "info.cemu.cemu.MainActivity")
            .pinnedPastPathPattern("wua", filterRejectsRealPath)

        assertEquals("info.cemu.cemu.MainActivity", pinned.activityClass)
    }

    @Test
    fun `a non-VIEW launch is left alone`() {
        val pinned = viewCommand(action = Intent.ACTION_MAIN)
            .pinnedPastPathPattern("wua", filterRejectsRealPath)

        assertNull(pinned.activityClass)
    }

    @Test
    fun `a rom without an extension is left alone`() {
        val pinned = viewCommand().pinnedPastPathPattern("") { path -> if (path != null) probeActivity else null }

        assertNull(pinned.activityClass)
    }
}
