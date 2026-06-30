package architecture

/**
 * Annotation that marks a declaration as exempt from one or more architecture
 * rules.
 *
 * The annotation is consumed by the Konsist tests in `:platform:common:architecture`.
 * Each rule that supports exemptions checks whether the offending declaration
 * (or its containing file) carries an `@ArchitectureException` listing that
 * rule's ID, and skips the offence if it does.
 *
 * Use sparingly. An exemption is an admission that the code does not meet the
 * rule and that the right resolution has not yet been agreed. The architecture
 * README §6 lists the rules around adding exemptions; the short version:
 *
 * 1. Get human sign-off before adding one.
 * 2. Every entry must explain *why* it exists.
 * 3. Treat exemptions as temporary. Pair them with a `trackingIssue` where
 *    possible so the cleanup work is captured.
 *
 * Targets:
 *  * **Class-level**: annotate the class declaration.
 *  * **File-level**: use `@file:ArchitectureException(...)` at the top of the
 *    file, before the `package` line.
 *
 * @param ruleIds The architecture rule path ids this declaration is exempt from
 *   (e.g. `["servicesLayer.internalHierarchicalVisibility"]`). At least one id is
 *   required. See the architecture README "Rule IDs" section.
 * @param reason Free-form explanation of *why* the exemption is justified.
 *   Should be specific enough to read on its own without context.
 * @param trackingIssue Optional issue number / URL where the cleanup work is
 *   tracked. Empty string when none has been filed yet.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FILE,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
annotation class ArchitectureException(
    val ruleIds: Array<String>,
    val reason: String,
    val trackingIssue: String = "",
)
