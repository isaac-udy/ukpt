package feature.shop.server.web

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.nav
import kotlinx.html.title
import platform.server.web.DocumentState
import platform.server.web.ukptDocument

internal data class ShopShellLayoutState(val title: String)

internal data class OrdersPageState(val shell: ShopShellLayoutState)

internal fun HTML.shopShellLayout(state: ShopShellLayoutState, content: FlowContent.() -> Unit) {
    ukptDocument(DocumentState(state.title)) {
        nav { +"Shop" }
        content()
    }
}

internal fun HTML.ordersPage(state: OrdersPageState) {
    shopShellLayout(state.shell) {
        h1 { +"Orders" }
    }
}

internal fun HTML.bareLayout(title: String, content: FlowContent.() -> Unit) {
    head { title(title) }
    body { content() }
}
