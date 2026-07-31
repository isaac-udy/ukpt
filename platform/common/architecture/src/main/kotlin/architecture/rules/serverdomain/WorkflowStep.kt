package architecture.rules.serverdomain

import architecture.rules.shared.WorkflowStepRules
import dev.isaacudy.udytils.architecture.Describe

@Describe("""
    A top-level class implementing a [Workflow](#workflow)'s nested `Step` contract — one unit of a
    declared process, in its own file.

    A step is an adapter, and a thin one. It reads its inputs from the workflow's context, calls the
    [domain interfaces](#domain-interface) that do the real work, and writes its outputs back. What
    makes it a step rather than a [UseCase](#use-case) is that it **declares** its inputs and
    outputs instead of being called in a fixed position: the workflow reads those declarations and
    decides when it runs.

    Steps live at the top level, not nested in the workflow object, precisely so the catalog
    governs them. The workflow holds the definition; the steps are the behaviour.

    * **Note:** Name a step for what it does — `[Verb]Step`, as in `ImportStep` or
      `PersistEventsStep`. The suffix is what a reader scans for; the verb is what they read.
    * **Note:** A step that needs another step's output declares the artifact, never the step. That
      is the whole mechanism — declaring the dependency is what lets the workflow order the two, and
      naming the sibling directly is how a workflow decays back into a call sequence.
""")
object WorkflowStep : WorkflowStepRules<ServerDomain>(side = "server", outerCaller = "ServiceImpl")
