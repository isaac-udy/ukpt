package architecture.registry

/**
 * A single architecture rule. Its [id] is the dotted *path* of the object/property names that
 * declare it (e.g. `DomainLayer.UseCase.noOverridingDefaults`), so identity is derived from the
 * `object`/`val` structure — no axis, no numbers, no lockfile, unique by construction. The id is
 * resolved lazily: a construct's rules register while their group is still initializing its
 * `constructs` list, before the construct↔group link can be read. [tag] is derived from
 * [enforcement] and so can never disagree with reality.
 */
class Rule internal constructor(
    private val idProvider: () -> String,
    val title: String,              // the canonical one-line statement (docs + failure header)
    val rationale: String,          // the "why" (failure body + docs); blank when none
    val enforcement: Enforcement,
    val status: Status,
    val notes: List<String>,        // authored sub-bullets carried into the docs
) {
    internal constructor(
        id: String,
        title: String,
        rationale: String,
        enforcement: Enforcement,
        status: Status,
        notes: List<String>,
    ) : this({ id }, title, rationale, enforcement, status, notes)

    val id: String get() = idProvider()
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
