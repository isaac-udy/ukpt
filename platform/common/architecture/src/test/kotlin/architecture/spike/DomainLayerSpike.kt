package architecture.spike

/**
 * SPIKE port of the `domain` layer in the object style. Compare with the live
 * `architecture.rules.DomainRules`.
 *
 * Requirements (the `🔶 construct` classification) are the composable predicate list in each
 * `Construct(...)` header — no individual ids. Rules ("what the construct must do") keep their own
 * ids derived from the exact names, e.g. `DomainLayer.DomainInterface.errorsViaExceptions`.
 */
object DomainLayer : RuleGroup(inPackage = "feature..domain..") {

    object DomainInterface : Construct(
        isFunInterface,
    ) {
        val operatorInvoke by rule("The primary function of a domain interface must be an `operator fun invoke`") {
            guidance()
        }
        val errorsViaExceptions by rule("Functions propagate errors via thrown exceptions, never the return type") {
            rationale("`@Throws` on suspend functions must include `CancellationException` for Kotlin/Native.")
            // A construct-scoped check is just a scope check filtered to this construct's population —
            // no separate `ConstructConstraint` needed. (Body trivial here; the live engine does the work.)
            scope { scope, exempt ->
                scope.interfaces()
                    .filter { DomainInterface.test(it) }
                    .filterNot { exempt(it) }
                    .flatMap { emptyList() }
            }
        }
    }

    object DomainObject : Construct(
        oneOf(isDataClass, isEnum, isSealed, isValueClass),
        isAnnotatedWith("Serializable"),
    ) {
        val immutable by rule("Domain objects must be immutable (val properties only)") { guidance() }
        val invariantInitBlocks by rule("Should include `init` blocks that enforce invariants") { guidance() }
    }

    object UseCase : Construct(
        isClass,
        hasNameEndingWith("Impl"),
    ) {
        val noOverridingDefaults by rule("Must not override any default function of its domain interface") {
            scope { _, _ -> emptyList() }
        }
        val mayInjectDomainInterfaces by rule("May inject domain interfaces to perform its logic") { guidance() }
    }
}
