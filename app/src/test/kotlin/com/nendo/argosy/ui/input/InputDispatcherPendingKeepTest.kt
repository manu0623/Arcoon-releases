package com.nendo.argosy.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDispatcherPendingKeepTest {

    private class Recorder : InputHandler {
        var downs = 0
        override fun onDown(): InputResult {
            downs++
            return InputResult.HANDLED
        }
    }

    private val down = GamepadInput(GamepadEvent.Down)

    @Test
    fun `a handler parked for the current route survives the topology reset and takes the dpad`() {
        val dispatcher = InputDispatcher()
        val home = Recorder()
        dispatcher.subscribeView(home, forRoute = "home")

        dispatcher.clearPendingViewSubscription(keepRoute = "home")
        dispatcher.setCurrentRoute("home")
        val result = dispatcher.dispatch(down)

        assertTrue(result.handled)
        assertEquals(1, home.downs)
    }

    @Test
    fun `a handler parked for a bare route survives a reset that keeps its destination pattern`() {
        val dispatcher = InputDispatcher()
        val gameDetail = Recorder()
        dispatcher.subscribeView(gameDetail, forRoute = "game")

        dispatcher.clearPendingViewSubscription(keepRoute = "game/{gameId}")
        dispatcher.setCurrentRoute("game/{gameId}")
        val result = dispatcher.dispatch(down)

        assertTrue(result.handled)
        assertEquals(1, gameDetail.downs)
    }

    @Test
    fun `a handler parked for another route is dropped by the topology reset`() {
        val dispatcher = InputDispatcher()
        val settings = Recorder()
        dispatcher.subscribeView(settings, forRoute = "settings")

        dispatcher.clearPendingViewSubscription(keepRoute = "home")
        dispatcher.setCurrentRoute("settings")
        val result = dispatcher.dispatch(down)

        assertFalse(result.handled)
        assertEquals(0, settings.downs)
    }

    @Test
    fun `a reset with no route to keep drops every parked handler`() {
        val dispatcher = InputDispatcher()
        val home = Recorder()
        val settings = Recorder()
        dispatcher.subscribeView(home, forRoute = "home")
        dispatcher.subscribeView(settings, forRoute = "settings")

        dispatcher.clearPendingViewSubscription()
        dispatcher.setCurrentRoute("home")
        val homeResult = dispatcher.dispatch(down)
        dispatcher.setCurrentRoute("settings")
        val settingsResult = dispatcher.dispatch(down)

        assertFalse(homeResult.handled)
        assertFalse(settingsResult.handled)
        assertEquals(0, home.downs)
        assertEquals(0, settings.downs)
    }
}
