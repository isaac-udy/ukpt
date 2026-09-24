package architecture.rules.serverweb

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.isServerModule
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoParameterDeclaration
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider

@Describe("""
    The class that registers a feature's HTTP routes: an `internal class [Name]Routes` implementing
    `platform.server.web.WebRoutes`, whose `Route.install()` declares the `get`, `post` and `sse`
    handlers. Its constructor takes the `server.domain` interfaces the handlers call, and the
    feature's dependency module binds it with `singleOf(::[Name]Routes) bind WebRoutes::class`.

    A handler reads what it needs from the domain, builds a [View State](#view-state), and responds
    with a [Page](#page), a fragment rendered by a [Component](#component), a redirect, or an
    [Event Stream](#event-stream).
""")
object Routes : Construct<ServerWeb>(
    requirements = listOf(
        isClassWhere("is named `[Name]Routes`") { it.name.endsWith("Routes") },
        predicate("is declared in a `:server` module") { it.isServerModule() },
    ),
) {
    @Describe("A Routes class must be `internal`")
    val internalVisibility by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.hasInternalModifier) emptyList() else listOf(Violation(cls, "Routes class must be `internal`"))
        }
    }

    @Describe("A Routes class must implement `WebRoutes`")
    val implementsWebRoutes by rule {
        rationale("The server installs the routes of every `WebRoutes` binding; a Routes class that does not implement it is never installed.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.parents().any { it.name == "WebRoutes" }) emptyList() else listOf(Violation(cls, "`${cls.name}` does not implement `WebRoutes`"))
        }
    }

    @Describe("A Routes class must not inject persistence: neither a Repository nor a StorageClass")
    val noPersistenceInjection by rule {
        rationale(
            """
            A Routes class answers a request by composing the feature's `server.domain` interfaces.
            A Repository is the wiring that provides those interfaces; injecting it, or the
            StorageClass under it, states the table the handler wants instead of the contract it
            needs.
            """.trimIndent(),
        )
        note("Tested on the primary constructor: a parameter whose type is named `[Name]Repository`, `[Name]Storage` or `[Name]Store`, or whose type resolves into `server.data`.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.primaryConstructor?.parameters.orEmpty()
                .filter { it.namesPersistence() }
                .map { Violation(cls, "Routes class injects persistence `${it.type.name}` — state a `server.domain` interface and let a Repository provide it") }
        }
    }

    @Describe("A Routes class may inject its feature's `server.domain` interfaces, and other features' `server.domain` interfaces published to `:api`")
    val mayInjectDomainInterfaces by guidance
}

private fun KoParameterDeclaration.namesPersistence(): Boolean {
    val head = type.name.substringBefore('<').trimEnd('?').substringAfterLast('.')
    if (head.endsWith("Repository") || head.endsWith("Storage") || head.endsWith("Store")) return true
    val source = type.sourceDeclaration as? KoFullyQualifiedNameProvider ?: return false
    return source.fullyQualifiedName.orEmpty().contains(".server.data.")
}
