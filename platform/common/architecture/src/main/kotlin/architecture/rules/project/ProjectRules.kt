package architecture.rules.project

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.isFeatureModule
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoKDocProvider
import com.lemonappdev.konsist.api.provider.KoResideInPackageProvider

/*
 * Not a package layer: no classifying constructs, no `inPackage`, hence no exhaustiveness rule.
 */
@Describe("""
    These rules are not tied to a Construct or a single package; they apply across every feature
    module. Several govern the process for [architecture exceptions](exceptions.md); the mechanism
    itself is documented there.

    Context for the exception-handling rules: exceptions defined in the
    [services contract](services.md#service-interface) cross the client/server boundary as
    serialised payloads, and the deserialised types don't always extend `Exception`. `AsyncState`
    is the async-result wrapper that [ViewModels](ui.md#view-model) consume.
""")
object ProjectRules : RuleGroup() {

    // ---- §5.1 Exception handling -------------------------------------------------------------
    @Describe("A `try/catch` block must never catch `Exception`; use `catch (t: Throwable)` or a specific exception type instead")
    val noCatchException by rule {
        rationale(
            """
            The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions
            into types that may not extend `Exception`, such as kotlinx-serialization error types.
            A `catch (Exception)` block silently misses these, so the error propagates uncaught and
            crashes on an internal thread instead of being handled by application code.
            """.trimIndent(),
        )
        note("On the client, prefer `AsyncState.fromSuspending` over manual `try/catch`: it captures exceptions correctly and integrates with the ViewModel state pattern.")
        note("Catching a specific exception type, such as `catch (t: IllegalArgumentException)`, is always acceptable when you only want to handle that case.")
        scope { scope, exempt ->
            val tryDeclarationRegex = Regex(
                pattern = ".*\\btry\\s*\\{.*\\}.*\\bcatch\\s*\\(.*\\bException\\s*\\).*",
                option = RegexOption.DOT_MATCHES_ALL,
            )
            (scope.functions() + scope.properties())
                .filterNot { exempt(it) }
                .filter { it.text.matches(tryDeclarationRegex) }
                .map { Violation(it, "try/catch catches `Exception` — catch `Throwable` or a specific type instead") }
        }
    }

    @Describe("An exception type defined in `services` (the client/server contract) must be annotated with `@Serializable`")
    val serviceExceptionsSerializable by rule {
        rationale(
            """
            The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions
            into typed payloads on the client; without `@Serializable` the type and message are lost
            in transit and the client receives a generic deserialisation failure. Exceptions inside
            `services.internal.*` stay server-side and never reach the client, so they are out of
            scope.
            """.trimIndent(),
        )
        note("Prefer subclassing `PresentableException` with a deliberate `retryable` flag: streaming flows auto-retry retryable errors and surface terminal ones, and the unary error UI offers a Retry action only when `retryable`.")
        scope { scope, exempt ->
            scope.classes(includeNested = true)
                .filter { it.isFeatureModule() }
                .filter { isInServicesContractPackage(it) }
                .filter { clazz ->
                    clazz.parents().any { parent ->
                        parent.name == "RuntimeException" ||
                            parent.name == "Exception" ||
                            parent.name == "PresentableException"
                    }
                }
                .filterNot { exempt(it) }
                .filterNot { it.hasAnnotationWithName("Serializable") }
                .map { Violation(it, "service-defined exception is not annotated with @Serializable") }
        }
    }

    // ---- §5.2 Imports ------------------------------------------------------------------------
    @Describe("An import must not use a wildcard; always list the explicit symbols")
    val noWildcardImports by rule {
        rationale(
            """
            Wildcards hide which symbols a file depends on, break several architecture tests
            (which inspect import names directly), and silently pull in new names when the
            imported package adds members.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .filter { file -> file.imports.any { it.isWildcard } }
                .map { Violation(it.path, "file uses a wildcard import") }
        }
    }

    @Describe("An `AsyncState` must never be constructed directly via `Loading`/`Success`/`Error`; use `AsyncState.fromSuspending`/`fromFlow` instead")
    val noDirectAsyncStateConstruction by rule {
        rationale(
            """
            Direct construction skips the exception capture, cancellation, and state-flow protocol
            that `AsyncState.fromSuspending`/`fromFlow` handle uniformly, silently breaking the
            contract the rest of the codebase relies on. Files that legitimately build AsyncState
            values (defining its semantics, or the server-side status pattern) opt out with
            `@file:ArchitectureException`.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            val constructionRegex = Regex("""AsyncState\.(Loading|Success|Error)\s*[(<]""")
            scope.files
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .filter { file -> file.text.lines().any { constructionRegex.containsMatchIn(it) } }
                .map { Violation(it.path, "constructs AsyncState.Loading/Success/Error directly — use fromSuspending/fromFlow") }
        }
    }

    // ---- §5.3 Action and request types -------------------------------------------------------
    @Describe("An action/request type must model its variants as a `sealed interface`/`sealed class` (each variant a `data class`), not as a single type with an `enum` discriminator and nullable fields")
    val sealedActionVariants by rule {
        rationale(
            """
            A sealed hierarchy makes illegal field combinations unrepresentable and lets `when`
            exhaustiveness drive handling, so adding a variant surfaces every site that must handle it.
            """.trimIndent(),
        )
        note("\"An enum that should be a sealed class\" can't be detected reliably by the tests.")
        unverifiable()
    }

    // ---- §6.3 Architecture-exception sign-off (all guidance — enforced by human review) ----
    @Describe("An architecture exception may only be added after discussing the exception with a human author")
    val exceptionsNeedHumanSignOff by rule { unverifiable() }

    @Describe("An architecture exception is not a valid way to resolve an immediate architecture-test failure; fix the code or the rule first")
    val exceptionNotForFailingTests by rule { unverifiable() }

    @Describe("An architecture exception must include a KDoc-style (`/** ... */`) comment explaining why it exists and the intended resolution")
    val exceptionNeedsKdoc by rule {
        note("The test covers `@ArchitectureException` on declarations; `// architecture-exception:` comments in build files carry their reason inline and are out of scope.")
        scope { scope, exempt ->
            scope.declarations(includeNested = true)
                .filter { (it as? KoAnnotationProvider)?.hasAnnotationWithName("ArchitectureException") == true }
                .filterNot { exempt(it) }
                .filterNot { (it as? KoKDocProvider)?.hasKDoc == true }
                .map { Violation(it, "declaration carries @ArchitectureException without a KDoc explaining why and the intended resolution") }
        }
    }

    @Describe("An architecture exception should be temporary; revisit it periodically and remove it once the underlying issue is resolved")
    val exceptionsAreTemporary by guidance
}

/**
 * The cross-the-wire `services` contract package (`feature..services..`), excluding the server-only
 * `internal`/`storage`/`tools` sub-axes.
 */
private fun isInServicesContractPackage(declaration: KoResideInPackageProvider): Boolean {
    if (!declaration.resideInPackage("feature..services..")) return false
    return listOf(
        "feature..services..internal..",
        "feature..services..storage..",
        "feature..services..tools..",
    ).none { declaration.resideInPackage(it) }
}
