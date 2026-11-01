package com.nendo.argosy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FooterHostControllerTest {

    private fun entry(vararg buttons: InputButton, reservesSpace: Boolean = true, forced: Boolean = false) = FooterEntry(
        hints = buttons.map { FooterHintItem(it, it.name) },
        style = FooterStyleConfig(),
        variant = FooterVariant.STANDARD,
        onHintClick = null,
        trailingContent = null,
        forced = forced,
        reservesSpace = reservesSpace
    )

    private fun FooterHostController.push(entry: FooterEntry): Long = allocateId().also { set(it, entry) }

    @Test
    fun `an entry that does not reserve space leaves the bar's space to the entry below`() {
        val controller = FooterHostController()
        controller.push(entry(InputButton.A, InputButton.Y))
        val overlay = entry(InputButton.B, reservesSpace = false)
        controller.push(overlay)

        assertTrue(controller.isBarVisible)
        assertEquals(overlay, controller.top)

        controller.push(entry(reservesSpace = false))
        assertTrue(controller.isBarVisible)
    }

    @Test
    fun `an entry that does not reserve space over a screen with no hints holds no space`() {
        val controller = FooterHostController()
        controller.push(entry())
        controller.push(entry(InputButton.B, reservesSpace = false))

        assertFalse(controller.isBarVisible)
    }

    @Test
    fun `the topmost entry that reserves space decides, forced or with a hint that is not obvious`() {
        val controller = FooterHostController()
        assertFalse(controller.isBarVisible)

        val screen = controller.push(entry(InputButton.Y))
        assertTrue(controller.isBarVisible)

        controller.set(screen, entry(InputButton.A, InputButton.B))
        assertFalse(controller.isBarVisible)

        controller.set(screen, entry())
        assertFalse(controller.isBarVisible)

        controller.set(screen, entry(forced = true))
        assertTrue(controller.isBarVisible)

        controller.remove(screen)
        assertFalse(controller.isBarVisible)
    }
}
