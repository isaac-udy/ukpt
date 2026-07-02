package architecture.registry

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.provider.KoLocationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoResideInPackageProvider

/**
 * The four enforcement statuses from the architecture README legend. The tag is *derived* from a
 * rule's [Enforcement], never set by hand.
 */
enum class Tag(val marker: String) {
    TESTED("tested"),
    CONSTRUCT("construct"),
    GUIDANCE("guidance"),
    CODEGEN("codegen"),
}

/** A single violation: where it is + why. The runner stamps the rule id on top for reporting. */
data class Violation(
    val where: String,
    val message: String,
) {
    /** Convenience: derive the location from a declaration so rule code never names a helper. */
    constructor(at: KoBaseDeclaration, message: String) : this(at.sourceLocation(), message)
}

/** Best-effort human location for a declaration in a violation message. */
internal fun KoBaseDeclaration.sourceLocation(): String =
    (this as? KoLocationProvider)?.location
        ?: (this as? KoNameProvider)?.name
        ?: toString()

/**
 * Does this declaration reside in [pkg] (Konsist's `..`-glob package syntax)? Uses the public
 * [KoResideInPackageProvider]; top-level declarations always implement it, so a declaration that
 * doesn't (shouldn't occur for the shapes the catalog classifies) is treated as not matching.
 */
internal fun KoBaseDeclaration.residesIn(pkg: String): Boolean =
    (this as? KoResideInPackageProvider)?.resideInPackage(pkg) == true

/** tested over the whole Konsist scope. `exempt` is pre-keyed to the rule's id by the runner. */
fun interface ScopeCheck {
    fun run(scope: KoScope, exempt: (KoBaseDeclaration) -> Boolean): List<Violation>
}

/** tested over a single declaration the owning [Construct] classifies. `exempt` is pre-keyed to the rule id. */
fun interface ConstructCheck {
    fun run(declaration: KoBaseDeclaration, exempt: (KoBaseDeclaration) -> Boolean): List<Violation>
}

/** tested over the parsed module dependency graph (build.gradle.kts edges), not the Konsist scope. */
fun interface ModuleGraphCheck {
    fun run(graph: ModuleGraph, exempt: (ModuleEdge) -> Boolean): List<Violation>
}

/**
 * How (or whether) a rule is enforced. Sealed + input-typed: each variant takes a *different*
 * input and returns a violation list rather than throwing, so the runner can build each input
 * once, dispatch by subtype, and aggregate every violation before failing.
 */
sealed interface Enforcement {
    val tag: Tag
}

/** tested, free over the whole Konsist scope. */
class ScopeConstraint(
    val check: ScopeCheck,
) : Enforcement {
    override val tag get() = Tag.TESTED
}

/** tested, over the module dependency graph parsed from build files. */
class ModuleGraphConstraint(
    val check: ModuleGraphCheck,
) : Enforcement {
    override val tag get() = Tag.TESTED
}

/**
 * tested, but enforced *transitively* by the rules it names (e.g. cross-feature domain access is
 * enforced by the cross-feature module rules). Runs nothing; [by] documents the enforcing rule ids —
 * resolved lazily so a rule can reference another rule that is still initializing.
 */
class DelegatedConstraint(
    private val byProvider: () -> List<String>,
) : Enforcement {
    constructor(by: List<String>) : this({ by })

    val by: List<String> get() = byProvider()
    override val tag get() = Tag.TESTED
}

/**
 * guidance / codegen — never fails the build. Guidance may carry an optional [audit]: a
 * ScopeConstraint/ModuleGraphConstraint the suite runs and *reports* (without failing), so
 * soft conventions like "keep cross-feature `:api` dependencies minimal" stay visible.
 */
class NotEnforced(
    override val tag: Tag,
    val audit: Enforcement? = null,
) : Enforcement
