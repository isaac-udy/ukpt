package architecture.rules.clientui

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    The complete, immutable representation of a Screen's data at a single point in time.

    * **Note:** `AsyncState` covers action progress as well as loads: a "save" action can be an
      `AsyncState<Unit>`. Never directly construct `AsyncState.Loading`/`Success`/`Error`; use
      `AsyncState.fromSuspending`/`fromFlow`. That prohibition is enforced project-wide by
      `ProjectRules.noDirectAsyncStateConstruction`.
""")
object ViewModelState : Construct<ClientUi>(
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
    @Describe("A ViewModel State object must not pair a progress-verb Boolean property with an error-synonym sibling — this is a hand-rolled async lifecycle; use `AsyncState<T>` / `AsyncState<Unit>` (via `fromFlow`/`fromSuspending`) or `UpdatableState<T>`")
    val noManualAsyncLifecycleFields by rule {
        rationale(
            """
            A Boolean progress flag paired with an error property reimplements the state machine
            `AsyncState` already provides. The hand-rolled pair lacks idle/loading distinction,
            drops progress reporting, and forces every consumer to combine two fields that
            `AsyncState` keeps atomic.
            """.trimIndent(),
        )
        val progressPattern = Regex("^(is)?(loading|saving|sending|submitting|refreshing|deleting|updating)", RegexOption.IGNORE_CASE)
        val errorPattern = Regex("error|failure|exception|throwable", RegexOption.IGNORE_CASE)
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            val props = cls.properties()
            val errorProps = props.filter { errorPattern.containsMatchIn(it.name) }
            if (errorProps.isEmpty()) return@constrain emptyList()
            props
                .filter { it.type?.name == "Boolean" }
                .filter { progressPattern.containsMatchIn(it.name) }
                .map { progressProp ->
                    val pairedError = errorProps.first()
                    Violation(progressProp, "hand-rolled async lifecycle: `${progressProp.name}: Boolean` paired with `${pairedError.name}` — use `AsyncState<T>` / `AsyncState<Unit>` (via `fromFlow`/`fromSuspending`) or `UpdatableState<T>`")
                }
        }
    }

    @Describe("A ViewModel State object must use `AsyncState<T>` / `UpdatableState<T>` for asynchronously loaded data and action progress")
    val usesAsyncState by rule {
        rationale(
            """
            Sentinel defaults (`""`, `emptyList()`, `false`) conflate a legitimate successful
            value with not-started, loading, and error. A required async value needs an explicit
            lifecycle state: `AsyncState<T>` for load-once data and action progress
            (`AsyncState<Unit>` for fire-and-observe actions like save/send/submit), or
            `UpdatableState<T>` when already-loaded data should stay visible through a
            refresh or error. An ordinary nullable or synchronous property remains valid
            when `null` has exactly one meaning.
            """.trimIndent(),
        )
        unverifiable { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@unverifiable emptyList()
            val props = cls.properties()
            val errorProps = props.filter { Regex("error|failure|exception|throwable", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
            val progressPattern = Regex("^(is)?(loading|saving|sending|submitting|refreshing|deleting|updating)", RegexOption.IGNORE_CASE)
            // Flag lone progress-verb Booleans (no error sibling — those are caught by noManualAsyncLifecycleFields)
            props
                .filter { it.type?.name == "Boolean" }
                .filter { progressPattern.containsMatchIn(it.name) }
                .filter { errorProps.isEmpty() }
                .map { Violation(it, "likely manual progress flag `${it.name}: Boolean` — consider `AsyncState<Unit>` (via `fromSuspending`/`fromFlow`)") }
        }
    }
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
    @Describe("A ViewModel State object must not contain dialog or sheet visibility flags (`show.*Dialog`, `.*DialogVisible`, `show.*Sheet`, `.*SheetVisible`) — dialog visibility is navigation state, not screen state")
    val noDialogVisibilityFlags by rule {
        rationale(
            """
            A boolean flag that toggles a modal — whether rendered through an inline `AlertDialog`,
            a design-system dialog wrapper, or a bottom sheet — couples the modal's lifecycle to
            the screen's state object instead of to the navigation backstack. The modal is still a
            destination with its own ViewModel and state, regardless of how it is rendered. Making
            it a destination eliminates the flag; the destination's ViewModel performs
            `complete`/`requestClose`, and the opener consumes the outcome through a navigation
            result channel (add `WithResult<R>` only when complete/close cannot carry the data).
            """.trimIndent(),
        )
        val pattern = Regex("show.*Dialog|.*DialogVisible|show.*Sheet|.*SheetVisible", RegexOption.IGNORE_CASE)
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.properties().filter { pattern.containsMatchIn(it.name) }
                .map { Violation(it, "dialog visibility is navigation state, not screen state — make the dialog a destination") }
        }
    }

    @Describe("A ViewModel State object must not expose calculated properties that flatten an `AsyncState` property back into a nullable or default value — the `AsyncState` remains the visible source of truth at the rendering boundary")
    val noFlattenedAsyncProxies by rule {
        rationale(
            """
            A proxy getter that returns `asyncProp.getOrNull()` or `asyncProp.getOrNull()?.field`
            reads as an independently authoritative field although it is conditional on the
            `AsyncState` succeeding. `.orEmpty()` and default values make Loading, Error, and
            legitimately-empty data indistinguishable. Call sites stop revealing which async
            operation owns the value, and the Screen can render a plausible invented partial
            frame before required data exists.
            """.trimIndent(),
        )
        note("Calculated State properties remain correct when they combine two or more top-level state properties, apply a real `if`/`when` decision, validate a draft against independently loaded options, or derive an affordance such as `canSubmit` — rather than renaming stored data.")
        note("A domain projection may expose genuine domain derivations (for example a conversation's suggested responder derived from participants and message order); the State must not mirror such a value through another nullable alias.")
        val asyncTypePattern = Regex("AsyncState|UpdatableState")
        // Narrow audit: single-expression getters whose body is <asyncProp>.getOrNull() or
        // <asyncProp>.getOrNull()?.<field>, optionally followed by .orEmpty() or ?: <fallback>.
        unverifiable { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@unverifiable emptyList()
            val props = cls.properties()
            val asyncPropNames = props
                .filter { p -> asyncTypePattern.containsMatchIn(p.type?.name.orEmpty()) }
                .map { it.name }
                .toSet()
            if (asyncPropNames.isEmpty()) return@unverifiable emptyList()
            // Build a regex matching: <asyncProp>.getOrNull() optionally ?.field, .orEmpty(), ?: fallback
            val asyncNames = asyncPropNames.joinToString("|") { Regex.escape(it) }
            val proxyPattern = Regex(
                """^\s*(?:get\(\)\s*=\s*)?($asyncNames)\.getOrNull\(\)(\?\.\w+)?(\s*\.orEmpty\(\)|\s*\?\:\s*.+)?\s*$"""
            )
            props
                .filter { it.text.contains("get()") }
                .filter { p ->
                    // Extract the getter expression — the text after "get() ="
                    val getterMatch = Regex("""get\(\)\s*=\s*(.+)""", RegexOption.DOT_MATCHES_ALL).find(p.text)
                    val getterBody = getterMatch?.groupValues?.get(1)?.trim() ?: return@filter false
                    proxyPattern.matches("get() = $getterBody")
                }
                .map { Violation(it, "likely flattened async proxy `${it.name}` — read the `AsyncState` at the rendering boundary instead") }
        }
    }

    @Describe("A ViewModel State object should be a transparent container for domain objects, not a lossy UI-level mapping")
    val transparentContainer by guidance
    @Describe("A ViewModel State object should include `init` blocks that enforce invariants")
    val invariantInitBlocks by guidance
    @Describe("A ViewModel State object's formatting and visual representation must be handled by the Screen or specialized `@Composable` properties/functions")
    val formattingInScreen by rule { unverifiable() }
}
