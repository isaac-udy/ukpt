package architecture.registry

/**
 * A single architecture rule. Its [id] is the dotted *path* of the property names that declare it
 * (e.g. `domainLayer.domainInterface.operatorInvoke`), so identity is derived from the `by val`
 * structure — no axis, no numbers, no lockfile, unique by construction. [tag] is derived from
 * [enforcement] and so can never disagree with reality.
 */
class Rule internal constructor(
    val id: String,                 // dotted path, e.g. "domainLayer.domainInterface.operatorInvoke"
    val title: String,              // the canonical one-line statement (README + failure header)
    val rationale: String,          // the "why" (failure body + README); blank for shape requirements
    val enforcement: Enforcement,
    val status: Status,
    val notes: List<String>,        // authored sub-bullets carried into the README
) {
    val tag: Tag get() = enforcement.tag
}

/** Lifecycle of a rule. Active rules are enforced; retired rules document a removal. */
sealed interface Status {
    data object Active : Status

    /** A removed/renamed rule, kept to document the removal and point at its replacement. */
    data class Retired(
        val reason: String,
        val replacedBy: String?,
    ) : Status
}
