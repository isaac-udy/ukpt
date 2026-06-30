package architecture.definitions

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration

class ConstructDefinition private constructor(
    private val layerName: String?,
    private val constructName: String?,
    private val requirements: List<Pair<String, DefinitionPredicate<KoBaseDeclaration>>>,
) {
    fun evaluate(declaration: KoBaseDeclaration?): EvaluationResult {
        return EvaluationResult(
            definition = this,
            declaration = declaration,
        )
    }

    fun test(declaration: KoBaseDeclaration?): Boolean {
        return evaluate(declaration).isAllRequirementsMet
    }

    class EvaluationResult(
        private val definition: ConstructDefinition,
        private val declaration: KoBaseDeclaration?,
    ) {
        val layerName = definition.layerName ?: "UnknownLayer"
        val constructName = definition.constructName ?: "UnknownConstruct"
        val requirements = definition.requirements
            .associate { (requirementName, requirementPredicate) ->
                requirementName to requirementPredicate.test(declaration)
            }

        val countOfRequirements = requirements.size
        val countOfRequirementsMet = requirements.count { (_, isMet) -> isMet }

        /**
         * The percentage of requirements met as a double between 0 and 1, where 0 means none of
         * the requirements are met, and 1 means all of the requirements are met. To convert to a
         * String for display, such as "56%", you will need to multiply this number by 100.
         */
        val percentageOfRequirementsMet: Double = run {
            if (countOfRequirements == 0) return@run 1.0
            if (countOfRequirementsMet == 0) return@run 0.0
            return@run countOfRequirementsMet.toDouble()
                .div(countOfRequirements.toDouble())
        }

        val isAllRequirementsMet: Boolean
            get() = requirements.all { (_, isMet) -> isMet }
    }

    class Builder internal constructor(
        private val layerName: String?,
        private val constructName: String?,
    ) {
        private val rules = mutableListOf<Pair<String, DefinitionPredicate<KoBaseDeclaration>>>()

        fun rule(
            name: String,
            rule: DefinitionPredicate.Companion.() -> DefinitionPredicate<KoBaseDeclaration>,
        ) {
            rules.add(
                name to DefinitionPredicate.run(rule)
            )
        }

        fun build(): ConstructDefinition {
            return ConstructDefinition(
                layerName = layerName,
                constructName = constructName,
                requirements = rules.toList(),
            )
        }
    }

    companion object {
        fun define(
            constructName: String?,
            block: Builder.() -> Unit
        ): ConstructDefinition {
            return Builder(
                layerName = null,
                constructName = constructName,
            )
                .apply(block)
                .build()
        }

        fun createDebugMessage(
            declaration: KoBaseDeclaration,
            evaluations: List<EvaluationResult>,
        ): String {
            val matched = evaluations.count { it.isAllRequirementsMet }
            val layerNames = evaluations
                .map { it.layerName }
                .toSet()
                .let {
                    when (it.size) {
                        1 -> it.first()
                        else -> it.joinToString(
                            prefix = "[",
                            separator = ", ",
                            postfix = "]",
                        )
                    }
                }
            return buildString {
                when(matched) {
                    1 -> {
                        val match = evaluations.first { it.isAllRequirementsMet }
                        appendLine("${declaration.toDebugString()} is a ${match.constructName} in ${match.layerName}")
                    }
                    0 -> {
                        val partialMatches = evaluations
                            .filter { it.percentageOfRequirementsMet > 0.0 }

                        when (partialMatches.size) {
                            0 -> {
                                appendLine("${declaration.toDebugString()} does not match any ConstructDefinitions in $layerNames")
                            }
                            else -> {
                                val bestMatchPercent = partialMatches.maxOf { it.percentageOfRequirementsMet }
                                // We're going to mark anything within 15% points of the best match as
                                // a potential match, so we can print out the suggestions/requirements
                                val potentialMatches = partialMatches.filter {
                                    (bestMatchPercent - it.percentageOfRequirementsMet) < 0.15
                                }
                                appendLine("${declaration.toDebugString()} does not match any ConstructDefinitions in $layerNames, but has potential matches:")
                                potentialMatches.forEach { potentialMatch ->
                                    appendLine("    ${potentialMatch.layerName}.${potentialMatch.constructName} (${(potentialMatch.percentageOfRequirementsMet * 100).toInt()}%)")
                                    potentialMatch.requirements
                                        .toList()
                                        .joinToString(separator = "\n") { (name, matches) ->
                                            when (matches) {
                                                true  -> "[✓] $name"
                                                false -> "[ ] $name"
                                            }
                                        }
                                        .prependIndent("        ")
                                        .also { appendLine(it) }
                                }
                            }
                        }
                    }
                    else -> {
                        val matches = evaluations.filter { it.isAllRequirementsMet }
                        appendLine("${declaration.toDebugString()} matches multiple ConstructDefinitions in $layerNames: ${matches.joinToString { it.constructName }}")
                    }
                }
            }
        }
    }
}

fun ConstructDefinition.asDefinitionPredicate(): DefinitionPredicate<KoBaseDeclaration> {
    return DefinitionPredicate.any { declaration ->
        this@asDefinitionPredicate.test(declaration)
    }
}
