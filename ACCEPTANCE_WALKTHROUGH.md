# PantryPal acceptance walkthrough

Date: October 21, 2026. Current product scope: pantry/recipes, TheMealDB import,
local persistence, Meal Roulette, and **Walmart-only Broke Mode**. Other chains
are deferred and are not acceptance requirements for this version.

## Result and method

**PASS for the exercised scope: 193 tests passed, with no failures or skips.**
The full `scripts/test.ps1` run compiled 22 application/support sources using
JDK 17.0.8.1, Java 11 compatibility, and the course JUnit 4.12 library.

The new `PantryPalAcceptanceTest.testCompleteUserJourney` exercises the actual
Swing controls in a continuous scenario using a temporary save beneath
`build/test-data`. It creates fresh controllers and views to verify reopening.
Recipe and pricing HTTP transports are injected offline fixtures: no sockets,
real API key, live quota, or personal pantry save are involved. This is automated
UI acceptance evidence, not a claim of a new live retailer/provider audit or
manual clicking through native desktop dialogs.

## Continuous user journey

| Step | Expected and observed outcome |
| --- | --- |
| Start empty | Five tabs available; pantry/recipes empty; Roulette explains that no recipes are cookable. |
| Reject bad input | Zero quantity is rejected, the form remains usable, and inventory stays empty. |
| Manage pantry | Add 200 g rice; add, update, search, and remove a separate flour entry without changing rice. |
| Create a recipe | Save Rice supper with 800 g rice, two instructions, and a 10-minute cook time. Comparison identifies a 600 g shortage. |
| Search TheMealDB | Fixture results appear asynchronously. Supported measurements and the ambiguous `1 tin` measurement remain visible. |
| Save unfinished import | Change rice to 600 g; leave beans unresolved and a time field unfinished. Save for later persists the edits without making the draft a cookable recipe. |
| Reopen offline | Fresh controller/view, with no recipe API service, restores pantry, recipe, draft, units, partial text, and the original measurements. |
| Finish import | Import rejects unresolved beans. Enter 200 g beans and clear the unfinished time field; promotion creates the recipe and removes its saved draft. Provider attribution remains in the recipe details. |
| Filter recipes | Maximum cook time includes Rice supper and excludes the imported recipe with unknown time. Show all restores both. |
| Estimate Walmart shortages | Explicitly search the 600 g shortage, review a 400 g package at $2.50, and select it. The estimate requires two packages and totals $5.00, labeled Lake Ridge, VA, store 1852. |
| Preserve inventory | Pricing does not rewrite the save, add groceries, or make an understocked recipe cookable. Attempting to cook leaves 200 g rice unchanged. |
| Change pantry | Update rice to 800 g. Returning to pricing invalidates the previous estimate; Reload shortages gives $0.00 and needs no additional price request. |
| Roulette and cooking | Roulette finds Rice supper. Cooking deducts the required stock and saves; Roulette then reports no cookable recipes. |
| Cook the imported recipe | Add 800 g rice and 200 g beans, then cook Rice & beans. Exactly 600 g rice and 200 g beans are deducted, leaving 200 g rice. |
| Reopen again | Both recipes, zero drafts, and the final pantry state persist. Pricing starts without a saved quote or automatic request. |
| Remove a recipe | Removing Rice supper updates the saved collection and the on-disk snapshot. |

The continuous scenario used one synthetic recipe search and one synthetic
Walmart product search. Neither transport ran on Swing's event thread.

## Failure and boundary checks

These were also rerun successfully in the complete suite:

| Area | Acceptance evidence |
| --- | --- |
| Import empty/error/retry/cancel | `RecipeImportPanelTest`: empty results, HTTP failure and retry, cancellation ignoring late responses, invalid measurements/times, duplicate import protection. |
| Draft recovery | `RecipeImportPanelTest`, `PantryPalPersistenceTest`: update rather than duplicate a draft, partial edits, offline resume, promotion/removal, failed-save recovery. |
| Save/load recovery | `PantryPalGUITest`, `PantryPalPersistenceTest`, `DataStoreTest`: failed saves retain edited memory for retry; invalid saves block overwrites; retry after load recovery; format validation and version-1 compatibility. |
| Partial Walmart totals | `BrokeModePanelTest`: unknown requirements, unknown package sizes, skipping, no candidates, and unchanged pantry. |
| Walmart failure/retry/cancel | `BrokeModePanelTest`: provider error followed by explicit retry; cancellation ignores late results; missing configuration leaves other features usable. |
| Pricing correctness | `PriceFinderTest`, `StoreOfferTest`: whole-package rounding, compatible conversions, money arithmetic, wrong stores/sellers, incompatible forms, unknown stock, conditional offers, freshness, quota, and bounded responses. |
| Request limits | `BrokeModePanelTest`, `PriceFinderTest`: 15-search batch limit, explicit remaining searches, valid cache reuse, expiry, and no automatic retry. |
| Core inventory behavior | Pantry/matcher/controller tests: repeated requirements, aliases, compatible units, insufficient stock, invalid input, and atomic cooking deductions. |
| Desktop startup | Existing `PantryPalGUITest` entry-point coverage opens and closes the actual desktop window with isolated data and pricing credential loading disabled. |

## Issue found and fixed

The pricing panel previously displayed the normal instruction to select a listing
even when a provider error stopped the search, or the search returned no listings.
The final message now identifies a stopped search and its safe error/retry action,
or explicitly reports no candidates. Completed results are retained. Regression
checks reproduce the error and empty-result paths and verify the corrected
messages; the full suite passed after the fix.

## Visual review and boundaries

Rendered the actual Swing component tree at 1100 x 820 and 920 x 720 for the saved
draft, completed Walmart estimate, and post-cooking comparison. Checked the six
PNGs in `build/acceptance/`; controls and totals remain visible in the reviewed
states. Long listing text still truncates in narrow columns and uses tooltips;
broader visual redesign remains optional polish. Screenshots contain synthetic
data, not current Walmart prices. The render helper supplies JTable's normal
display-time column headers without opening a native window.

No blocking functional defect remained in these exercised journeys. Native
save/discard/cancel dialogs, browser launch from a listing, high-DPI behavior,
and a sustained live-provider session were not manually driven in this pass.
Automated save/recovery tests and previous bounded provider audits provide
separate evidence; they do not turn those items into manual acceptance checks.

Remaining course work includes team review of revised class contracts/UML,
attribution, and final rubric/demo preparation. Existing provider-use questions
before public distribution remain documented in `PROVIDER_RESEARCH.md`.
This pass does not commit, push, merge, or change billing/provider accounts.
