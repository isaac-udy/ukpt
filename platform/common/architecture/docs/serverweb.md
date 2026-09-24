> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/serverweb/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Server Web](../src/main/kotlin/architecture/rules/serverweb/ServerWeb.kt)

`feature.[name].server.web` answers HTTP requests. It renders pages and fragments with
kotlinx.html, streams server-sent events, and registers its routes through a
[Routes](#routes) class that the feature's dependency module binds as a `WebRoutes`.

The layer consumes [`server.domain` interfaces](serverdomain.md#domain-interface) and never
imports [`server.data`](serverdata.md). A route handler reads what a page needs from the domain,
builds a [View State](#view-state), and renders it; a [Component](#component) renders only the
values it is given.

A request made by htmx carries the `HX-Request` header. A handler answers it with a fragment and
answers any other request with a full page or a redirect, so every form also works without
JavaScript. A form that fails validation is re-rendered with its errors under
`422 Unprocessable Content`; the template's htmx configuration swaps that status.

The markup is typed. `hx-*`, `sse-*` and Alpine attributes are written with the
`dev.isaacudy.udytils.htmx` builders, which have no form for a value htmx or Alpine would
evaluate as JavaScript. Every script is a file under a module's `static/` resource directory,
loaded from this origin; the platform's content security policy (`script-src 'self'`) blocks
any other script. A feature keeps its scripts and styles in `static/[name]/`.

##### Constructs

* [Routes](#routes)
* [Page](#page)
* [Component](#component)
* [View State](#view-state)
* [Form](#form)
* [Event Stream](#event-stream)
* [View Helper](#view-helper)
* [Web Constants](#web-constants)

##### Rules

* The `server.web` layer must never import `server.data`
    * **Why:** `server.domain` sits between the web layer and persistence and imports neither: route handlers consume domain interfaces, and Repositories provide them. A handler that reaches a table directly states the table it wants instead of the contract it needs. `ServerData.noEntryPointImports` is the other half.
    * **Note:** Tested over imports of `feature.[name].server.data` and of the generated tables in `platform.server.postgres.tables`.
* The `server.web` layer must not import Koin
    * **Why:** A Routes class receives its domain interfaces through its constructor, and the feature's dependency module binds it. A lookup inside a handler is a dependency the constructor does not state, so `ServerDependenciesTest` cannot verify it.
* Markup must set `hx-*`, `sse-*` and Alpine attributes through the `dev.isaacudy.udytils.htmx` builders, never as string attribute names
    * **Why:** The builders accept only values that htmx and Alpine do not evaluate as JavaScript: no `hx-on`, no trigger filters, and Alpine directives that name a member of a registered component. An attribute name written as a string bypasses that check, and a typo in it renders an attribute htmx ignores.
    * **Note:** Tested over every Kotlin file that imports `kotlinx.html`, in feature and platform modules: a string literal that starts with `hx-`, `sse-`, `x-`, `@` or `:` followed by a letter.
* Markup must not carry inline script: no `<script>` without `src`, no `on*` event-handler attributes, and no `unsafe` HTML
    * **Why:** The platform's content security policy allows only scripts loaded from this origin, so the browser blocks inline script, and a page that relies on it breaks. Script in a Kotlin string is also invisible to every JavaScript tool and cannot be cached. `unsafe { }` writes unescaped HTML, which is where both inline script and injected markup come from.
    * **Note:** Tested over every Kotlin file that imports `kotlinx.html`: a `script` builder with a body or without a named `src =` argument, an `onClick =`-style property, an `attributes["on…"]` assignment, and any `unsafe` block.
* Markup must load scripts and stylesheets from this origin
    * **Why:** The platform's content security policy allows scripts and styles from this origin only. htmx and Alpine are served from the udytils htmx artifact, and a feature's files from its own `static/` directory.
    * **Note:** Tested over every Kotlin file that imports `kotlinx.html`: a `script(src = …)` or `link(href = …)` whose URL has a scheme or starts with `//`.
* A `server.web` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root
    * **Enforced by:** `ProjectRules.subsystemVisibility`
* A `server.web` subsystem package imports `server.domain` only through the matching `server.domain` subsystem package, that package's direct children, and their ancestors
    * **Note:** A file at the layer root — a Routes class — sees the whole of `server.domain`.
    * **Enforced by:** `ProjectRules.subsystemMirrorsDomain`

---

## [Routes](../src/main/kotlin/architecture/rules/serverweb/Routes.kt)

The class that registers a feature's HTTP routes: an `internal class [Name]Routes` implementing
`platform.server.web.WebRoutes`, whose `Route.install()` declares the `get`, `post` and `sse`
handlers. Its constructor takes the `server.domain` interfaces the handlers call, and the
feature's dependency module binds it with `singleOf(::[Name]Routes) bind WebRoutes::class`.

A handler reads what it needs from the domain, builds a [View State](#view-state), and responds
with a [Page](#page), a fragment rendered by a [Component](#component), a redirect, or an
[Event Stream](#event-stream).

##### Requirements

* A Routes resides in `feature..server.web..`
* A Routes is named `[Name]Routes`
* A Routes is declared in a `:server` module

##### Rules

* A Routes class must be `internal`
* A Routes class must implement `WebRoutes`
    * **Why:** The server installs the routes of every `WebRoutes` binding; a Routes class that does not implement it is never installed.
* A Routes class must not inject persistence: neither a Repository nor a StorageClass
    * **Why:** A Routes class answers a request by composing the feature's `server.domain` interfaces. A Repository is the wiring that provides those interfaces; injecting it, or the StorageClass under it, states the table the handler wants instead of the contract it needs.
    * **Note:** Tested on the primary constructor: a parameter whose type is named `[Name]Repository`, `[Name]Storage` or `[Name]Store`, or whose type resolves into `server.data`.

##### Guidance

* A Routes class may inject its feature's `server.domain` interfaces, and other features' `server.domain` interfaces published to `:api`

##### Examples

A Routes class answering a page load, a form post from htmx or a plain form, and an event stream:

```kotlin
internal class OrdersRoutes(
    private val flowOfOrders: FlowOfOrders,
    private val placeOrder: PlaceOrder,
) : WebRoutes {

    override fun Route.install() {
        get(OrdersPaths.ORDERS) {
            val orders = flowOfOrders().first()
            call.respondHtml { ordersPage(OrdersPageState(orders, OrderForm.Empty, errors = null)) }
        }

        post(OrdersPaths.ORDERS) {
            val form = OrderForm.from(call.receiveParameters())
            call.varyOnHtmx()
            when (val result = form.validate()) {
                is FormResult.Invalid -> if (call.isHtmx) {
                    call.respondHtmlFragment(HttpStatusCode.UnprocessableEntity) { orderForm(form, result.errors) }
                } else {
                    val orders = flowOfOrders().first()
                    call.respondHtml(HttpStatusCode.UnprocessableEntity) { ordersPage(OrdersPageState(orders, form, result.errors)) }
                }
                is FormResult.Valid -> {
                    placeOrder(result.value)
                    if (call.isHtmx) call.respondHtmlFragment { orderForm(OrderForm.Empty, errors = null) } else call.respondSeeOther(OrdersPaths.ORDERS)
                }
            }
        }

        sse(OrdersPaths.ORDER_EVENTS) {
            orderEvents(flowOfOrders).collect { send(it) }
        }
    }
}
```

The dependency module binds it:

```kotlin
singleOf(::OrdersRoutes) bind WebRoutes::class
```

---

## [Page](../src/main/kotlin/architecture/rules/serverweb/Page.kt)

A whole HTML document: a top-level `fun HTML.[name]Page(state: [Name]State)` that renders its
[View State](#view-state) inside the platform layout. A handler responds with it through
`call.respondHtml { [name]Page(state) }`.

A Page composes [Components](#component). A handler that answers an htmx request renders the
Component it replaces, not the Page.

##### Requirements

* A Page resides in `feature..server.web..`
* A Page has an `HTML` receiver and is named `[name]Page`

##### Rules

* A Page must take exactly one parameter, its View State
    * **Why:** Everything a Page shows is in its View State, so a snapshot test of the Page covers every value the handler can put on it.
* A Page must render through the platform layout
    * **Why:** The layout carries the htmx configuration, the scripts in the order Alpine needs, and the error region the platform script fills.
    * **Note:** Tested on the function body: a call to a function whose name ends in `Layout`.

##### Examples

A Page renders its View State through the layout and composes Components. The list connects to
its event stream through a sink element that swaps nothing itself:

```kotlin
internal fun HTML.ordersPage(state: OrdersPageState) {
    ukptLayout(title = "Orders", scripts = listOf(OrdersPaths.QUANTITY_SCRIPT)) {
        h1 { +"Orders" }
        orderForm(state.form, state.errors)
        orderList(state.orders)
    }
}

internal fun FlowContent.orderList(orders: List<Order>) {
    ul("list") {
        elementId = OrdersIds.orders
        orders.forEach { orderItem(it) }
    }
    div {
        sse {
            connect(OrdersPaths.ORDER_EVENTS)
            swap(OrdersIds.ORDERS_EVENT)
        }
        hx { swap(SwapStyle.None) }
    }
}

internal fun UL.orderItem(order: Order) {
    li {
        elementId = OrdersIds.order(order.id)
        +order.summary
    }
}
```

---

## [Component](../src/main/kotlin/architecture/rules/serverweb/Component.kt)

A piece of markup: a top-level function whose receiver is a kotlinx.html tag or content type
other than `HTML` — `FlowContent`, `UL`, `TBODY` — such as `fun FlowContent.greetingForm(…)`.
Pages compose Components, and a handler that answers an htmx request renders the Component the
request replaces.

A Component that renders one item of a live list takes the list's parent tag as its receiver
(`UL.greetingItem`), so the page and the list's out-of-band updates render the item with the
same function.

##### Requirements

* A Component resides in `feature..server.web..`
* A Component has a kotlinx.html tag or content receiver other than `HTML`

##### Rules

* A Component must render only the values it is given: no parameter may be a domain interface or a Routes class
    * **Why:** kotlinx.html builders do not suspend, so a Component cannot call a domain interface; a Component that takes one passes it on to something that runs outside the render. The handler reads from the domain and passes the values down.

---

## [View State](../src/main/kotlin/architecture/rules/serverweb/ViewState.kt)

The values a [Page](#page) renders: a `data class`, sealed type or enum named `[Name]State`,
built by a handler from domain models and form input.

##### Requirements

* A View State resides in `feature..server.web..`
* A View State is a class or interface
* A View State is named `[Name]State`
* A View State satisfies one of: {is a `data class`, is `sealed`, is an `enum class`}

##### Rules

* A View State must be immutable
* A View State must not hold a domain interface
    * **Why:** A Page renders a View State without suspending, so a domain interface on it is a call that has not been made.

---

## [Form](../src/main/kotlin/architecture/rules/serverweb/Form.kt)

The fields of one HTML form as the browser submitted them: a `data class [Name]Form` whose
properties are raw input, read with `[Name]Form.from(call.receiveParameters())`. Its
`validate()` returns a `platform.server.web.FormResult`: the typed value the handler passes to
the domain, or the form's `Errors`.

A handler re-renders an invalid form from the same `[Name]Form` and its `Errors`, so the page
shows exactly what was submitted.

##### Requirements

* A Form resides in `feature..server.web..`
* A Form is a `data class`
* A Form is named `[Name]Form`

##### Rules

* A Form's constructor properties must be raw input: `String`, `String?`, `Boolean`, or `List<String>`
    * **Why:** A property of any other type is a conversion that can fail before `validate()` runs, where the failure has no field to report against.
* A Form must declare `validate()`

##### Examples

A Form holds what was submitted; `validate()` produces the value the domain takes, or the errors
the re-rendered form shows:

```kotlin
internal data class OrderForm(
    val item: String,
    val quantity: String,
) {
    fun validate(): FormResult<NewOrder, Errors> {
        val quantity = quantity.trim().toIntOrNull()
        val errors = Errors(
            item = if (item.isBlank()) "Choose an item." else null,
            quantity = if (quantity == null || quantity < 1) "Enter a quantity of at least 1." else null,
        )
        return if (errors.item == null && errors.quantity == null) {
            FormResult.Valid(NewOrder(item = item.trim(), quantity = checkNotNull(quantity)))
        } else {
            FormResult.Invalid(errors)
        }
    }

    data class Errors(val item: String?, val quantity: String?)

    companion object {
        val Empty = OrderForm(item = "", quantity = "1")

        fun from(parameters: Parameters): OrderForm =
            OrderForm(item = parameters["item"].orEmpty(), quantity = parameters["quantity"].orEmpty())
    }
}
```

---

## [Event Stream](../src/main/kotlin/architecture/rules/serverweb/EventStream.kt)

The server-sent events a page subscribes to: a top-level function named `[name]Events` that
returns `Flow<ServerSentEvent>`, built from a domain `Flow`. A handler sends it from an `sse(…)`
route, and the page connects with the udytils `sse { connect(…) }` attributes.

For a list, `dev.isaacudy.udytils.htmx.OobList` turns each version of the list into
out-of-band swaps rendered by the same [Component](#component) the page uses for each item.

##### Requirements

* An Event Stream resides in `feature..server.web..`
* An Event Stream is named `[name]Events` and returns `Flow<ServerSentEvent>`

##### Guidance

* An Event Stream should send the whole of what it keeps current as its first event, then only the changes
    * **Note:** A page is rendered before its stream connects, and a stream reconnects after a dropped connection; the first event brings the page up to date in both cases. `OobList` does this through `oobUpdates`.

---

## [View Helper](../src/main/kotlin/architecture/rules/serverweb/ViewHelper.kt)

A top-level function that computes a value for rendering and has no kotlinx.html receiver: a
formatter, or a mapper such as `GreetingSummary.toPageState()`.

##### Requirements

* A View Helper resides in `feature..server.web..`
* A View Helper returns a value, has no kotlinx.html receiver, and is not an Event Stream

##### Rules

* A View Helper must not suspend
    * **Why:** A View Helper runs while a page renders, and rendering does not suspend; reading from the domain is the handler's job.

---

## [Web Constants](../src/main/kotlin/architecture/rules/serverweb/WebConstants.kt)

The names the layer's markup and handlers share: an `object [Name]Paths` holding the URL paths
of its routes and static files, and an `object [Name]Ids` holding the `ElementId`s that
`hx-target`, `sse-swap` and out-of-band swaps address, and its event names.

A path or id written once here is the same string in the route that serves it and the
attribute that requests it.

##### Requirements

* A Web Constants resides in `feature..server.web..`
* A Web Constants is named `[Name]Paths` or `[Name]Ids`

##### Rules

* A Web Constants object must hold no `var`
