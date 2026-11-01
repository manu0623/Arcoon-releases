package com.nendo.argosy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DockReorderInputHandlerTest {
    @Test
    fun `controller directions follow physical positions and stop at each end`() {
        val pins = listOf("a", "b", "c")
        for (isRtl in listOf(false, true)) {
            val original = DockReorderSession.lift(1, true, pins, pins, "b")!!
            var session = original
            var saved: List<String>? = null
            val handler = DockReorderInputHandler(isRtl,
                onMove = { session = session.target(session.targetIndex + it) },
                onSave = { saved = session.order },
                onCancel = { session = original }
            )
            handler.onLeft()
            handler.onLeft()
            assertEquals(if (isRtl) listOf("a", "c", "b") else listOf("b", "a", "c"), session.order)
            handler.onRight()
            assertEquals(pins, session.order)
            handler.onRight()
            handler.onRight()
            assertEquals(if (isRtl) listOf("b", "a", "c") else listOf("a", "c", "b"), session.order)
            handler.onConfirm()
            assertEquals(session.order, saved)
            handler.onBack()
            assertEquals(pins, session.order)
        }
    }
}
