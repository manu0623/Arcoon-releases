package com.nendo.argosy.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class OffScreenLaunchTargetTest {

    private val handheld = mockk<Display>(relaxed = true) {
        every { displayId } returns Display.DEFAULT_DISPLAY
        every { state } returns Display.STATE_ON
    }
    private val tv = mockk<Display>(relaxed = true) {
        every { displayId } returns TV_DISPLAY
        every { state } returns Display.STATE_ON
    }
    private val displayManager = mockk<DisplayManager>(relaxed = true) {
        every { displays } returns arrayOf(handheld, tv)
    }
    private val context = mockk<Context>(relaxed = true) {
        every { getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
    }
    private val screenCatalog = mockk<ScreenCatalog>(relaxed = true) {
        every { attachedScreens() } returns listOf(
            AttachedScreen("handheld", Display.DEFAULT_DISPLAY, 1, 1920, 1080, true),
            AttachedScreen(TV_KEY, TV_DISPLAY, 2, 3840, 2160, false)
        )
    }
    private val helper = DisplayAffinityHelper(context, screenCatalog).apply {
        dualScreenEnabled = true
        roleDisplayIds = Display.DEFAULT_DISPLAY to Display.DEFAULT_DISPLAY
    }

    @Before
    fun setUp() {
        mockkObject(ScreenCatalog.Companion)
        every { ScreenCatalog.isScreen(any()) } returns true
        mockkObject(DisplayAffinityHelper.Companion)
        every { DisplayAffinityHelper.dockedExternalDisplayId(context) } returns null
    }

    @After
    fun tearDown() {
        unmockkObject(ScreenCatalog.Companion)
        unmockkObject(DisplayAffinityHelper.Companion)
    }

    @Test
    fun `a screen that is on takes an explicit pick`() {
        assertEquals(TV_DISPLAY, helper.launchDisplayId(forEmulator = false, overrideDisplayId = TV_DISPLAY))
        assertEquals(TV_DISPLAY, helper.largestDisplayId())
    }

    @Test
    fun `an explicit pick of an off screen lands on the primary screen`() {
        helper.offDisplayIds = setOf(TV_DISPLAY)

        assertEquals(
            Display.DEFAULT_DISPLAY,
            helper.launchDisplayId(forEmulator = true, overrideDisplayId = TV_DISPLAY)
        )
        assertEquals(
            Display.DEFAULT_DISPLAY,
            helper.launchDisplayId(forEmulator = false, overrideDisplayId = TV_DISPLAY)
        )
    }

    @Test
    fun `the largest screen skips an off screen`() {
        helper.offDisplayIds = setOf(TV_DISPLAY)

        assertEquals(Display.DEFAULT_DISPLAY, helper.largestDisplayId())
    }

    @Test
    fun `an app pinned to an off screen does not open there`() {
        helper.offDisplayIds = setOf(TV_DISPLAY)

        assertNotEquals(TV_DISPLAY, helper.appLaunchDisplayId(preferredScreenKey = TV_KEY))
    }

    @Test
    fun `the usable check passes on screens and drops off ones`() {
        helper.offDisplayIds = setOf(TV_DISPLAY)

        assertEquals(Display.DEFAULT_DISPLAY, helper.usableDisplayId(Display.DEFAULT_DISPLAY))
        assertEquals(null, helper.usableDisplayId(TV_DISPLAY))
    }

    private companion object {
        const val TV_DISPLAY = 2
        const val TV_KEY = "tv"
    }
}
