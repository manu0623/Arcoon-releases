package com.nendo.argosy.data.remote.jellyfin

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JellyfinItemQueryParamsTest {

    private val client = JellyfinApiClient(mockk(relaxed = true))

    @Test
    fun `a query that asks for box-set members un-collapsed sends collapseBoxSetItems false`() {
        val params = client.buildItemQueryParams(userId = "u", includeItemTypes = "Movie", collapseBoxSetItems = false)

        assertEquals("false", params["collapseBoxSetItems"])
    }

    @Test
    fun `a query that leaves grouping to the server sends no collapse parameter`() {
        val params = client.buildItemQueryParams(userId = "u", includeItemTypes = "Movie")

        assertFalse("collapseBoxSetItems" in params)
    }
}
