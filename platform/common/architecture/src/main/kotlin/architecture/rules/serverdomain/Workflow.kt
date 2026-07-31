package architecture.rules.serverdomain

import architecture.rules.shared.WorkflowRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    An `object` named `[Name]Workflow` holding the definition of a multi-step process: the `Step`
    contract its steps implement, the vocabulary those steps hand values through, and the pure
    function that orders them.

    A workflow exists when a process is described by **data rather than by a call sequence**. Its
    steps declare what they need and what they produce; the workflow reads those declarations and
    derives an order. That is what separates it from a [UseCase](#use-case) that calls three
    contracts in a row — a UseCase *is* the sequence, and changing the order means editing it, while
    a workflow's order falls out of what its steps say about themselves.

    What lives inside the object is the **definition**, and only the definition: the `Step`
    interface, the typed keys and registry its steps exchange values through, the context passed
    down the chain, and pure functions over that vocabulary. Everything that *does* something is a
    top-level declaration another construct governs — the [steps](#workflow-step) themselves, and
    the [UseCase](#use-case) that injects them, calls `resolve`, and runs the plan.

    * **Note:** Nesting is what makes the definition readable as one unit — a reader opens one file
      and sees the whole vocabulary. It is also the one place the catalog's membership rule does not
      reach, which is why `nestsOnlyDefinition` exists: nesting is for definition, never a way to
      keep behaviour out of the catalog's sight.
    * **Note:** The ordering function stays on the object while it is pure and dependency-free. The
      day it needs a collaborator is the day it becomes a [domain interface](#domain-interface) with
      a [UseCase](#use-case) behind it, like anything else with a dependency.
    * **Note:** A second workflow is the signal to lift the shared key/registry machinery into
      `:platform`. One workflow does not make a framework.
""")
object Workflow : WorkflowRules<ServerDomain>()
