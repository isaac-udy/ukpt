package architecture.registry

import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule
import architecture.definitions.isKotlinxSerializable
import architecture.definitions.isMutable
import architecture.definitions.primitiveTypeNames
import architecture.utils.collectionTypeNames
import architecture.utils.validateTypeName
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration

/**
 * The `domain` layer (§3.1, §4.1) in the object style. Each construct's requirements (the
 * `🔶 construct` classification) are the predicate list in its `Construct(...)` header; its rules
 * ("what it must do") are `val x by rule(...)` in the body. Layer-level package rules live on the
 * group. Rule ids are the exact object/property names, e.g. `DomainLayer.UseCase.noOverridingDefaults`.
 */
object DomainLayer : RuleGroup(inPackage = "feature..domain..") {

    // §4.1.1 Domain Interfaces
    object DomainInterface : Construct(
        isInterfaceWhere("Domain interfaces must be a `fun interface`") { it.hasFunModifier && !it.hasSealedModifier },
        isInterfaceWhere("The primary function of a domain interface must be an `operator fun invoke`") { decl ->
            decl.functions().any { it.name == "invoke" && it.hasOperatorModifier }
        },
        isInterfaceWhere("All functions in a domain interface must be `suspend` or return a `Flow<T>`") { decl ->
            decl.functions()
                .filter { it.name == "invoke" || !it.text.contains("=") }
                .all { it.hasSuspendModifier || it.returnType?.name?.contains("Flow") == true }
        },
        isInterfaceWhere("Flow-returning domain interfaces are prefixed with `FlowOf`") { decl ->
            val hasFlowReturn = decl.functions().any { it.name == "invoke" && it.returnType?.name?.contains("Flow") == true }
            !hasFlowReturn || decl.name.startsWith("FlowOf")
        },
    ) {
        val interfaceDefaults by rule("May define additional default functions that call the primary function") { guidance() }
        val primaryParameterTypes by rule("Primary-function parameters must be domain objects, nested types, primitives, or collections of those") { guidance() }
        val primaryReturnType by rule("Primary-function return type must be domain objects, nested types, primitives, collections of those, or no value") { guidance() }
        val implementedByRepositoryOrUseCase by rule("Must be implemented by a Repository (as a property) or by a UseCase") { guidance() }

        val errorsViaExceptions by rule("Functions propagate errors via thrown exceptions, never via the return type") {
            rationale(
                """
                @Throws on suspend functions must include CancellationException (or a superclass like
                Exception) — required for Kotlin/Native: kotlinc rejects the function on iOS targets otherwise.
                """.trimIndent(),
            )
            note("Known exceptions should be their own type extending RuntimeException, marked with `@Throws`.")
            note("`@Throws` on `suspend` functions must include `kotlin.coroutines.cancellation.CancellationException`.")
            constrain { decl, _ ->
                val iface = decl as? KoInterfaceDeclaration ?: return@constrain emptyList()
                iface.functions()
                    .filter { it.hasSuspendModifier }
                    .filter { fn -> fn.hasAnnotation { it.name == "Throws" } }
                    .filterNot { fn ->
                        val text = fn.annotations.first { it.name == "Throws" }.text
                        text.contains("CancellationException::class") ||
                            Regex("""(?<!\w)Exception::class""").containsMatchIn(text)
                    }
                    .map { Violation(it, "@Throws on a suspend function must include CancellationException") }
            }
        }
    }

    // §4.1.2 Domain Objects
    object DomainObject : Construct(
        isClassOrInterface,
        oneOf(isSealed, isDataClass, isEnum, isValueClass),
        predicate("Domain objects must be immutable (val properties only)") { d ->
            when (d) {
                is KoClassDeclaration -> d.properties().none { it.isMutable() }
                is KoInterfaceDeclaration -> d.properties().none { it.isMutable() }
                else -> false
            }
        },
        predicate("Domain objects must be annotated with `@Serializable`") { it.isKotlinxSerializable() },
    ) {
        val nestedValueClassIds by rule("Should use nested value classes for identifiers where appropriate") { guidance() }
        val sealedHierarchies by rule("Should use sealed interface hierarchies to model polymorphic data where appropriate") { guidance() }
        val invariantInitBlocks by rule("Should include `init` blocks that enforce invariants") { guidance() }
        val nestedTypes by rule("Should use nested types when conceptually inseparable from the parent") { guidance() }
    }

