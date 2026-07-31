package architecture.rules.shared

import architecture.definitions.isFeatureModule
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import dev.isaacudy.udytils.architecture.*

/**
 * The WorkflowStep rules, shared by both sided domain groups. [side] scopes the one rule that
 * reaches outside the domain layer; [outerCaller] names the side's characteristic outer-layer
 * class in that rule's rationale (a ViewModel on the client, a ServiceImpl on the server).
 */
abstract class WorkflowStepRules<G : RuleGroup>(
    side: String,
    outerCaller: String,
) : Construct<G>(
    requirements = listOf(
        isClass,
        isClassWhere("implements a workflow's nested `Step` contract") { cls ->
            cls.parents().any { it.name == "Step" || it.name.endsWith(".Step") }
        },
    ),
) {
    private val sideMarker = ".$side."

    @Describe("A WorkflowStep must not inject another step")
    val noSiblingInjection by rule {
        rationale(
            """
            A step that holds another step calls it directly, which puts the order back in the code
            and takes it away from the declarations. Dependencies between steps are expressed as
            artifacts the workflow resolves.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            val stepNames = scope.classes().filter { test(it) }.mapNotNull { it.name }.toSet()
            scope.classes()
                .filter { test(it) }
                .filterNot { exempt(it) }
                .flatMap { cls ->
                    cls.primaryConstructor?.parameters.orEmpty()
                        .filter { it.type.name.namesAStep(stepNames) }
                        .map { Violation(cls, "step injects sibling `${it.name}: ${it.type.name}` — declare the artifact instead") }
                }
        }
    }

    @Describe("Only a Workflow's composing UseCase may take steps as dependencies")
    val composedNotInjected by rule {
        rationale(
            """
            A step is meaningful only in the order its workflow derives. A $outerCaller or an
            unrelated UseCase that injects one calls it out of that order, in a position nothing
            declared and the workflow cannot see — which is how half a process ends up running
            somewhere else.
            """.trimIndent(),
        )
        note("The composer is the `[Interface]Impl` UseCase that injects the steps, asks the workflow to order them, and runs the plan.")
        scope { scope, exempt ->
            val stepNames = scope.classes().filter { test(it) }.mapNotNull { it.name }.toSet()
            scope.classes()
                .filter { it.isFeatureModule() && it.isOnThisSide() }
                .filterNot { exempt(it) }
                .filterNot { test(it) }
                .filterNot { it.name.endsWith("Impl") }
                .flatMap { cls ->
                    cls.primaryConstructor?.parameters.orEmpty()
                        .filter { it.type.name.namesAStep(stepNames) }
                        .map {
                            Violation(
                                cls,
                                "`${cls.name}` injects workflow step `${it.name}: ${it.type.name}` — only the workflow's composing UseCase may hold steps",
                            )
                        }
                }
        }
    }

    @Describe("A WorkflowStep should be named for the work it does, as `[Verb]Step`")
    val namedForItsVerb by guidance

    /** The side this construct governs — `composedNotInjected` reaches outside `domain`, so it is scoped by hand. */
    private fun KoClassDeclaration.isOnThisSide(): Boolean =
        containingFile.packagee?.name?.contains(sideMarker) == true
}

/**
 * True for a type that names a workflow step: one of the [stepNames] resolved from the scope, or a
 * workflow's nested contract written as `[Name]Workflow.Step`, in either case bare or inside a
 * collection. A bare `Step` is deliberately not enough — plenty of unrelated types are called that.
 */
private fun String.namesAStep(stepNames: Set<String>): Boolean {
    val written = substringBefore('<').trimEnd()
    val argument = if ('<' in this) substringAfter('<').substringBeforeLast('>').trim().trimEnd('?') else null
    return listOfNotNull(written, argument).any { name ->
        name in stepNames || (name.endsWith(".Step") && name.substringBeforeLast(".Step").endsWith("Workflow"))
    }
}
