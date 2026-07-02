> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Provided by the udytils architecture system. To override it, add a file named `authoring.md` next to this project's rule definitions.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# Authoring rules

A guide for adding rules to this project. Rules are declared in Kotlin code (see the
[README](../README.md)); this guide explains which declaration type to use and how to word
the documentation.

## Choosing a declaration type

Work down this list and use the first type that fits:

1. **Requirement:** decides whether a declaration *is* a particular Construct.
   - Requirements test identity, not correctness: declaration kind, name, package, annotations.
   - Requirements are declared in the Construct's constructor. They never fail on their own; a
     declaration that matches no Construct fails the RuleGroup's exhaustiveness test.
   - Use a requirement when a failure should mean "this is not an X", rather than "this X is wrong".
2. **Rule:** a mandatory statement about a Construct or RuleGroup.
   - If the statement can be tested, declare it with `rule { constrain { … } }` (applies to each
     matching declaration), `rule { scope { … } }` (applies to the whole scope),
     `rule { moduleGraph { … } }` (applies to module dependencies), or `rule { enforcedBy(…) }`
     (another Rule already enforces it).
   - If the statement cannot be tested, declare it with `rule { unverifiable() }`. It is still a
     Rule: it is enforced by review, and its documentation carries a "not automatically
     verifiable" note. Add a `note("…")` explaining why it can't be tested if that isn't obvious.
3. **Guidance:** an advisory statement about a Construct or RuleGroup.
   - Declare it as `@Describe("…") val x by guidance`, or use `guidance { … }` to add notes,
     rationale, or an audit.
4. **Audit:** a test that reports findings without ever failing.
   - Attach an audit to Guidance, or to an unverifiable Rule, when a heuristic exists but is too
     brittle to fail the build, or when a permission should be watched.
   - Findings appear in the test output under `<rule> [audit]`.
   - Declare it with `audit { … }` (Construct Guidance), `auditModuleGraph { … }` (RuleGroup
     Guidance), or `unverifiable { … }` (Rules).

Two questions resolve most cases:

- "If code breaks this, should the build fail?" Yes, and it can be tested: a tested Rule. Yes,
  but it can't be tested: an unverifiable Rule. No: Guidance.
- "Would a declaration violating this still be an X?" No: a requirement. Yes: a Rule.

## Writing style

All rule documentation uses the same voice:

- Be short and direct. Use simple words.
- Only include information that is critical to understanding the rule. Leave out anything the
  reader would discover by running the tests.
- Make each section self-contained: provide the minimum context needed to understand it without
  reading the surrounding sections.
- Use "tests", not "checks". Use "documentation", not "docs". Write "ID", not "id".
- Capitalise the framework types when naming them: RuleGroup, Construct, Rule, Guidance.

How to word each element:

- **Statements** (`@Describe` on a Rule or Guidance property): a single sentence that reads
  standalone, such as "A Repository must not inject other Repositories". Start with
  "A/An <noun>" for Construct-level statements, or "The <name> layer" / "A <thing>" for
  RuleGroup-level statements. Rules use must, never, or may only; Guidance uses may or should.
  If you find yourself writing "must" in Guidance, it is a Rule.
- **Requirement descriptions:** subject-free verb phrases, such as "is a class" or "is named
  `[Name]Repository`". The documentation generator prefixes the Construct as the subject
  ("A Repository is a class").
- **`rationale("…")`:** why the rule exists. Rendered as a single line under **Why**, so write
  it as one flowing sentence.
- **`note("…")`:** a caveat or pointer. Rendered as a single line. Multiple notes are fine;
  keep each self-contained.
- **Object `@Describe`** (on a RuleGroup or Construct): what the thing is and why it exists.
  No `$` may appear in any `@Describe` text (Kotlin string interpolation); code that needs `$`
  belongs in an examples file.
- **Examples** (`<Construct>.examples.md` beside the Construct's `.kt` file): one context
  sentence per code block, no headings above `###`.

## Adding a new Construct

1. Create `<Name>.kt` in the RuleGroup's package: `object <Name> : Construct<Group>(requirements = listOf(…))`,
   annotated with an object-level `@Describe`.
2. Add it to the RuleGroup's `constructs = listOf(…)` in the position it should appear in the
   documentation. The tests fail if a Construct is missing from this list.
3. Optional: add `<Name>.examples.md` beside it.
4. Regenerate the documentation and commit it with the change:

```
./gradlew :platform:common:architecture:updateArchitectureDocumentation
```
