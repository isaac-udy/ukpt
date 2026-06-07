package architecture.utils

class NameWithGenerics private constructor(
    val baseName: String,
    val generics: List<String>,
) {
    companion object {
        fun from(input: String): NameWithGenerics {
            val baseName = input.substringBefore("<")
            val generics = getTopLevelGenerics(input)
            return NameWithGenerics(
                baseName = baseName,
                generics = generics,
            )
        }
    }
}

private fun getTopLevelGenerics(input: String): List<String> {
    // Extract the content between the first '<' and the last '>'
    val startIndex = input.indexOf('<')
    val endIndex = input.lastIndexOf('>')

    if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
        return emptyList()
    }

    val innerContent = input.substring(startIndex + 1, endIndex)
    val results = mutableListOf<String>()
    val currentPart = StringBuilder()
    var depth = 0

    for (char in innerContent) {
        when (char) {
            '<' -> {
                depth++
                currentPart.append(char)
            }
            '>' -> {
                depth--
                currentPart.append(char)
            }
            ',' -> {
                if (depth == 0) {
                    // We found a top-level comma, save the current part
                    results.add(currentPart.toString().trim())
                    currentPart.clear()
                } else {
                    currentPart.append(char)
                }
            }
            else -> currentPart.append(char)
        }
    }

    // Add the final remaining part
    if (currentPart.isNotEmpty()) {
        results.add(currentPart.toString().trim())
    }

    return results
}