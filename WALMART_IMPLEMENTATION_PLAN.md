# Walmart pricing implementation plan

Prepared October 21, 2026. The first course prototype described below is now
implemented. The user confirmed Walmart-only scope for now on October 21;
other chains are deferred. Evidence and store validation: [live audit](WALMART_SERPAPI_FEASIBILITY.md).

## Implemented status

- Reference store 1852 was verified and is configured by default. The provider
  labels it Lake Ridge, VA; no user location is inferred.
- `StoreOffer`, `PriceFinder`, controller integration, `BrokeModePanel`, and the
  shared `Json` reader are implemented. Only those last two are new top-level
  application classes. Quotes remain session-only; the save format is unchanged.
- The UI requires product review and excludes unresolved package/food-form/price
  data from totals. Unknown requirements remain visible. A batch stops on the
  first provider failure and is limited to 15 rows; retries are explicit.
- All 193 automated tests pass, including the existing import/persistence suite
  and new pricing/UI regressions. Explicit-store responses were replayed through
  the Java parser offline without additional quota use.
- The [acceptance walkthrough](ACCEPTANCE_WALKTHROUGH.md) passed a continuous
  draft/save/reopen/import/price/cook journey and reviewed recovery and UI states.
- Public distribution/display and long-term storage permissions remain an
  unresolved provider dependency. No provider inquiry has been sent.
- The detailed sequence below records the implementation design and course
  contract changes, with future provider expansion still outside this release.

## First-release scope

Estimate the cost of buying the missing ingredients for one saved recipe from
Walmart, using SerpApi and one explicit reference store. Reuse the current pantry
comparison to price shortages, not the recipe's full ingredient amounts.

The user selects a recipe, enables Broke Mode, and clicks **Estimate Walmart
cost**. The pricing view shows shortages and candidate Walmart products, lets
the user select an appropriate product or skip a row, and calculates enough whole
packages where the quantities are reliable. No user price collection or mandatory
manual package-size entry is needed. Unknown requirements and skipped/unpriced
rows remain visible, with a partial subtotal instead of an invented complete total.

Walmart is the first implemented chain. Kroger and Food Lion remain future
providers pending evidence of suitable access and coverage. Exclude smaller/local
retailers. Do not promise the cheapest retailer, national uniform prices, automatic
checkout, or guaranteed stock. Use **Estimated Walmart purchase cost**, with the
reference store, checked time, coverage count, and exclusions visible.

## 1. Close the store-context validation gap

Before writing provider-dependent UI, run at most four additional search requests
using the existing key and an explicit store ID. Use an already observed store,
such as Dale City store `1852`, as a validation fixture; this is not an inferred
user location or a permanent nationwide default.

- Query milk, rice, and chicken with the same `store_id`; repeat one identical
  query to inspect consistency/caching without forcing cache bypass.
- Confirm each response reports the requested store and preserves usable seller,
  price, package, and availability fields. Record the actual reported context.
- Stop if store context is missing or mismatched; never silently combine different
  stores. Investigate the provider behavior before implementing basket totals.
- Check account quota before/after, redact credentials, and retain only sanitized
  research results. Automated development thereafter uses fixtures.

Also record the supported display/storage use from the provider's published
terms. The completed technical test did not establish an explicit Walmart
redistribution license. If clarification is still required, document the precise
unresolved use and prepare an inquiry; sending it requires the user's instruction.
Offline implementation can proceed while that is unresolved, but do not describe
the integration as cleared for public distribution.

**Exit:** three useful ingredient responses tied to one verified store, a recorded
usage-policy decision or explicit unresolved item, and sanitized fixtures.

## 2. Implement the pricing model and calculation contracts

Implement the existing `StoreOffer` and `PriceFinder` skeletons. Replace their
nearby-store contract and update callers/tests together; no working nearby-price
feature currently depends on it.

`StoreOffer` becomes an immutable snapshot containing:

