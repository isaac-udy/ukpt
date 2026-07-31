package architecture.rules.shared

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import dev.isaacudy.udytils.architecture.*

/**
 * The Repository rules, shared by the two sided data groups: one pattern — provide domain
 * interfaces as `public val` properties over whatever sits behind you — appearing on both sides.
 * The side decides only which `domain` package the provided interfaces live in and what a
 * Repository may inject to satisfy them; the latter is each concrete object's own guidance.
 */
abstract class RepositoryRules<G : RuleGroup>(
    private val side: String,
) : Construct<G>(
    requirements = listOf(
        isClass,
        hasNameEndingWith("Repository"),
        hasFileNameMatchingDeclaration,
    ),
) {
    @Describe("A Repository must be `internal`")
    val internalVisibility by rule {
        rationale("Callers depend on the domain interfaces it provides, never on the Repository itself; `internal` is what makes that the only reachable surface.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.hasInternalModifier) emptyList() else listOf(Violation(cls, "Repository must be `internal`"))
        }
    }

    @Describe("A Repository must not implement domain interfaces directly")
    val doesNotImplementDomainInterfaces by rule {
        rationale("Inheriting the interface makes one class *be* many contracts, so its surface can only grow; exposing them as properties keeps each contract separately nameable and separately injectable.")
        note("Matched by name against the side's classified domain interfaces: a parent reference to an `:api`-declared interface often resolves to no source declaration, so resolution-based matching would silently skip exactly the published contracts.")
        scope { scope, exempt ->
            val domainInterfaces = scope.domainInterfaceNamesOnSide(side)
            scope.classes()
                .filter { test(it) }
                .filterNot { exempt(it) }
                .flatMap { cls ->
                    cls.parents()
                        .filter { it.name.substringAfterLast('.') in domainInterfaces }
                        .map { Violation(cls, "Repository implements domain interface `${it.name}` directly — expose it as a property instead") }
                }
        }
    }

    @Describe("A Repository must expose domain interfaces as `public val` properties")
    val exposesDomainInterfacesAsProperties by rule {
        rationale("The property name is the interface name in lowerCamelCase, so the wiring reads as a list of the contracts this Repository answers.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            val domainImports = cls.containingFile.imports
                .filter { it.name.contains(".$side.domain.") }
                .map { it.name.substringAfterLast(".") }
                .toSet()
            val exposes = cls.properties()
                .filter { it.isVal && it.hasPublicOrDefaultModifier }
                .any { prop -> domainImports.any { prop.text.contains(it) } }
            if (exposes) emptyList()
            else listOf(Violation(cls, "Repository must expose at least one `$side.domain` interface as a `public val` property"))
        }
    }

    @Describe("A Repository must not inject domain interfaces")
    val doesNotInjectDomainInterfaces by rule {
        rationale("A Repository that injects a contract is calling a sibling adapter through the abstract layer, which makes the graph unreadable and easy to cycle. Logic that needs several interfaces is a UseCase.")
        note("Matched by name against the side's classified domain interfaces, bare or inside a wrapper such as `Lazy<…>` — an `:api`-declared parameter type often resolves to no source declaration, so resolution-based matching would silently skip exactly the published contracts.")
        scope { scope, exempt ->
            val domainInterfaces = scope.domainInterfaceNamesOnSide(side)
            scope.classes()
                .filter { test(it) }
                .filterNot { exempt(it) }
                .flatMap { cls ->
                    cls.primaryConstructor?.parameters.orEmpty()
                        .filter { param -> param.type.name.simpleTypeNames().any { it in domainInterfaces } }
                        .map { Violation(cls, "Repository injects domain interface `${it.type.name}` — move multi-interface logic to a UseCase") }
                }
        }
    }

    @Describe("A Repository must not inject other Repositories")
    val doesNotInjectRepositories by rule {
        rationale("Two Repositories over one domain object have no single edge, and the second reaches its data through the first's mapping rather than through the source that owns it.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.primaryConstructor?.parameters.orEmpty()
                .filter { it.type.name.endsWith("Repository") }
                .map { Violation(cls, "Repository injects another Repository `${it.type.name}`") }
        }
    }

    @Describe("A Repository's domain-interface properties must be initialized immediately: no `by lazy`, no custom getter")
    val propertiesEagerlyInitialized by rule {
        rationale("Eager initialisation lets Koin's graph validation catch a missing or cyclic dependency at startup rather than at first use, and it makes the wiring obvious from a quick read of the constructor.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.properties()
                .filter { it.hasPublicOrDefaultModifier }
                .filter { it.text.contains("by lazy") || it.text.contains("get()") }
                .map { Violation(it, "Repository property must be initialized immediately — no `by lazy` or custom `get()`") }
        }
    }
}