    // §4.1.3 UseCases
    object UseCase : Construct(
        isClassWhere("A UseCase is a non-sealed/data/enum/value class named `[DomainInterface]Impl`") { decl ->
            !decl.hasSealedModifier && !decl.hasDataModifier && !decl.hasEnumModifier && !decl.hasValueModifier &&
                decl.name == "${decl.associatedDomainInterfaceName()}Impl"
        },
        isClassWhere("A UseCase must implement exactly one domain interface") { it.associatedDomainInterfaceName() != null },
        isClassWhere("A UseCase must not contain mutable state — all properties are `val`") { decl ->
            decl.properties().all { !it.isMutable() }
        },
    ) {
        val noOverridingDefaults by rule("Must not override any default function of its domain interface") {
            rationale(
                """
                The only abstract member is the primary `operator fun invoke`; every other function is a
                default. Overriding a default per-implementation defeats the point of the interface helpers.
                """.trimIndent(),
            )
            constrain { decl, _ ->
                val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
                cls.functions()
                    .filter { it.hasOverrideModifier }
                    .filterNot { it.name == "invoke" }
                    .map { Violation(it, "UseCase overrides a default interface function") }
            }
        }

        val mayInjectDomainInterfaces by rule("May inject domain interfaces to perform its logic") { guidance() }
        val breakDownComplexUseCases by rule("If it becomes too complex, break it into private/file-private/nested parts") { guidance() }
    }

    // §4.1 Domain exceptions, constants, extensions
    object DomainException : Construct(
        isClassWhere("A domain exception is a class extending RuntimeException/Exception/PresentableException") { decl ->
            decl.parents().any { it.name == "RuntimeException" || it.name == "Exception" || it.name == "PresentableException" }
        },
    )

    object DomainConstants : Construct(
        isObjectWhere("Domain constants are an `object` with only `val` properties and no functions") { decl ->
            decl.functions().isEmpty() && decl.properties().all { it.isVal && !it.isMutable() }
        },
    )

    object DomainExtensionFunction : Construct(
        isFunctionWhere("Receiver/return/parameter types are domain objects, primitives, or collections of those") { decl ->
            val receiverOk = decl.receiverType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            val returnOk = decl.returnType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            val parametersOk = decl.parameters.all { isDomainCompatibleType(it.type.name, decl.containingFile) }
            receiverOk && returnOk && parametersOk
        },
    ) {
        val noPlatformDeps by rule("Domain extension functions must not introduce platform-specific dependencies") { guidance() }
    }

    object DomainExtensionProperty : Construct(
        isPropertyWhere("Receiver/type is a domain object, primitive, or collection of those") { decl ->
            val receiverOk = decl.receiverType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            val typeOk = decl.type?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
            receiverOk && typeOk
        },
    )

    // §3.1 domain package dependencies (layer-level — not tied to one construct)
    val noPlatformDeps by rule("Domain must not contain platform-specific dependencies (Android, Ktor, SQL, …)") {
        rationale(
            """
            The domain layer stays pure Kotlin so it ports across :client/:server and every KMP target
            and stays unit-testable. Expose a domain interface and implement it in `data`/`services`.
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

    val noUiDataServicesDeps by rule("Domain must not depend on `ui`, `data`, or `services` packages within the feature") {
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

    val crossFeatureViaApi by rule("May depend on another feature's `domain` only via that feature's `:api` module") {
        enforcedBy("ModuleRules.clientApiOnly", "ModuleRules.serverApiOnly")
    }
}

/** The single domain interface a UseCase class implements, or null if it isn't exactly one. */
private fun KoClassDeclaration.associatedDomainInterfaceName(): String? {
    val parents = this.parents()
    if (parents.size != 1) return null
    val parent = parents.single()
    return if (DomainLayer.DomainInterface.test(parent)) parent.name else null
}

/** A type is domain-compatible if it (and its generics) are primitives, collections, platform, or domain types. */
private fun isDomainCompatibleType(typeName: String, declaredIn: KoFileDeclaration): Boolean =
    validateTypeName(typeName, declaredIn) {
        it in primitiveTypeNames || it in collectionTypeNames || it.startsWith("platform.") || it.contains(".domain.")
    }
