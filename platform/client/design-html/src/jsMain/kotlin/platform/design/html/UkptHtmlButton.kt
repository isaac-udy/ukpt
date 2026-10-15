package platform.design.html

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text

/** The Compose HTML twin of `UkptButtonVariant`. */
enum class UkptHtmlButtonVariant(internal val className: String) {
    Primary("ukpt-button-primary"),
    Secondary("ukpt-button-secondary"),
    Ghost("ukpt-button-ghost"),
}

/** The Compose HTML twin of `UkptButton`: a native `<button>` styled by `ukpt-design.css`. */
@Composable
fun UkptHtmlButton(
    label: String,
    onClick: () -> Unit,
    variant: UkptHtmlButtonVariant = UkptHtmlButtonVariant.Primary,
    enabled: Boolean = true,
) {
    Button(
        attrs = {
            classes("ukpt-button", variant.className)
            if (!enabled) disabled()
            onClick { onClick() }
        },
    ) {
        Text(label)
    }
}
