> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Narrative source: `src/test/kotlin/architecture/rules/exceptions.md`; rule content comes from the rule catalog.
> Regenerate with `./gradlew :platform:common:architecture:test -PupdateArchitectureDocs=true`.

# Architecture exceptions

Architecture rules are enforced by the registry-driven Konsist tests in `:platform:common:architecture`. When a specific declaration cannot conform to a rule (e.g. a transitional class whose ideal location hasn't been determined yet), the declaration can be marked exempt from that rule so the tests pass while the exception is tracked explicitly.

## How to add an exception

There are two exemption mechanisms, depending on what kind of file the exempt code lives in. Both reference rules by their [path id](../README.md#rule-ids).

### Kotlin source files: `@ArchitectureException`

Add the [`@ArchitectureException`](../src/main/kotlin/architecture/ArchitectureException.kt) annotation either at file level (above the `package` line) or on the specific declaration:

```kotlin
@file:ArchitectureException(
    ruleIds = ["ServicesLayer.internalHierarchicalVisibility"],
    reason = "Sessions' audio subsystem reaches a sibling subsystem's helper for transcription " +
        "phrase hints. The shared accessor hasn't been promoted to a common ancestor yet — until " +
        "it is, this cross-subsystem import is the cheapest way to keep a single authoritative path.",
    trackingIssue = "",
)

package feature.sessions.services.internal.audio

import architecture.ArchitectureException
// ...
```

`ruleIds` lists the rule path ids the declaration is exempt from (see [Rule IDs](../README.md#rule-ids)). `reason` is free-form prose; `trackingIssue` is optional but recommended.

The architecture tests look up the annotation via Konsist when checking each rule, and skip declarations / files that list the rule's id.

### Gradle build files: `// architecture-exception:` comment

`build.gradle.kts` files can't carry the annotation (no compile classpath), and the module-dependency rules (the `ModuleRules` group) are the ones that apply to them. Place a comment immediately above the dependency line:

```kotlin
sourceSets {
    commonMain.dependencies {
        // architecture-exception: ModuleRules.platformNotFeature
        // reason="Pulls feature-level analytics types that haven't yet been promoted to " +
        //   ":platform:common:analytics. Refactor tracked separately."
        implementation(projects.feature.core.api)
    }
}
```

The exemption applies to the immediately-following dependency line. Multiple `architecture-exception:` lines may stack to exempt one declaration from several rules (`// architecture-exception: ModuleRules.platformNotFeature, ModuleRules.platformNotApp`).

## Rules for adding exceptions

* An architecture exception may only be added after discussing the exception with a human author
    * **Verification**: not automatically verifiable — enforced by review.
* An architecture exception is not a valid way to resolve an immediate architecture-test failure without user feedback — fix the code or the rule first
    * **Verification**: not automatically verifiable — enforced by review.
* An architecture exception must include a KDoc-style (`/** ... */`) comment explaining why it exists and the intended resolution
    * **Note**: Checked for `@ArchitectureException` on declarations; `// architecture-exception:` comments in build files carry their reason inline and are out of scope.
* An architecture exception should be temporary — revisit it periodically and remove it once the underlying issue is resolved

