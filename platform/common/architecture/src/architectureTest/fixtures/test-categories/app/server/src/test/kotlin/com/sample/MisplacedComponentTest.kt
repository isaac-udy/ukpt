package com.sample

import feature.sample.server.web.SampleForm
import feature.sample.server.web.sampleForm
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.jsoup.Jsoup
import platform.testing.ComponentTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ComponentTest
class MisplacedComponentTest {

    @Test
    fun `the form shows its error`() {
        val html = createHTML().div { sampleForm(SampleForm.Empty, SampleForm.Errors(label = "Enter a label.")) }

        assertEquals("Enter a label.", Jsoup.parse(html).select(".field-error").text())
    }
}
