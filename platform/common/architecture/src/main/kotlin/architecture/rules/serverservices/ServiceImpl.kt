package architecture.rules.serverservices

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.containsPackageSegment
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoParameterDeclaration
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider

@Describe("""
    An implementation of a `Service` interface (see [Service Interface](#service-interface)). A
    ServiceImpl lives in `feature.[name].server.services` of `:server`, the same package as the
    contract, so it belongs to this layer, not the top-level feature group.
""")
object ServiceImpl : Construct<ServerServices>(
    requirements = listOf(
        isClassWhere("is named `[Name]ServiceImpl`, matching its `[Name]Service` contract") { it.name.endsWith("ServiceImpl") },
        predicate("resides in `feature.[name].server.services` itself, beside the contract, not in a sub-package") { it.isInServicesRoot() },
    ),
) {
    @Describe("A Service implementation must be `internal`")
    val internalVisibility by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.hasInternalModifier) emptyList() else listOf(Violation(cls, "Service implementation must be `internal`"))
        }
    }

    @Describe("A Service implementation must not inject persistence: neither a Repository nor a StorageClass")
    val noPersistenceInjection by rule {
        rationale(
            """
            A ServiceImpl answers a request by composing the feature's `server.domain` interfaces;
            persistence sits on the far side of those interfaces, where `ServerServices.noDataImports`
            keeps it. A Repository is the wiring that provides those interfaces, not a thing to hold —
            injecting it, or the StorageClass under it, states the table the handler wants instead of
            the contract it needs, so nothing else can reuse that access and nothing names what the
            request actually required. It is the same rule that keeps ViewModels off client
            Repositories.
            """.trimIndent(),
        )
        note("Tested on the primary constructor: a parameter whose type is named `[Name]Repository`, `[Name]Storage`, or `[Name]Store`, or whose type resolves into a persistence package.")
        note("This is the constructor-shaped half of `ServerServices.noDataImports`, which measures the same coupling over imports; a ServiceImpl reaching persistence some other way is counted there.")
        note("Constructor discipline beyond persistence comes from the layer rules, not from a whitelist here: session authentication, domain interfaces, and platform types are all legitimate parameters.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.primaryConstructor?.parameters.orEmpty()
                .filter { param -> param.namesPersistence() }
                .map { Violation(cls, "ServiceImpl injects persistence `${it.type.name}` — state a `server.domain` interface and let a Repository provide it") }
        }
    }

    @Describe("A Service implementation may inject its feature's `server.domain` interfaces, and other features' `server.domain` interfaces published to `:api`")
    val mayInjectDomainInterfaces by guidance {
        note("Another feature's Service contract is not on that list: it is the client's door, and injecting it is `ServerServices.noForeignServiceContractInjection`.")
    }

    @Describe("A Service implementation must not depend on the `ui` package")
    val noUiDependency by rule {
        rationale(
            """
            ServiceImpls run on the server and have no Compose runtime: a UI import here would
            either fail to compile in `:server` or mean a UI type is being treated as data, both
            of which are wrong. If you need a shape shared with the UI, put it in the feature's
            `:api` domain or services package.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.containingFile.imports.any { it.name.containsPackageSegment("ui") }) {
                listOf(Violation(decl, "service implementation imports the `ui` package"))
            } else {
                emptyList()
            }
        }
    }
}

/**
 * True when a constructor parameter names persistence: a `[Name]Repository` / `[Name]Storage` /
 * `[Name]Store` type, or a type that resolves into a persistence package.
 */
private fun KoParameterDeclaration.namesPersistence(): Boolean {
    val head = type.name.substringBefore('<').trimEnd('?').substringAfterLast('.')
    if (head.endsWith("Repository") || head.endsWith("Storage") || head.endsWith("Store")) return true
    val source = type.sourceDeclaration as? KoFullyQualifiedNameProvider ?: return false
    val fqn = source.fullyQualifiedName.orEmpty()
    return fqn.contains(".server.data.") || fqn.contains(".services.storage.")
}
