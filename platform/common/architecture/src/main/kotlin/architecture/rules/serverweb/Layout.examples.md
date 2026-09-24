A shell with navigation draws its own regions, so it renders through the platform's document
rather than `ukptLayout`. Its View State carries everything the shell shows, and each Page passes
its own:

```kotlin
data class ShopShellLayoutState(
    val title: String,
    val signedInAs: String,
    val section: ShopSection,
)

fun HTML.shopShellLayout(state: ShopShellLayoutState, content: FlowContent.() -> Unit) {
    ukptDocument(DocumentState(state.title, stylesheets = listOf(ShopPaths.SHELL_STYLESHEET))) {
        shopNavigation(state.section, state.signedInAs)
        main("page") { content() }
    }
}

internal fun HTML.ordersPage(state: OrdersPageState) {
    shopShellLayout(state.shell) {
        h1 { +"Orders" }
        orderList(state.orders)
    }
}
```
