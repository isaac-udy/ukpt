package architecture.rules.data

import architecture.registry.*

import architecture.definitions.containingFilePackage
import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoParentDeclaration

/**
 * The `data` layer (§3.3, §4.3) in the object style. The `data` axis is **client-only**: Repositories
 * that fan out across Services and client-side local persistence (`data.storage`). Server-side
 * persistence lives in the `services` axis, not here.
 *
 * Each construct's requirements (the `construct` classification) are the predicate list in its
 * `Construct(...)` header; its rules ("what the construct must do") are `val x by rule(...)` in the
 * body. Only the package-dependency rules, which aren't tied to a single construct, live at the layer
 * level. Rule ids are the exact object/property names, e.g. `DataLayer.Repository.doesNotInjectRepositories`.
 */
object DataLayer : RuleGroup(inPackage = "feature..data..") {

    // §4.3.1 Repositories
    object Repository : Construct(
        isClass,
        hasNameEndingWith("Repository"),
        hasFileNameMatchingDeclaration,
    ) {
        val internalVisibility by rule("Repositories must be marked as `internal`") {
            constrain { decl, _ ->
                val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
                if (cls.hasInternalModifier) emptyList() else listOf(Violation(cls, "Repository must be `internal`"))
            }
        }

        val doesNotImplementDomainInterfaces by rule("Repositories must not implement domain interfaces directly") {
            constrain { decl, _ ->
                val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
                cls.parents()
                    .filter { isDomainInterfaceInDomainPackage(it) }
                    .map { Violation(cls, "Repository implements domain interface `${it.name}` directly — expose it as a property instead") }
            }
        }

        val exposesDomainInterfacesAsProperties by rule("Repositories must expose domain interfaces as `public val` properties") {
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

        val doesNotInjectDomainInterfaces by rule("Repositories are forbidden from injecting domain interfaces") {
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

        val doesNotInjectRepositories by rule("Repositories are forbidden from injecting other Repositories") {
            constrain { decl, _ ->
                val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
                cls.primaryConstructor?.parameters.orEmpty()
                    .filter { it.type.name.endsWith("Repository") }
                    .map { Violation(cls, "Repository injects another Repository `${it.type.name}`") }
            }
        }

        val propertiesEagerlyInitialized by rule("Repository domain-interface properties must be initialized immediately — no `by lazy`, no custom getter") {
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

        val mayInjectServicesStorageOrClients by guidance("May inject Services, client-side `data.storage` Storage objects, or database clients to fulfill their domain properties")
    }

    // §4.3.1.1 Non-Repository client data abstractions
    object ClientDataInterface : Construct(
        isInterface,
        predicate("Must live in `feature.[name].data` (not `data.storage`)") { decl ->
            val pkg = decl.containingFilePackage()
            pkg.containsPackageSegment("data") && !pkg.containsPackageSegment("storage")
        },
    )

    object ClientDataImplementation : Construct(
        isClass,
        isClassWhere("Must not be named `Repository`") { !it.name.endsWith("Repository") },
        predicate("Must live in `feature.[name].data` (not `data.storage`)") { decl ->
            val pkg = decl.containingFilePackage()
            pkg.containsPackageSegment("data") && !pkg.containsPackageSegment("storage")
        },
    )

    // §4.3.2.1 Client-side Storage classes
    object ClientStorage : Construct(
        isClass,
        hasNameEndingWith("Storage"),
        isClassWhere("Storage classes must not be abstract") { !it.hasAbstractModifier },
        isClassWhere("Storage classes must not be `data class`") { !it.hasDataModifier },
        predicate("Storage classes must reside in the `data.storage` package on `:client`") { decl ->
            val pkg = decl.containingFilePackage()
            pkg.containsPackageSegment("data") && pkg.containsPackageSegment("storage")
        },
    ) {
        val internalVisibility by guidance("Storage classes must be marked as `internal` (the `expect` declaration may be public, but `actual` implementations should be `internal` where the language allows)")

        val doesNotInjectDomainRepositoriesOrServices by rule("Storage classes are forbidden from injecting domain interfaces, Repositories, or Services") {
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

    // §3.3 `data` package dependencies (layer-level — not tied to one construct)
    val providesDomainImplementations by guidance("Provides implementations of `domain` interfaces — by exposing them as properties, not by inheriting them") {
        note("Enforced via the `DataLayer.Repository` construct's classification: a class that implements a domain interface (or doesn't expose one as a `public val`) isn't recognised as a Repository.")
    }

    val noInjectingDomainInterfaces by rule("Forbidden from injecting `domain` interfaces — logic requiring multiple domain interfaces must be moved to a UseCase") {
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

    val storageInternalVisibility by guidance("`data.storage` classes use `internal` visibility where the language allows (see `DataLayer.ClientStorage.internalVisibility` for the canonical statement, incl. the `expect`/`actual` nuance)")

    val noUiDeps by rule("Must not depend on the `ui` package") {
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
private fun isDomainInterfaceInDomainPackage(declaration: KoBaseDeclaration?): Boolean {
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
