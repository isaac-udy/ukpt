package architecture.definitions

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceTextTest {

    @Test
    fun `strings and comments are blanked, template expressions kept`() {
        val source = """val a = "feature.x.Foo" // feature.y.Bar""" + "\n" +
            """val b = "${'$'}{feature.z.Baz()} \" escaped" /* feature.w.Qux */"""
        assertEquals("val a =  \nval b = \${feature.z.Baz()} ", source.withoutStringsAndComments())
    }

    @Test
    fun `a very long string literal does not overflow the stack`() {
        val literal = "\"" + ("let x = 1; ".repeat(200) + "\\n").repeat(100) + "\""
        assertEquals("val js =  ", "val js = $literal ".withoutStringsAndComments())
    }
}
