package architecture.rules.clientui

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    A class that manages the UI state for a Screen and orchestrates calls to domain interfaces
    to load data and perform side effects based on user actions.

    * **Note:** The `navigation` handle is used to read Destination parameters and perform
      navigation. When closing a screen, use `NavigationHandle.close` when the user is cancelling
      or backing out, and `NavigationHandle.complete` when the user has successfully performed
      an action.
""")
object ViewModel : Construct<ClientUi>(
    requirements = listOf(
        isClassWhere("extends `androidx.lifecycle.ViewModel`") { declaration ->
            declaration.parents().any { parent -> parent.name == "ViewModel" }
        },
        isClassWhere("is named `[Name]ViewModel`") { declaration ->
            declaration.name.endsWith("ViewModel")
        },
        isClassWhere("declares its `state` property as a `ViewModelState<[Name]State>` (1:1 with the ViewModel's State type)") { declaration ->
            val stateProperty = declaration.properties()
                .filter { it.hasPublicOrDefaultModifier || it.hasInternalModifier }
                .singleOrNull { it.name == "state" }
                ?: return@isClassWhere true
            stateProperty.text.contains("viewModelState") &&
                stateProperty.text.contains(declaration.name.replace("ViewModel", "State"))
        },
        isClassWhere("has a `private val navigation` obtained via `navigationHandle<[Name]Destination>()`") { declaration ->
            val navigationProperty = declaration.properties()
                .filter { it.hasPrivateModifier }
                .singleOrNull { it.name == "navigation" }
                ?: return@isClassWhere false
            val destinationName = declaration.name.replace("ViewModel", "Destination")
            // Regex (not exact string match) to tolerate whitespace/line-break differences.
            Regex("""by\s+navigationHandle\s*<\s*${Regex.escape(destinationName)}\s*>""")
                .containsMatchIn(navigationProperty.text)
        },
        hasFileNameMatchingDeclaration,
    ),
) {
    @Describe("A ViewModel must expose a single public `state` property, or no public properties at all")
    val singlePublicStateProperty by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            // includeNested = false: only the ViewModel's OWN properties count toward its public surface.
            val publicProperties = cls.properties(includeNested = false)
                .filter { it.hasPublicOrDefaultModifier || it.hasInternalModifier }
            val ok = when (publicProperties.size) {
                0 -> true
                1 -> publicProperties.single().name == "state"
                else -> false
            }
            if (ok) emptyList() else listOf(Violation(cls, "ViewModel must expose only a single public `state` property (found: ${publicProperties.joinToString { it.name }})"))
        }
    }

    @Describe("A ViewModel's `public`/`internal` functions must only return `Unit` (or omit a return type)")
    val publicFunctionsReturnUnit by rule {
        rationale("State is the single source of truth; a public method that returns a value is a side channel around it.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.functions()
                // `hasPublicOrDefaultModifier` (not `hasPublicModifier`) so default-public functions,
                // which carry no explicit visibility modifier, are also caught.
                .filter { (it.hasPublicOrDefaultModifier || it.hasInternalModifier) && !it.hasOverrideModifier }
                .filterNot { it.returnType?.name == "Unit" || it.returnType == null }
                .map { Violation(it, "ViewModel function `${it.name}` returns `${it.returnType?.name}` — public/internal ViewModel functions must return `Unit`") }
        }
    }

    @Describe("A ViewModel's `public`/`internal` functions must not be `suspend`")
    val publicFunctionsNotSuspend by rule {
        rationale(
            """
            A suspending public method makes the caller await work the ViewModel should own; on
            Android the awaiter (a composition scope, a `CompletableDeferred`) is lost on process
            death, silently dropping the result. Launch into `viewModelScope` and reflect the
            outcome in `state` instead.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.functions()
                .filter { (it.hasPublicOrDefaultModifier || it.hasInternalModifier) && !it.hasOverrideModifier }
                .filter { it.hasModifier(KoModifier.SUSPEND) }
                .map { Violation(it, "ViewModel function `${it.name}` is `suspend` — launch into `viewModelScope` and reflect the outcome in `state` instead") }
        }
    }

    @Describe("A ViewModel must not declare `private var` properties")
    val noPrivateVarProperties by rule {
        rationale(
            """
            A mutable private field is a side channel around `state` (the source of truth) and is
            lost on process death — for example a `pendingX` captured across a navigation round-trip.
            Carry per-open context on the navigation itself (key fields, or `instance.metadata` via a
            `NavigationKey.MetadataKey`) so the result handler recovers it process-death-safe; put
            genuine UI state in `state`.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            // includeNested = false so a private nested helper's own vars don't count against the VM.
            cls.properties(includeNested = false)
                .filter { it.hasPrivateModifier && it.isVar }
                .map { Violation(it, "ViewModel declares `private var ${it.name}` — state is the source of truth; carry per-open context on the navigation instead") }
        }
    }

    @Describe("A ViewModel should inject domain interfaces to load and manipulate domain objects")
    val injectsDomainInterfaces by guidance

    @Describe("A ViewModel must use `JobManager` to manage coroutines, never a `var job: Job?` reference")
    val usesJobManager by rule {
        rationale(
            """
            `dev.isaacudy.udytils.coroutines.JobManager` provides cancel-then-replace semantics
            and ties every job to `viewModelScope`; manual `var job: Job?` tracking leaks the
            previous job and skips lifecycle cancellation.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.properties()
                .filter {
                    val typeName = it.type?.name.orEmpty()
                    typeName == "Job" || typeName == "Job?"
                }
                .map { Violation(it, "ViewModel holds a `Job` reference — use `JobManager` instead") }
        }
    }
}
