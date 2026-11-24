# Declare file inputs on category test tasks

JVM and Android host category tasks, including Subcutaneous tasks, now use Gradle's build cache.

## Detection

Look for tests that read a file outside the test runtime classpath. Examples include stylesheet
checks, documentation checks and tests that read fixtures directly from the checkout.

## Migration

Declare each file on every task that runs the test. A multiplatform module with a `@CheckTest`
that reads a stylesheet can configure both entry points in its build file.

```kotlin
tasks.withType<Test>().matching { it.name == "jvmTest" || it.name == "checkTest" }.configureEach {
    inputs.file(layout.projectDirectory.file("src/css/theme.css"))
        .withPropertyName("stylesheet").withPathSensitivity(PathSensitivity.RELATIVE)
}
```

## Verification

Run the module's category task, edit the input and run the same task again with `--info`.
Gradle should report the changed input and execute the test.
