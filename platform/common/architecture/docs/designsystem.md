> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/designsystem/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Design System Rules](../src/main/kotlin/architecture/rules/designsystem/DesignSystemRules.kt)

The design system lives in `:platform:client:ui` (package root `platform.ui`): a token layer,
the primitives built on it, and a `design-system/` docs folder whose every image is a committed
Paparazzi golden. Its own conventions are documented there — these two rules are the boundaries
that the architecture suite can check.

The first keeps the module lean. The second is the contract in the other direction: features
read tokens rather than restating values.

##### Rules

* The design-system module must not depend on navigation or dependency injection
    * **Why:** The design system is a pure Compose module: tokens and stateless primitives. Pulling in navigation or DI turns it into application infrastructure, and every consumer then inherits those dependencies to draw a button. Other client-side infrastructure belongs in sibling `:platform:client:*` modules, not here.
    * **Note:** A primitive that seems to need navigation is usually missing a callback parameter — the caller navigates, the primitive reports.

##### Guidance

* A feature's `ui` package should read colours and dimensions from the design system rather than declaring them literally
    * **Note:** Audited rather than enforced: a literal is occasionally right — a one-off illustration, an aspect ratio — and the judgement is easier to make in review than in a rule.
    * **Note:** A value the tokens don't have is a signal the palette or scale is missing a role. Add the role rather than the literal, so a theme change reaches it.
    * **Audited:** a test reports non-conforming code without ever failing.
