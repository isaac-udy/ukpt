> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Provided by the udytils architecture system. To override it, add a file named `authoring.md` next to this project's rule definitions.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# Authoring rules

A guide for adding rules to this project. Rules are declared in Kotlin code (see the
[README](../README.md)); this guide explains how to structure and word them.

All rule documentation uses the same voice:

- Be short and direct. Use simple words.
- Only include information that is critical to understanding the rule. Leave out anything the
  reader would discover by running the tests.
- Make each section self-contained: provide the minimum context needed to understand it without
  reading the surrounding sections.
- Use "tests", not "checks". Use "documentation", not "docs". Write "ID", not "id".
- Capitalise the framework types when naming them: RuleGroup, Construct, Rule, Guidance.
- No `$` may appear in any `@Describe` text (Kotlin string interpolation); code that needs `$`
  belongs in an examples file.

Two questions resolve most cases when deciding what to declare:

- "If code breaks this, should the build fail?" Yes, and it can be tested: a tested Rule. Yes,
  but it can't be tested: an unverifiable Rule. No: Guidance.
- "Would a declaration violating this still be an X?" No: a requirement. Yes: a Rule.

---

## RuleGroups

A RuleGroup names and defines a set of Constructs, Rules, and Guidance for one layer of the
architecture.

- Declare it as `object <Name> : RuleGroup(inPackage = "…", constructs = listOf(…))`, annotated
  with an object-level `@Describe` that explains what the layer is and why it exists.
- `inPackage` scopes the group to a package pattern. A scoped group gains an exhaustiveness
  test: every top-level declaration in the package must match exactly one of the group's
  Constructs.
- `constructs = listOf(…)` is the documentation order.
- Rules and Guidance that are not tied to one Construct are declared as properties on the group
  object.
- A `<Group>.examples.md` beside the group's `.kt` file holds layer-level examples.

---

## Constructs

A Construct names and defines the Rules and Guidance for a code-level construct (a class,
interface, function, or property). Declare it as
`object <Name> : Construct<Group>(requirements = listOf(…))`, annotated with an object-level
`@Describe` that explains what the shape is and when to use it.

### Requirements

Requirements decide whether a declaration *is* the Construct. They test identity, not
correctness: declaration kind, name, package, annotations.

- A requirement never fails on its own; a declaration that matches no Construct fails the
  RuleGroup's exhaustiveness test.
- Use a requirement when a failure should mean "this is not an X", rather than "this X is
  wrong".
- Requirement descriptions are subject-free verb phrases, such as "is a class" or "is named
  `[Name]Repository`". The documentation generator prefixes the Construct as the subject
  ("A Repository is a class").

### Adding a new Construct

1. Create `<Name>.kt` in the RuleGroup's package: `object <Name> : Construct<Group>(requirements = listOf(…))`,
   annotated with an object-level `@Describe`.
2. Add it to the RuleGroup's `constructs = listOf(…)` in the position it should appear in the
   documentation. The tests fail if a Construct is missing from this list.
3. Optional: add `<Name>.examples.md` beside it, with one context sentence per code block and no
   headings above `###`.
4. Regenerate the documentation and commit it with the change:

```
./gradlew :platform:common:architecture:updateArchitectureDocumentation
```

---

## Rules

A Rule is a mandatory statement about a Construct or RuleGroup. Declare it as a property:
`@Describe("…") val x by rule { … }`.

- The statement is a single sentence that reads standalone, such as "A Repository must not
  inject other Repositories". Start with "A/An <noun>" for Construct-level statements, or
  "The <name> layer" for RuleGroup-level statements. Rules use must, never, or may only.
- If the statement can be tested, end the `rule { }` block with `constrain { … }` (applies to
  each matching declaration), `scope { … }` (applies to the whole scope), `moduleGraph { … }`
  (applies to module dependencies), or `enforcedBy(…)` (another Rule already enforces it).
- If the statement cannot be tested, end it with `unverifiable()`. It is still a Rule: it is
  enforced by review, and its documentation carries a "not automatically verifiable" note. Add
  a `note("…")` explaining why it can't be tested if that isn't obvious.
- `rationale("…")` records why the Rule exists. Rendered as a single line under **Why**, so
  write it as one flowing sentence.
- `note("…")` records a caveat or pointer. Rendered as a single line. Multiple notes are fine;
  keep each self-contained.

---

## Guidance

Guidance is an advisory statement about a Construct or RuleGroup. Declare it as
`@Describe("…") val x by guidance`, or use `guidance { … }` to add notes, rationale, or an
audit.

- Guidance uses may or should. If you find yourself writing "must", it is a Rule.
- The statement, `rationale`, and `note` wording rules for Rules apply to Guidance too.

### Audits

An audit is a test that reports findings without ever failing.

- Attach an audit to Guidance when a heuristic exists but is too brittle to fail the build, or
  when a permission should be watched ("allowed, but keep these minimal").
- Findings appear in the test output under `<rule> [audit]`.
- Declare it with `audit { … }` (Construct Guidance) or `auditModuleGraph { … }` (RuleGroup
  Guidance). An unverifiable Rule may also carry an audit, via `unverifiable { … }`.