- Product ID, product name, seller identity, validated Walmart product URL.
- Requested/returned store ID and returned city/state; address only if supplied.
- USD offer price as `BigDecimal`, original package description, optional positive
  package quantity and unit, and price basis (`EACH`, `PACK_WEIGHT`,
  `EACH_WEIGHT`, `UNKNOWN`). Preserve relevant price-range/promotion information.
- Availability (`IN_STOCK`, `OUT_OF_STOCK`, `UNKNOWN`), retrieval time, and provider
  search timestamp when available. These are not promises of shelf availability
  or a retailer's last price-update timestamp.

Use nested enums for the small status sets. Remove required distance/radius fields;
do not fill missing values with zero. Unknown quantity/unit must be a consistent
pair. Unsupported candidates can still be displayed but cannot enter package-cost
ranking. A product's inferred ingredient match is not a fact supplied by the API.

Proposed service contracts:

| Method | Responsibility |
| --- | --- |
| `findOffers(Ingredient shortage, String storeId)` | Fetch bounded candidates and store/search context; return a small nested `SearchResult`. |
| `calculateRequiredPackages(Ingredient shortage, StoreOffer offer)` | Convert compatible units and round up sufficient whole packages; reject unpriceable inputs. |
| `calculatePurchaseCost(Ingredient shortage, StoreOffer offer)` | Multiply required packages by the offered package price using decimal money arithmetic. |
| `findCheapestOffer(Ingredient shortage, StoreOffer[] eligibleOffers)` | Rank only reviewed/matched, comparable offers at the same store by sufficient-package cost. Return null if none qualify. |

Example: shortage 600 g, package 400 g at $2.50 => two packages, **$5.00 purchase
cost**. Do not show the prorated $3.75 as the checkout estimate. Use the existing
`IngredientMatcher` for compatible unit conversion; apply an explicitly tested
roundoff tolerance at package boundaries and guard overflow. Do not convert mass
to volume or item counts using assumed densities or average item weights.

**Exit:** model and cost tests pass without network access.

## 3. Add SerpApi retrieval and credential loading

Keep HTTP work in `PriceFinder`, using injected `HttpClient`, base URI, credential,
and a controllable clock for tests. Keep the current Java 11 compatibility and
JDK 17 build; no new third-party runtime library is needed.

- Read `SERPAPI_API_KEY` and `WALMART_STORE_ID` from environment variables first,
  then the project's ignored `.env.local`. Implement this as a bounded loader
  owned by the pricing service; do not execute file contents. Use an explicit
  configured file path if launched outside the project working directory.
- Keep credentials out of GUI messages, URLs in logs, exceptions, fixtures,
  recipe files, and version control. Because SerpApi authenticates in the URL,
  never expose a raw HTTP request URI or exception containing it.
- Configure the optional `PriceFinder` in `PantryPalGUI.main`. Without valid
  configuration, show a clear setup state and allow pantry/recipe features to work.
- Send HTTPS requests to the fixed SerpApi endpoint with `engine=walmart`,
  `walmart_domain=walmart.com`, explicit `store_id`, encoded query, and `page=1`.
  Treat mismatched/missing returned store context as a failed estimate search.
- Extract the generic JSON parser from `RecipeImporter` into package-private
  `Json.java`; both providers use it. Preserve nesting/body limits, decimal
  number parsing, and malformed-input handling. Avoid copying the parser or
  making grocery pricing depend on recipe-import internals.
- Distinguish a successful empty result from invalid credentials, quota/rate
  limits, network failure, malformed JSON, missing result fields, provider
  `error`, or a non-success search status. Display safe actionable messages.
- Use a 10-second connection timeout and a bounded response timeout (initially
  45 seconds). Bound response bytes as they are read. Do not automatically retry
  search requests in the first version: a timed-out request may already use quota.

**Exit:** fixture-backed tests verify request parameters, response parsing,
credential redaction, store validation, empty results, and failures.

## 4. Normalize packages and review ingredient matches

