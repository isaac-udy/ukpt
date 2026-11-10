package com.sample

import feature.sample.tests.samples
import platform.testing.EndToEndTest
import platform.testing.subcutaneous.inProcessServer
import kotlin.test.Test
import kotlin.test.assertEquals

@EndToEndTest
class RequestOnlyEndToEndTest {

    @Test
    fun `the server starts`() = inProcessServer(::serverDependencies) {
        // Names asUserInBrowser( only in this comment.
        assertEquals(listOf("a"), given { samples("a") })
    }
}
