package architecture.rules

import architecture.registry.Construct

/**
 * Cross-layer classifiers. A rule in one layer sometimes needs to recognise another layer's
 * construct (e.g. "UI must not implement a domain interface", "a ServiceImpl implements a service
 * interface"). Constructs are block-local `val`s, so we resolve them from the assembled catalog by
 * their stable path id — keeping the rules self-contained (no dependency on the legacy `*Layer`
 * definitions). Resolved lazily via `get()`, so this is safe to reference from rule lambdas, which
 * only run after the whole catalog is built.
 */
internal object Classifiers {
    val domainInterface: Construct get() = domainLayer.constructs.single { it.id == "domainLayer.domainInterface" }
    val serviceInterface: Construct get() = servicesLayer.constructs.single { it.id == "servicesLayer.serviceInterface" }
}
