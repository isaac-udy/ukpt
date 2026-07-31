package architecture.rules.shared

import architecture.definitions.isMutable
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import dev.isaacudy.udytils.architecture.*

/** The Workflow definition-object rules, shared by both sided domain groups. */
abstract class WorkflowRules<G : RuleGroup> : Construct<G>(
    requirements = listOf(
        isObject,
        hasNameEndingWith("Workflow"),
    ),
) {
    @Describe("A Workflow must nest a `Step` contract")
    val definesAStepContract by rule {
        rationale(
            """
            The `Step` interface is what makes the object a workflow rather than a namespace: it is
            the contract the process is assembled from. An object named `[Name]Workflow` that
            declares no `Step` is a misnamed constants holder, and saying so is more useful than
            leaving it unclassified.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val obj = decl as? KoObjectDeclaration ?: return@constrain emptyList()
            if (obj.interfaces().any { it.name == "Step" }) {
                emptyList()
            } else {
                listOf(Violation(obj, "workflow `${obj.name}` nests no `Step` contract"))
            }
        }
    }

    @Describe("A Workflow's `Step` contract must declare the metadata the workflow composes by")
    val stepsDeclareTheirComposition by rule {
        rationale(
            """
            A workflow derives its order from what each step says about itself, so the contract has
            to carry that as data. A `Step` whose only members are functions can only be
            hand-sequenced, which is the thing a workflow exists not to be.
            """.trimIndent(),
        )
        note("Typical members are `requires` and `produces`, stated over the workflow's nested artifact vocabulary; another workflow may compose by something else.")
        constrain { decl, _ ->
            val obj = decl as? KoObjectDeclaration ?: return@constrain emptyList()
            val step = obj.interfaces().firstOrNull { it.name == "Step" } ?: return@constrain emptyList()
            if (step.properties().isEmpty()) {
                listOf(Violation(step, "`Step` declares no composition metadata — a workflow orders steps by their declarations, not by hand"))
            } else {
                emptyList()
            }
        }
    }

    @Describe("A Workflow must nest only its definition: no `suspend` function on the object itself or on a nested class")
    val nestsOnlyDefinition by rule {
        rationale(
            """
            The membership rule classifies top-level declarations only, so anything nested inside an
            object is invisible to the catalog. That is correct for a definition and dangerous for
            anything else: a Repository or a UseCase nested here would answer to no construct at
            all. Behaviour stays at the top level where a construct governs it.
            """.trimIndent(),
        )
        note("`suspend` is this codebase's marker for reaching outside the process, so it is what separates a definition from work. The nested `Step` contract is exempt — declaring suspending work is exactly its job; performing it is the step implementation's, at the top level.")
        note("What a nested declaration may *hold* is left to the layer's `pure` rule, which already forbids this layer the adapters a hidden Repository or UseCase would need. This rule holds the line that matters here: nothing nested inside a workflow does work.")
        constrain { decl, _ ->
            val obj = decl as? KoObjectDeclaration ?: return@constrain emptyList()
            val onTheObject = obj.functions(includeNested = false)
                .filter { it.hasSuspendModifier }
                .map { Violation(it, "workflow function `${it.name}` is `suspend` — a workflow's own functions are pure") }
            val inNestedBodies = (obj.classes(includeNested = true) + obj.objects(includeNested = true))
                .flatMap { nested ->
                    nested.functions()
                        .filter { it.hasSuspendModifier }
                        .map {
                            Violation(
                                it,
                                "nested `${nested.name}.${it.name}` is `suspend` — a workflow nests its definition; work belongs in a step or a UseCase",
                            )
                        }
                }
            onTheObject + inNestedBodies
        }
    }

    @Describe("A Workflow's own vocabulary must be immutable: no `var` properties and no mutable collection types")
    val immutableVocabulary by rule {
        rationale("The object is a single shared instance read by every step. State that changes on it would make one run observable from another.")
        constrain { decl, _ ->
            val obj = decl as? KoObjectDeclaration ?: return@constrain emptyList()
            obj.properties()
                .filter { it.isMutable() || it.type?.name?.startsWith("Mutable") == true }
                .map { Violation(it, "workflow property `${it.name}` is mutable") }
        }
    }
}
