package architecture.rules.domain

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule
import architecture.definitions.primitiveTypeNames
import architecture.utils.collectionTypeNames
import architecture.utils.validateTypeName
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

@Describe("""
    The `domain` axis is the deepest layer of a feature and appears in all three modules: `:api`,
    `:client`, and `:server`. Its contents are pure Kotlin: data models
    ([domain objects](#domain-object)) and single-function interfaces
    ([domain interfaces](#domain-interface)). `domain` depends on no other axis, and every other
    axis depends on it. On the client, [Repositories](data.md#repository) implement the domain
    interfaces that [ViewModels](ui.md#view-model) consume; on the server, the
    [`services` axis](services.md) implements them.

    The `domain` package must only contain [domain interfaces](#domain-interface),
    [domain objects](#domain-object), [UseCases](#use-case),
    [domain exceptions](#domain-exception), [domain constants](#domain-constants),
    [domain extension functions](#domain-extension-function), and
    [domain extension properties](#domain-extension-property).

    The [Rules](#rules) below apply across the whole `feature.[name].domain` package.

    * **Note:** Cross-feature domain dependencies are permitted, because real-world domains depend
      on each other, but they should be kept to a minimum. Get the direction of each dependency
      right and avoid circular dependencies.
""")
object DomainLayer : RuleGroup(
    inPackage = "feature..domain..",
    constructs = listOf(
        DomainInterface,
        DomainObject,
        UseCase,
        DomainException,
        DomainConstants,
        DomainExtensionFunction,
        DomainExtensionProperty,
    ),
) {

    // §3.1 domain package dependencies (layer-level — not tied to one construct)
    @Describe("The `domain` layer must not contain platform-specific dependencies, such as Android, Ktor, or SQL")
    val noPlatformDeps by rule {
        rationale(
            """
            The domain layer stays pure Kotlin so it can be used in `:client`, `:server`, and every
            KMP target, and stays unit-testable. Expose a domain interface and implement it in
            `data` or `services` instead.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.packagee?.name?.contains(".domain") == true }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any { import ->
                        val name = import.name
                        name.startsWith("android.") || name.startsWith("androidx.") ||
                            name.startsWith("io.ktor.") || name.contains(".sql.") ||
                            name.contains("sqldelight") || name.contains("room")
                    }
                }
                .map { Violation(it.path, "domain file imports a platform-specific dependency") }
        }
    }

    @Describe("The `domain` layer must not depend on `ui`, `data`, or `services` packages within the feature")
    val noUiDataServicesDeps by rule {
        rationale(
            """
            The dependency graph is `ui → domain ← data`, with `services` depending on domain. Importing
            those into domain would invert the graph or create a cycle.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.packagee?.name?.contains(".domain") == true }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any {
                        it.name.containsPackageSegment("ui") ||
                            it.name.containsPackageSegment("data") ||
                            it.name.containsPackageSegment("services")
                    }
                }
                .map { Violation(it.path, "domain file imports a ui/data/services package") }
        }
    }

    @Describe("The `domain` layer may depend on another feature's `domain` only via that feature's `:api` module")
    val crossFeatureViaApi by rule {
        enforcedBy("ModuleRules.clientApiOnly", "ModuleRules.serverApiOnly")
    }
}

/** A type is domain-compatible if it (and its generics) are primitives, collections, platform, or domain types. */
internal fun isDomainCompatibleType(typeName: String, declaredIn: KoFileDeclaration): Boolean =
    validateTypeName(typeName, declaredIn) {
        it in primitiveTypeNames || it in collectionTypeNames || it.startsWith("platform.") || it.contains(".domain.")
    }
