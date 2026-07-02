package architecture.testing

import architecture.registry.ArchitectureDefinition
import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.TestFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Meta-rule: the catalog checks itself. `Construct<Group>` declarations and each group's
 * `constructs` list are deliberately redundant — this scan of the catalog's own sources fails when
 * a declared construct isn't listed (it would be silently unenforced) or a declared group isn't in
 * the definition's `groups`.
 */
fun assertCatalogSourcesRegistered(definition: ArchitectureDefinition) {
    // Konsist resolves relative to the repo root, not the test working directory.
    val catalogPath = "${definition.docs.module}/${definition.docs.sourceRoot}/" +
        definition.javaClass.packageName.replace('.', '/')
    val scope = Konsist.scopeFromDirectory(catalogPath)
    val declaredConstructs = scope.objects()
        .filter { obj -> obj.parents().any { it.name.substringBefore('<') == "Construct" } }
        .map { "${it.packagee?.name}.${it.name}" }
        .toSet()
    val registeredConstructs = definition.groups
        .flatMap { group -> group.constructs.map { "${it.javaClass.packageName}.${it.javaClass.simpleName}" } }
        .toSet()
    assertEquals(
        declaredConstructs,
        registeredConstructs,
        "every top-level Construct object in the catalog must be listed in its RuleGroup's `constructs`",
    )

    val declaredGroups = scope.objects()
        .filter { obj -> obj.parents().any { it.name.substringBefore('<') == "RuleGroup" } }
        .map { it.name }
        .toSet()
    assertEquals(
        declaredGroups,
        definition.groups.map { it.id }.toSet(),
        "every RuleGroup object in the catalog must be listed in ${definition.name}'s groups",
    )
}

/**
 * The consumer's architecture test declares one `@TestFactory` per group so the groups hang
 * directly off the test class — which means a new group needs a new factory. This fails when one
 * is missing, so a group can't be registered in the catalog yet silently never run.
 */
fun assertEveryGroupHasATestFactory(definition: ArchitectureDefinition, testClass: KClass<*>) {
    val factoryNames = testClass.declaredFunctions
        .filter { it.findAnnotation<TestFactory>() != null }
        .map { it.name }
        .toSet()
    val missing = definition.groups
        .map { it.id.replaceFirstChar(Char::lowercase) }
        .filterNot { it in factoryNames }
    assertTrue(
        missing.isEmpty(),
        "${testClass.simpleName} needs a @TestFactory for: $missing (name it after the group, lowercase first letter)",
    )
}
