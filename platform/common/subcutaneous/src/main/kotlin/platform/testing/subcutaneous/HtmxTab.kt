package platform.testing.subcutaneous

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import platform.server.web.APP_ERROR_ID
import platform.testing.robots.Locator
import platform.testing.robots.PageDriver
import java.net.URI

/**
 * A browser tab over the server's HTML. With [scripts], it sends the requests htmx sends for
 * `hx-get`/`hx-post`/…, swaps the responses as `hx-target` and `hx-swap` say, applies
 * out-of-band swaps, follows `sse-connect`/`sse-swap` streams, and shows a failed request's text in
 * the platform's error region as the platform's `app.js` does. Without [scripts], forms and links
 * work as plain HTML. It runs no JavaScript: Alpine and the project's own scripts need an End To
 * End Test.
 *
 * An attribute that changes what htmx sends or swaps and that this tab does not implement fails the
 * step that reaches it, naming the attribute.
 */
internal class HtmxTab(engine: HttpClientEngine, private val scripts: Boolean) : PageDriver, AutoCloseable {

    private val http = HttpClient(engine) {
        followRedirects = false
        expectSuccess = false
        install(HttpCookies)
        install(SSE)
    }
    private val streams = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var location = Url(ORIGIN)
    private var document: Document = Document.createShell(ORIGIN)
    private var generation = 0
    private var streamFailure: String? = null

    override val path: String get() = synchronized(lock) { location.encodedPath.ifEmpty { "/" } }

    override fun open(path: String) = perform(Navigation(HttpMethod.Get, resolve(path), Parameters.Empty))

    override fun texts(locator: Locator): List<String> = synchronized(lock) { find(locator).map { it.text() } }

    override fun values(locator: Locator): List<String> = synchronized(lock) { find(locator).map(::valueOf) }

    override fun errorText(): String? = synchronized(lock) {
        val region: Element? = document.getElementById(APP_ERROR_ID)
        region?.takeUnless { it.hasAttr("hidden") }?.text()?.ifBlank { null } ?: streamFailure
    }

    override fun click(locator: Locator) {
        val action = synchronized(lock) { clickAction(single(locator)) }
        perform(action)
    }

    override fun fill(locator: Locator, text: String) {
        val action = synchronized(lock) {
            val element = single(locator)
            when (element.tagName()) {
                "textarea" -> element.text(text)
                "input" -> element.attr("value", text)
                else -> throw AssertionError("Cannot type into <${element.tagName()}> at $locator")
            }
            changeAction(element)
        }
        action?.let(::perform)
    }

    override fun selectOption(locator: Locator, value: String) {
        val action = synchronized(lock) {
            val select = single(locator)
            if (select.tagName() != "select") throw AssertionError("$locator is <${select.tagName()}>, not <select>")
            val options = select.select("option")
            val chosen = options.firstOrNull { it.attr("value") == value } ?: options.firstOrNull { it.text() == value }
                ?: throw AssertionError("$locator has no option \"$value\"; it has ${options.map { it.text() }}")
            options.forEach { it.removeAttr("selected") }
            chosen.attr("selected", true)
            changeAction(select)
        }
        action?.let(::perform)
    }

    override fun setChecked(locator: Locator, checked: Boolean) {
        val action = synchronized(lock) {
            val input = single(locator)
            val type = input.attr("type").lowercase()
            if (input.tagName() != "input" || type !in setOf("checkbox", "radio")) {
                throw AssertionError("$locator is not a checkbox or radio button")
            }
            if (type == "radio" && checked) {
                val group = input.closest("form") ?: document
                group.select("input[type=radio][name=${input.attr("name")}]").forEach { it.removeAttr("checked") }
            }
            input.attr("checked", checked)
            changeAction(input)
        }
        action?.let(::perform)
    }

    override fun close() {
        streams.cancel()
        http.close()
    }

    private sealed interface Action

    private class Navigation(val method: HttpMethod, val url: Url, val parameters: Parameters) : Action

