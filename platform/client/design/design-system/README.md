# Design system

The design system for this project: its tokens, its primitives, and the rules that keep them
coherent. It lives in `:platform:client:design` (package root `platform.design`) and is consumed by feature
modules as `implementation(projects.platform.client.design)`.

**Read [principles.md](principles.md) before any visual change.** It is short, and it is the part
that stops the system eroding.

## Pages

| | |
|---|---|
| [principles.md](principles.md) | How the system is meant to be used, and what it refuses to do |
| [tokens/colors.md](tokens/colors.md) | Semantic colour roles and the palettes |
| [components/button.md](components/button.md) | `UkptButton` |

## Status: a scaffold, not an identity

This system ships with the template, so its palette is a deliberately neutral greyscale and its
typography uses system faces. That is **placeholder, not design**: it renders honestly and is
snapshot-tested, but it carries no identity.

Authoring the identity is the first real design task on a new project — the palette, the type
scale, the typefaces, the principles' wording, and the prohibition list. The `ukpt-design-system`
skill drives that, including renaming the `Ukpt` prefix to the project's own name.

Every page below is written to be edited, not just read.

## What to build primitives on

The scaffold's one primitive is built from bare `foundation` — a `Box` with `clickable` — and that
is a **scaffold choice, not a recommendation**. It keeps the template from imposing a component
library on every project that starts from it. Most real applications should pick one of these
instead, and should pick it early, because converting primitives later is a rewrite:

| Basis | You get | You pay |
|---|---|---|
| **Material3** | Correct semantics, minimum touch targets, state layers, ripple, focus handling and a11y, for free. Restyle through `ButtonDefaults`/`ButtonColors`, shapes and typography. | Material's structural opinions — internal padding, sizing, its component vocabulary — which can be stubborn to override when the identity diverges. |
| **Compose Unstyled** | Behaviourally complete primitives with no visual opinion: the interaction and accessibility work is done, the styling is entirely yours. | A dependency, and a smaller component set than Material's. |
| **From scratch** (what this scaffold does) | Total control; nothing to override. | You re-implement interaction and accessibility yourself, and the omissions are the kind no visual review catches. |

That last cost is not hypothetical. `UkptButton` has to set `role = Role.Button` by hand precisely
because a bespoke clickable `Box` announces nothing to a screen reader — and it still lacks the
minimum touch target, focus indication and state layers a Material button would have given it. Read
[components/button.md](components/button.md) with that in mind: it is a worked example of the
*shape* of a primitive, not a finished component.

Basing primitives on Material3 composes cleanly with the rest of the system, because
[`UkptTheme`](../src/commonMain/kotlin/platform/design/UkptTheme.kt) already wraps a `MaterialTheme`
derived from the tokens — a material3 `Button` inside `UkptTheme` picks up the palette and type
scale automatically. If you take that route, change `material3` from `implementation` to `api` in
the module's `build.gradle.kts`, since material types will then appear in the primitives' own
surface.

## How the docs stay true

Every image on these pages is a committed Paparazzi golden produced by a hand-written **doc-surface
test** in `src/androidHostTest/`. Docs and code therefore cannot drift: change a component's
appearance and the golden changes with it.

`DesignSystemDocImagesTest` fails the build if a page links to an image that does not exist, so a
renamed test method cannot silently leave a hole in a page.

When a page and the code disagree, **trust the code and fix the page**.

Doc surfaces are deliberately *not* `@Preview`-driven, unlike feature-module snapshots. A preview is
one state of a real screen; a doc surface is a curated composition that exists only to be read — a
labelled grid of every variant in both palettes. Their names are load-bearing because pages
reference them, and they stay in the test source set so they never ship in the artifact.

## Working on the system

```bash
# Re-record after changing a token or a component's appearance
./gradlew :platform:client:design:recordPaparazzi --no-configuration-cache --max-workers=2

# Verify goldens still match
./gradlew :platform:client:design:verifyPaparazzi --no-configuration-cache --max-workers=2
```

`--no-configuration-cache` is required: Paparazzi's resource-preparation task cannot be stored in
the configuration cache.

## Rules

- Read [principles.md](principles.md) and the relevant page before changing anything visual.
- Re-record snapshots in the same change that alters a token or a component's appearance — never
  as a follow-up.
- A new primitive is a discussion, not a commit. Prefer a new variant on an existing one.
- Every page ends in a Rules section. If a page has nothing to forbid, it is describing an
  implementation detail rather than a design decision.
