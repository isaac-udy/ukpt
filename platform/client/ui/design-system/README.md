# Design system

The design system for this project: its tokens, its primitives, and the rules that keep them
coherent. It lives in `:platform:client:ui` (package root `platform.ui`) and is consumed by feature
modules as `implementation(projects.platform.client.ui)`.

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
./gradlew :platform:client:ui:recordPaparazzi --no-configuration-cache --max-workers=2

# Verify goldens still match
./gradlew :platform:client:ui:verifyPaparazzi --no-configuration-cache --max-workers=2
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
