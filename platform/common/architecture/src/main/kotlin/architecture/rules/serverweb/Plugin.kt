package architecture.rules.serverweb

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.isServerModule
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    A class that installs the Ktor plugins a feature's routes rely on, such as an authentication
    provider: an `internal class [Name]Plugin` implementing `platform.server.web.ServerPlugin`,
    whose `Application.install()` installs them. Its constructor takes what the plugins need, and
    the feature's dependency module binds it with `singleOf(::[Name]Plugin) bind ServerPlugin::class`.

    The server installs every bound plugin before the web platform and the routes, so a route can
    use `authenticate { … }`. The web platform installs server-sent events and websockets itself;
    a plugin that installs `WebSockets` first sets its configuration.
""")
object Plugin : Construct<ServerWeb>(
    requirements = listOf(
        isClassWhere("is named `[Name]Plugin`") { it.name.endsWith("Plugin") },
        predicate("is declared in a `:server` module") { it.isServerModule() },
    ),
) {
    @Describe("A Plugin class must be `internal`")
    val internalVisibility by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.hasInternalModifier) emptyList() else listOf(Violation(cls, "`${cls.name}` must be `internal`"))
        }
    }

    @Describe("A Plugin class must implement `ServerPlugin`")
    val implementsServerPlugin by rule {
        rationale("The server installs every `ServerPlugin` binding; a Plugin class that does not implement it is never installed.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.parents().any { it.name == "ServerPlugin" }) emptyList() else listOf(Violation(cls, "`${cls.name}` does not implement `ServerPlugin`"))
        }
    }

    @Describe("A Plugin class must not inject persistence: neither a Repository nor a StorageClass")
    val noPersistenceInjection by rule {
        rationale(
            """
            A plugin that checks a credential or a session asks the feature's `server.domain`
            interfaces, as a Routes class does. Injecting a Repository or the StorageClass under it
            states the table it reads instead of the contract it needs.
            """.trimIndent(),
        )
        note("Tested on the primary constructor, as `ServerWeb.Routes.noPersistenceInjection` is.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.primaryConstructor?.parameters.orEmpty()
                .filter { it.namesPersistence() }
                .map { Violation(cls, "`${cls.name}` injects persistence `${it.type.name}` — state a `server.domain` interface and let a Repository provide it") }
        }
    }
}