Keep provider-specific matching and package parsing inside `PriceFinder`. Preserve
the conservative pantry aliases in `IngredientMatcher`; branded product text must
not weaken pantry/cooking matching rules.

- Exclude sellers other than explicitly recognized Walmart identities, invalid
  or non-USD prices, and reported out-of-stock rows from selectable estimates.
  Show unknown availability as unavailable for totals in this first release.
- Deduplicate by product, offer, and store identity; do not collapse materially
  different variants or promotions into a single arbitrary price.
- Parse explicit mass, fluid volume, and count sizes. Normalize US gallons,
  quarts, and pints to existing volume units. Handle clear total sizes such as
  `4 sticks, 16 oz` without multiplying the total by four. Accept multipacks only
  when the per-pack versus total meaning is unambiguous and consistent.
- Reject contradictory sizes, ambiguous ounces, ranges, unsupported multipacks,
  conditional prices with unknown eligibility, and variant-price ambiguity from
  automatic calculations. Keep the listing link for review.
- Initially display variable-weight offers with their reported unit price and
  explanation, but exclude them from fixed-package totals. Do not infer package
  weight by dividing the estimated offer price by its per-pound price.
- Preserve food forms: fresh versus canned/dried, raw versus cooked, salted versus
  unsalted, milk versus substitutes, and net versus drained weight. Require user
  product selection for uncertain matches. Selection does not override invalid
  quantities, incompatible units, or an unverified store.
- Provide **Skip for now** and **Open Walmart listing**. A user who cannot resolve
  missing package information can continue with a visibly incomplete estimate.

**Exit:** all 15 audited ingredient cases have either a reviewed, compatible
candidate or a specific visible reason they cannot be included in a total.

## 5. Integrate recipe shortages and the Swing flow

Add one focused `BrokeModePanel` rather than enlarging `PantryPalGUI` with all
network/search state. Reuse the existing controller's Broke Mode flag and price
service injection. Add a Broke Mode control and **Estimate Walmart cost** action
to the recipe comparison area; the action opens the pricing panel for that recipe.

1. Snapshot the selected recipe and comparison. Use
   `RecipeMatchResult.getMissingIngredients()` for known shortages and also
   `getUncomparableIngredients()` so unknown requirements are not silently lost.
2. Display ingredient, missing amount/unit, selected product/package, package
   count, estimated purchase cost, and row status. Show the reference store and
   checked time in the header.
3. Search explicitly on the button action, not on every keystroke or recipe
   selection. Let users choose or change products, retry a failed row, and skip
   unresolved rows. Render progress as individual rows finish.
4. Use `SwingWorker` and a request-generation token, following the import panel's
   cancellation pattern. Only update widgets on the event thread. Cancel/ignore
   results when the recipe/store changes or Broke Mode closes.
5. Invalidate the estimate when the pantry or recipe changes. Recompute shortages
   before displaying a refreshed total; cached offers can be reused if still
   valid, but an old shortage snapshot cannot be treated as current.
6. Combine genuinely equivalent shortages before package rounding. Never merge
   different food forms or incompatible units merely because they share a word.
   Do not charge for the same selected package twice when it covers duplicate
   equivalent requirements within the recipe.
7. Show **Estimated purchase cost** only when every positive shortage is covered
   and there are no unknown requirements. Otherwise show **Partial subtotal —
   X of Y requirements priced**, with unpriced reasons. Exclude taxes, shipping,
   delivery fees, and unverified discounts explicitly. Estimates do not change
   pantry quantities or mark a recipe cookable.

**Exit:** an end-to-end fixture-backed UI flow handles complete, partial, empty,
failed, cancelled, and stale estimates without freezing or changing saved data.

## 6. Protect the free quota and define retention

- Fetch at most one page per ingredient initially; no automatic pagination,
  product-detail fan-out, background polling, or startup grocery searches.
- Permit one search in flight at a time. Reuse in-flight identical queries and
  a bounded session cache keyed by store ID, normalized query, and search options.
  Cache for at most 15 minutes locally (a proposed app policy, not a claim about
  provider licensing); expire entries, discard them at exit, and mark stale
  displayed estimates as needing refresh. Account for older cached provider
  timestamps when determining freshness.
