package architecture.rules.ui

import architecture.registry.*

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    The complete, immutable representation of a Screen's data at a single point in time.

    * **Note**: `AsyncState` covers action progress as well as loads — e.g. a "save" action as
      `AsyncState<Unit>`. Never directly construct `AsyncState.Loading`/`Success`/`Error` — use
      `AsyncState.fromSuspending`/`fromFlow`; that prohibition is enforced project-wide by
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
    @Describe("ViewModel State objects must be immutable (val properties only)")
    val immutable by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.properties().filterNot { it.isVal }.map { Violation(it, "ViewModel State property `${it.name}` is a `var` — State objects must be immutable") }
        }
    }

    @Describe("ViewModel State objects have a 1:1 relationship with a ViewModel type")
    val viewModelRelationship by guidance
    @Describe("ViewModel State objects must use `AsyncState<T>` / `UpdatableState<T>` for asynchronously loaded data and action progress")
    val usesAsyncState by guidance
    @Describe("ViewModel State objects must not define custom sealed types for loading/success/error — use `AsyncState<T>`")
    val noCustomAsyncSealedTypes by guidance
    @Describe("ViewModel State objects should be a transparent container for domain objects, not lossy UI-level mappings")
    val transparentContainer by guidance
    @Describe("ViewModel State objects should include `init` blocks that enforce invariants")
    val invariantInitBlocks by guidance
    @Describe("Formatting and visual representation must be handled by the Screen or specialized `@Composable` properties/functions")
    val formattingInScreen by guidance
}
