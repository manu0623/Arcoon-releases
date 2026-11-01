package com.nendo.argosy.ui.screens.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test

class GameActivityStarterTest {

    private val appContext = mockk<Context>(relaxed = true)
    private val options = mockk<Bundle>()

    private fun intent(alreadyLaunched: Boolean = false): Intent = mockk(relaxed = true) {
        every { getBooleanExtra("argosy.already_launched", false) } returns alreadyLaunched
    }

    private fun activity(finishing: Boolean = false, destroyed: Boolean = false): Activity =
        mockk(relaxed = true) {
            every { isFinishing } returns finishing
            every { isDestroyed } returns destroyed
        }

    @Test
    fun `a live activity starts the game with its placement`() {
        val host = activity()
        val intent = intent()

        val result = GameActivityStarter(appContext) { host }.start(GameLaunchRequest(intent, options))

        assertTrue(result.isSuccess)
        verify(exactly = 1) { host.startActivity(intent, options) }
        verify(exactly = 0) { appContext.startActivity(any(), any()) }
        verify(exactly = 0) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    @Test
    fun `a destroyed activity hands the start to the application as a new task`() {
        val host = activity(destroyed = true)
        val intent = intent()

        GameActivityStarter(appContext) { host }.start(GameLaunchRequest(intent, options))

        verify(exactly = 0) { host.startActivity(any(), any()) }
        verify(exactly = 1) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        verify(exactly = 1) { appContext.startActivity(intent, options) }
    }

    @Test
    fun `a finishing activity hands the start to the application`() {
        val host = activity(finishing = true)
        val intent = intent()

        GameActivityStarter(appContext) { host }.start(GameLaunchRequest(intent, options))

        verify(exactly = 0) { host.startActivity(any(), any()) }
        verify(exactly = 1) { appContext.startActivity(intent, options) }
    }

    @Test
    fun `no activity at all starts from the application`() {
        val intent = intent()

        GameActivityStarter(appContext) { null }.start(GameLaunchRequest(intent, options))

        verify(exactly = 1) { appContext.startActivity(intent, options) }
    }

    @Test
    fun `an emulator the launch already started is not started twice`() {
        val host = activity()
        val intent = intent(alreadyLaunched = true)

        val result = GameActivityStarter(appContext) { host }.start(GameLaunchRequest(intent, options))

        assertTrue(result.isSuccess)
        verify(exactly = 0) { host.startActivity(any(), any()) }
        verify(exactly = 0) { appContext.startActivity(any(), any()) }
    }

    @Test
    fun `a refused start is reported as a failure`() {
        val host = activity()
        val intent = intent()
        every { host.startActivity(intent, options) } throws SecurityException("denied")

        val result = GameActivityStarter(appContext) { host }.start(GameLaunchRequest(intent, options))

        assertTrue(result.exceptionOrNull() is SecurityException)
    }
}
