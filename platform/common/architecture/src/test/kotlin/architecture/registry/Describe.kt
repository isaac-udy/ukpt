package architecture.registry

import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.full.findAnnotation

/**
 * The documentation text for a catalog declaration — the statement of a rule/guidance property, or
 * the narrative description of a `RuleGroup`/`Construct` object. Markdown; rendered into the
 * generated docs. Annotation arguments must be compile-time constants, so the text can't call
 * `trimIndent()` itself — readers apply it.
 *
 *     @Describe("Repositories must be marked as `internal`")
 *     val internalVisibility by rule { constrain { … } }
 *
 *     @Describe("May inject Services or Storage objects to fulfill their domain properties")
 *     val mayInjectServices by guidance
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class Describe(val text: String)

/** The trimmed [Describe] text of a property/object, or null when absent. */
internal fun KAnnotatedElement.describeText(): String? = findAnnotation<Describe>()?.text?.trimIndent()?.trim()
