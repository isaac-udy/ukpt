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
module's JVM tests that execute it, and reports whether a test failed. A **survived** mutant is a change to
the code that no test of an expected category detects.

Mutation mode compiles every module again (the build cache stores the result), so a first run
takes minutes. Later runs reuse the compiled classes.

## Run

| Scope | Command |
|---|---|
| A branch's changed lines | `./gradlew runMutants -Pmutation.changedSince=origin/main` |
| One or more modules | `./gradlew runMutants -Pmutation.modules=:feature:<name>:server` |
| The whole catalog | `./gradlew runMutants` |
| A fraction of one operator's mutants | add `-Pmutation.sample=remove-call=0.1`; each run samples at random and prints its seed, and `-Pmutation.sampleSeed=<seed>` repeats a sample |

Run in the background with `--max-workers=2`, and run no other heavy build at the same time
(see `UKPT.md`, "Building as an agent"). An interrupted run resumes when its inputs are unchanged.

The run prints `build/reports/mutation/summary.txt`. The full data is in
`build/reports/mutation/results.json`. When `results.json` exists, `./gradlew testCoverageReport`
adds the results to `build/reports/test-coverage/index.html`:

- a **Mutation testing** section: the run's start time; a table of the declarations with mutants
  that groups, filters, searches and sorts like the coverage table, with killed, uncovered and
  survived shares; and collapsed **Not killed** and **By operator** tables;
- on each file page, a **Mutants** table of the file's mutants, not killed first, and a `K`, `S`,
  `T` or `U` button on each mutated line. Each status button opens a popup with the mutated
  expression, the change, the removed call, the Construct, the expected categories, the killing
  test and its failure, and the mutant ID.

`results.json` records the Kotlin sources at the start of the run. When a test file changed after
the run, the report shows no mutation results and asks for a new run, because a test in one module
can kill mutants in another. When a production file changed, the report leaves out that file's
mutants.

## What runs

- **Catalog:** the rules in the root `build.gradle.kts` `mutation { }` block select mutants by
  Construct, package or file. The template's rules select the domain and data Constructs, and the
  web Constructs that hold logic: Routes, Form, ViewState, ViewHelper and EventStream.
- **Operators:** every operator below is compiled in. `-Pmutation.operators=remove-call` runs the
  mutants of the named operators only; a rule's `operators()` limits that rule. An unknown ID fails
  the build.

  | ID | Change |
  |---|---|
  | `negate-condition` | negates an `if`, `when` branch, `while` or `do-while` condition |
  | `boolean-return` | a `Boolean` function returns `true`, and `false` |
  | `empty-return` | a function returns `null`, an empty collection, `""` or `0` |
  | `remove-call` | removes an unused call, a property assignment, or a compound assignment or increment; not a safe call (`a?.save()`) |
  | `conditionals-boundary` | `<` ↔ `<=`, `>` ↔ `>=` |
  | `logical-connector` | `&&` ↔ `\|\|`, and each operand alone |
  | `elvis` | `a ?: b` → `b` |

- **Tests:** only tests in a category that the mutant's Construct lists in "Tested by" count. A
  Routes mutant killed only by a Unit Test is reported as survived, because the Construct
  expects a Subcutaneous Test.
- **Snapshot Tests never count.** `ukpt.mutation` excludes the `snapshot` category, so an HTML
  golden never kills a mutant. A mutant whose Construct expects only Snapshot Tests is not run.
- **Hosts:** every module runs its mutants against its JVM `test` task.

## Triage

Handle each `survived` and `uncovered` entry in `results.json`. The fields that matter:
`path`, `line`, `operator`, `variant` (the change, for example `< → <=`), `text` (the mutated expression),
`callee` (the function or property a `remove-call` mutant removes), `constructs`, `expected` (the
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
     that makes the mutant equivalent. When every `remove-call` mutant of one function or library is
     equivalent (logging, metrics), add an `excludeCalls()` pattern for the `callee` field instead
     and report the pattern to the user (see "Excluded calls").
   - **Dead code:** no caller reaches the branch. Report the branch to the user. Remove the branch
     only when the user asks.
3. **Report** per mutant: `path:line`, the behaviour change, the case, and the action taken.

`timed-out` counts as a kill: the mutant made a test exceed its baseline duration × 2 + 1 s,
usually because a wait in the test never matched.

## Excluded calls

`excludeCalls()` in the `mutation { }` block leaves out every `remove-call` mutant whose `callee`
matches a pattern. The template starts with console output (`kotlin.io.println`,
`kotlin.io.print`, `printStackTrace`), Koin bindings (`org.koin.*`), SLF4J loggers
(`org.slf4j.Logger.*`) and Ktor client timeouts. Before a project's first run, add the project's own calls of the same kinds:

- the project's logging facade, for example `platform.log.Logger.*`;
- metrics, tracing and analytics clients;
- other configuration that no test can observe, such as connection or retry timeouts.

Do not exclude calls whose removal changes behaviour, even when many of their mutants survive.
Survivors there are missing tests:

- `require`, `check` and other validation, authorization and permission checks;
- audit records and other writes a user or operator reads;
- serialization settings, such as `ignoreUnknownKeys`;
- HTTP client settings other than timeouts, such as `followRedirects` and plugin installation.

After a run, group the survived `remove-call` mutants by `callee` to find further candidates. A
pattern removes a mutant from every rule, so prefer `ignore("<id>", "<reason>")` for one
equivalent call site of a call that matters elsewhere.

## Catalog rules

The catalog is the `mutation { }` block in the root `build.gradle.kts`. Add a rule when a group of
code that the catalog does not select needs auditing:

```kotlin
mutation {
    rule("parsers") { files("feature/*/server/**/parse/*.kt") }
    rule("pages") { constructs("ServerWeb.Page") }
}
```

Selectors: `constructs()`, `packages()`, `files()` (globs from the repository root, `**` across
directories), `excludeConstructs()`, `excludeFiles()`, `excludeCalls()`, and `operators()`. Every
non-empty selector must match. `excludeCalls("org.koin.*")` in the `mutation { }` block leaves out
every `remove-call` mutant whose `callee` matches; `*` matches any characters, dots included.
`sample("remove-call", 0.1)` runs a random 10% of one operator's selected mutants in each run. `build/mutation/sites.json` lists every compiled mutant site with its Constructs and the
rules that select the site; use it to check what a new rule selects before running it.

## Limits

- Code in Alpine or the project's scripts is not mutated; only Kotlin is.
- A mutant in a top-level or `object` property initialiser runs in its own JVM (a few seconds
  each).
- `baselineFailures` in `results.json` lists tests that failed with no mutant active; fix those
  tests first, because the runner excludes them from every mutant.
