package architecture.rules.domain

import architecture.registry.*

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

@Describe("""
    The `domain` axis is the deepest layer of a feature and appears in all three modules — `:api`,
    `:client`, and `:server`. Its contents are pure Kotlin: data models
    ([domain objects](#domain-object)) and single-function interfaces, sometimes called Interactors
    ([domain interfaces](#domain-interface)). `domain` is the centre of gravity on both sides of the
    wire: it depends on no other axis, and every other axis depends on it — on the client,
    [Repositories](data.md#repository) implement the domain interfaces that
    [ViewModels](ui.md#view-model) consume; on the server, the [`services` axis](services.md)
    implements them.

    The `domain` package must only contain [domain interfaces](#domain-interface),
    [domain objects](#domain-object), [UseCases](#use-case),
    [domain exceptions](#domain-exception), [domain constants](#domain-constants),
    [domain extension functions](#domain-extension-function), and
    [domain extension properties](#domain-extension-property).

    The [Rules](#rules) below apply across the whole `feature.[name].domain` package.

    * **Note**: Cross-feature domain dependencies should be minimised where possible, but are
      permitted because real-world domains have genuine dependencies between them. The important
      thing is getting the direction of dependencies correct and avoiding circular dependencies.
""")
object DomainLayer : RuleGroup(inPackage = "feature..domain..") {