    private class HtmxRequest(
        val method: HttpMethod,
        val url: Url,
        val parameters: Parameters,
        val source: Element,
        val target: Element,
        val swap: String,
        val pushUrl: String?,
    ) : Action

    private fun clickAction(element: Element): Action {
        if (scripts && element.tagName() != "form") htmxRequest(element)?.let { return it }
        val form = if (isSubmitButton(element)) element.closest("form") else null
        if (form != null) return submitAction(form, submitter = element)
        if (element.tagName() == "a" && element.hasAttr("href")) return Navigation(HttpMethod.Get, resolve(element.attr("href")), Parameters.Empty)
        throw AssertionError(
            "Clicking <${element.tagName()}> does nothing here: it has no hx-get/hx-post, is not a submit button in a form, and is not a link",
        )
    }

    private fun submitAction(form: Element, submitter: Element): Action {
        val parameters = formParameters(form, submitter)
        if (scripts) htmxRequest(form, parameters)?.let { return it }
        val method = HttpMethod.parse(submitter.attr("formmethod").ifBlank { form.attr("method") }.ifBlank { "get" }.uppercase())
        val action = submitter.attr("formaction").ifBlank { form.attr("action") }
        return Navigation(method, if (action.isBlank()) location else resolve(action), parameters)
    }

    /** The request htmx sends when [element] changes, if it carries an `hx-` verb. */
    private fun changeAction(element: Element): Action? = if (scripts) htmxRequest(element) else null

    private fun htmxRequest(source: Element, parameters: Parameters? = null): HtmxRequest? {
        val (method, url) = HX_VERBS.firstNotNullOfOrNull { (attribute, method) ->
            if (source.hasAttr(attribute)) method to source.attr(attribute) else null
        } ?: return null
        UNSUPPORTED.firstOrNull { inherited(source, it) != null }?.let {
            throw AssertionError("$it on or above <${source.tagName()}> is not implemented by the Subcutaneous tab; test this page in an End To End Test")
        }
        val values = ParametersBuilder().apply {
            appendAll(parameters ?: requestParameters(source, method))
            inherited(source, "hx-vals")?.let { (vals, _) -> appendVals(vals) }
        }.build()
        return HtmxRequest(
            method = method,
            url = if (url.isBlank()) location else resolve(url),
            parameters = values,
            source = source,
            target = target(source),
            swap = inherited(source, "hx-swap")?.first?.substringBefore(' ') ?: "innerHTML",
            pushUrl = inherited(source, "hx-push-url")?.first,
        )
    }

    /** What htmx includes for a request from a non-form element: its own value, and its form's values unless it is a GET. */
    private fun requestParameters(source: Element, method: HttpMethod): Parameters = ParametersBuilder().apply {
        val form = source.closest("form")
        if (form != null && method != HttpMethod.Get) appendAll(formParameters(form, submitter = source))
        else if (source.hasAttr("name") && source.tagName() in setOf("input", "select", "textarea", "button")) append(source.attr("name"), valueOf(source))
    }.build()

    private fun ParametersBuilder.appendVals(json: String) {
        val values = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
            ?: throw AssertionError("hx-vals \"$json\" is not a JSON object")
        values.forEach { (name, value) -> append(name, (value as? JsonPrimitive)?.content ?: value.toString()) }
    }

    private fun target(source: Element): Element {
        val (spec, owner) = inherited(source, "hx-target") ?: return source
        val found = when {
            spec == "this" -> owner
            spec.startsWith("closest ") -> source.closest(spec.removePrefix("closest "))
            spec.startsWith("find ") -> source.selectFirst(spec.removePrefix("find "))
            spec.startsWith("next ") -> following(source, spec.removePrefix("next ")).firstOrNull()
            spec.startsWith("previous ") -> preceding(source, spec.removePrefix("previous ")).lastOrNull()
            else -> document.selectFirst(spec)
        }
        return found ?: throw AssertionError("hx-target \"$spec\" on <${owner.tagName()}> matches nothing")
    }

