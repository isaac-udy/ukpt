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

`./gradlew runMutants` compiles mutants into the code, runs each selected mutant against the
module's JVM or Android host tests that execute it, and reports whether a test failed. A **survived** mutant is a change to
the code that no test of an expected category detects.

Mutation mode compiles every module again (the build cache stores the result), so a first run
takes minutes. Later runs reuse the compiled classes.

## Run

| Scope | Command |
|---|---|
| A branch's changed lines | `./gradlew runMutants -Pmutation.changedSince=origin/main` |
| One or more modules | `./gradlew runMutants -Pmutation.modules=:feature:<name>:client,:feature:<name>:server` |
| The whole catalog | `./gradlew runMutants` |
| Android host tests in place of JVM tests | add `-Pmutation.hosts=android,jvm` |

Run in the background with `--max-workers=2`, and run no other heavy build at the same time
(see `UKPT.md`, "Building as an agent"). An interrupted run resumes when its inputs are unchanged.

The run prints `build/reports/mutation/summary.txt`. The full data is in
`build/reports/mutation/results.json`. When `results.json` exists, `./gradlew testCoverageReport`
adds the results to `build/reports/test-coverage/index.html`:

- a **Mutants** column (killed of all mutants, then the survived `S` and uncovered `U` counts) and
  a Mutants filter in the pivot table;
- a **Mutants not killed** section listing each survived and uncovered mutant with its
  Construct, expected categories, operator, change and mutated expression;
- a `K`, `S`, `T` or `U` badge on each mutated line of a file page; the badge's tooltip names
  the killing test and the failure.

The report leaves out a mutant whose source text no longer starts on its line, because the code
changed after the run.

## What runs

- **Catalog:** the rules in the root `build.gradle.kts` `mutation { }` block select mutants by
  Construct, package or file. The template's rules select the domain, data, ViewModel and server
  service Constructs.
- **Operators:** every operator below is compiled in. `-Pmutation.operators=remove-call` runs the
  mutants of the named operators only; a rule's `operators()` limits that rule. An unknown ID fails
  the build.

  | ID | Change |
  |---|---|
  | `negate-condition` | negates an `if`, `when` branch, `while` or `do-while` condition |
  | `boolean-return` | a `Boolean` function returns `true`, and `false` |
  | `empty-return` | a function returns `null`, an empty collection, `""` or `0` |
  | `remove-call` | removes an unused call, a property assignment, or a compound assignment or increment |
  | `conditionals-boundary` | `<` ↔ `<=`, `>` ↔ `>=` |
  | `logical-connector` | `&&` ↔ `\|\|`, and each operand alone |
  | `elvis` | `a ?: b` → `b` |

- **Tests:** only tests in a category that the mutant's Construct lists in "Tested by" count. A
  ServiceImpl mutant killed only by a Unit Test is reported as survived, because the Construct
  expects a Subcutaneous Test.
- **Snapshot Tests never count.** `ukpt.mutation` excludes the `snapshot` category and the JUnit
  Vintage engine that runs Paparazzi. A mutant whose Construct expects only Snapshot Tests is not
  run.
- **Hosts:** each module runs its mutants on one host. A module with a JVM target uses its JVM
  tests; a module with only an Android target uses its Android host tests. `-Pmutation.hosts=android,jvm`
  selects the Android host for every module with an Android target.

## Triage

Handle each `survived` and `uncovered` entry in `results.json`. The fields that matter:
`path`, `line`, `operator`, `variant` (the change, for example `< → <=`), `text` (the mutated expression), `constructs`, `expected` (the
categories that count) and `candidateTests` (the expected tests that executed the mutant).

1. **Read the code at `path:line`** and state what the `variant` applied to `text` changes in
   behaviour, for example "a reset with an empty history now runs the delete". A survived
   `remove-call` usually means no test checks the call's effect.
2. **Decide which case applies:**
   - **Missing assertion:** the behaviour is reachable and observable, but no expected test
     asserts on it. For `survived`, the `candidateTests` run the line without checking the
     outcome. Extend the closest test or add one in an expected category (`ukpt-testing`), then
     run the scoped `runMutants` again to confirm the kill.
   - **Uncovered:** no expected test executes the line. Write a test in an expected category that
     reaches the line. A test in another category does not count.
   - **Equivalent mutant:** the change does not alter observable behaviour, for example a guard
     that a caller already guarantees, a cache check whose miss path computes the same value, or a
     removed logging call. Add `ignore("<id>", "<reason>")` to the `mutation { }` block. The reason names the fact
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

- Mutants in iOS, JS or wasm source sets are not compiled or reported. Mutants in `androidMain` of
  a module with a JVM target are reported only with `-Pmutation.hosts=android,jvm`.
- A mutant in a top-level or `object` property initialiser runs in its own JVM (a few seconds
  each).
- `baselineFailures` in `results.json` lists tests that failed with no mutant active; fix those
  tests first, because the runner excludes them from every mutant.
