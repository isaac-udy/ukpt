package architecture.definitions

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/**
 * The file's text with string literals and comments blanked, so a fully-qualified name cited in a
 * KDoc, a `//` note, or a log message is not mistaken for a code reference. Strings go first —
 * a `//` inside a URL literal is not a comment — then block and line comments.
 */
fun String.withoutStringsAndComments(): String =
    replace(Regex('"'.toString().repeat(3) + ".*?" + '"'.toString().repeat(3), RegexOption.DOT_MATCHES_ALL), "\"\"")
        .replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), "\"\"")
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

/**
 * The file's body with strings, comments, the package declaration, and every import statement
 * blanked — what remains is code, so a fully-qualified reference found in it is a real use site
 * rather than the import that merely names it.
 */
fun KoFileDeclaration.codeBodyText(): String =
    imports
        .fold(text.withoutStringsAndComments()) { stripped, import -> stripped.replace(import.text, "") }
        .let { stripped -> packagee?.text?.let { stripped.replace(it, "") } ?: stripped }
