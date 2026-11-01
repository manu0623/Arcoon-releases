package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.screens.settings.sections.InterfaceItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceLayoutState
import com.nendo.argosy.ui.screens.settings.sections.interfaceFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.interfaceItemAtFocusIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceHomeAppLayoutTest {

    private val notHome = InterfaceLayoutState(display = DisplayState(isDefaultHome = false))
    private val home = InterfaceLayoutState(display = DisplayState(isDefaultHome = true))

    @Test
    fun `the switch row is reachable while argosy is not the default home`() {
        assertTrue(InterfaceItem.HomeApp.visibleWhen(notHome))
        assertFalse(InterfaceItem.HomeAppLocked.visibleWhen(notHome))
        val index = interfaceFocusIndexOf(InterfaceItem.HomeApp, notHome)
        assertEquals(InterfaceItem.HomeApp, interfaceItemAtFocusIndex(index, notHome))
    }

    @Test
    fun `the locked row replaces the switch and takes no focus while argosy is the default home`() {
        assertFalse(InterfaceItem.HomeApp.visibleWhen(home))
        assertTrue(InterfaceItem.HomeAppLocked.visibleWhen(home))
        assertFalse(InterfaceItem.HomeAppLocked.isFocusable)
        assertEquals(-1, interfaceFocusIndexOf(InterfaceItem.HomeApp, home))
        assertEquals(-1, interfaceFocusIndexOf(InterfaceItem.HomeAppLocked, home))
    }

    @Test
    fun `the home app row sits in the screens group after lock screen art`() {
        assertEquals(
            InterfaceItem.ALL.indexOf(InterfaceItem.LockScreenArt) + 1,
            InterfaceItem.ALL.indexOf(InterfaceItem.HomeApp)
        )
        assertEquals(InterfaceItem.LockScreenArt.section, InterfaceItem.HomeApp.section)
        assertEquals(InterfaceItem.LockScreenArt.section, InterfaceItem.HomeAppLocked.section)
    }
}