- Limit one estimate run to 15 distinct uncached queries. For larger recipes,
  show partial results and require an explicit **Search remaining ingredients**
  action. A local counter limits calls; it is not an authoritative account balance.
- Use the account endpoint before a live search batch when its quota snapshot is
  stale. Stop on exhausted quota or an unverified balance; do not change billing
  settings or purchase credits. Surface 429/403 responses distinctly and avoid
  automatic retries that consume searches.
- Keep quotes, selections, cache, and reference-store configuration outside
  `PantryPalData`/`DataStore` in version one. No save-format migration is needed.
  Existing version-2 pantry/recipe/draft persistence remains the application
  storage contract. Users recheck prices after restarting.

**Exit:** repeated requests reuse valid cached data, store changes cannot reuse
another store's offers, and partial/cancelled work cannot bypass request limits.

## 7. Verification, documentation, and delivery

Add `StoreOfferTest`, `PriceFinderTest`, `BrokeModePanelTest`, and targeted shared
JSON tests; extend controller and GUI tests. Generalize the existing test-only
`RecipeHttpStub` if useful rather than duplicating an HTTP framework. Use synthetic
or minimally sanitized fixtures and fake credentials; ordinary tests never read
the real key or contact SerpApi.

Required behavioral cases include sufficient-package rounding, cheaper-unit-price
versus cheaper-purchase-cost, incompatible units, unknown quantities/stock,
conditional promotions, duplicate products, wrong food forms, variable weights,
missing/mismatched store, quota exhaustion, response limits, malformed JSON,
timeout/cancellation, changed pantry state, expired cache, and partial subtotals.
Run the existing recipe-import tests after extracting JSON support, then the full
`scripts/test.ps1` suite after integration. Perform a small bounded live smoke
test only after offline checks pass and when it resolves remaining uncertainty.

Update README configuration/usage, provider findings, and class-contract/course
documentation. Clearly distinguish validated behavior from remaining source
rights or other-chain coverage gaps. Review the diff for credentials and confirm
`.env.local` stays ignored before any eventual commit.

## Class impact and recommended checkpoints

| File/class | Planned change |
| --- | --- |
| `StoreOffer` | Implement immutable observed-offer data and explicit unknown states. |
| `PriceFinder` | Implement configuration, HTTP/parsing, package handling, cache, matching, and cost calculation. Keep cohesive private helpers and small nested result/status types. |
| `PantryPalController` | Replace nearby-search delegation, expose pricing availability, and coordinate snapshots/shortages. |
| `PantryPalGUI` | Configure the optional service and connect recipe actions to the pricing panel. |
| `RecipeImporter` | Delegate generic JSON parsing to the shared helper; retain recipe behavior. |
| **New: `BrokeModePanel`** | Own pricing UI, asynchronous progress, selections, cancellation, and incomplete-result display. |
| **New: `Json`** | Share the existing bounded JSON parser between the two HTTP integrations. |

Target: **two new top-level application classes**, with no provider abstraction
framework or new external dependency. Add an abstraction later only if a second
validated provider needs it. Update the UML to reflect removed ZIP/radius/distance
requirements and explicit unknown values.

Implement in reviewable checkpoints: (1) explicit-store findings and fixtures;
(2) model/math/shared parser; (3) SerpApi retrieval/configuration/matching/cache;
(4) controller/UI workflow; (5) full verification and course documentation.
The first useful milestone is a verified fixed-package offer for one shortage;
the release milestone is a complete-or-explicitly-partial recipe estimate.

## User work

The SerpApi key is already available; no new account, paid plan, or manual price
collection is required for the initial work. Choose the desired reference Walmart
before finalizing the default demo configuration, or retain a clearly labeled
verified demo store. Provider inquiries or paid-plan changes, if later necessary,
remain separate actions requiring the user's instruction.
