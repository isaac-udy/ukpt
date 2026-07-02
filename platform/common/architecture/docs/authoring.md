> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Shipped by the architecture framework (`dev.isaacudy.udytils:architecture-core`); override it by adding `authoring.md` to the catalog root.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# Authoring rules

How to decide what kind of thing you're writing, and how to word it. The catalog is the source of
truth — see the [README](../README.md) for how the documentation is generated from it.

## The decision ladder

Work down this list; the first fit wins.

1. **Requirement** — *classification*. Answers "is this declaration this construct at all?"
   Identity, not correctness: declaration kind, name shape, package residence, annotations,
   structural markers. If failing the check should mean "this is **not** an X" (and let the
   layer's exhaustiveness/membership check deal with it), it's a requirement. Requirements have
   no ids and never fail on their own — they live in the `Construct(requirements = listOf(…))`
   header.
2. **Rule** — *correctness*, mandatory. Answers "given this **is** an X, is it well-formed?"
   The statement uses **must / never / may only**.
   - Testable → `rule { constrain { … } }` (construct-scoped), `rule { scope { … } }` (whole
     scope), `rule { moduleGraph { … } }` (module edges), or `rule { enforcedBy(…) }` when
     another rule already enforces it transitively.
   - Not testable → `rule { unverifiable() }`. It is still a rule — it renders under **Rules**
     with an automatic "not automatically verifiable" note and is enforced by review. Add a
     `note("…")` saying *why* it can't be tested when that isn't obvious.
3. **Guidance** — *advice or permission*. The statement uses **may / should**. Never use
   mandatory phrasing in guidance; if you find yourself writing "must", it's a rule (see above).
   Declared as `@Describe("…") val x by guidance` (block form for notes/rationale).
4. **Audit** — *visibility without enforcement*, attached to guidance or an unverifiable rule.
   A test that **reports without ever failing** — findings appear in the test output under
   `<rule> [audit]`. Use one when a heuristic exists but is too brittle to fail the
   build, or when a permission should be watched ("allowed, but keep these minimal"). Declared
   with `audit { … }` (guidance), `auditModuleGraph { … }` (group guidance), or
   `unverifiable { … }` (rules).

Two litmus tests: *"If code breaks this, should the build fail?"* — yes and testable → tested
rule; yes but untestable → unverifiable rule; no → guidance. *"Would a declaration violating
this still be an X?"* — no → requirement; yes → rule.

## Language

- **Statements** (`@Describe` on a rule/guidance property) are single sentences that read
  standalone, with no surrounding context: "A Repository must not inject other Repositories",
  "The `data` layer must not depend on the `ui` package". Start with "A/An <noun>" for
  construct-level entries or "The <layer> layer" / "A <thing>" for group-level ones. Rules use
  must/never/may only; guidance uses may/should.
- **Requirement descriptions** are subject-free, lowercase verb phrases — "is a class",
  "resides in `feature.[name].data`", "is named `[Name]Repository`" — because the doc generator
  prefixes the construct as subject ("A Repository is a class"). Never include the subject.
- **`rationale("…")`** is the *why* — the design pressure behind the rule. It renders as
  **Why** and is collapsed to one line, so write it as flowing prose, not bullets.
- **`note("…")`** is a caveat, nuance, or pointer ("the check skips `expect`/`actual` …").
  Also collapsed to one line. Several notes are fine; keep each self-contained.
- **Object `@Describe`** (on a group or construct) is narrative markdown: what the thing is and
  why it exists, `* **Note**:` bullets for conventions. No `$` may appear in any `@Describe`
  (Kotlin string interpolation) — code that needs `$` belongs in an examples file.
- **Examples** live in `<Construct>.examples.md` beside the construct's `.kt` (group-level:
  `<Group>.examples.md`): a one-line context sentence per fence, no headings above `###`, no
  markers. They render after the section's rules under **Examples**.

## Mechanics checklist for a new construct

1. Create `<Name>.kt` in the layer's package: `object <Name> : Construct<Group>(requirements = listOf(…))`
   with an object-level `@Describe`.
2. Add it to the group's `constructs = listOf(…)` **in the position it should render** — the
   list is the doc order. (Forgetting this fails the build: the meta-rule scans the sources.)
3. Optional: `<Name>.examples.md` beside it.
4. Regenerate the documentation and commit it with the change:

```
./gradlew :platform:common:architecture:updateArchitectureDocumentation
```
