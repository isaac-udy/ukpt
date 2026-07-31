package architecture

import com.lemonappdev.konsist.api.Konsist
import java.io.File

/**
 * The root of the build being scanned: the nearest ancestor of the test JVM's working directory
 * that holds a `settings.gradle.kts`, which is how Konsist resolves its own project root.
 */
private val buildRoot: String = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
    .firstOrNull { File(it, "settings.gradle.kts").isFile }
    ?.path
    ?: File(System.getProperty("user.dir")).absolutePath

/**
 * Agent worktrees live under `<buildRoot>/.claude/`. The exclusion is anchored to the build root
 * the run resolved, which gives two properties at once: a run in the main checkout skips the
 * worktrees nested inside it, and a run from inside a worktree scans that worktree — its own
 * `.claude/` directory is the one excluded, not the path it happens to sit under.
 */
private val nestedWorktrees = "$buildRoot/.claude/"

/**
 * Matches any test source set so none of them are architecture-scanned: plain-JVM `src/test`, KMP's
 * shared `commonTest`, every per-platform set (`desktopTest`, `iosSimulatorArm64Test`, `wasmJsTest`,
 * …), and Android's `androidHostTest`/`androidUnitTest`/`screenshotTest` (under AGP 9.0's
 * `com.android.kotlin.multiplatform.library` plugin, Paparazzi host tests live in `androidHostTest`,
 * formerly `androidUnitTest`, rather than `src/test`). The `[^/]*[Tt]est` segment matches the source
 * set directly after `/src/`; no `…Main` set ends in `test`, so production code is never excluded.
 */
private val testSourceSet = Regex("/src/[^/]*[Tt]est/")

val projectScope = Konsist
    .scopeFromProject()
    .slice {
        // The architecture module itself (the rule catalog + definition) is meta-code,
        // not governed code — scanning it would classify the catalog's own objects.
        !it.path.contains("/platform/common/architecture/") &&
                !it.path.contains("embedded-enro") &&
                !it.path.contains("embedded-udytils") &&
                // build-logic is an includeBuild composite of template tooling (not app code),
                // sibling to the embedded composite builds — its sources aren't governed either.
                !it.path.contains("/build-logic/") &&
                // Agent worktrees under `.claude/` are complete second copies of the repository.
                // Scanning them double-counts every declaration, and — worse — makes any rule
                // about cross-file uniqueness fail against a file's own reflection.
                !it.path.startsWith(nestedWorktrees) &&
                // Test sources of every kind are out of scope (see testSourceSet above).
                !testSourceSet.containsMatchIn(it.path)
    }
