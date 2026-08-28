---
name: ukpt-architecture-review
description: >-
  Review a UKPT feature or screen for semantic architecture rules that static
  verification cannot prove, especially asynchronous ViewModel state, domain
  read projections, loading/error rendering, and dialog-as-destination
  boundaries. Use for explicit architecture reviews, large ViewModel/State
  refactors, or codebase audits; not for ordinary compile/test verification.
---

# ukpt-architecture-review

Semantic review of Client UI architecture rules that `verifyArchitecture` cannot prove. This skill
covers the rules the checker audits or leaves unverifiable; enforced rules and compilation belong to
`ukpt-verify`.

## Procedure

1. Read `AGENTS.md`, `UKPT.md`, and only the generated architecture pages for the layers under
   review (`platform/common/architecture/docs/clientui.md`, `clientdomain.md`, etc.).

2. Run verification and audit:
   ```
   ./gradlew verifyArchitecture --max-workers=2
   ./gradlew auditArchitecture --max-workers=2
   ```
   `verifyArchitecture` proves enforced rules and prints a one-line advisory audit summary.
   `auditArchitecture` prints the full advisory report (written to the build directory as
   `audit.md` under `reports/architecture/`).
   Advisory findings are review prompts — act on those touching the code under review; a finding
   is not a proven violation.

3. For every selected Screen/ViewModel/State, produce an **async-state inventory**:

   | Field or field-group | Source (Flow, suspending op, sync UI input, navigation state) | Empty/null a legitimate success? | Required vs auxiliary | Consistency/failure boundary | How loading, error, retry, and cancellation render |
   |---|---|---|---|---|---|

4. Flag these patterns in the code under review:

   - Sentinel defaults standing in for "not loaded" (`ClientUi.ViewModelState.usesAsyncState`).
   - Manual progress/error pairs — a Boolean progress flag paired with an error-synonym sibling
     (`ClientUi.ViewModelState.noManualAsyncLifecycleFields`).
   - Lone progress-verb Boolean flags (`ClientUi.ViewModelState.usesAsyncState` audit).
   - Several independent collectors reconstructing one concept
     (`ClientUi.ViewModel.aggregateReadProjection`).
   - Required data rendered through `getOrNull()` fallbacks that make unavailable data look
     successfully empty (`ClientUi.Screen.asyncStateExhaustiveRendering`).
   - Calculated proxy getters that flatten an `AsyncState` back into nullable/default values
     (`ClientUi.ViewModelState.noFlattenedAsyncProxies`) — after any State adopts
     `AsyncState<Projection>`, explicitly search for proxy getters that flatten the projection.
   - Inline dialogs/sheets behind any wrapper, toggled by boolean flags in screen state
     (`ClientUi.ViewModelState.noDialogVisibilityFlags`).

5. When several required sources form one concept, recommend a client-domain read projection
   (a `FlowOf...` domain interface returning an immutable data class). `:feature:core`'s
   `GreetingSummary`/`FlowOfGreetingSummary` is the worked example.

6. Verify that Loading, Error, populated Success, and legitimately-empty Success
   previews/snapshot tests exist for each async screen.

7. **Findings vs authorization:** a review request reports; only a change request implements
   and verifies.

## Closing checklist

- [ ] Screen branches on the required `AsyncState` near its top level.
- [ ] Loaded content receives a non-null domain object from the Success branch of the `AsyncState`.
- [ ] Loading and Error are distinguishable from legitimate empty data.
- [ ] The async owner of inline optional data is visible at the call site.
- [ ] Remaining calculated state combines data or encodes a decision — not a renamed proxy.
- [ ] No `orEmpty()` or default value invents a plausible loaded state.
- [ ] Success, loading, and error surfaces are covered by previews or snapshot tests.
