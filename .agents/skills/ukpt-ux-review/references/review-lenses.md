# Review lenses

Use these prompts to investigate an actual task. They are a curated application of UX principles,
not an exhaustive catalogue of laws. Link a relevant source when a finding depends on that
principle; the application evidence must still carry the finding.

## Journeys and decisions

- Trace what brings the user here, what they expect, and where success, cancellation, and failure
  leave them. After a detour, can they resume their original task with their work intact?
- Identify decisions required before the user receives value. Can a safe, editable default remove
  needless input? Would deferral merely move an unavoidable decision somewhere less understandable?
- Compare the meaning of choices, not just their count. Remove equivalent choices and explain
  consequential differences; do not hide useful options simply to shorten a menu.
  [Hick's law](https://lawsofux.com/hicks-law/) helps reason about decision effort, not impose a
  universal maximum number of controls.
- Look for repeated explanations, terminology introduced before it is useful, and instructions
  that users must remember across screens. Put help beside the action it supports and preserve
  information needed for an informed choice. [Cognitive load](https://lawsofux.com/cognitive-load/)
  is a useful lens for identifying mental work unrelated to the task.
- Defaults must not invent personal information, consent, equipment suitability, or business
  intent. When a recommendation depends on who uses the product or how often, identify the
  assumption and the evidence needed to establish it.

## Interaction and feedback

- Check whether users can tell what is active, saved, selected, still running, or unavailable.
  Make retries, corrections, and escape routes discoverable. Check what happens when an optional
  capability fails and when an operation is interrupted.
- Review whether controls match the meaning of the action, and whether the same action remains
  recognisable across states. Check keyboard focus, selection semantics, names, and announcements
  in code or runtime as appropriate.
- Evaluate target acquisition in context: input method, effective hit area, separation, reach,
  and movement between repeated actions. [Fitts's law](https://lawsofux.com/fittss-law/) supports
  that investigation; visual size alone does not prove accessibility or usability.
- Check acknowledgment, progress, completion, and recovery separately. Timing claims require
  measurements. Treat named timing thresholds as context for responsiveness, not universal SLAs.
- User control, status visibility, recognition, error prevention, and recovery also draw on
  [Nielsen's usability heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/).
  A useful finding need not fit a named UX law.

## Visual polish

- Identify the primary action and reading order. Does emphasis guide attention to the task, or
  do secondary actions, status labels, and explanatory cards compete with it?
- Use proximity, common region, and similarity to investigate ambiguous relationships. Borders
  and cards are possible tools, not mandatory decoration. Whitespace can clarify grouping;
  an empty area is not automatically wasted space.
- Compare equivalent controls and states against the project's tokens and primitives. Distinguish
  meaningful hierarchy from accidental differences in size, placement, language, or treatment.
- Inspect typography, wrapping, alignment, density, truncation, and content extremes at relevant
  viewports. Identify which sizes, themes, text scales, and input conditions were actually viewed.
- Preserve the authored visual identity. Attractive presentation can help perceived usability,
  but does not establish task success. Taste-based proposals need an articulated visual benefit
  and should be labelled as design judgment.

The grouping, familiarity, attention, and aesthetic lenses above are drawn from the
[Laws of UX collection](https://lawsofux.com/). The
[UX Design Institute overview](https://www.uxdesigninstitute.com/blog/laws-of-ux/) and
[IDNZ collection](https://www.id.ac.nz/idnz-the-ux-laws) provide additional context. Consult a
specific principle when relevant rather than importing every rule into every review. For example,
do not turn working-memory findings into a seven-item menu limit or an 80/20 heuristic into a
claim about this application's usage. Explain conflicting considerations and choose according to
the task and evidence.