    private fun following(source: Element, css: String): List<Element> {
        val all = document.getAllElements()
        val index = all.indexOf(source)
        return document.select(css).filter { all.indexOf(it) > index }
    }

    private fun preceding(source: Element, css: String): List<Element> {
        val all = document.getAllElements()
        val index = all.indexOf(source)
        return document.select(css).filter { all.indexOf(it) < index }
    }

    /** The attribute's value on [element] or its nearest ancestor that sets it, and that element. */
    private fun inherited(element: Element, attribute: String): Pair<String, Element>? =
        generateSequence(element) { it.parent() }.firstOrNull { it.hasAttr(attribute) }?.let { it.attr(attribute) to it }

    private fun perform(action: Action) = runBlocking {
        when (action) {
            is Navigation -> navigate(action.method, action.url, action.parameters)
            is HtmxRequest -> send(action)
        }
    }

    private suspend fun navigate(method: HttpMethod, url: Url, parameters: Parameters) {
        var requestMethod = method
        var requestUrl = url
        var requestParameters = parameters
        repeat(MAX_REDIRECTS) {
            val response = request(requestMethod, requestUrl, requestParameters) {
                header(HttpHeaders.Accept, "text/html")
                header("Sec-Fetch-Mode", "navigate")
            }
            val redirect = response.headers[HttpHeaders.Location]
            if (response.status.value !in 300..399 || redirect == null) {
                load(Url(requestUrl.toString().substringBefore('?') + queryOf(requestMethod, requestUrl, requestParameters)), response.bodyAsText())
                return
            }
            requestUrl = resolve(redirect)
            if (response.status.value != 307 && response.status.value != 308) {
                requestMethod = HttpMethod.Get
                requestParameters = Parameters.Empty
            }
        }
        throw AssertionError("$url redirected more than $MAX_REDIRECTS times")
    }

    private suspend fun send(request: HtmxRequest) {
        synchronized(lock) { document.getElementById(APP_ERROR_ID)?.attr("hidden", true) }
        val response = request(request.method, request.url, request.parameters) {
            header("HX-Request", "true")
            header("HX-Current-URL", location.toString())
            header("Sec-Fetch-Mode", "cors")
            request.target.id().takeIf { it.isNotEmpty() }?.let { header("HX-Target", it) }
            request.source.id().takeIf { it.isNotEmpty() }?.let { header("HX-Trigger", it) }
            request.source.attr("name").takeIf { it.isNotEmpty() }?.let { header("HX-Trigger-Name", it) }
        }
        val body = response.bodyAsText()
        response.headers["HX-Redirect"]?.let { return navigate(HttpMethod.Get, resolve(it), Parameters.Empty) }
        response.headers["HX-Location"]?.let { return navigate(HttpMethod.Get, resolve(locationPath(it)), Parameters.Empty) }
        if (response.headers["HX-Refresh"] == "true") return navigate(HttpMethod.Get, location, Parameters.Empty)

        val status = response.status
        if (status.value == 204) return
        if (!status.isSuccess() && status.value != 422) return showError(body)
        synchronized(lock) {
            val target = response.headers["HX-Retarget"]?.let { document.selectFirst(it) } ?: request.target
            swap(target, response.headers["HX-Reswap"]?.substringBefore(' ') ?: request.swap, body)
            pushUrl(request, response)
        }
    }

    private fun locationPath(header: String): String =
        if (header.trimStart().startsWith("{")) (Json.parseToJsonElement(header) as JsonObject)["path"]?.let { (it as JsonPrimitive).content } ?: "/"
        else header

    private fun pushUrl(request: HtmxRequest, response: HttpResponse) {
        val pushed = response.headers["HX-Push-Url"] ?: response.headers["HX-Replace-Url"] ?: request.pushUrl ?: return
        when (pushed) {
            "false" -> Unit
            "true" -> location = Url(request.url.toString().substringBefore('?') + queryOf(request.method, request.url, request.parameters))
            else -> location = resolve(pushed)
        }
    }

