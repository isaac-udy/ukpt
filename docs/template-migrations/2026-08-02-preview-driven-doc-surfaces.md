# Design-system doc surfaces are preview-driven

The design module's doc surfaces — the curated compositions whose goldens the `design-system/`
pages embed — were the one place the template ran a second snapshot style: hand-written JUnit tests
on `SnapshotRule`, with Paparazzi's stock flat golden naming (`<package>_<Class>_<method>.png`),
documented as deliberate. They are now `@Preview` composables discovered by the same preview-driven,
directory-grouped pipeline the feature modules use, with two module-local pieces: a
`PreviewSnapshotTest` that renders in `RenderingMode.SHRINK`, and a fixed-size `DocSurface` root
container in the test source set that sets each golden's exact canvas — SHRINK crops to the
container, behaviourally identical to the old `SnapshotRule.screen(width, height)`. Preview
function names take over the load-bearing role the test-method names had, guarded as before by
`DesignSystemDocImagesTest`. `SnapshotRule` remains the escape hatch for snapshots a parameterless
`@Preview` genuinely cannot express — it is no longer the default for anything.

## Detection

A project is affected if its design module still carries hand-written doc-surface tests:

```bash
ls <design-module>/src/androidHostTest/snapshots/images/*_*_*.png 2>/dev/null
grep -rln "SnapshotRule" <design-module>/src/androidHostTest
```

Flat-named goldens or `SnapshotRule` classes under the design module's `androidHostTest` → migrate.
No matches → this migration is a no-op (the project has no doc pipeline, or already converted).

## Migration

Follow the step order — it keeps `DesignSystemDocImagesTest` green at every point:

1. `git mv` each doc-surface test file to a non-`Test` name (e.g. `<X>DocSurface.kt`) and replace
   the test class with `@Preview` functions wrapping each sheet in
   `DocSurface(colors, width, height)`. Copy each `screen(width, height)` call's geometry onto the
   matching preview; a `component()` call was content-sized, so pick an explicit size that fits and
   check the recorded PNG. Sheet composables are untouched.
2. Add `DocSurface.kt` and the SHRINK-mode `PreviewSnapshotTest` from the template's
   `platform/client/design/src/androidHostTest/`, renaming `Ukpt` to the project's type prefix, and
   add `implementation(compose.preview)` to the design module's `androidHostTest` dependencies.
3. **Record before repointing the docs** —
   `./gradlew <design-module>:recordPaparazzi --no-configuration-cache`. The old flat goldens and
   old doc references stay in place for this run, so the doc-images test never sees a gap. Where a
   preview copied a `screen(width, height)` geometry, the new golden should be pixel-identical to
   the old one — same composition, same device config, same SHRINK crop (verifiable by PNG→BMP
   conversion and `cmp`, if wanted).
4. Repoint every markdown image embed from the old flat name to
   `…/snapshots/images/<package-dirs>/<PreviewName>.png`, and grep the docs for the old test-class
   names too — the doc-images test only checks images, so prose references to the renamed files are
   not caught by it. Delete the flat goldens.
5. `./gradlew <design-module>:verifyPaparazzi --no-configuration-cache` — green means goldens and
   doc references both hold. Update any project-owned guidance (AGENTS.md) that restates the old
   "doc surfaces are hand-written, do not convert" rule.

## Verification

`verifyPaparazzi` is green; no `SnapshotRule` usage is left in the design module; no
`<package>_<Class>_<method>.png` goldens are left under the design module's snapshots; a grep for
the old test-class names finds nothing.
