package architecture.utils

import architecture.definitions.isApiModule
import architecture.definitions.isFeatureModule
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.provider.KoNameProvider

/**
 * Fully-qualified names published to `:api` for one side's domain layer (`client.domain` or
 * `server.domain`, passed as [domainSegment]) — the cross-feature channel `ClientDomain.pure`,
 * `ServerDomain.pure`, and `ClientUi.crossFeatureDomainViaApi` all read from.
 *
 * A declaration counts as published when it is a top-level declaration of a file whose package
 * contains `.$domainSegment` and which resides in an `:api` module; the FQN is the file's package
 * plus the declaration's name. Publishing is moving the file, not changing the package, so this is
 * exactly what makes a declaration reachable across a feature boundary.
 */
fun publishedDomainFqns(scope: KoScope, domainSegment: String): Set<String> {
    return scope.files
        .filter { it.isFeatureModule() && it.isApiModule() }
        .filter { (it.packagee?.name ?: "").contains(".$domainSegment") }
        .flatMap { file ->
            val pkg = file.packagee?.name ?: return@flatMap emptyList<String>()
            file.declarations(includeNested = false)
                .filterIsInstance<KoNameProvider>()
                .map { "$pkg.${it.name}" }
        }
        .toSet()
}

/**
 * True if [importName] names a published declaration itself, or something nested inside one (a
 * companion member, a nested type) — reached through its enclosing declaration's published FQN.
 */
fun resolvesToPublishedFqn(importName: String, published: Set<String>): Boolean =
    published.any { publishedFqn -> importName == publishedFqn || importName.startsWith("$publishedFqn.") }
