# `subcutaneousJvmTest` replaces `subcutaneousTest`

`ukpt.feature-tests` registers `subcutaneousJvmTest` on each `:feature:<name>:tests` module, and
`ukpt.test-categories` no longer registers `subcutaneousTest`. The `:tests` modules stay plain JVM
modules with `src/main` and `src/test`: the in-process server and embedded Postgres run on the
JVM. The task name matches the JVM host task of the multiplatform template branch.

## Detection

```bash
grep -rn "subcutaneousTest\b" --include='*.yml' --include='*.yaml' --include='*.kts' --include='*.md' --include='*.sh' .
```

A project is affected if the command prints a line outside `docs/template-migrations/`.

## Migration

1. Replace `subcutaneousTest` with `subcutaneousJvmTest` in CI workflows, scripts, run
   configurations and guidance.

## Verification

```bash
./gradlew subcutaneousJvmTest
```
