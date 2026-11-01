package com.nendo.argosy.libretro

import android.content.ComponentName
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameWindowThemeTest {

    @Test
    fun builtInGameWindowOpensOnBlack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, LibretroActivity::class.java),
            0
        )
        assertEquals(R.style.Theme_Argosy_Game, info.theme)
        val attrs = ContextThemeWrapper(context, info.theme)
            .obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        val background = try {
            attrs.getColor(0, Color.TRANSPARENT)
        } finally {
            attrs.recycle()
        }
        assertEquals(Color.BLACK, background)
    }
}
