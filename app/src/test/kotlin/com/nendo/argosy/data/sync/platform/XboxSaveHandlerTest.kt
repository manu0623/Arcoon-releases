package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class XboxSaveHandlerTest {

    private val fal = realFsFal()
    private val handler = XboxSaveHandler(
        mockk<Context>(relaxed = true),
        fal,
        SaveArchiver(mockk<AndroidDataAccessor>(relaxed = true), fal)
    )
    private val config = SavePathRegistry.getConfigIncludingUnsupported("hakux")!!

    @Test
    fun `a save path override never moves hakuX off its native UDATA folder`() {
        val resolved = handler.resolveBasePath(
            config,
            "/storage/emulated/0/Android/data/com.rfandango.haku_x/files/x1box"
        )

        assertEquals(UDATA, resolved)
        assertEquals(UDATA, handler.resolveBasePath(config, null))
    }

    private companion object {
        const val UDATA = "/data/data/com.rfandango.haku_x/files/native/hdd/Partition1/UDATA"
    }
}
