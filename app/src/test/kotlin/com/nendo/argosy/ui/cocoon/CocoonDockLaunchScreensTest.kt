package com.nendo.argosy.ui.cocoon

import com.nendo.argosy.util.AttachedScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CocoonDockLaunchScreensTest {

    private fun screen(key: String, number: Int, displayId: Int = number) = AttachedScreen(
        key = key,
        displayId = displayId,
        number = number,
        widthPx = 1920,
        heightPx = 1080,
        builtIn = number == 1
    )

    @Test
    fun keepsDisplaysInTheirNumberedOrder() {
        val screens = cocoonLaunchScreens(
            listOf(screen("upper", 1), screen("lower", 2), screen("monitor", 3))
        )
        assertEquals(listOf("upper", "lower", "monitor"), screens.map { it.key })
        assertEquals(listOf(1, 2, 3), screens.map { it.number })
    }

    @Test
    fun oneScreenOffersNoPicker() {
        assertFalse(needsCocoonScreenPicker(cocoonLaunchScreens(listOf(screen("upper", 1)))))
    }

    @Test
    fun twoScreensOfferThePicker() {
        assertTrue(
            needsCocoonScreenPicker(
                cocoonLaunchScreens(listOf(screen("upper", 1), screen("lower", 2)))
            )
        )
    }

    @Test
    fun noScreensOffersNoPicker() {
        assertFalse(needsCocoonScreenPicker(cocoonLaunchScreens(emptyList())))
    }
}
