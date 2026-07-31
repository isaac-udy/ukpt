# `ukpt.kmp-library` pins the `jvm()` target to bytecode 11

The convention used to pin JVM 11 bytecode on a KMP module's **Android** variant only; the `jvm()`
target floated to the ambient JDK (verified in the template: Java 21 class files when Gradle runs
on JDK 21). On a machine whose JDK is newer than 11, that breaks a plain-JVM module pinned by
`ukpt.jvm-base` the moment it consumes the KMP module's `jvm()` variant — as a Gradle
variant-resolution refusal where the variant carries a JVM-version attribute, or as the Kotlin
compiler refusing to inline higher-target bytecode into a pinned-11 compilation.

The convention now applies the same `JvmTarget.JVM_11` pin to the `jvm()` target it declares. The
pin must live at the DSL level (`jvm { compilerOptions { jvmTarget.set(…) } }`) — a
`tasks.withType<KotlinJvmCompile>` approach changes the compiled bytecode but **not** the target's
published metadata, so the consumption failure remains.

## Detection

A project is affected if any module worked around the float itself with a per-module pin:

```bash
grep -rn -A3 "jvm {" --include="*.gradle.kts" . | grep -B2 "jvmTarget.set(JvmTarget.JVM_11)"
```

Matches outside `build-logic/` in modules applying `ukpt.kmp-library` (directly or via
`ukpt.compose-library` / `ukpt.snapshot-testing`) are now redundant. A project with no matches is
still affected in the good sense: syncing `build-logic/` fixes the latent resolution failure.

## Migration

1. Sync `build-logic/` from the template; it is template-owned.
2. Delete each per-module `jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }` stanza from
   modules that apply the convention — the pin now comes from `ukpt.kmp-library`. A module with
   other configuration inside `jvm { … }` keeps the block and drops only the `compilerOptions`
   pin. Do **not** touch modules that hand-wire their targets without the convention (for example a
   shell module declaring a named `jvm("desktop")` target): those keep their own pin.

## Verification

Compile a KMP module's `jvm()` target and inspect the class-file version — it must be Java 11
(major version 55) regardless of the JDK running Gradle:

```bash
./gradlew :feature:core:api:compileKotlinJvm --max-workers=2
javap -v "$(find feature/core/api/build/classes/kotlin/jvm/main -name '*.class' | head -1)" \
  | grep "major version"   # expect: major version: 55
```

Then compile a pinned-JVM consumer of a KMP module (in the template,
`./gradlew :app:server:compileKotlin`) to confirm consumption succeeds.
