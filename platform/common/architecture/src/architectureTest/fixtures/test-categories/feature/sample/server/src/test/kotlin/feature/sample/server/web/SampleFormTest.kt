package feature.sample.server.web

import platform.server.web.FormResult
import platform.testing.UnitTest
import kotlin.test.Test
import kotlin.test.assertEquals

@UnitTest
class SampleFormTest {

    @Test
    fun `an empty label is invalid`() {
        val state = SamplePageState(emptyList(), SampleForm(label = "  "), errors = null)

        assertEquals(FormResult.Invalid(SampleForm.Errors(label = "Enter a label.")), state.form.validate())
    }
}
