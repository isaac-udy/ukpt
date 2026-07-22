# The feature `:api` dependency graph must be acyclic

`ModuleRules.apiGraphAcyclic` is a **new** tested rule: the graph formed by cross-feature
`:api` → `:api` imports must have no cycles. It complements `ModuleRules.crossFeatureCodeViaApi`
(which keeps every cross-feature import resolving through `:api`) by ensuring the resulting `:api`
graph can actually be split into separate modules.

Only `:api` → `:api` edges are inspected. A feature's `:client`/`:server` depends on other
features' `:api` but never the reverse, so those edges can never close a Gradle cycle; the rule
therefore reads only imports declared in `:api` sources that resolve to another feature's `:api`
code.

## Detection

The project is affected if a feature module (typically `:feature:core`) hosts more than one
`feature.[name]` namespace and their `:api` code forms a cycle — feature `A`'s `:api` imports
feature `B`'s `:api` while `B`'s `:api` imports `A`'s (directly or transitively). `verifyArchitecture`
reports each cycle as a set of features with the edges that form it, annotated with how many imports
create each edge, after the rule sync.

## Migration

For each reported cycle, break it by cutting the thinnest edge (the one with the fewest imports —
the rule lists the count and an example file per edge):

1. Move the shared type to the feature that should own it, so the dependency points one way, or
2. Refactor the coupling away (introduce a shared type in a third feature's `:api` or in
   `:platform`, or invert the dependency behind a domain interface), or
3. With explicit sign-off, annotate the `:api` source file holding the edge you are deliberately
   keeping with `@file:ArchitectureException(ruleIds = ["ModuleRules.apiGraphAcyclic"], reason = "…")`
   — its edges are excluded from the graph. This defers the lift-blocker rather than removing it.

Cutting one edge often resolves a whole cycle; re-run `verifyArchitecture` after each change.

## Companion audit (advisory, no action required)

The same version adds `ModuleRules.apiMayUseApiSameModule`, a Guidance audit that reports
same-module cross-feature `:api` → `:api` dependencies grouped by feature pair — the staged-module
counterpart to `ModuleRules.apiMayUseApi`, whose module-graph audit only sees features already
housed separately. It never fails the build; its findings surface under `apiMayUseApiSameModule
[audit]` in the test output so the "keep `:api` → `:api` minimal" signal is visible while features
are still staged. No migration step is needed.

## Verification

`verifyArchitecture` passes with no new exceptions, or with exceptions the user has signed off.
