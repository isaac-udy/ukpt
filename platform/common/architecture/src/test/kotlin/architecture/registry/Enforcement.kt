package architecture.registry

import architecture.definitions.DefinitionPredicate
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.provider.KoLocationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider

/**
 * The four enforcement tags from the architecture README legend. The tag is *derived* from a
 * rule's [Enforcement], never set by hand.
 */
enum class Tag(val marker: String) {
    TESTED("✅ tested"),
    CONSTRUCT("🔶 construct"),
    GUIDANCE("📋 guidance"),
    CODEGEN("⚙️ codegen"),
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

/** ✅ tested over the whole Konsist scope. `exempt` is pre-keyed to the rule's id by the runner. */
fun interface ScopeCheck {
    fun run(scope: KoScope, exempt: (KoBaseDeclaration) -> Boolean): List<Violation>
}

/** ✅ tested over a single declaration the owning [Construct] classifies. `exempt` is pre-keyed to the rule id. */
fun interface ConstructCheck {
    fun run(declaration: KoBaseDeclaration, exempt: (KoBaseDeclaration) -> Boolean): List<Violation>
}

/** ✅ tested over the parsed module dependency graph (build.gradle.kts edges), not the Konsist scope. */
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

/** 🔶 construct — a classifying shape requirement (a predicate over one declaration), never a verdict. */
class ShapeRequirement(
    val predicate: DefinitionPredicate<KoBaseDeclaration>,
) : Enforcement {
    override val tag get() = Tag.CONSTRUCT
}

/**
 * ✅ tested, scoped to the population a [Construct] classifies. The construct is resolved lazily so
 * a rule declared *inside* a construct can target the construct currently being defined.
 */
class ConstructConstraint(
    private val constructProvider: () -> Construct,
    val check: ConstructCheck,
) : Enforcement {
    val construct: Construct get() = constructProvider()
    override val tag get() = Tag.TESTED
}

/** ✅ tested, free over the whole Konsist scope. */
class ScopeConstraint(
    val check: ScopeCheck,
) : Enforcement {
    override val tag get() = Tag.TESTED
}

/** ✅ tested, over the module dependency graph parsed from build files. */
class ModuleGraphConstraint(
    val check: ModuleGraphCheck,
) : Enforcement {
    override val tag get() = Tag.TESTED
}

/**
 * ✅ tested, but enforced *transitively* by the rules it names (e.g. cross-feature domain access is
 * enforced by the cross-feature module rules). Runs nothing; [by] documents the enforcing rule ids.
 */
class DelegatedConstraint(
    val by: List<String>,
) : Enforcement {
    override val tag get() = Tag.TESTED
}

/** 📋 guidance / ⚙️ codegen — no executable body. Distinct from a vacuous `scope { emptyList() }`. */
class NotEnforced(
    override val tag: Tag,
) : Enforcement
