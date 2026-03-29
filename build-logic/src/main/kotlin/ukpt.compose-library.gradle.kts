/**
 * Convention plugin for KMP modules that also use Compose Multiplatform.
 *
 * Applies: ukpt.kmp-library, ComposeMultiplatform, ComposeCompiler
 *
 * This builds on the kmp-library convention and adds Compose support.
 */
plugins {
    id("ukpt.kmp-library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
