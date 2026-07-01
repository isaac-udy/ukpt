package architecture.rules.project

import architecture.registry.*

import architecture.definitions.isFeatureModule
import com.lemonappdev.konsist.api.provider.KoResideInPackageProvider

/**
 * Project-wide code rules (§5) and the architecture-exception sign-off rules (§6.3) — the
 * `R-PROJ` family.
 *
 * Unlike the other groups this is **not** a package layer: there is no `ProjectLayer` of
 * classifying constructs, so every rule here is layer-level (project-wide) and there is no
 * `inPackage`, hence no exhaustiveness rule. The tested rules are expressed with `scope { }`
 * over the whole Konsist scope; the §6 sign-off rules are 📋 guidance enforced by human review.
 *
 * Rule ids are the object/property path, e.g. `ProjectRules.noCatchException`.
 */
object ProjectRules : RuleGroup() {

    // ---- §5.1 Exception handling -------------------------------------------------------------
    val noCatchException by rule("`try/catch` blocks must never catch `Exception` — use `catch (t: Throwable)` or a specific exception type") {
        rationale(
            """
            The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions
            into types that may not extend `Exception` (e.g. kotlinx-serialization / kRPC error
            types). A `catch (Exception)` block silently misses these, so the error propagates
            uncaught and crashes on an internal thread instead of being handled by application code.
            """.trimIndent(),
        )
        note("On the client, prefer `AsyncState.fromSuspending` over manual `try/catch` — it captures exceptions correctly and integrates with the ViewModel state pattern.")
        note("Catching a specific exception type (e.g. `catch (t: IllegalArgumentException)`) is always acceptable when you only want to handle that case.")
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

    val serviceExceptionsSerializable by rule("Exception types defined in `services` (the cross-the-wire contract) must be annotated with `@Serializable`") {
        rationale(
            """
            The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions
            into typed payloads on the client; without `@Serializable` the type and message are lost
            in transit and the client receives a generic deserialisation failure. Exceptions inside
            `services.internal.*` stay server-side and don't cross the wire, so they are out of scope.
            """.trimIndent(),
        )
        note("Prefer subclassing `PresentableException` with a deliberate `retryable` flag — streaming flows auto-retry retryable errors and surface terminal ones; the unary error UI offers a Retry action only when `retryable`.")
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
    val noWildcardImports by rule("Imports must not use wildcards — always list the explicit symbols") {
        rationale(
            """
            Wildcards hide which symbols a file depends on, break a number of architecture-test
            checks (which inspect import names directly), and silently pull in new names when the
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

    val noDirectAsyncStateConstruction by rule(
        "`AsyncState.Loading`/`Success`/`Error` must not be constructed directly — use `AsyncState.fromSuspending`/`fromFlow`",
    ) {
        rationale(
            """
            Direct construction skips the exception capture, cancellation, and state-flow protocol
            that `AsyncState.fromSuspending`/`fromFlow` handle uniformly — silently breaking the
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
    val sealedActionVariants by rule(
        "Model action/request variants as a `sealed interface`/`sealed class` (each variant a `data class`), not a single type with an `enum` discriminator and nullable fields",
    ) { guidance() }

    // ---- §6.3 Architecture-exception sign-off (all 📋 guidance — enforced by human review) ----
    val exceptionsNeedHumanSignOff by rule(
        "Architecture exceptions may only be added after discussing the exception with a human author",
    ) { guidance() }

    val exceptionNotForFailingTests by rule(
        "Adding an architecture exception is not a valid way to resolve an immediate architecture-test failure without user feedback — fix the code or the rule first",
    ) { guidance() }

    val exceptionNeedsKdoc by rule(
        "Every architecture exception must include a KDoc-style (`/** ... */`) comment explaining why it exists and the intended resolution",
    ) { guidance() }

    val exceptionsAreTemporary by rule(
        "Architecture exceptions are temporary — revisit them periodically and remove them once the underlying issue is resolved",
    ) { guidance() }
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
