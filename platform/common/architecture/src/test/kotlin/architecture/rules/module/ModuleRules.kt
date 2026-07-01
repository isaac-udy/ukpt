package architecture.rules.module

import architecture.registry.*


/**
 * §2 Gradle module dependency rules. These are module-graph rules: their input is the parsed
 * `build.gradle.kts` dependency edges, not the Konsist scope. The `// architecture-exception:`
 * comment channel is honoured via `ModuleEdge.exemptRuleIds`. No `inPackage`, so no exhaustiveness.
 */
object ModuleRules : RuleGroup() {

    val featureNotApp by rule("`:feature` modules must never depend on `:app` modules") {
        moduleGraph { graph, exempt ->
            graph.edges
                .filter { isApp(it.to) && isFeature(it.from) && !exempt(it) }
                .map { Violation(it.location, "forbidden :feature → :app dependency") }
        }
    }

    val featureMayUsePlatform by rule("`:feature` modules may depend on `:platform` modules") { guidance() }

    val clientApiOnly by rule("`:feature:[name]:client` must never depend on another `:client`/`:server` module") {
        rationale("A feature's client may only reach other features through their `:api` contract, or `:platform`.")
        moduleGraph { graph, exempt ->
            graph.edges
                .filter { featureSubmoduleType(it.from) == "client" }
                .filter { isFeature(it.to) && featureSubmoduleType(it.to) != "api" && !exempt(it) }
                .map { Violation(it.location, "feature :client may only depend on :api or :platform") }
        }
    }

    val clientMayUseApi by rule("`:feature:[name]:client` may depend on any `:feature:[name]:api` module") {
        enforcedBy(clientApiOnly)
    }

    val serverApiOnly by rule("`:feature:[name]:server` must never depend on another `:client`/`:server` module") {
        rationale("A feature's server may only reach other features through their `:api` contract, or `:platform`.")
        moduleGraph { graph, exempt ->
            graph.edges
                .filter { featureSubmoduleType(it.from) == "server" }
                .filter { isFeature(it.to) && featureSubmoduleType(it.to) != "api" && !exempt(it) }
                .map { Violation(it.location, "feature :server may only depend on :api or :platform") }
        }
    }

    val serverMayUseApi by rule("`:feature:[name]:server` may depend on any `:feature:[name]:api` module") {
        enforcedBy(serverApiOnly)
    }

    val apiMayUseApi by rule("`:feature:[name]:api` may depend on another feature's `:api` module to share models") { guidance() }
    val featuresMayBeGrouped by rule("`:feature` modules may be grouped (`:feature:[group]:[name]:…`)") { guidance() }

    val platformNotApp by rule("`:platform` modules must never depend on `:app` modules") {
        moduleGraph { graph, exempt ->
            graph.edges
                .filter { isPlatform(it.from) && isApp(it.to) && !exempt(it) }
                .map { Violation(it.location, "forbidden :platform → :app dependency") }
        }
    }

    val platformNotFeature by rule("`:platform` modules must never depend on `:feature` modules") {
        moduleGraph { graph, exempt ->
            graph.edges
                .filter { isPlatform(it.from) && isFeature(it.to) && !exempt(it) }
                .map { Violation(it.location, "forbidden :platform → :feature dependency") }
        }
    }

    val platformMayUsePlatform by rule("`:platform` modules may depend on other `:platform` modules") { guidance() }
}

private fun isApp(path: String) = path.startsWith(":app:") || path == ":app"
private fun isFeature(path: String) = path.startsWith(":feature:") || path == ":feature"
private fun isPlatform(path: String) = path.startsWith(":platform:") || path == ":platform"

/** `:feature:core:server` → "server"; null if [path] isn't a `:feature:…:{api,client,server}` module. */
private fun featureSubmoduleType(path: String): String? {
    if (!isFeature(path)) return null
    return path.removePrefix(":").split(":").lastOrNull()?.takeIf { it in setOf("api", "client", "server") }
}
