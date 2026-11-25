---
name: ukpt-test-audit
description: >-
  Audit whether UKPT tests fail when the code is wrong, with mutation testing:
  run `./gradlew runMutants` (whole build, one module, or the lines changed on a
  branch), read build/reports/mutation/results.json, and triage each survived or
  uncovered mutant as a missing assertion (write the test), an equivalent mutant
  (an ignore entry), or dead code. Also covers the catalog rules in the root
  build's `mutation { }` block. Use when asked to audit test quality, check that
  a feature's tests catch regressions, review a branch's tests before a pull
  request, or act on a mutation report.
---

# Test audit

`./gradlew runMutants` compiles mutants into the JVM code, runs each selected mutant against the
JVM tests that execute it, and reports whether a test failed. A **survived** mutant is a change to
the code that no test of an expected category detects.

Mutation mode compiles every JVM module again (the build cache stores the result), so a first run
takes minutes. Later runs reuse the compiled classes.

## Run

| Scope | Command |
|---|---|
| A branch's changed lines | `./gradlew runMutants -Pmutation.changedSince=origin/main` |
| One or more modules | `./gradlew runMutants -Pmutation.modules=:feature:<name>:client,:feature:<name>:server` |
| The whole catalog | `./gradlew runMutants` |

Run in the background with `--max-workers=2`, and run no other heavy build at the same time
(see `UKPT.md`, "Building as an agent"). An interrupted run resumes when its inputs are unchanged.

The run prints `build/reports/mutation/summary.txt`. The full data is in
`build/reports/mutation/results.json`. The test coverage report (`./gradlew testCoverageReport`)
shows the same results per file and per Construct when `results.json` exists.

## What runs

- **Catalog:** the rules in the root `build.gradle.kts` `mutation { }` block select mutants by
  Construct, package or file. The template's rules select the domain, data, ViewModel and server
  service Constructs.
- **Operators:** `negate-condition` (an `if`, a `when` branch, a `while`) and
  `negate-boolean-return`.
- **Tests:** only tests in a category that the mutant's Construct lists in "Tested by" count. A
  ServiceImpl mutant killed only by a Unit Test is reported as survived, because the Construct
  expects a Subcutaneous Test.
- **Hosts:** JVM test tasks only.

## Triage

Handle each `survived` and `uncovered` entry in `results.json`. The fields that matter:
`path`, `line`, `operator`, `text` (the mutated expression), `constructs`, `expected` (the
categories that count) and `candidateTests` (the expected tests that executed the mutant).

1. **Read the code at `path:line`** and state what the negated `text` changes in behaviour, for
   example "a reset with an empty history now runs the delete".
2. **Decide which case applies:**
   - **Missing assertion:** the behaviour is reachable and observable, but no expected test
     asserts on it. For `survived`, the `candidateTests` run the line without checking the
     outcome. Extend the closest test or add one in an expected category (`ukpt-testing`), then
     run the scoped `runMutants` again to confirm the kill.
   - **Uncovered:** no expected test executes the line. Write a test in an expected category that
     reaches the line. A test in another category does not count.
   - **Equivalent mutant:** the negation does not change observable behaviour, for example a
     guard that a caller already guarantees, or a cache check whose miss path computes the same
     value. Add `ignore("<id>", "<reason>")` to the `mutation { }` block. The reason names the fact
     that makes the mutant equivalent.
   - **Dead code:** no caller reaches the branch. Report the branch to the user. Remove the branch
     only when the user asks.
3. **Report** per mutant: `path:line`, the behaviour change, the case, and the action taken.

`timed-out` counts as a kill: the mutant made a test exceed its baseline duration × 2 + 1 s,
usually because a wait in the test never matched.

## Catalog rules

The catalog is the `mutation { }` block in the root `build.gradle.kts`. Add a rule when a group of
code that the catalog does not select needs auditing:

```kotlin
mutation {
    rule("parsers") { files("feature/*/server/**/parse/*.kt") }
    rule("ui-values") { constructs("ClientUi.UiValueType") }
}
```

Selectors: `constructs()`, `packages()`, `files()` (globs from the repository root, `**` across
directories), `excludeConstructs()`, `excludeFiles()`, and `operators()`. Every non-empty selector
must match. `build/mutation/sites.json` lists every compiled mutant site with its Constructs and the
rules that select the site; use it to check what a new rule selects before running it.

## Limits

- Mutants in iOS, JS, wasm or Android-only source sets are not compiled or reported.
- A mutant in a top-level or `object` property initialiser runs in its own JVM (a few seconds
  each).
- `baselineFailures` in `results.json` lists tests that failed with no mutant active; fix those
  tests first, because the runner excludes them from every mutant.
