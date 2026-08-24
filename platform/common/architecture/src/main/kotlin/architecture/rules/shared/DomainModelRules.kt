package architecture.rules.shared

import architecture.definitions.isMutable
import architecture.definitions.typeExpressionResolvesTo
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import dev.isaacudy.udytils.architecture.*

/** The domain model rules, shared by both the client and server domain groups. */
abstract class DomainModelRules<G : RuleGroup>(
    private val side: String,
) : Construct<G>(
    requirements = listOf(
        isClassOrInterface,
        oneOf(isSealed, isDataClass, isEnum, isValueClass),
    ),
) {
    @Describe("A domain model must be immutable — no `var` properties")
    val immutable by rule {
        rationale("Mutable state in the domain layer makes results depend on the order of earlier calls, and the layer untestable in isolation.")
        constrain { decl, _ ->
            // The construct classifies interfaces too (a sealed interface hierarchy is a model),
            // so both declaration kinds are checked.
            val props = when (decl) {
                is KoClassDeclaration -> decl.properties()
                is KoInterfaceDeclaration -> decl.properties()
                else -> emptyList()
            }
            props
                .filter { it.isMutable() }
                .map { Violation(it, "domain model has a mutable (`var`) property") }
        }
    }

    @Describe("A domain model must not hold a domain interface: no property or constructor parameter whose type is a domain interface of the same client or server `domain` layer, bare, nullable, or inside a wrapper such as `Lazy<…>` or `List<…>`")
    val noDomainInterfaceProperties by rule {
        rationale("A domain model is data; a property typed as a domain interface is a dependency that is supplied at the model's construction site rather than injected, so Koin's startup validation does not see it and the class that consumes the model has a dependency its constructor does not declare.")
        note("A property type is resolved through its file's imports and matched against the $side's classified domain interfaces by fully-qualified name.")
        scope { scope, exempt ->
            val domainInterfaces = scope.domainInterfaceFqnsOnSide(side)
            val classViolations = scope.classes()
                .filter { test(it) }
                .filterNot { exempt(it) }
                .flatMap { cls ->
                    val file = cls.containingFile
                    val message = { typeName: String -> "domain model holds domain interface `$typeName` — a model is data; inject the interface where it is used" }
                    val interfaceProperties = cls.properties()
                        .filter { file.typeExpressionResolvesTo(it.type?.name.orEmpty(), domainInterfaces) }
                    // A `val` constructor parameter is also a property; report it once.
                    val propertyNames = interfaceProperties.map { it.name }.toSet()
                    val paramViolations = cls.primaryConstructor?.parameters.orEmpty()
                        .filterNot { it.name in propertyNames }
                        .filter { file.typeExpressionResolvesTo(it.type.name, domainInterfaces) }
                        .map { Violation(cls, message(it.type.name)) }
                    interfaceProperties.map { Violation(cls, message(it.type?.name.orEmpty())) } + paramViolations
                }
            val interfaceViolations = scope.interfaces()
                .filter { test(it) }
                .filterNot { exempt(it) }
                .flatMap { iface ->
                    val file = iface.containingFile
                    iface.properties()
                        .filter { file.typeExpressionResolvesTo(it.type?.name.orEmpty(), domainInterfaces) }
                        .map { Violation(iface, "domain model holds domain interface `${it.type?.name}` — a model is data; inject the interface where it is used") }
                }
            classViolations + interfaceViolations
        }
    }

    @Describe("A domain model that needs to cross the network belongs in the feature root instead")
    val notForWire by rule {
        note("Crossing the network is the test, not carrying `@Serializable`: a payload a StorageClass writes into a column, or a state a client restores after a process death, is serialized and still private to the client or server that owns it.")
        note("Persistence is `server.data`'s concern: a model that is stored but not shared is mapped to a [storage record](serverdata.md#storage-record) there, not promoted to the root.")
        unverifiable()
    }

    @Describe("A domain model must not re-implement a concept a shared domain model already defines; use or compose the shared model instead")
    val composesSharedDomainModels by rule {
        note("The feature's vocabulary has one source of truth in the root; a private copy of a concept drifts from it as both change.")
        unverifiable()
    }
}
