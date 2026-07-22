# Principles

The rules that keep the system coherent. They are meant to be *reworded* for each project — the
wording below is the template's, and a real identity will state them in its own terms — but the
shape of each one generalises.

## The identity is load-bearing

When a screen feels off, lean **into** the identity, not away from it. The instinct to reach for a
neutral, "safe" treatment is what turns a designed product into a default one. If the identity is
genuinely wrong for a case, change the identity deliberately and re-record every golden — do not
carve out a local exception.

*(This template ships a neutral placeholder identity, so this principle has nothing to bite on yet.
It becomes real the moment a project authors its palette and typefaces.)*

## Narrow surface area, on purpose

Every primitive is the only one of its kind. There is one button, not a `PrimaryButton`, a
`SmallButton` and a `TextButton`. Growth means **a new variant on an existing primitive**, not a new
primitive.

A new primitive is a discussion before it is a commit. The question to answer is not "is this
useful?" — it always is — but "which existing primitive should have absorbed this, and why can't
it?"

## One density

Spacing is a fixed scale ([`UkptSpacing`](../src/commonMain/kotlin/platform/ui/UkptSpacing.kt)). It
is not theme-scoped and it is not a user preference. Layouts adapt by changing *which* layout
renders at a breakpoint, never by shrinking every gap.

Values between steps are a smell. Reach for the nearest step.

## Stateless components, scaffolded surfaces

Primitives render props and report events. They own no state, and the system exposes no
`rememberSomething()` API that hides state inside a component.

The caller already has the state. A component that keeps its own creates a second source of truth,
and the two disagree exactly when it matters.

## Tokens cascade; literals don't

Every colour, dimension and text style in feature code resolves to a token via `UkptTheme.*`. A raw
`Color(0xFF…)` or a bare `.dp` in a feature is a bug in the system, not a shortcut: it is a value
that a palette swap will silently miss.

If a component needs a value the tokens don't have, the palette is missing a role. Add the role —
and if it is an addition the design spec never defined (a scrim, a max dialog width), say so in a
comment naming whose judgement the value is, so the system stays explainable line by line.

## The system says no, on purpose

An explicit list of things this system will not do. It is as load-bearing as the tokens: without it
every "just this once" is arguable.

- No new primitive without a discussion.
- No literals in feature code.
- No per-screen spacing scales.
- No component-owned state.
- No colour-only signalling — see below.

*(A real project extends this list as it makes decisions. The list is most useful when it records
the argument that was actually had.)*

## Never colour alone — and never visual alone

Meaning carried only by colour is invisible to a large number of users. Pair it with text, an icon,
or a shape.

The accessibility corollary is easy to miss: when a control stops being interactive — a locked
selection, a submitted answer — the natural implementation drops the `selectable`/`clickable`
modifier, which silently deletes *which option was chosen* from the accessibility tree even though
it is still plainly visible on screen. A locked state must keep that fact, via an explicit
`stateDescription`.

Interactive primitives also need an explicit `role`: a bespoke clickable `Box` announces nothing to
a screen reader without one.

## The docs and the code can't drift

Every visual in these pages is a committed golden from a doc-surface test. When a page and the code
disagree, the code is right and the page is stale.

## Rules

- A new primitive requires a discussion; a new variant does not.
- No literal colours, dimensions or text styles in feature code — always `UkptTheme.*`.
- Components stay stateless; state belongs to the caller.
- Meaning never rides on colour alone, and an unavailable control keeps its information in
  semantics.
- Re-record goldens in the same change that alters appearance.
- State a rule, not an instance: a page that says *why* a treatment exists travels to the next
  component; one that only describes the treatment does not.
