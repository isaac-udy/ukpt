package architecture.rules.services

import architecture.registry.*

import architecture.definitions.containsPackageSegment
import architecture.rules.domain.DomainInterface
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    Implementations of `Service` interfaces (see [Services](#service-interface)). A ServiceImpl
    lives in `feature.[name].services` of `:server` — dual-life with the contract — so it
    belongs to the `services` axis, not the top-level feature group.
""")
object ServiceImpl : Construct<ServicesLayer>(
    requirements = listOf(
        isClassWhere("is named `[Name]ServiceImpl`, matching its `[Name]Service` contract") { it.name.endsWith("ServiceImpl") },
        predicate("resides in `feature.[name].services` of the `:server` module (dual-life with the contract)") { it.isInServicesRoot() },
    ),
) {
    @Describe("A Service implementation must be `internal`")
    val internalVisibility by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.hasInternalModifier) emptyList() else listOf(Violation(cls, "Service implementation must be `internal`"))
        }
    }

    @Describe("A Service implementation must not inject domain interfaces")
    val noInjectingDomainInterfaces by rule {
        rationale(
            """
            A ServiceImpl is the server-side request handler; it reaches *down* into services.storage
            and services.internal, not sideways into the domain interfaces a client would consume.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.primaryConstructor?.parameters.orEmpty()
                .filter { param -> DomainInterface.test(param.type.sourceDeclaration as? KoBaseDeclaration) }
                .map { Violation(cls, "ServiceImpl injects domain interface `${it.type.name}` — reach down into storage/internal instead") }
        }
    }
    @Describe("A Service implementation may inject `services.storage` Storage classes and `services.internal` orchestrators of the same feature, plus other features' Service contracts via `:api`")
    val mayInjectStorageAndInternal by guidance

    @Describe("A Service implementation must not depend on the `ui` package")
    val noUiDependency by rule {
        rationale(
            """
            ServiceImpls run on the server and have no Compose runtime — a UI import here would
            either fail to compile in `:server` or mean a UI type has been pulled out of `ui` and
            is being treated as data, both of which are wrong (§4.4.2, §3.4.4). If you need a
            shared shape with the UI, put it in the feature's `:api` domain or services package.
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
