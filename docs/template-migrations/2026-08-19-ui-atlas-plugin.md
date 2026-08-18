# Wire the UI atlas Gradle plugin

The template now ships the `dev.isaacudy.udytils.atlas` Gradle plugin, applied at the root project.
It registers `generateUiAtlas`: scans the repo for Enro `@NavigationDestination` nodes, `.open()`
edges, and Paparazzi goldens, then writes `build/ui-atlas/` with an interactive `index.html`, a
machine-readable `manifest.json`, and copied golden images.

## Detection

```bash
grep -q "udytils.atlas" build.gradle.kts   # plugin already applied?
```

## Migration

1. **Bump `embedded-udytils`** past the atlas commit (`fbd51bf` or later).

2. **`settings.gradle.kts`** — add two substitutions to the `embedded-udytils` `dependencySubstitution` block:
   ```kotlin
   substitute(module("dev.isaacudy.udytils:atlas-core")).using(project(":atlas-core"))
   substitute(module("dev.isaacudy.udytils:atlas-gradle-plugin")).using(project(":atlas-gradle-plugin"))
   ```

3. **`gradle/libs.versions.toml`** — add two catalog entries:
   ```toml
   # [libraries]
   udytils-atlasGradlePlugin = { module = "dev.isaacudy.udytils:atlas-gradle-plugin" }

   # [plugins]
   udytilsAtlas = { id = "dev.isaacudy.udytils.atlas" }
   ```

4. **Root `build.gradle.kts`** — add the classpath dependency and apply the plugin. The root
   project's `plugins {}` block cannot resolve version-free plugin ids from the buildscript
   classpath (subproject `plugins {}` blocks can, because they inherit the root classpath); use
   `apply(plugin = ...)` after the `plugins {}` block instead:
   ```kotlin
   buildscript {
       dependencies {
           classpath(libs.udytils.atlasGradlePlugin)
       }
   }

   // after the plugins { } block:
   apply(plugin = "dev.isaacudy.udytils.atlas")
   ```

5. The `ukpt-ui-atlas` skill arrives via the template file sync.

## Verification

```bash
./gradlew generateUiAtlas
```

The task must succeed. If the project has Enro destinations with recorded Paparazzi goldens, the
output should report nonzero nodes and goldens. Open `build/ui-atlas/index.html` to confirm.
