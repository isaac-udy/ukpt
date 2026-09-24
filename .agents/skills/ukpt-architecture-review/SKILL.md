---
name: ukpt-architecture-review
description: >-
  Review a UKPT feature or page for semantic architecture rules that static
  verification cannot prove: web request flows (htmx and non-JavaScript
  answers, form errors, live lists), domain read projections, and domain
  contract shape (interfaces that mirror storage calls, read families with one
  consumer, UseCase and Routes fan-in). Use for explicit architecture reviews,
  new pages or form flows, changes that add several domain interfaces, or
  codebase audits; not for ordinary compile/test verification.
---

# ukpt-architecture-review

Semantic review of the architecture rules `verifyArchitecture` cannot prove: how the web layer
answers requests, and the domain contract guidance. Enforced rules and compilation belong to
`ukpt-verify`.

## Procedure

1. Read `AGENTS.md`, `UKPT.md`, and only the generated architecture pages for the layers under
   review (`platform/common/architecture/docs/serverweb.md`, `serverdomain.md`, `serverdata.md`).

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
   (`ServerDomain.inventory`) and grouped domain-interface candidates (read families with one
   consumer, high fan-in, update families, uninjected interfaces, one-dependency UseCases), each
   with its declarations as evidence: start the domain contract inventory in step 4 from those rows.

3. For every route under review, produce a **request inventory**:

   | Route | htmx answer (fragment, `204`, retarget) | Answer without JavaScript (page, `303`) | Invalid input (`422` + what re-renders) | What else on the page must change, and how (event stream, `HX-Trigger`, out-of-band) |
   |---|---|---|---|---|

4. For every domain interface the change adds, touches, or consumes, produce a **domain contract
   inventory**. Walk the whole composition chain: the Routes class or event stream, every UseCase
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

   - A form that works only with JavaScript: no `action`/`method`, or a handler with no answer
     for a request without `HX-Request`.
   - Validation errors answered with `200`, or a redirect answered to htmx instead of a fragment.
   - A handler that renders part of the page from a second read the page does not share, so the
     fragment and the full page can disagree.
   - A live list whose items are rendered by a different function on the page and in its event
     stream, or an event stream that does not send the whole list first
     (`ServerWeb.EventStream.snapshotFirst`).
   - An htmx swap target inside an Alpine root whose state the swap resets unintentionally.
   - Behaviour in a script file that the server could render instead (a toggle whose state the
     server needs, a computed value the page could carry).
   - A group of reads from one Repository whose only consumer is one UseCase, Routes class or
     event stream (`ServerDomain.DomainInterface.readProjections`).
   - A UseCase or Routes constructor taking many domain interfaces from one provider
     (`ServerDomain.DomainInterface.namesACapability`).
   - A mutation followed by a read of the same model, or a mutation returning an identifier when
     the caller needs the model (`ServerDomain.DomainInterface.mutationResults`).
   - A UseCase that forwards one dependency, or an orchestration phase with one caller published
     as its own interface (`ServerDomain.UseCase.breakDownComplexUseCases`).
   - Repository properties that are one storage call plus a mapping, with their results assembled
     downstream (`ServerData.Repository.mayInjectStorage`).

6. When several required sources form one concept, recommend a read projection (a `FlowOf...` or
   `Get...` domain interface returning an immutable data class) provided by the Repository that
   owns the storage, or by a UseCase when the inputs are independent capabilities. `:feature:core`'s
   `GreetingSummary`/`FlowOfGreetingSummary` is the worked example.

7. Verify that each page has HTML snapshots for its empty, populated and invalid-input states, and
   route tests for both the htmx and the non-JavaScript answer of each handler.

8. **Findings vs authorization:** a review request reports; only a change request implements
   and verifies.

## Closing checklist

- [ ] Every form posts without JavaScript and re-renders with its errors under `422`.
- [ ] Every handler whose body depends on `HX-Request` calls `varyOnHtmx()`.
- [ ] A page and its fragments are rendered by the same Components from the same View State.
- [ ] Live lists send the whole list first and render items with the page's item Component.
- [ ] Snapshots and route tests cover the states and both answers above.
- [ ] Every domain interface in the inventory has a consumer that uses it alone, or a stated
      boundary that keeps it separate.
- [ ] No family of reads exists only to feed one aggregate.
- [ ] Mutations return what their caller needs next, and nothing a caller discards.
