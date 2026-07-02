> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Shipped by the architecture framework (`dev.isaacudy.udytils:architecture-core`); override it by adding `exceptions.md` to the catalog root.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# Architecture exceptions

Architecture rules are enforced by the catalog-driven test suite. When a specific declaration
cannot conform to a rule (e.g. a transitional class whose ideal location hasn't been determined
yet), the declaration can be marked exempt from that rule so the tests pass while the exception is
tracked explicitly.

## How to add an exception

There are two exemption mechanisms, depending on what kind of file the exempt code lives in. Both
reference rules by their [path id](../README.md#rule-ids).

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

`ruleIds` lists the rule path ids the declaration is exempt from (see [Rule IDs](../README.md#rule-ids)).
`reason` is free-form prose; `trackingIssue` is optional but recommended.

The architecture tests look up the annotation when running each rule's test, and skip
declarations / files that list the rule's id.

### Gradle build files: `// architecture-exception:` comment

`build.gradle.kts` files can't carry the annotation (no compile classpath), and the
module-dependency rules are the ones that apply to them. Place a comment immediately above the
dependency line:

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
`architecture-exception:` lines may stack to exempt one declaration from several rules
(`// architecture-exception: ModuleRules.platformNotFeature, ModuleRules.platformNotApp`).

## Rules for adding exceptions

An exemption is an admission that the code does not meet a rule and that the right resolution has
not yet been agreed. Treat these as process rules (projects may formalise them as `unverifiable`
rules in their own catalog):

* An architecture exception may only be added after discussing it with a human author — adding one
  requires human judgement about whether the non-conformance is acceptable.
* An architecture exception is not a valid way to resolve an immediate test failure — fix the code
  or the rule first.
* Every exception must explain *why* it exists and what the intended resolution is.
* Exceptions should be temporary — revisit them periodically and remove them once the underlying
  issue is resolved.
