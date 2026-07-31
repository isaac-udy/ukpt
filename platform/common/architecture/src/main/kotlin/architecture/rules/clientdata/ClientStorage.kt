package architecture.rules.clientdata

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.containingFilePackage
import architecture.definitions.containsPackageSegment
import architecture.rules.shared.domainInterfaceNamesOnSide
import architecture.rules.shared.simpleTypeNames
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    A class responsible for local-device data persistence and retrieval, such as credentials,
    preferences, or cached data on disk. Backed by Keychain (iOS), SharedPreferences (Android),
    DataStore, etc.

    * **Note:** A Storage class may be an `expect`/`actual` class when the underlying storage
      mechanism is platform-specific, such as Keychain on iOS and SharedPreferences on Android.
""")
object ClientStorage : Construct<ClientData>(
    requirements = listOf(
        isClass,
        hasNameEndingWith("Storage"),
        isClassWhere("is not abstract") { !it.hasAbstractModifier },
        isClassWhere("is not a `data class`") { !it.hasDataModifier },
        predicate("resides in `feature.[name].client.data.storage`") { decl ->
            val pkg = decl.containingFilePackage()
            pkg.containsPackageSegment("data") && pkg.containsPackageSegment("storage")
        },
    ),
) {
    @Describe("A Storage class must be marked as `internal` (the `expect` declaration may be public, but `actual` implementations should be `internal` where the language allows)")
    val internalVisibility by rule {
        note("The test skips `expect`/`actual` declarations: an `actual`'s visibility must match its `expect`, so the language decides there, not this Rule.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.hasExpectModifier || cls.hasActualModifier || cls.hasInternalModifier) {
                emptyList()
            } else {
                listOf(Violation(cls, "Storage class must be `internal`"))
            }
        }
    }

    @Describe("A Storage class must not inject domain interfaces, Repositories, or Services")
    val doesNotInjectDomainRepositoriesOrServices by rule {
        rationale(
            """
            Storage is the lowest layer of the stack: it should depend on the database or keychain
            client and nothing higher. Injecting a domain interface, Repository, or Service would
            embed orchestration logic in the persistence layer.
            """.trimIndent(),
        )
        note("Domain interfaces are matched by name against the client's classified set — an `:api`-declared parameter type often resolves to no source declaration, so resolution-based matching would silently skip exactly the published contracts.")
        scope { scope, exempt ->
            val domainInterfaces = scope.domainInterfaceNamesOnSide("client")
            scope.classes()
                .filter { test(it) }
                .filterNot { exempt(it) }
                .flatMap { cls ->
                    cls.primaryConstructor?.parameters.orEmpty()
                        .filter { param ->
                            val typeName = param.type.name
                            typeName.simpleTypeNames().any { it in domainInterfaces } ||
                                typeName.endsWith("Repository") || typeName.endsWith("Service")
                        }
                        .map { Violation(cls, "Storage class injects a forbidden dependency: ${it.name}: ${it.type.name}") }
                }
        }
    }
}
