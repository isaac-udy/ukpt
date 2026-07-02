package architecture.rules.data

import architecture.registry.*

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    A class that provides implementations for [domain interfaces](domain.md#domain-interface),
    providing the "edge" of the domain layer.

    * **Note**: The property name must match the interface name using `lowerCamelCase`
      (e.g., `val createUser = CreateUser { ... }`).
""")
object Repository : Construct<DataLayer>(
    requirements = listOf(
        isClass,
        hasNameEndingWith("Repository"),
        hasFileNameMatchingDeclaration,
    ),
) {
    @Describe("Repositories must be marked as `internal`")
    val internalVisibility by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.hasInternalModifier) emptyList() else listOf(Violation(cls, "Repository must be `internal`"))
        }
    }

    @Describe("Repositories must not implement domain interfaces directly")
    val doesNotImplementDomainInterfaces by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.parents()
                .filter { isDomainInterfaceInDomainPackage(it) }
                .map { Violation(cls, "Repository implements domain interface `${it.name}` directly — expose it as a property instead") }
        }
    }

    @Describe("Repositories must expose domain interfaces as `public val` properties")
    val exposesDomainInterfacesAsProperties by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            val domainImports = cls.containingFile.imports
                .filter { it.name.contains(".domain.") }
                .map { it.name.substringAfterLast(".") }
                .toSet()
            val exposes = cls.properties()
                .filter { it.isVal && it.hasPublicOrDefaultModifier }
                .any { prop -> domainImports.any { prop.text.contains(it) } }
            if (exposes) emptyList() else listOf(Violation(cls, "Repository must expose at least one domain interface as a `public val` property"))
        }
    }

    @Describe("Repositories are forbidden from injecting domain interfaces")
    val doesNotInjectDomainInterfaces by rule {
        rationale("Logic requiring multiple domain interfaces must be moved to a UseCase in the `domain` package.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.primaryConstructor?.parameters.orEmpty()
                .filter { param ->
                    val source = param.type.sourceDeclaration as? KoBaseDeclaration
                    source != null && isDomainInterfaceInDomainPackage(source)
                }
                .map { Violation(cls, "Repository injects domain interface `${it.type.name}` — move multi-interface logic to a UseCase") }
        }
    }

    @Describe("Repositories are forbidden from injecting other Repositories")
    val doesNotInjectRepositories by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.primaryConstructor?.parameters.orEmpty()
                .filter { it.type.name.endsWith("Repository") }
                .map { Violation(cls, "Repository injects another Repository `${it.type.name}`") }
        }
    }

    @Describe("Repository domain-interface properties must be initialized immediately — no `by lazy`, no custom getter")
    val propertiesEagerlyInitialized by rule {
        rationale(
            """
            Eager initialisation lets Koin's graph validation catch missing or cyclic dependencies
            at startup instead of at the first injection at runtime, and it makes the wiring obvious
            from a quick read of the Repository constructor.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.properties()
                .filter { it.hasPublicOrDefaultModifier }
                .filter { it.text.contains("by lazy") || it.text.contains("get()") }
                .map { Violation(it, "Repository property must be initialized immediately — no `by lazy` or custom `get()`") }
        }
    }

    @Describe("May inject Services, client-side `data.storage` Storage objects, or database clients to fulfill their domain properties")
    val mayInjectServicesStorageOrClients by guidance
}
