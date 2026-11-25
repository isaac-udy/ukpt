package ukpt.coverage

internal fun CoverageManifest.checkInstrumentation(classification: Classification) {
    // Manifests from before package filtering used unrestricted instrumentation.
    if (instrumentedPackages.isEmpty()) return
    val configuredPatterns = (instrumentedPackages + excludedClasses).map(::classPattern)
    val directories = modules.map { it.second }.toSet()
    val sourceRoots = sourceDirsByVariant.values.flatten().distinct().map { "$it/" }
    val missing = classification.declarations.asSequence()
        .filter { it.production }
        .filterNot { declaration -> configuredPatterns.any { it.matches(declaration.name) } }
        .filter { it.path.substringBefore("/src/") in directories || sourceRoots.any(it.path::startsWith) }
        .groupBy { it.name.substringBeforeLast('.', "").substringBefore('.') }
        .toSortedMap()
    check(missing.isEmpty()) {
        missing.entries.joinToString("\n") { (root, declarations) ->
            val example = declarations.first()
            if (root.isEmpty()) {
                "Kover does not instrument the default package (`${example.path}`). Move the declaration into an instrumented package."
            } else {
                val pkg = declarations.map { it.name }.reduce { a, b -> a.commonPrefixWith(b) }.substringBeforeLast('.')
                "Kover does not instrument package `$pkg` (`${example.path}`). " +
                    "Add `instrumentedPackages(\"$pkg.*\")` to the root testCoverage { } block and rerun testCoverageReport."
            }
        }
    }
}

private fun classPattern(pattern: String) = buildString {
    for (char in pattern) {
        append(when (char) {
            '*' -> ".*"
            '?' -> "."
            '#' -> "[^.]*"
            else -> Regex.escape(char.toString())
        })
    }
}.toRegex()
