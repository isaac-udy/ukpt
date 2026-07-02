package architecture.rules.data

import architecture.registry.*

import architecture.definitions.containingFilePackage
import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoParentDeclaration

@Describe("""
    The `data` axis is **client-only**: Repository implementations and client-side local persistence
    (Keychain, SharedPreferences, etc.). Server-side persistence and service implementations live in
    the `services` axis — the server has no `data.*` package (see [the services layer](services.md)).
    Repositories fan out across [Services](services.md#service-interface) (the `:api` contract) and
    client-side local storage, and expose [domain interfaces](domain.md#domain-interface) for the
    rest of the feature to consume.
""")
object DataLayer : RuleGroup(
    inPackage = "feature..data..",
    constructs = listOf(
        Repository,
        ClientDataInterface,
        ClientDataImplementation,
        ClientStorage,
    ),
) {

    // §3.3 `data` package dependencies (layer-level — not tied to one construct)
    @Describe("Provides implementations of `domain` interfaces — by exposing them as properties, not by inheriting them")
    val providesDomainImplementations by rule {
        note("A Repository that implements a domain interface, or fails to expose one as a `public val`, fails the enforcing rules directly.")
        enforcedBy(Repository.doesNotImplementDomainInterfaces, Repository.exposesDomainInterfacesAsProperties)
    }

    @Describe("Forbidden from injecting `domain` interfaces — logic requiring multiple domain interfaces must be moved to a UseCase")
    val noInjectingDomainInterfaces by rule {
        rationale(
            """
            Repositories *implement* domain interfaces — if one injects a domain interface, it's calling
            a sibling Repository through the abstract layer, which makes the dependency graph unreadable
            and easy to cycle. Logic that needs multiple domain interfaces belongs in a UseCase.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.classes()
                .filter { it.isFeatureModule() }
                .filter { it.resideInPackage("..data..") }
                .filterNot { exempt(it) }
                .flatMap { cls ->
                    cls.primaryConstructor?.parameters.orEmpty()
                        .filter { param ->
                            val source = param.type.sourceDeclaration as? KoBaseDeclaration
                            isDomainInterfaceInDomainPackage(source)
                        }
                        .map { param -> Violation(cls, "data class injects domain interface `${param.type.name}`") }
                }
        }
    }

    @Describe("`data.storage` classes use `internal` visibility where the language allows (see `DataLayer.ClientStorage.internalVisibility` for the canonical statement, incl. the `expect`/`actual` nuance)")
    val storageInternalVisibility by rule {
        enforcedBy(ClientStorage.internalVisibility)
    }

    @Describe("Must not depend on the `ui` package")
    val noUiDeps by rule {
        rationale(
            """
            UI is the outermost layer; `data` sits beneath it and supplies the domain interfaces the UI
            consumes. If `data` imports a UI type the layering becomes circular and the Repository can no
            longer be tested without a Compose runtime.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filter { it.packagee?.name?.containsPackageSegment("data") == true }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any { import -> import.name.containsPackageSegment("ui") }
                }
                .map { Violation(it.path, "data file imports a ui package") }
        }
    }
}

/**
 * True if [declaration] (or, for a parent reference, its source declaration) is a domain interface
 * declared in a feature's `domain` package. Re-expresses the domain-interface classification +
 * domain-package residence inline so the `data` layer stays self-contained.
 */
internal fun isDomainInterfaceInDomainPackage(declaration: KoBaseDeclaration?): Boolean {
    val source = when (declaration) {
        is KoParentDeclaration -> declaration.sourceDeclaration as? KoBaseDeclaration
        else -> declaration
    }
    val iface = source as? KoInterfaceDeclaration ?: return false
    if (!iface.containingFilePackage().containsPackageSegment("domain")) return false
    if (!iface.hasFunModifier || iface.hasSealedModifier) return false
    val hasOperatorInvoke = iface.functions().any { it.name == "invoke" && it.hasOperatorModifier }
    if (!hasOperatorInvoke) return false
    val abstractFunctionsSuspendOrFlow = iface.functions()
        .filter { it.name == "invoke" || !it.text.contains("=") }
        .all { it.hasSuspendModifier || it.returnType?.name?.contains("Flow") == true }
    if (!abstractFunctionsSuspendOrFlow) return false
    val hasFlowReturn = iface.functions()
        .any { it.name == "invoke" && it.returnType?.name?.contains("Flow") == true }
    return !hasFlowReturn || iface.name.startsWith("FlowOf")
}
