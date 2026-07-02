package architecture.rules.ui

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    A class that manages the UI state for a Screen and orchestrates calls to domain interfaces
    to load data and perform side effects based on user actions.

    * **Note**: The `navigation` handle is used to read Destination parameters and perform
      navigation. When closing/completing a screen, use `NavigationHandle.close` when the user
      is cancelling or backing out, and `NavigationHandle.complete` when the user has
      successfully performed an action.
""")
object ViewModel : Construct<UiLayer>(
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
    @Describe("A ViewModel exposes a single public `state` property, or no public properties at all")
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
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.functions()
                .filter { (it.hasPublicModifier || it.hasInternalModifier) && !it.hasOverrideModifier }
                .filterNot { it.returnType?.name == "Unit" || it.returnType == null }
                .map { Violation(it, "ViewModel function `${it.name}` returns `${it.returnType?.name}` — public/internal ViewModel functions must return `Unit`") }
        }
    }

    @Describe("A ViewModel should inject domain interfaces to load and manipulate domain objects")
    val injectsDomainInterfaces by guidance

    @Describe("A ViewModel must use `JobManager` to manage coroutines — never hold `var job: Job?` references")
    val usesJobManager by rule {
        rationale(
            """
            Manual `var job: Job?` tracking is error-prone: the previous job leaks if a new one
            starts before the old one completes, and lifecycle cancellation is easy to forget.
            `dev.isaacudy.udytils.coroutines.JobManager` handles cancel-then-replace and ties
            everything to `viewModelScope`.
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
