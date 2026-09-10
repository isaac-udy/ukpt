package architecture.rules.shared

import architecture.definitions.containingFilePackage
import architecture.definitions.featureName
import architecture.definitions.isApiModule
import architecture.definitions.isFeatureModule
import architecture.definitions.resolveTypeToken
import architecture.definitions.typeTokens
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider

/**
 * A class whose primary constructor takes a domain interface. Test sources are outside the scope
 * and a DI module binds through a property, not a constructor, so neither appears here.
 */
internal data class DomainInterfaceConsumer(
    val className: String,
    val fqn: String,
    val feature: String,
)

/** One classified domain interface on a side, with the classes that provide and consume it. */
internal data class DomainInterfaceNode(
    val fqn: String,
    val name: String,
    val feature: String,
    val published: Boolean,
    val providers: List<String>,
    val consumers: List<DomainInterfaceConsumer>,
    /** Files resolving this interface out of the Koin container — see [koinResolutionSite]. */
    val resolutionSites: List<String>,
) {
    val hasUseCase: Boolean get() = providers.any { it == "${name}Impl" }

    /**
     * Constructor consumers plus Koin resolution sites: every production reference that takes the
     * interface to call it. The grouped audits count [consumers] alone, because what they report —
     * interfaces injected together into one class — is a property of constructor injection.
     */
    val consumerCount: Int get() = consumers.size + resolutionSites.size
}

internal class DomainInterfaceGraph(
    val side: String,
    val nodes: List<DomainInterfaceNode>,
) {
    fun byFeature(): Map<String, List<DomainInterfaceNode>> = nodes.groupBy { it.feature }
}

private val adapterSuffixes = listOf("Repository", "Client", "Provider")

/**
 * The provider/consumer graph over every domain interface classified on [side]. Providers are
 * resolved the way the provided-by rules resolve them: a class whose parent resolves to the
 * interface (a UseCase, or an adapter implementing it directly), or an adapter-suffixed class with
 * a property whose declaration head resolves to it. Consumers are resolved through each parameter
 * type's tokens and the file's imports, so an alias or a wrapper such as `Lazy<…>` still counts and
 * an unrelated type sharing the simple name does not. Files resolving an interface out of the Koin
 * container are collected separately, as [DomainInterfaceNode.resolutionSites].
 */
internal fun KoScope.domainInterfaceGraph(side: String): DomainInterfaceGraph {
    val interfaces = interfaces()
        .filter { isDomainInterfaceOnSide(it, side) }
        .mapNotNull { iface -> (iface as? KoFullyQualifiedNameProvider)?.fullyQualifiedName?.let { it to iface } }
    val fqns = interfaces.map { it.first }.toSet()

    val providers = mutableMapOf<String, MutableList<String>>()
    val consumers = mutableMapOf<String, MutableList<DomainInterfaceConsumer>>()
    val resolutionSites = mutableMapOf<String, MutableList<String>>()

    files.forEach { file ->
        koinResolvedInterfaces(file, fqns).forEach { fqn ->
            resolutionSites.getOrPut(fqn) { mutableListOf() } += file.name
        }
    }

    classes().forEach { cls ->
        val file = cls.containingFile
        cls.parents()
            .mapNotNull { file.resolveTypeToken(it.name) }
            .filter { it in fqns }
            .forEach { providers.getOrPut(it) { mutableListOf() } += cls.name }
        if (adapterSuffixes.any { cls.name.endsWith(it) }) {
            cls.properties()
                .flatMap { prop -> typeTokens(prop.text.substringBefore('{')).mapNotNull { file.resolveTypeToken(it) } }
                .filter { it in fqns }
                .distinct()
                .forEach { providers.getOrPut(it) { mutableListOf() } += cls.name }
        }
        consumedInterfaces(cls, fqns).forEach { fqn ->
            consumers.getOrPut(fqn) { mutableListOf() } += DomainInterfaceConsumer(
                className = cls.name,
                fqn = (cls as? KoFullyQualifiedNameProvider)?.fullyQualifiedName ?: cls.name,
                feature = cls.featureName(),
            )
        }
    }

    val nodes = interfaces.map { (fqn, iface) ->
        DomainInterfaceNode(
            fqn = fqn,
            name = iface.name,
            feature = iface.featureName(),
            published = iface.isApiModule(),
            providers = providers[fqn].orEmpty().distinct(),
            consumers = consumers[fqn].orEmpty().distinctBy { it.fqn },
            resolutionSites = resolutionSites[fqn].orEmpty().distinct(),
        )
    }
    return DomainInterfaceGraph(side, nodes)
}

/** The domain-interface FQNs among [cls]'s primary-constructor parameter types. */
internal fun consumedInterfaces(cls: KoClassDeclaration, fqns: Set<String>): Set<String> {
    val file = cls.containingFile
    return cls.primaryConstructor?.parameters.orEmpty()
        .flatMap { param -> typeTokens(param.type.name).mapNotNull { file.resolveTypeToken(it) } }
        .filter { it in fqns }
        .toSet()
}

/**
 * The three Koin resolution call shapes, written as a type argument: `get<T>(`, `inject<T>(`, and
 * Compose's `koinInject<T>(`. The lookbehind admits a receiver dot (`koin.get<T>()`,
 * `getKoin().get<T>()`) while rejecting a longer identifier that merely ends in the same letters.
 *
 * A bare reference to the type is deliberately not matched. A Koin module writes its binding as
 * `single { get<AuthRepository>().flowOfAccessToken } bind FlowOfAccessToken::class`, where
 * `FlowOfAccessToken::class` names what is being *provided*; counting it would make every bound
 * interface look consumed. The `get<AuthRepository>()` on the same line is a resolution and does
 * count — against `AuthRepository`, not against the interface the line binds.
 */
private val koinResolutionSite = Regex("""(?<![A-Za-z0-9_])(?:koinInject|inject|get)<([A-Za-z_][A-Za-z0-9_.]*)>\s*\(""")

/**
 * The domain-interface FQNs [file] resolves out of the Koin container. A resolution site is a
 * consumer without a constructor — an app module's startup wiring, a Compose entry point, a
 * `single { … }` body — so the type token is resolved through the file's imports exactly as
 * [consumedInterfaces] resolves a parameter type.
 */
private fun koinResolvedInterfaces(file: KoFileDeclaration, fqns: Set<String>): Set<String> =
    koinResolutionSite.findAll(file.text)
        .mapNotNull { file.resolveTypeToken(it.groupValues[1]) }
        .filter { it in fqns }
        .toSet()

/**
 * The domain models visible to [side]'s domain layer: data, sealed, enum, and value declarations
 * in that layer's package or in a feature root (a package under `feature.<name>` that names
 * neither `client` nor `server`).
 */
internal fun KoScope.domainModelsOnSide(side: String): List<KoBaseDeclaration> =
    declarations(includeNested = false)
        .filter { it.isFeatureModule() && it.isModelShape() }
        .filter { decl ->
            val pkg = decl.containingFilePackage()
            pkg.contains(".$side.domain") || (!pkg.contains(".client") && !pkg.contains(".server"))
        }

private fun KoBaseDeclaration.isModelShape(): Boolean = when (this) {
    is KoClassDeclaration -> hasDataModifier || hasSealedModifier || hasEnumModifier || hasValueModifier
    is KoInterfaceDeclaration -> hasSealedModifier
    else -> false
}
