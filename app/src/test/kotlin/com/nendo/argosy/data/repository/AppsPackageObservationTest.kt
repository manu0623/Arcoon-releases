package com.nendo.argosy.data.repository

import android.content.Context
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

class AppsPackageObservationTest {
    @Test
    fun `initial package refresh arrives after receiver registration`() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        var registered = false
        mockkStatic(ContextCompat::class)
        try {
            every { ContextCompat.registerReceiver(context, any(), any(), any()) } answers {
                registered = true
                null
            }
            val repository = AppsRepository(context)
            val observed = withTimeoutOrNull(1000) { repository.packageChanges.first(); registered }

            assertEquals(true, observed)
        } finally {
            unmockkStatic(ContextCompat::class)
        }
    }
}
