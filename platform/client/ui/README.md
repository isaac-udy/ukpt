# `:platform:client:ui` — common platform UI

Reusable, cross-feature client UI that is **more than a design-system primitive**: composite
components, shared scaffolds, and anything that legitimately needs navigation or dependency
injection. It is built on top of [`:platform:client:design`](../design/design-system/README.md) and
depends on it with `api`, so consumers of this module also see the design tokens the components
expose.

This module starts **empty** — it is scaffolding, present so the pattern is known and ready. Add
components here as they appear; there is nothing to build until you do.

## `ui` vs `design`

| | `:platform:client:design` | `:platform:client:ui` (here) |
|---|---|---|
| Holds | Tokens + stateless primitives | Composite / behavioural common components |
| Navigation, DI | **Forbidden** (`DesignSystemRules.noNavigationOrDi`) | **Allowed** |
| Depends on | Compose only | `:platform:client:design` + whatever a component needs |
| Rule of thumb | "How a button looks" | "A screen scaffold that navigates" |

If a thing is a stateless primitive styled purely from tokens, it belongs in `design`. If it wires
several primitives together, carries behaviour, or reports/handles navigation, it belongs here.

## Adding the first component

1. Uncomment the dependencies the component needs in [`build.gradle.kts`](build.gradle.kts) — the
   Compose building blocks, `libs.enro.core` for navigation (add `libs.enro.processor` via KSP as in
   `:feature:core:client`), `libs.koin.core` for DI.
2. Put the component under `src/commonMain/kotlin/platform/ui/`, reading tokens and primitives from
   `platform.design`.
3. Add a `@Preview` and a `PreviewSnapshotTest` (see `:platform:client:design` / `:feature:core:client`)
   so it is snapshot-covered like the rest of the UI.
