package com.nendo.argosy.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppsRepositoryPackageChangesTest {
    @Test
    fun `replacing removal does not invalidate installed package state`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val receiver = slot<BroadcastReceiver>()
        mockkStatic(ContextCompat::class)
        try {
            every { ContextCompat.registerReceiver(context, capture(receiver), any(), any<Int>()) } returns null
            val repository = AppsRepository(context)
            val changes = mutableListOf<String>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.packageChanges.collect { changes += it }
            }
            val uri = mockk<Uri> { every { schemeSpecificPart } returns "app.gamenative" }
            val removal = mockk<Intent> {
                every { action } returns Intent.ACTION_PACKAGE_REMOVED
                every { data } returns uri
                every { getBooleanExtra(Intent.EXTRA_REPLACING, false) } returns true
            }

            receiver.captured.onReceive(context, removal)
            runCurrent()

            assertEquals(listOf(""), changes)
            assertTrue(repository.isPackageUpdating("app.gamenative"))
            assertFalse(repository.isPackageMissing("app.gamenative"))

            val added = mockk<Intent> {
                every { action } returns Intent.ACTION_PACKAGE_ADDED
                every { data } returns uri
            }
            receiver.captured.onReceive(context, added)
            runCurrent()
            assertFalse(repository.isPackageUpdating("app.gamenative"))
            assertEquals(listOf("", "app.gamenative"), changes)

            val uninstall = mockk<Intent> {
                every { action } returns Intent.ACTION_PACKAGE_REMOVED
                every { data } returns uri
                every { getBooleanExtra(Intent.EXTRA_REPLACING, false) } returns false
            }
            receiver.captured.onReceive(context, uninstall)
            runCurrent()
            assertEquals(listOf("", "app.gamenative", "app.gamenative"), changes)
        } finally {
            unmockkStatic(ContextCompat::class)
        }
    }

    @Test
    fun `only verified package absence counts as an uninstall`() {
        val manager = mockk<PackageManager>()
        val context = mockk<Context> { every { packageManager } returns manager }
        val repository = AppsRepository(context)
        every { manager.getPackageInfo("missing", 0) } throws PackageManager.NameNotFoundException()
        every { manager.getPackageInfo("denied", 0) } throws SecurityException()
        every { manager.getPackageInfo("installed", 0) } returns mockk<PackageInfo>()

        assertTrue(repository.isPackageMissing("missing"))
        assertFalse(repository.isPackageMissing("denied"))
        assertFalse(repository.isPackageMissing("installed"))
    }
}
