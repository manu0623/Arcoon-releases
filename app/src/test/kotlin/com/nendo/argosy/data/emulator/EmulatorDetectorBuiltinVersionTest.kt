package com.nendo.argosy.data.emulator

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmulatorDetectorBuiltinVersionTest {

    @Suppress("DEPRECATION")
    @Test
    fun `the built-in emulator never carries the launcher's own version`() = runBlocking {
        val packageManager = mockk<PackageManager>()
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } throws PackageManager.NameNotFoundException()
        every { packageManager.getPackageInfo(APP_PACKAGE, any<Int>()) } returns
            PackageInfo().apply { versionName = "2.22.1" }
        every { packageManager.getInstalledPackages(any<Int>()) } returns emptyList()
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns APP_PACKAGE
        every { context.packageManager } returns packageManager

        val builtin = EmulatorDetector(context).detectEmulators()
            .single { it.def.id == EmulatorRegistry.BUILTIN_ID }

        assertNull(builtin.versionName)
        assertEquals(0L, builtin.versionCode)
    }

    private companion object {
        const val APP_PACKAGE = "com.nendo.argosy"
    }
}
