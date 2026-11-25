# Wording rules for comments and documentation

[`docs/code-comments.md`](../code-comments.md) has a new "Wording" section, and `UKPT.md` points
to the section. The rules apply to comments, KDoc, documentation, commit messages and PR
descriptions:

- State the component, the action and the condition, with a literal verb: "the parser requires a
  terminator", not "the parser wants a terminator".
- State the actor in place of "nothing" or "nobody" as a subject.
- Repeat the noun in place of "it", "this" and "they", unless the noun is the subject of the same
  sentence and no other noun could be meant.
- A counting word keeps its noun: "each migration", "all three modules".

The template's own comments, skills and rule catalog were rewritten to follow the rules. The
rules are a convention; no architecture rule checks them.

## Detection

Both files are template-owned. A non-zero exit means the file sync has not run:

```bash
grep -q "^## Wording" docs/code-comments.md && grep -q "code-comments.md#wording" UKPT.md
```

## Migration

1. Sync `docs/code-comments.md` and `UKPT.md` from the template. No hand-editing is required.
2. Apply the rules to new and modified text from this version on.
3. If `AGENTS.md` or another project-owned file contains wording guidance, delete the parts that
   duplicate the "Wording" section.

## Optional: rewrite existing text

A project may skip this step. The rewrite changes many files, and the rewrite changes no
behaviour.

Ask the project owner before starting. If the project owner agrees:

1. Make the rewrite its own pull request, separate from the template update.
2. List the candidate sentences in project-owned files:

   ```bash
   git ls-files -- '*.md' '*.kt' '*.kts' ':!embedded-enro' ':!embedded-udytils' \
     ':!platform/common/architecture/docs' ':!docs/template-migrations' |
     xargs grep -nEiw "wants|asks|reach(es)?|arrives?|lands?|landed|survives?|holds?|carr(y|ies|ied)|spends|costs?|earns?|remembers|nothing|nobody"
   ```

3. Rewrite each listed sentence where the word is a metaphor or attributes an intention to
   software. Leave literal uses unchanged ("the invariant holds", "the function returns
   nothing"). Change only prose: no code, identifiers, rule IDs or strings that a test asserts.
4. If the project has its own `@Describe` text in the rule catalog, regenerate the documentation:
   `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

## Verification

No mechanical check exists. After an optional rewrite, run `./gradlew verifyArchitecture` and
compile the modules whose Kotlin files changed, because `@Describe` text is inside Kotlin string
literals.
