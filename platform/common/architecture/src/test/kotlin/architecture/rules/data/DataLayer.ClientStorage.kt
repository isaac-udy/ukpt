package architecture.rules.data

import architecture.registry.*

import architecture.definitions.containingFilePackage
import architecture.definitions.containsPackageSegment
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    A class responsible for local-device data persistence and retrieval (e.g., credentials,
    preferences, cached data on disk) — `expect`/`actual` `Storage` classes backed by Keychain
    (iOS), SharedPreferences (Android), DataStore, etc.

    * **Note**: Client-side Storage classes may be `expect`/`actual` classes when the underlying
      storage mechanism is platform-specific (e.g., Keychain on iOS, SharedPreferences on Android).
""")
object ClientStorage : Construct<DataLayer>(
    requirements = listOf(
        isClass,
        hasNameEndingWith("Storage"),
        isClassWhere("Storage classes must not be abstract") { !it.hasAbstractModifier },
        isClassWhere("Storage classes must not be `data class`") { !it.hasDataModifier },
        predicate("Storage classes must reside in the `data.storage` package on `:client`") { decl ->
            val pkg = decl.containingFilePackage()
            pkg.containsPackageSegment("data") && pkg.containsPackageSegment("storage")
        },
    ),
) {
    @Describe("Storage classes must be marked as `internal` (the `expect` declaration may be public, but `actual` implementations should be `internal` where the language allows)")
    val internalVisibility by guidance

    @Describe("Storage classes are forbidden from injecting domain interfaces, Repositories, or Services")
    val doesNotInjectDomainRepositoriesOrServices by rule {
        rationale(
            """
            Storage is the lowest layer of the stack — it should depend on the database/keychain
            client and nothing higher. Injecting a domain interface, Repository, or Service would
            embed orchestration logic in the persistence layer.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.primaryConstructor?.parameters.orEmpty()
                .filter { param ->
                    val source = param.type.sourceDeclaration as? KoBaseDeclaration
                    val isDomainInterface = source != null && isDomainInterfaceInDomainPackage(source)
                    val typeName = param.type.name
                    isDomainInterface || typeName.endsWith("Repository") || typeName.endsWith("Service")
                }
                .map { Violation(cls, "Storage class injects a forbidden dependency: ${it.name}: ${it.type.name}") }
        }
    }
}
