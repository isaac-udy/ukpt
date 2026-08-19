package architecture.rules.feature

import architecture.definitions.isApiModule
import architecture.definitions.isFeatureRootPackage

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.isMutable

@Describe("""
    An `object` declaration whose only members are `val` constants. It holds the feature's magic
    numbers, lookup tables, and named tags — values both the client and server need to agree on.

    * **Note:** A constants object is the right home for values such as `val MAX_PARTY_SIZE = 6`
      or a lookup table. Anything with behaviour belongs on a
      [shared domain model](#shared-domain-model) as a member or extension.
""")
object SharedConstants : Construct<FeatureRules>(
    requirements = listOf(
        predicate("resides in the feature root package `feature.[name]`") { it.isFeatureRootPackage() },
        predicate("is declared in the feature's `:api` module") { it.isApiModule() },
        isObjectWhere("is an `object` with only `val` properties and no functions") { decl ->
            decl.functions().isEmpty() && decl.properties().all { it.isVal && !it.isMutable() }
        },
    ),
)
