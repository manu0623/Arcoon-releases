package com.nendo.argosy

import org.junit.Assert.assertEquals
import org.junit.Test

class DualScreenManagerHolderTest {

    @Test
    fun `a finishing activity sees the activity that replaced it`() {
        DualScreenManagerHolder.attach()
        DualScreenManagerHolder.attach()

        assertEquals(1, DualScreenManagerHolder.detachAndCountRemaining())
        assertEquals(0, DualScreenManagerHolder.detachAndCountRemaining())
    }

    @Test
    fun `detaching more than attached never goes negative`() {
        assertEquals(0, DualScreenManagerHolder.detachAndCountRemaining())
        DualScreenManagerHolder.attach()
        assertEquals(0, DualScreenManagerHolder.detachAndCountRemaining())
    }
}