    private fun showError(body: String) = synchronized(lock) {
        val region = document.getElementById(APP_ERROR_ID) ?: return@synchronized
        region.text(if (body.isNotEmpty() && body.length < 200) body else "The request failed.")
        region.removeAttr("hidden")
    }

    private suspend fun request(
        method: HttpMethod,
        url: Url,
        parameters: Parameters,
        headers: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): HttpResponse = http.request(url.toString().substringBefore('?') + queryOf(method, url, parameters)) {
        this.method = method
        headers()
        if (method != HttpMethod.Get) setBody(FormDataContent(parameters))
    }

    /** A GET carries its parameters as the query string, replacing the URL's own when there are any. */
    private fun queryOf(method: HttpMethod, url: Url, parameters: Parameters): String {
        val query = if (method == HttpMethod.Get && !parameters.isEmpty()) URLBuilder().apply { this.parameters.appendAll(parameters) }.build().encodedQuery
        else url.encodedQuery
        return if (query.isEmpty()) "" else "?$query"
    }

    private fun load(url: Url, html: String) = synchronized(lock) {
        location = url
        document = Jsoup.parse(html, url.toString())
        generation++
        if (scripts) connectStreams(generation)
    }

    private fun connectStreams(loaded: Int) {
        document.select("[sse-connect]").forEach { source ->
            val url = resolve(source.attr("sse-connect")).toString()
            streams.launch {
                try {
                    http.sse(url) {
                        incoming.collect { event -> onEvent(loaded, source, event) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    synchronized(lock) { streamFailure = "the event stream $url failed: $failure" }
                }
            }
        }
    }

    private fun onEvent(loaded: Int, source: Element, event: ServerSentEvent) = synchronized(lock) {
        if (loaded != generation) return@synchronized
        val name = event.event ?: "message"
        source.select("[sse-swap]")
            .filter { element -> element.attr("sse-swap").split(',').map(String::trim).contains(name) }
            .forEach { element ->
                swap(target(element), inherited(element, "hx-swap")?.first?.substringBefore(' ') ?: "innerHTML", event.data.orEmpty())
            }
    }

    /** Swaps [html] into [target] as `hx-swap` [style] does, after applying its out-of-band swaps. */
    private fun swap(target: Element, style: String, html: String) {
        val fragment = Jsoup.parseBodyFragment("<template>$html</template>").body().child(0)
        applyOutOfBand(fragment)
        val nodes = fragment.childNodes().toList()
        when (style) {
            "innerHTML" -> {
                target.empty()
                nodes.forEach(target::appendChild)
            }
            "outerHTML" -> {
                nodes.forEach(target::before)
                target.remove()
            }
            "beforebegin" -> nodes.forEach(target::before)
            "afterbegin" -> target.insertChildren(0, nodes)
            "beforeend" -> nodes.forEach(target::appendChild)
            "afterend" -> nodes.asReversed().forEach(target::after)
            "delete" -> target.remove()
            "textContent" -> target.text(html)
            "none" -> Unit
            else -> throw AssertionError("hx-swap \"$style\" is not an htmx swap style")
        }
    }

    private fun applyOutOfBand(fragment: Element) {
        fragment.children().toList().forEach { child ->
            when {
                child.hasAttr("hx-swap-oob") -> {
                    swapOutOfBand(child)
                    child.remove()
                }
                child.tagName() == "template" -> {
                    child.children().filter { it.hasAttr("hx-swap-oob") }.forEach { swapOutOfBand(it); it.remove() }
                    if (child.children().isEmpty()) child.remove()
                }
            }
        }
    }

    private fun swapOutOfBand(element: Element) {
        val value = element.attr("hx-swap-oob")
        element.removeAttr("hx-swap-oob")
        val (style, selector) = when {
            value == "true" -> "outerHTML" to "#${element.id()}"
            ':' in value -> value.substringBefore(':') to value.substringAfter(':')
            else -> value to "#${element.id()}"
        }
        document.select(selector).forEach { target ->
            if (style == "outerHTML") {
                target.before(element.clone())
                target.remove()
            } else {
                swapNodes(target, style, element.clone().childNodes().toList())
            }
        }
    }

    private fun swapNodes(target: Element, style: String, nodes: List<Node>) = when (style) {
        "innerHTML" -> {
            target.empty()
            nodes.forEach(target::appendChild)
        }
        "beforebegin" -> nodes.forEach(target::before)
        "afterbegin" -> target.insertChildren(0, nodes)
        "beforeend" -> nodes.forEach(target::appendChild)
        "afterend" -> nodes.asReversed().forEach(target::after)
        "delete" -> target.remove()
        "none" -> Unit
        else -> throw AssertionError("hx-swap-oob \"$style\" is not an htmx swap style")
    }

    private fun find(locator: Locator): List<Element> {
        var current: List<Element> = listOf(document)
        locator.steps.forEach { step ->
            current = current
                .flatMap { root -> root.select(step.css).filter { it !== root } }
                .distinct()
                .filter { step.hasText == null || it.text().contains(step.hasText!!, ignoreCase = true) }
        }
        return current.filter { element -> element.parents().none { it.tagName() == "template" } }
    }

    private fun single(locator: Locator): Element {
        val found = find(locator)
        return found.singleOrNull() ?: throw AssertionError("Expected exactly one element to match $locator; found ${found.size}")
    }

    private fun resolve(href: String): Url = Url(URI(location.toString()).resolve(href).toString())

    private companion object {
        const val ORIGIN = "http://localhost/"
        const val MAX_REDIRECTS = 10

        val HX_VERBS = listOf(
            "hx-get" to HttpMethod.Get,
            "hx-post" to HttpMethod.Post,
            "hx-put" to HttpMethod.Put,
            "hx-patch" to HttpMethod.Patch,
            "hx-delete" to HttpMethod.Delete,
        )

        val UNSUPPORTED = listOf("hx-include", "hx-select", "hx-select-oob", "hx-params", "hx-encoding", "hx-prompt")

        fun isSubmitButton(element: Element): Boolean = when (element.tagName()) {
            "button" -> element.attr("type").ifBlank { "submit" }.lowercase() == "submit"
            "input" -> element.attr("type").lowercase() in setOf("submit", "image")
            else -> false
        }

        fun valueOf(element: Element): String = when (element.tagName()) {
            "textarea" -> element.wholeText()
            "select" -> {
                val option: Element? = element.selectFirst("option[selected]") ?: element.selectFirst("option")
                when {
                    option == null -> ""
                    option.hasAttr("value") -> option.attr("value")
                    else -> option.text()
                }
            }
            else -> element.attr("value")
        }

        /** The form's successful controls, as a browser submits them, with [submitter]'s name and value. */
        fun formParameters(form: Element, submitter: Element?): Parameters = ParametersBuilder().apply {
            form.select("input, select, textarea, button")
                .filter { it.hasAttr("name") && !it.hasAttr("disabled") && it.parents().none { p -> p.tagName() == "fieldset" && p.hasAttr("disabled") } }
                .forEach { control ->
                    val name = control.attr("name")
                    when (control.tagName()) {
                        "button" -> if (control === submitter) append(name, control.attr("value"))
                        "input" -> when (control.attr("type").lowercase()) {
                            "submit", "image" -> if (control === submitter) append(name, control.attr("value"))
                            "reset", "button", "file" -> Unit
                            "checkbox", "radio" -> if (control.hasAttr("checked")) append(name, control.attr("value").ifEmpty { "on" })
                            else -> append(name, control.attr("value"))
                        }
                        else -> append(name, valueOf(control))
                    }
                }
        }.build()
    }
}
