# The embedded builds are given the Android SDK location

`settings.gradle.kts` now applies `gradle/embedded-sdk-location.settings.gradle.kts`, which copies
the `sdk.dir` entry from the project's `local.properties` into `embedded-*/local.properties` before
the embedded builds are included.

AGP reads `sdk.dir` from the `local.properties` in the root directory of the build that owns the
project, falling back to the `ANDROID_SDK_ROOT` and `ANDROID_HOME` environment variables. An
included build is a separate build with its own root directory, so `embedded-enro` and
`embedded-udytils` never see the project's file. Android Studio writes `local.properties` for the
project it opens and exports nothing, so a clone on a machine with no SDK environment variable
configures the project and then fails to find an SDK inside the submodules.

The script finds the embedded builds by scanning for directories named `embedded-*` that contain a
`settings.gradle.kts`. It writes nothing when the project has no `local.properties` or that file
declares no `sdk.dir` — on a machine that exports `ANDROID_HOME`, every build in the tree already
resolves the SDK from the environment.

## Detection

```bash
grep -n "embedded-sdk-location" settings.gradle.kts
ls gradle/embedded-sdk-location.settings.gradle.kts
```

A project whose submodule directories are not named `embedded-*` is affected even after the file
sync: the scan will not find them.

## Migration

1. Take `gradle/embedded-sdk-location.settings.gradle.kts` from the template.
2. Add the `apply` to `settings.gradle.kts`, above the `includeBuild` calls:
   ```kotlin
   apply(from = "gradle/embedded-sdk-location.settings.gradle.kts")
   ```
   It must not become a `build-logic` convention plugin. Applying one from `settings.gradle.kts`
   puts build-logic's runtime classpath, AGP included, on the settings classpath, and the root build
   then fails to resolve the AGP version it declares.
3. A project whose included builds are named something other than `embedded-*` widens the filter in
   the script to match its own directories.
4. Delete any hand-written `local.properties` in the included builds. The script rewrites the
   `sdk.dir` entry and keeps every other entry, so one holding only `sdk.dir` needs no attention.

## Verification

```bash
rm -f embedded-*/local.properties
./gradlew help
cat embedded-*/local.properties
```

Each included build holding an `sdk.dir` matching the project's `local.properties` is the check that
matters. A second `./gradlew help` reporting `Configuration cache entry reused` confirms the script
writes nothing once the files agree.
