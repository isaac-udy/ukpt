package architecture.definitions

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

// Order matters throughout withoutStringsAndComments: character literals before strings (a '"'
// literal would otherwise open a phantom string), strings before comments (a `//` inside a URL
// literal is not a comment), and block comments peeled innermost-first because Kotlin nests them.
private val characterLiteral = Regex("""'(?:\\.|[^'\\])'""")
private val rawString = Regex("\"\"\"" + """.*?""" + "\"\"\"", RegexOption.DOT_MATCHES_ALL)
private val quotedString = Regex("\"(?:\\\\.|[^\"\\\\])*\"")
private val templateExpression = Regex("""\$\{[^{}]*\}""")

/** A block comment containing no nested opener — the innermost one, removed repeatedly. */
private val innermostBlockComment = Regex("""/\*(?:(?!/\*).)*?\*/""", RegexOption.DOT_MATCHES_ALL)
private val lineComment = Regex("""//[^\n]*""")

/**
 * The file's text with string literals and comments blanked, so a fully-qualified name cited in a
 * KDoc, a `//` note, or a log message is not mistaken for a code reference. String-template
 * expressions are kept — `"${'$'}{Foo.bar()}"` executes `Foo.bar()`, so the reference inside the
 * braces is live code, not prose.
 */
fun String.withoutStringsAndComments(): String {
    var text = replace(characterLiteral, "' '")
        .replace(rawString) { match -> templateExpression.findAll(match.value).joinToString(" ") { it.value } }
        .replace(quotedString) { match -> templateExpression.findAll(match.value).joinToString(" ") { it.value } }
    while (true) {
        val peeled = text.replace(innermostBlockComment, "")
        if (peeled == text) break
        text = peeled
    }
    return text.replace(lineComment, "")
}

/**
 * The file's body with strings, comments, the package declaration, and every import statement
 * blanked — what remains is code, so a fully-qualified reference found in it is a real use site
 * rather than the import that merely names it.
 */
fun KoFileDeclaration.codeBodyText(): String =
    imports
        .fold(text.withoutStringsAndComments()) { stripped, import -> stripped.replace(import.text, "") }
        .let { stripped -> packagee?.text?.let { stripped.replace(it, "") } ?: stripped }
