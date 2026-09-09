---
name: ukpt-architecture-review
description: >-
  Review a UKPT feature or screen for semantic architecture rules that static
  verification cannot prove: asynchronous ViewModel state, domain read
  projections, loading/error rendering, dialog-as-destination boundaries, and
  domain contract shape on client and server (interfaces that mirror storage
  calls, read families with one consumer, UseCase and ServiceImpl fan-in). Use
  for explicit architecture reviews, large ViewModel/State refactors, changes
  that add several domain interfaces, or codebase audits; not for ordinary
  compile/test verification.
---

# ukpt-architecture-review

Semantic review of the architecture rules `verifyArchitecture` cannot prove: the Client UI State
rules, and the domain contract guidance on client and server. Enforced rules and compilation
belong to `ukpt-verify`.

## Procedure

1. Read `AGENTS.md`, `UKPT.md`, and only the generated architecture pages for the layers under
   review (`platform/common/architecture/docs/clientui.md`, `clientdomain.md`, `serverdomain.md`,
   `serverdata.md`).

2. Run verification and audit:
   ```
   ./gradlew verifyArchitecture --max-workers=2
   ./gradlew auditArchitecture --max-workers=2
   ```
   `verifyArchitecture` proves enforced rules and prints a one-line advisory audit summary.
   `auditArchitecture` prints the full advisory report (written to the build directory as
   `audit.md` under `reports/architecture/`).
   Advisory findings are review prompts — act on those touching the code under review; a finding
   is not a proven violation. The report carries the per-feature domain inventory
   (`ClientDomain.inventory`, `ServerDomain.inventory`) and grouped domain-interface candidates
   (read families with one consumer, high fan-in, update families, uninjected interfaces,
   one-dependency UseCases), each with its declarations as evidence: start the domain contract
   inventory in step 4 from those rows.

3. For every selected Screen/ViewModel/State, produce an **async-state inventory**:

   | Field or field-group | Source (Flow, suspending op, sync UI input, navigation state) | Empty/null a legitimate success? | Required vs auxiliary | Consistency/failure boundary | How loading, error, retry, and cancellation render |
   |---|---|---|---|---|---|

4. For every domain interface the change adds, touches, or consumes, produce a **domain contract
   inventory**. Walk the whole composition chain: the ViewModel or ServiceImpl, every UseCase
   under it, and the Repository properties under those. A review that stops at the aggregate
   misses the family of inputs that exist only to feed it.

   | Contract or group | Provider | Production consumers, and whether any uses it alone | Scope, freshness, failure and retry boundary | Reason to stay separate, or the proposed aggregate |
   |---|---|---|---|---|

   Ask of each row:
   - Is it used independently anywhere, or always as part of one set?
   - Do its lifecycle and failure semantics differ from its neighbours'?
   - Does it name a capability, or repeat the shape of its source (one storage call, one field,
     one implementation step)?
   - Would one immutable projection keep the required distinctions with fewer contracts?
   - Did the change remove boundaries, or move them behind a facade, a package, or a dependency
     container?

5. Flag these patterns in the code under review:

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
   - A group of reads from one Repository whose only consumer is one UseCase, ViewModel, or
     ServiceImpl (`ClientDomain.DomainInterface.readProjections`,
     `ServerDomain.DomainInterface.readProjections`).
   - A UseCase, ViewModel, or ServiceImpl constructor taking many domain interfaces from one
     provider (`ClientDomain.DomainInterface.namesACapability`,
     `ServerDomain.DomainInterface.namesACapability`).
   - A mutation followed by a read of the same model, or a mutation returning an identifier when
     the caller needs the model (`ClientDomain.DomainInterface.mutationResults`,
     `ServerDomain.DomainInterface.mutationResults`).
   - A UseCase that forwards one dependency, or an orchestration phase with one caller published
     as its own interface (`ClientDomain.UseCase.breakDownComplexUseCases`,
     `ServerDomain.UseCase.breakDownComplexUseCases`).
   - Repository properties that are one storage call plus a mapping, with their results assembled
     downstream (`ServerData.Repository.mayInjectStorage`).

6. When several required sources form one concept, recommend a read projection (a `FlowOf...` or
   `Get...` domain interface returning an immutable data class) provided by the Repository that
   owns the storage, or by a UseCase when the inputs are independent capabilities. `:feature:core`'s
   `GreetingSummary`/`FlowOfGreetingSummary` is the worked example.

7. Verify that Loading, Error, populated Success, and legitimately-empty Success
   previews/snapshot tests exist for each async screen.

8. **Findings vs authorization:** a review request reports; only a change request implements
   and verifies.

## Closing checklist

- [ ] Screen branches on the required `AsyncState` near its top level.
- [ ] Loaded content receives a non-null domain object from the Success branch of the `AsyncState`.
- [ ] Loading and Error are distinguishable from legitimate empty data.
- [ ] The async owner of inline optional data is visible at the call site.
- [ ] Remaining calculated state combines data or encodes a decision — not a renamed proxy.
- [ ] No `orEmpty()` or default value invents a plausible loaded state.
- [ ] Success, loading, and error surfaces are covered by previews or snapshot tests.
- [ ] Every domain interface in the inventory has a consumer that uses it alone, or a stated
      boundary that keeps it separate.
- [ ] No family of reads exists only to feed one aggregate.
- [ ] Mutations return what their caller needs next, and nothing a caller discards.
