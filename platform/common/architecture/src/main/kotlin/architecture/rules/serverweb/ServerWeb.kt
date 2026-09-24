package architecture.rules.serverweb

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.codeBodyText
import architecture.definitions.isFeatureModule
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

@Describe("""
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
""")
object ServerWeb : RuleGroup(
    inPackage = "feature..server.web..",
    constructs = listOf(
        Routes,
        Page,
        Component,
        ViewState,
        Form,
        EventStream,
        ViewHelper,
        WebConstants,
    ),
) {

    @Describe("The `server.web` layer must never import `server.data`")
    val noDataImports by rule {
        rationale(
            """
            `server.domain` sits between the web layer and persistence and imports neither: route
            handlers consume domain interfaces, and Repositories provide them. A handler that reaches
            a table directly states the table it wants instead of the contract it needs.
            `ServerData.noEntryPointImports` is the other half.
            """.trimIndent(),
        )
        note("Tested over imports of `feature.[name].server.data` and of the generated tables in `platform.server.postgres.tables`.")
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.isInServerWeb() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    file.imports
                        .filter { it.name.contains(".server.data.") || it.name.startsWith("platform.server.postgres.tables.") }
                        .map { Violation(file.path, "server.web imports persistence `${it.name}` — state a `server.domain` interface instead") }
                }
        }
    }

    @Describe("The `server.web` layer must not import Koin")
    val noKoinImports by rule {
        rationale(
            """
            A Routes class receives its domain interfaces through its constructor, and the feature's
            dependency module binds it. A lookup inside a handler is a dependency the constructor
            does not state, so `ServerDependenciesTest` cannot verify it.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.isInServerWeb() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("org.koin.") }
                        .map { Violation(file.path, "server.web imports Koin `${it.name}`") }
                }
        }
    }

    @Describe("Markup must set `hx-*`, `sse-*` and Alpine attributes through the `dev.isaacudy.udytils.htmx` builders, never as string attribute names")
    val typedHypermediaAttributes by rule {
        rationale(
            """
            The builders accept only values that htmx and Alpine do not evaluate as JavaScript: no
            `hx-on`, no trigger filters, and Alpine directives that name a member of a registered
            component. An attribute name written as a string bypasses that check, and a typo in it
            renders an attribute htmx ignores.
            """.trimIndent(),
        )
        note("Tested over every Kotlin file that imports `kotlinx.html`, in feature and platform modules: a string literal that starts with `hx-`, `sse-`, `x-`, `@` or `:` followed by a letter.")
        scope { scope, exempt ->
            scope.files
                .filter { it.importsKotlinxHtml() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    hypermediaAttributeLiteral.findAll(file.text)
                        .map { Violation(file.path, "string attribute name `${it.groupValues[1]}` — use the udytils htmx, sse or alpine builder") }
                }
        }
    }

    @Describe("Markup must not carry inline script: no `<script>` without `src`, no `on*` event-handler attributes, and no `unsafe` HTML")
    val noInlineScript by rule {
        rationale(
            """
            The platform's content security policy allows only scripts loaded from this origin, so
            the browser blocks inline script, and a page that relies on it breaks. Script in a
            Kotlin string is also invisible to every JavaScript tool and cannot be cached.
            `unsafe { }` writes unescaped HTML, which is where both inline script and injected
            markup come from.
            """.trimIndent(),
        )
        note("Tested over every Kotlin file that imports `kotlinx.html`: a `script` builder with a body or without a named `src =` argument, an `onClick =`-style property, an `attributes[\"on…\"]` assignment, and any `unsafe` block.")
        scope { scope, exempt ->
            scope.files
                .filter { it.importsKotlinxHtml() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    val code = file.codeBodyText()
                    val scriptsWithoutSrc = scriptCall.findAll(code).filter { "src" !in it.groupValues[1] }.map { "script(${it.groupValues[1]})" }
                    val scriptBodies = scriptContent.findAll(file.text).map { "script { … }" }
                    val handlers = eventHandlerProperty.findAll(code).map { it.value.trim() }
                    val handlerAttributes = eventHandlerAttribute.findAll(file.text).map { it.value }
                    val unsafeBlocks = unsafeBlock.findAll(code).map { "unsafe { … }" }
                    (scriptsWithoutSrc + scriptBodies + handlers + handlerAttributes + unsafeBlocks)
                        .map { Violation(file.path, "inline script or unescaped HTML: `$it`") }
                        .toList()
                }
        }
    }

    @Describe("Markup must load scripts and stylesheets from this origin")
    val selfHostedAssets by rule {
        rationale(
            """
            The platform's content security policy allows scripts and styles from this origin only.
            htmx and Alpine are served from the udytils htmx artifact, and a feature's files from its
            own `static/` directory.
            """.trimIndent(),
        )
        note("Tested over every Kotlin file that imports `kotlinx.html`: a `script(src = …)` or `link(href = …)` whose URL has a scheme or starts with `//`.")
        scope { scope, exempt ->
            scope.files
                .filter { it.importsKotlinxHtml() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    remoteAsset.findAll(file.text).map { Violation(file.path, "loads `${it.groupValues[1]}` from another origin") }.toList()
                }
        }
    }

    @Describe("A `server.web` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root")
    val subsystemVisibility by rule {
        enforcedBy("ProjectRules.subsystemVisibility")
    }

    @Describe("A `server.web` subsystem package imports `server.domain` only through the matching `server.domain` subsystem package, that package's direct children, and their ancestors")
    val subsystemMirrorsDomain by rule {
        note("A file at the layer root — a Routes class — sees the whole of `server.domain`.")
        enforcedBy("ProjectRules.subsystemMirrorsDomain")
    }
}

/** True for a file in `feature.[name].server.web` — the file-level form of the group's gate. */
internal fun KoFileDeclaration.isInServerWeb(): Boolean {
    val pkg = packagee?.name ?: return false
    return pkg.contains(".server.web")
}

internal fun KoFileDeclaration.importsKotlinxHtml(): Boolean = imports.any { it.name.startsWith("kotlinx.html.") }

private val hypermediaAttributeLiteral = Regex(""""((?:hx|sse|x)-[a-z][^"]*|[@:][a-z][^"]*)"""")
private val scriptCall = Regex("""\bscript\s*\(([^()]*)\)""")
// A script builder whose body adds content: `+"…"`, `+value` or `text(…)`. Attribute assignments
// such as `defer = true` are not content.
private val scriptContent = Regex("""\bscript\s*(?:\([^()]*\)\s*)?\{[^}]*?(?:\+\s*["\w]|\btext\s*\()""")
private val eventHandlerProperty = Regex("""(?<!val )(?<!var )\bon[A-Z][A-Za-z]*\s*=(?!=)""")
private val eventHandlerAttribute = Regex("""attributes\s*\[\s*"on[a-z]+"\s*]""")
private val unsafeBlock = Regex("""\bunsafe\s*\{""")
private val remoteAsset = Regex("""\b(?:script|link)\s*\([^()]*\b(?:src|href)\s*=\s*"((?:[a-z][a-z0-9+.-]*:)?//[^"]*)"""")
