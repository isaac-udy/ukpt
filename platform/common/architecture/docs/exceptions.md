> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Provided by the udytils architecture system. To override it, add a file named `exceptions.md` next to this project's rule definitions.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# Architecture exceptions

A guide for using `@ArchitectureException` to ignore rules. When a declaration cannot conform to a
Rule, it can be marked exempt from that Rule: the tests pass, and the exception is recorded in the
code that carries it.

## How to add an exception

There are two exemption mechanisms, depending on the kind of file the exempt code lives in. Both
reference Rules by their ID (see the [rule index](rule-index.md)).

### Kotlin source files: `@ArchitectureException`

Add the `@ArchitectureException` annotation (from the `dev.isaacudy.udytils:architecture-annotations`
artifact) either at file level (above the `package` line) or on the specific declaration:

```kotlin
@file:ArchitectureException(
    ruleIds = ["ServicesLayer.internalHierarchicalVisibility"],
    reason = "Sessions' audio subsystem reaches a sibling subsystem's helper for transcription " +
        "phrase hints. The shared accessor hasn't been promoted to a common ancestor yet — until " +
        "it is, this cross-subsystem import is the cheapest way to keep a single authoritative path.",
    trackingIssue = "",
)

package feature.sessions.services.internal.audio

import dev.isaacudy.udytils.architecture.ArchitectureException
// ...
```

`ruleIds` lists the rule IDs the declaration is exempt from. `reason` is free-form text.
`trackingIssue` is optional but recommended.

The tests skip a declaration or file for each Rule listed in its `ruleIds`.

### Gradle build files: `// architecture-exception:` comment

`build.gradle.kts` files can't use the annotation, so module-dependency Rules are exempted with a
comment placed immediately above the dependency line:

```kotlin
sourceSets {
    commonMain.dependencies {
        // architecture-exception: ModuleRules.platformNotFeature
        // reason="Pulls feature-level analytics types that haven't yet been promoted to " +
        //   "a platform module. Refactor tracked separately."
        implementation(projects.feature.core.api)
    }
}
```

The exemption applies to the immediately-following dependency line. Multiple
`architecture-exception:` lines may stack to exempt one declaration from several Rules
(`// architecture-exception: ModuleRules.platformNotFeature, ModuleRules.platformNotApp`).

## Rules for adding exceptions

An exception is an admission that the code does not meet a Rule and that the right resolution has
not yet been agreed:

* An exception may only be added after discussing it with a human author. Adding one requires
  human judgement about whether the non-conformance is acceptable.
* An exception is not a valid way to resolve an immediate test failure. Fix the code or the Rule
  first.
* Every exception must explain why it exists and what the intended resolution is.
* Exceptions should be temporary. Revisit them periodically and remove them once the underlying
  issue is resolved.
