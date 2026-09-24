package feature.shop.server.web

import feature.shop.server.data.CartRepository
import kotlinx.html.FlowContent
import kotlinx.html.HEAD
import kotlinx.html.button
import kotlinx.html.link
import kotlinx.html.script
import kotlinx.html.unsafe
import org.koin.core.component.get

internal fun FlowContent.checkoutButton() {
    button {
        attributes["hx-post"] = "/checkout"
        attributes["x-on:click"] = "open = !open"
        onClick = "track()"
        attributes["onclick"] = "track()"
        +"Checkout"
    }
}

internal fun HEAD.checkoutScripts() {
    script(src = "https://unpkg.com/htmx.org@2.0.0") { }
    link(rel = "stylesheet", href = "//cdn.example.com/checkout.css")
    script { unsafe { +"window.checkout = true" } }
    script(src = "/static/shop/checkout.js") { defer = true }
    link(rel = "stylesheet", href = "/static/shop/checkout.css")
}
