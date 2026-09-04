---
name: ukpt-ux-review
description: >-
  Review a UKPT application's user journeys, interactions, and visual polish
  using application code, snapshots, and available runtime evidence. Use for
  UX/UI reviews scoped to branch changes, a feature, the whole application,
  or a journey spanning features. Recommend concrete improvements and
  worthwhile redesign alternatives, grounded in UX principles and the
  project's design system.
---

# ukpt-ux-review

Produce a prioritised review that helps someone improve the experience of using the application.
Review requests produce suggestions; implement changes when the user requests implementation.
Preserve the project's identity and product intent. Treat the user's suspected problems as
hypotheses to investigate, including looking for evidence that contradicts them.

## Establish the scope and user task

Read the target repository's AGENTS.md and UKPT.md, its actual design-system principles, and
relevant product context. Infer the intended user, task, device, and environment from those
sources and state material assumptions. Ask only when an unknown would materially change the
review; continue independent inspection while awaiting the answer.

Translate the requested scope into journeys, screens, states, and shared components:

- **Branch compared with a base:** resolve the requested base and merge base, inspect committed
  changes since that point, and state how local edits are treated. Trace changed components into
  their affected callers and journeys. Distinguish introduced or worsened issues from pre-existing
  observations; use base evidence before claiming a regression. Do not switch or reset the user's
  checkout to obtain it.
- **Feature:** include entry, normal progression, relevant alternate states, exits, recovery,
  completion, and the immediate handoff into adjacent features.
- **Whole application:** inventory journeys and shared patterns first, then work through them in
  coherent groups. Track coverage so a sample is not represented as an exhaustive audit. Report
  shared causes once, with representative instances and their reach.
- **Journey or concern across features:** follow the user's goal across module boundaries, including
  interruptions and return paths. A paywall review, for example, follows discovery, the offer,
  purchase or dismissal, failure, and resumption of the originating task.

Inspect adjacent code when necessary to explain an in-scope experience. Keep unrelated observations
out of the main findings. Record the reviewed revision and relevant working-tree state.

## Gather evidence

Use [ukpt-ui-atlas](../ukpt-ui-atlas/SKILL.md) to consult the UI map and locate snapshot variants.
Check the manifest's age against current source: its navigation edges are heuristic and may omit
managed flows or retain old names. Confirm consequential edges and conditions in code.
If generation is unavailable or outside the permitted review environment, use targeted source and
golden discovery and record that limitation.

Actually view the relevant images. Follow screen events through state, ViewModel, and navigation
code far enough to understand their user-visible effects. Include empty, loading, error, disabled,
completed, permission, and interrupted states where relevant, rather than only default previews.
Treat missing variants as coverage gaps, not proof of bad behaviour.

Use [ukpt-drive-app](../ukpt-drive-app/SKILL.md) when available runtime inspection can resolve a
material uncertainty. Respect host-session restrictions and the review's authorised side effects.
Hardware-dependent behaviour needs the relevant device; a desktop walkthrough cannot validate a
phone's gestures, haptics, or screen-off behaviour.

Distinguish rendered evidence, source-established behaviour, and predictions about users:

- A golden shows a particular rendered state, not necessarily the current application. Check
  provenance and source alignment; do not re-record tracked goldens merely to make a review pass.
- A source handler establishes what an action requests, not that persistence, timing, focus,
  accessibility announcements, or device integration works at runtime.
- A visible button's dimensions are not necessarily its effective hit target. A screenshot cannot
  establish keyboard operation, screen-reader behaviour, or measured contrast ratios.
- If source and snapshots disagree, describe the discrepancy and how to resolve it. Do not silently
  choose one as proof of current runtime behaviour.
- State predicted user impact as an inference unless usability research or analytics establishes it.
  Never invent abandonment, conversion gains, timing, or usage frequency.

## Review the experience

Read [review lenses](references/review-lenses.md) and apply the relevant prompts to the user task.
Cover journey, interaction, and visual presentation together. The laws provide explanatory lenses;
they are not a compliance checklist or a requirement to produce a finding for every principle.

For each candidate, trace: **observation → user consequence → concrete improvement → validation**.
Check counterevidence: an existing shortcut, fallback, deliberate design choice, or different
platform behaviour may reduce or remove the concern. Check whether two differently labelled
actions really have different effects, and whether familiar labels accurately describe saving,
skipping, cancellation, and completion.

Suggest substantial layout or flow changes when the expected user benefit reasonably justifies
the implementation cost and disruption. Explain the gain, the supporting evidence, the tradeoff,
and why a smaller change would not achieve enough of it. Do not recommend a screen rewrite for a
minor preference. Include worthwhile polish opportunities even when nothing is functionally broken.

Prefer improvements that use the existing design system. Explain any proposed departure in terms
of the user's task. [ukpt-design-system](../ukpt-design-system/SKILL.md) governs implementing token
or primitive changes; [ukpt-architecture-review](../ukpt-architecture-review/SKILL.md) handles
semantic architecture audits. Keep this review focused on user-visible consequences.

## Deliver a review people can act on

Lead with the most consequential findings, then distinguish polish opportunities and any larger
alternatives. Scale the report to the scope; do not pad it to meet a finding quota. An alternative
may address existing findings rather than being another independent problem.

For each substantive finding, include:

- A concrete title and affected journey/state.
- Evidence linked to a source location, snapshot, or observed runtime step.
- The likely consequence for the user, with priority and confidence stated separately.
- A specific change: proposed wording, default, layout, or interaction, not just “simplify”.
- Approximate effort or material tradeoffs, and a way to validate the improvement.

Use priority to express user impact and reach: blocked tasks and misleading consequential choices
usually outrank extra effort, which usually outranks minor visual refinements. A rare blocker can
still matter. Use confidence to express the strength and limits of the evidence; avoid numerical
scores that imply measurements the review did not make.

Mention existing strengths when they constrain the recommendation or should be preserved. Finish
with reviewed coverage, material gaps, and the most useful next validation steps. A repository
review is not a claim that real users have been tested. When implementation is requested, follow
[ukpt-verify](../ukpt-verify/SKILL.md) and inspect the changed experience as well as test results.
