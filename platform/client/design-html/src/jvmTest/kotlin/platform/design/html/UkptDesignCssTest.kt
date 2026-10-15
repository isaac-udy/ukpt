package platform.design.html

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import platform.design.UkptColors
import platform.design.UkptFonts
import platform.design.UkptShapes
import platform.design.UkptSpacing
import platform.design.UkptTypography
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class UkptDesignCssTest {
    private val stylesheet = File(System.getProperty("ukpt.designStylesheet"))
    private val css = stylesheet.readText().replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
    private val darkQuery = "@media (prefers-color-scheme: dark)"
    private val lightTokens = rootTokens(css.substringBefore(darkQuery))
    private val darkTokens = rootTokens(css.substringAfter(darkQuery))

    @Test
    fun lightPaletteMatchesUkptColors() {
        assertTokens("UkptColors.Light", lightTokens, colorTokens(UkptColors.Light))
    }

    @Test
    fun darkPaletteMatchesUkptColors() {
        assertTokens("UkptColors.Dark", darkTokens, colorTokens(UkptColors.Dark))
    }

    @Test
    fun spacingMatchesUkptSpacing() {
        val spacing = mapOf(
            "xs" to UkptSpacing.xs,
            "sm" to UkptSpacing.sm,
            "md" to UkptSpacing.md,
            "lg" to UkptSpacing.lg,
            "xl" to UkptSpacing.xl,
            "xxl" to UkptSpacing.xxl,
        )
        assertEquals(objectPropertyNames(UkptSpacing::class.java), spacing.keys, "UkptSpacing steps")
        assertTokens("UkptSpacing", lightTokens, spacing.map { (name, value) -> "--ukpt-space-$name" to px(value) }.toMap())
    }

    @Test
    fun radiiMatchUkptShapes() {
        val shapes = mapOf(
            "small" to UkptShapes.small,
            "medium" to UkptShapes.medium,
            "large" to UkptShapes.large,
        )
        assertEquals(objectPropertyNames(UkptShapes::class.java), shapes.keys, "UkptShapes steps")
        assertTokens(
            "UkptShapes",
            lightTokens,
            shapes.map { (name, shape) ->
                val radius = (shape as RoundedCornerShape).topStart.toPx(Size(1000f, 1000f), Density(1f))
                "--ukpt-radius-$name" to "${radius.toInt()}px"
            }.toMap(),
        )
    }

    @Test
    fun typeScaleMatchesUkptTypography() {
        val typography = UkptTypography.from(UkptFonts.System)
        val roles = mapOf(
            "display" to typography.display,
            "title" to typography.title,
            "body" to typography.body,
            "label" to typography.label,
            "caption" to typography.caption,
        )
        assertEquals(dataClassRoleNames(typography, "TextStyle"), roles.keys, "UkptTypography roles")
        assertTokens("UkptTypography", lightTokens, roles.flatMap { (name, style) -> typeTokens(name, style) }.toMap())
    }

    private fun colorTokens(colors: UkptColors): Map<String, String> {
        val roles = mapOf(
            "background" to colors.background,
            "surface" to colors.surface,
            "onSurface" to colors.onSurface,
            "onSurfaceVariant" to colors.onSurfaceVariant,
            "accent" to colors.accent,
            "onAccent" to colors.onAccent,
            "outline" to colors.outline,
            "error" to colors.error,
            "onError" to colors.onError,
        )
        assertEquals(dataClassRoleNames(colors, "Color"), roles.keys, "UkptColors roles")
        return roles.map { (name, color) -> "--ukpt-${kebab(name)}" to hex(color) }.toMap()
    }

    private fun typeTokens(role: String, style: TextStyle): List<Pair<String, String>> = listOf(
        "--ukpt-$role-size" to "${style.fontSize.value.toInt()}px",
        "--ukpt-$role-line-height" to "${style.lineHeight.value.toInt()}px",
        "--ukpt-$role-weight" to "${style.fontWeight!!.weight}",
    )

    private fun assertTokens(source: String, actual: Map<String, String>, expected: Map<String, String>) {
        val mismatches = expected.mapNotNull { (name, value) ->
            val cssValue = actual[name]
            when {
                cssValue == null -> "$name is missing; $source has $value."
                !cssValue.equals(value, ignoreCase = true) -> "$name is $cssValue; $source has $value."
                else -> null
            }
        }
        if (mismatches.isNotEmpty()) {
            fail("${stylesheet.name} doesn't match $source. Change ${stylesheet.name}:\n" + mismatches.joinToString("\n"))
        }
    }

    private fun rootTokens(css: String): Map<String, String> {
        val block = Regex(""":root\s*\{([^}]*)}""").find(css)?.groupValues?.get(1).orEmpty()
        return Regex("""(--[\w-]+)\s*:\s*([^;]+);""").findAll(block)
            .associate { it.groupValues[1] to it.groupValues[2].trim() }
    }

    private fun dataClassRoleNames(instance: Any, type: String): Set<String> =
        Regex("""(\w+)=$type\(""").findAll(instance.toString()).map { it.groupValues[1] }.toSet()

    // Dp properties compile to mangled getters such as `getXs-D9Ej5fM`.
    private fun objectPropertyNames(type: Class<*>): Set<String> =
        type.declaredMethods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 }
            .map { it.name.removePrefix("get").substringBefore('-').replaceFirstChar(Char::lowercase) }
            .toSet()

    private fun kebab(name: String): String = name.replace(Regex("([A-Z])")) { "-" + it.value.lowercase() }

    private fun hex(color: Color): String {
        val argb = color.toArgb()
        val rgb = "#%06X".format(argb and 0xFFFFFF)
        val alpha = argb ushr 24
        return if (alpha == 0xFF) rgb else rgb + "%02X".format(alpha)
    }

    private fun px(value: Dp): String = "${value.value.toInt()}px"
}
