package feature.ukpt.server.web

import dev.isaacudy.udytils.htmlsnapshot.HtmlSnapshots
import feature.ukpt.Greeting
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlin.test.Test

class GreetingsPageSnapshotTest {

    private val snapshots = HtmlSnapshots()

    @Test
    fun empty() {
        snapshots.assertMatches("GreetingsPage/empty", render(GreetingsPageState(emptyList(), GreetingForm.Empty, errors = null)))
    }

    @Test
    fun withGreetings() {
        val greetings = listOf(Greeting(id = 1, text = "Hello, Ada"), Greeting(id = 2, text = "Hello again, Ada"))
        snapshots.assertMatches("GreetingsPage/withGreetings", render(GreetingsPageState(greetings, GreetingForm.Empty, errors = null)))
    }

    @Test
    fun invalidName() {
        val form = GreetingForm(name = "")
        snapshots.assertMatches("GreetingsPage/invalidName", render(GreetingsPageState(emptyList(), form, GreetingForm.Errors(name = "Enter a name."))))
    }

    private fun render(state: GreetingsPageState): String = "<!DOCTYPE html>" + createHTML().html { greetingsPage(state) }
}
