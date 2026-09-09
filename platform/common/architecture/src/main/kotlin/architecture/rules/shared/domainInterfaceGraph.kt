package architecture.rules.shared

import architecture.definitions.featureName
import architecture.definitions.isApiModule
import architecture.definitions.resolveTypeToken
import architecture.definitions.typeTokens
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
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
) {
    val hasUseCase: Boolean get() = providers.any { it == "${name}Impl" }
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
 * an unrelated type sharing the simple name does not.
 */
internal fun KoScope.domainInterfaceGraph(side: String): DomainInterfaceGraph {
    val interfaces = interfaces()
        .filter { isDomainInterfaceOnSide(it, side) }
        .mapNotNull { iface -> (iface as? KoFullyQualifiedNameProvider)?.fullyQualifiedName?.let { it to iface } }
    val fqns = interfaces.map { it.first }.toSet()

    val providers = mutableMapOf<String, MutableList<String>>()
    val consumers = mutableMapOf<String, MutableList<DomainInterfaceConsumer>>()

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
