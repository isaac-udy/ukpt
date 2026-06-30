package architecture.spike

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.provider.KoParentProvider

/**
 * SPIKE port of the `data` layer. The point of interest is [implementsDomainInterface]: a cross-layer
 * classification expressed as a **direct compile-time reference** `DomainLayer.DomainInterface.test(...)`,
 * replacing the live engine's `Classifiers` string-id indirection entirely.
 */
object DataLayer : RuleGroup(inPackage = "feature..data..") {

    object Repository : Construct(
        isClass,
        hasNameEndingWith("Repository"),
        isInternal,
        not(implementsDomainInterface),   // Repositories EXPOSE domain interfaces, never implement them
    ) {
        val propertiesEagerlyInitialized by rule("Domain-interface properties must be initialized immediately — no `by lazy`") {
            scope { _, _ -> emptyList() }
        }
        val mayInjectServicesStorageOrClients by rule("May inject Services, `data.storage`, or DB clients") {
            guidance()
        }
    }

    // A group-level rule (no construct) → id `DataLayer.noUiDeps`.
    val noUiDeps by rule("`data` must not depend on the `ui` package") {
        scope { _, _ -> emptyList() }
    }
}

/** Reuses the `domain` layer's classifier directly — no `Classifiers`, no string ids. */
private val implementsDomainInterface =
    predicate("implements a domain interface") { declaration: KoBaseDeclaration ->
        (declaration as? KoParentProvider)?.parents()?.any { DomainLayer.DomainInterface.test(it) } == true
    }
