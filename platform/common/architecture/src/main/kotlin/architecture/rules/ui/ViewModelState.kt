package architecture.rules.ui

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    The complete, immutable representation of a Screen's data at a single point in time.

    * **Note:** `AsyncState` covers action progress as well as loads: a "save" action can be an
      `AsyncState<Unit>`. Never directly construct `AsyncState.Loading`/`Success`/`Error`; use
      `AsyncState.fromSuspending`/`fromFlow`. That prohibition is enforced project-wide by
      `ProjectRules.noDirectAsyncStateConstruction`.
""")
object ViewModelState : Construct<UiLayer>(
    requirements = listOf(
        isClass,
        isDataClass,
        hasNameEndingWith("State"),
        hasFileNameMatchingDeclaration,
    ),
) {
    @Describe("A ViewModel State object must be immutable (val properties only)")
    val immutable by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.properties().filterNot { it.isVal }.map { Violation(it, "ViewModel State property `${it.name}` is a `var` — State objects must be immutable") }
        }
    }

    @Describe("A ViewModel State object must have a 1:1 relationship with a ViewModel type")
    val viewModelRelationship by rule { unverifiable() }
    @Describe("A ViewModel State object must use `AsyncState<T>` / `UpdatableState<T>` for asynchronously loaded data and action progress")
    val usesAsyncState by rule { unverifiable() }
    @Describe("A ViewModel State object must not define custom sealed types for loading/success/error; use `AsyncState<T>` instead")
    val noCustomAsyncSealedTypes by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            (cls.classes() + cls.interfaces())
                .filter { it.hasSealedModifier }
                .filter { nested -> nested.text.contains("Loading") && nested.text.contains("Error") }
                .map { Violation(it, "State declares a custom sealed loading/error hierarchy `${it.name}` — use `AsyncState<T>`") }
        }
    }
    @Describe("A ViewModel State object should be a transparent container for domain objects, not a lossy UI-level mapping")
    val transparentContainer by guidance
    @Describe("A ViewModel State object should include `init` blocks that enforce invariants")
    val invariantInitBlocks by guidance {
        audit { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@audit emptyList()
            if (cls.text.contains("init {")) {
                emptyList()
            } else {
                listOf(Violation(cls, "State has no `init` block — consider one if this state has invariants"))
            }
        }
    }
    @Describe("A ViewModel State object's formatting and visual representation must be handled by the Screen or specialized `@Composable` properties/functions")
    val formattingInScreen by rule { unverifiable() }
}
