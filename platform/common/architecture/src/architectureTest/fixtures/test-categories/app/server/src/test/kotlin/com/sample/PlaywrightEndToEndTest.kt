package com.sample

import com.microsoft.playwright.Playwright
import feature.sample.server.web.SamplePaths
import feature.sample.tests.onSamplePage
import platform.testing.EndToEndTest
import platform.testing.browser.asUserInBrowser
import platform.testing.subcutaneous.inProcessServer
import kotlin.test.Test
import kotlin.test.assertEquals

@EndToEndTest
class PlaywrightEndToEndTest {

    @Test
    fun `the list is a list`() = inProcessServer(::serverDependencies) {
        asUserInBrowser(SamplePaths.HOME) {
            onSamplePage { seeSamples() }
        }
        Playwright.create().use { playwright ->
            val page = playwright.chromium().launch().newPage()
            page.navigate(baseUrl + SamplePaths.HOME)
            assertEquals("UL", page.locator("#samples").evaluate("it => it.tagName"))
        }
    }
}