    @Describe("""
        A functional interface representing domain-level functionality/business logic.

        * **Note**: Default functions don't need to be `operator fun invoke` and should use
          expressive names; they should provide commonly used functionality (e.g. handling a
          particular exception type) or simplify calling the primary function with particular
          parameters.
        * **Note**: Implementations must never override an interface's default functions;
          convenience functions belong as default members, not top-level extensions, so they're
          discoverable and co-located with the interface.
        * **Note**: Generic/unknown errors don't need their own exception type or `@Throws` entry.
    """)
    object DomainInterface : Construct(
        requirements = listOf(
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
        ),
    ) {
        @Describe("May define additional default functions that call the primary function")
        val interfaceDefaults by guidance
        @Describe("Primary-function parameters must be domain objects, nested types, primitives, or collections of those")
        val primaryParameterTypes by guidance
        @Describe("Primary-function return type must be domain objects, nested types, primitives, collections of those, or no value")
        val primaryReturnType by guidance
        @Describe("Must be implemented by a Repository (as a property) or by a UseCase")
        val implementedByRepositoryOrUseCase by guidance

        @Describe("Functions propagate errors via thrown exceptions, never via the return type")
        val errorsViaExceptions by rule {
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

    @Describe("""
        An immutable type representing data at the domain-level.

        * **Note**: Nested types (enums, value classes, sealed interfaces/classes) belong nested
          only when conceptually inseparable from the parent — like `User.Id` or
          `Transport.Car.FuelType` in the examples below; otherwise model them as their own domain
          objects.
    """)
    object DomainObject : Construct(
        requirements = listOf(
            isClassOrInterface,
            oneOf(isSealed, isDataClass, isEnum, isValueClass),
            predicate("Domain objects must be annotated with `@Serializable`") { it.isKotlinxSerializable() },
        ),
    ) {
        @Describe("Domain objects must be immutable (val properties only)")
        val immutable by rule {
            constrain { decl, _ ->
                val props = when (decl) {
                    is KoClassDeclaration -> decl.properties()
                    is KoInterfaceDeclaration -> decl.properties()
                    else -> return@constrain emptyList()
                }
                props.filter { it.isMutable() }.map { Violation(it, "Domain object has a mutable (`var`) property — domain objects must be immutable") }
            }
        }

        @Describe("Should use nested value classes for identifiers where appropriate")
        val nestedValueClassIds by guidance
        @Describe("Should use sealed interface hierarchies to model polymorphic data where appropriate")
        val sealedHierarchies by guidance
        @Describe("Should include `init` blocks that enforce invariants")
        val invariantInitBlocks by guidance
        @Describe("Should use nested types when conceptually inseparable from the parent")
        val nestedTypes by guidance
    }

    @Describe("""
        A class that implements a single [domain interface](#domain-interface).

        * **Note**: Immutable helper properties (e.g., loggers) are permitted — "no mutable state"
          forbids `var` properties, not properties in general.
        * **Note**: If a UseCase only injects a single other domain interface, consider whether
          that logic should become a default function of the other domain interface instead.
        * **Note**: When breaking down a complex UseCase, reach for file-private extension
          functions, private functions, or nested classes — not additional domain
          interfaces/UseCases that pollute the namespace.
    """)
    object UseCase : Construct(
        requirements = listOf(
            isClassWhere("A UseCase is a non-sealed/data/enum/value class named `[DomainInterface]Impl`") { decl ->
                !decl.hasSealedModifier && !decl.hasDataModifier && !decl.hasEnumModifier && !decl.hasValueModifier &&
                    decl.name == "${decl.associatedDomainInterfaceName()}Impl"
            },
            isClassWhere("A UseCase must implement exactly one domain interface") { it.associatedDomainInterfaceName() != null },
        ),
    ) {
        @Describe("A UseCase must not contain mutable state — all properties are `val`")
        val noMutableState by rule {
            constrain { decl, _ ->
                val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
                cls.properties().filter { it.isMutable() }.map { Violation(it, "UseCase has a mutable (`var`) property — all UseCase properties must be `val`") }
            }
        }

        @Describe("Must not override any default function of its domain interface")
        val noOverridingDefaults by rule {
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

        @Describe("May inject domain interfaces to perform its logic")
        val mayInjectDomainInterfaces by guidance
        @Describe("If it becomes too complex, break it into private/file-private/nested parts")
        val breakDownComplexUseCases by guidance
    }

    @Describe("""
        A class that represents a known failure mode raised by a domain interface.

        * **Note**: Domain exceptions live at the top of the `domain` package when shared between
          multiple domain interfaces, or as a nested class on the
          [domain interface](#domain-interface) that throws them; they must be listed in `@Throws`
          on the throwing interface's primary function.
    """)
    object DomainException : Construct(
        requirements = listOf(
            isClassWhere("A domain exception is a class extending RuntimeException/Exception/PresentableException") { decl ->
                decl.parents().any { it.name == "RuntimeException" || it.name == "Exception" || it.name == "PresentableException" }
            },
        ),
    )

    @Describe("""
        An `object` declaration whose only members are `val` constants — used to anchor
        domain-level magic numbers, lookup tables, or named tags.

        * **Note**: A constants object is the right home for things like `val MAX_PARTY_SIZE = 6`
          or a sealed-but-keyed lookup table. Anything that wants behaviour belongs on a domain
          object as a member or extension.
    """)
    object DomainConstants : Construct(
        requirements = listOf(
            isObjectWhere("Domain constants are an `object` with only `val` properties and no functions") { decl ->
                decl.functions().isEmpty() && decl.properties().all { it.isVal && !it.isMutable() }
            },
        ),
    )

    @Describe("""
        A top-level extension function on a domain object that adds derived or convenience
        behavior.

        * **Note**: Prefer default member functions on [domain interfaces](#domain-interface) for
          domain-interface convenience logic. Extension functions are appropriate for adding
          behavior to domain objects (e.g., `CampaignRole.permissions()`).
    """)
    object DomainExtensionFunction : Construct(
        requirements = listOf(
            isFunctionWhere("Receiver/return/parameter types are domain objects, primitives, or collections of those") { decl ->
                val receiverOk = decl.receiverType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
                val returnOk = decl.returnType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
                val parametersOk = decl.parameters.all { isDomainCompatibleType(it.type.name, decl.containingFile) }
                receiverOk && returnOk && parametersOk
            },
        ),
    ) {
        @Describe("Domain extension functions must not introduce platform-specific dependencies")
        val noPlatformDeps by guidance
    }

    @Describe("""
        A top-level extension property on a domain object that exposes derived state.

        * **Note**: Same constraints as [domain extension functions](#domain-extension-function).
          Prefer a property when the value is a pure projection of the receiver and is cheap to
          compute on every read.
    """)
    object DomainExtensionProperty : Construct(
        requirements = listOf(
            isPropertyWhere("Receiver/type is a domain object, primitive, or collection of those") { decl ->
                val receiverOk = decl.receiverType?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
                val typeOk = decl.type?.let { isDomainCompatibleType(it.name, decl.containingFile) } ?: true
                receiverOk && typeOk
            },
        ),
    )

    // §3.1 domain package dependencies (layer-level — not tied to one construct)
    @Describe("Domain must not contain platform-specific dependencies (Android, Ktor, SQL, …)")
    val noPlatformDeps by rule {
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

    @Describe("Domain must not depend on `ui`, `data`, or `services` packages within the feature")
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

    @Describe("May depend on another feature's `domain` only via that feature's `:api` module")
    val crossFeatureViaApi by rule {
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
