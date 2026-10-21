# PantryPal

Virginia Tech CS 2114, Project 2, Group 62 — Deliverable 3.

A prototype for managing pantry ingredients and recipes, comparing stock,
cooking with inventory deductions, and Meal Roulette. **Pantry ingredients and
recipes now load at startup and autosave after changes.** Click **Load demo data**
when both collections are empty to try it.

## Build, run, and test

Requires **JDK 17**, Java 11 source compatibility, and the course
`CS2-Support/student.jar` (`student.TestCase` and JUnit 4.12).
From the repository root in PowerShell:

```powershell
.\scripts\build.ps1  # Compile only
.\scripts\run.ps1    # Build and launch the app
.\scripts\test.ps1   # Build and execute tests
```

The scripts look for course libraries in your Documents `eclipse-workspace`.
For other locations, pass `-JavaHome 'C:\path\to\jdk-17'` and
`-EclipseWorkspace 'C:\path\to\workspace'`.

In Eclipse, import **Existing Projects into Workspace**.
Run `PantryPalGUI.java` as a **Java Application**, or the `test` folder as a
**JUnit Test**.

## Saving and recovery

The desktop app saves to `${user.home}/.pantrypal/data.properties` (normally
`C:\Users\<you>\.pantrypal\data.properties` on Windows). Hover over the save-status
message to see the full path. To use a separate file for a demo:

```powershell
.\scripts\run.ps1 -DataFile '.\build\demo-data.properties'
```

In Eclipse, the equivalent VM argument is
`-Dpantrypal.dataFile=C:\path\to\data.properties`.

- Missing files start an empty pantry and recipe book. They are created on save.
- Saves write a temporary file beside the destination and then replace the
  destination atomically. Unsupported atomic moves or I/O failures report errors.
- If saving fails, changes remain in memory and the GUI refreshes to show them.
  Fix the file/location problem and click **Save now**; do not repeat the edit or
  cooking action. Closing with unsaved changes offers save, discard, or cancel.
- If loading fails, edits and saves are blocked to protect the existing file.
  Restore a valid copy, or move the damaged file aside, then click **Retry loading**.
- The versioned Java properties format preserves IDs, units, quantities, recipe
  metadata, ingredient/step order, Unicode, and multiline text. No library was
  added. Run one app instance per save file; concurrent editing is not supported.
- Saves now use format version 2 to include unfinished recipe imports and their
  partial edits. Existing version-1 saves load normally and upgrade on the next
  successful save. Older app versions cannot read version-2 saves.
- The no-argument controller remains an isolated session-only option for tests.
  Automated persistence tests use temporary files under `build/test-data`, not
  your personal save file.

## Importing recipes from TheMealDB

Open **Import recipes**, enter a recipe name, and click **Search TheMealDB**.
Select a result to review its original measurements alongside editable ingredient
names, quantities, and units. Recognized quantities (including fractions) are
suggested; blank quantity/unit cells need correction. No ingredient is silently
discarded. Package sizes, cloves, bare numbers, ranges, and "to taste" require
review: do not equate a garlic clove with a whole garlic bulb or guess a tin size.
All quantities use PantryPal's existing units (including US customary volumes).

If you do not know an amount, click **Save for later**. No missing measurements
need to be guessed: the recipe's original data, current ingredient edits,
unfinished time fields, and effort selection are saved locally. Click **Saved
for later (N)** in the same tab to reopen a draft, including offline. Draft rows
are marked **Needs review** and are kept out of the recipe collection, pantry
comparisons, cooking deductions, and Meal Roulette until all ingredient amounts
are valid and you click **Import reviewed recipe**. Successful import removes
the draft in the same save. **Remove saved draft** discards it without changing
your pantry. Saving the same draft again updates its progress instead of adding
a duplicate. If you search for an already-saved draft, selecting that result
resumes your saved corrections. Saving for later is explicit: click the button
before switching to another result or closing the app to keep your latest edits.

Leave prep/cook times blank and effort UNKNOWN when unavailable. Unknown cooking
times are excluded by an active maximum-time filter. Click **Import reviewed
recipe** to add the validated recipe and autosave it. Reimporting the same
TheMealDB recipe keeps your existing version; remove it first to import a new
reviewed version. Imported recipes work offline after saving, including pantry
comparison, cooking deductions, and Meal Roulette. Provider and original source
links are retained with the instructions and displayed in recipe details.

Search runs in the background, can be cancelled, and reports network/provider
errors without changing saved data. Click Search to retry. A failed autosave
keeps the import in memory: use **Save now**, as with other edits.

The desktop launcher uses TheMealDB's public educational key `1`; no signup or
new library is needed. HTTP setup is deferred until searching, so startup and
saved recipes work without networking. The no-argument controller still disables
external services for isolated tests. See the [API documentation](https://www.themealdb.com/documentation)
and [terms](https://www.themealdb.com/terms_of_use.php) before public distribution;
production/app-store use requires revisiting the supporter-key setup. This
integration imports text and source links, not artwork.

## Walmart estimates (Broke Mode)

Select a saved recipe in **Recipes** and click **Estimate Walmart cost**. In
**Broke Mode**, click **Search missing ingredients**, select an ingredient row,
review the Walmart listings, and click **Use selected product**. Confirm the food
form before selecting: a search result is a candidate, not an automatic substitute.
Use **Skip for now** if a package size, unit, or product cannot be resolved.

The initial reference is Walmart store **1852**, returned by SerpApi as
**Lake Ridge, VA**. Searches use this explicit store; prices are not nationwide
chain prices. Missing amounts come from the current pantry comparison. Equivalent
shortages are combined before rounding to sufficient whole packages. The screen
shows a full purchase estimate only when every requirement is priced; otherwise
it shows a partial subtotal and the unpriced rows. Estimates exclude taxes,
shipping, and delivery fees, and do not change pantry stock or cooking eligibility.

Set `SERPAPI_API_KEY` in the process environment or the project's ignored
`.env.local`. Optional `WALMART_STORE_ID` overrides `1852`; changing it requires
restarting the app. File settings use one `NAME=value` per line; environment
settings take precedence. When launching outside the project directory, set
`-Dpantrypal.priceConfig=C:\path\to\.env.local`. Credentials are not saved with
recipes, logged, or bundled. Restart after changing credentials. Without a valid
configuration, pantry and recipe workflows remain available and pricing explains
how to configure access. `-Dpantrypal.pricingDisabled=true` disables credential
loading and price access for isolated launch tests/troubleshooting.

Network searches run in a cancellable background worker. One batch requests at
most 15 ingredients, one page each; click **Search missing ingredients** again
for remaining rows. **Search selected ingredient** retries a row. Responses are
cached in memory for at most 15 minutes and can also come from SerpApi's cache.
Expired prices and changed pantry/recipe data require refreshing; cached offers
may be reused after **Reload shortages**. Quotes and selections are discarded at
exit. The free API quota is limited, and account/quota failures are shown without
automatic retries or purchases. This provider supplies Walmart candidates only;
Walmart is the agreed scope for this version; Kroger and Food Lion are deferred.

Variable-weight offers, ambiguous packages, unverified availability, incompatible
units, food-form conflicts, and unresolved conditional prices are excluded from
fixed-package totals. Listings can still be reviewed where a validated Walmart
link is provided. No manual price collection or required price-entry form was
added. The [live audit](WALMART_SERPAPI_FEASIBILITY.md) records provider tests;
[implementation details](WALMART_IMPLEMENTATION_PLAN.md) record the revised scope
and class contracts. Public redistribution/display and long-term data storage
permissions remain an unresolved provider dependency; this is a course prototype.

Validated October 21: **193 automated tests passed** with `scripts/test.ps1`.
Explicit-store responses for milk, rice, and chicken were also replayed through
application parsing offline. The API key is excluded from tracked files and
fixtures; automated tests use fake credentials and do not consume live quota.
The [acceptance walkthrough](ACCEPTANCE_WALKTHROUGH.md) records the continuous
draft/save/reopen/import/price/cook journey, recovery checks, visual review,
and the pricing-status issue found and fixed.

## Reflection

- **Changes from the spec:** We made a few changes to our original project plan as we started developing PantryPal.

  - The initial prototype used Java Swing and session-only storage. **Decided via group discussion.** Persistence is now implemented through `PantryPalData` and `DataStore`, with startup loading, autosave, and recovery controls. The proposed JSON file was replaced with versioned Java properties to use the existing JDK and preserve all model text without a new dependency. **GenAI implementation choice during the user-requested persistence increment; team review pending.**
  - We also clarified some parts of our original class design. For example, we decided to use unique IDs to compare certain objects and throw IllegalArgumentException when invalid values are provided. **Decided via reccomendation from GenAI.**
  - We also made sure that empty recipes or ingredient lists are handled properly and that ingredients are not counted multiple times when comparing a recipe to the pantry. **Decided via group discussion.**
  - We separated input handling between the interface and our classes. The GUI handles user input and allows users to correct mistakes, while the classes check whether the values are valid. **Decided via group discussion.**

- **LinkedChain:** We kept the instructor-provided LinkedChain class unchanged.

  - We also added IngredientMatch as a third type of object stored using LinkedChain, alongside Ingredient and Recipe. This was suggested with the help of GenAI and approved during development.
  - We only use the public methods provided by LinkedChain, keeping the application's other logic in separate classes.
  - We also corrected some of our original time complexity estimates. For example, contains() takes O(n) time rather than O(1), since it may need to check every node. Comparing a recipe to the pantry takes approximately O(r × p), where r is the number of required ingredients and p is the number of pantry entries.

- **Original unknowns:**
  - Name normalization, aliases, and compatible unit
  conversions now work through `IngredientMatcher`; different food forms are not
  automatically equivalent.
  - TheMealDB is now the implemented recipe provider. An immutable import draft
    preserves unresolved measurements; the importer and external-search controller
    return drafts instead of `Recipe` objects. The review screen creates validated
    recipes only after every row is resolved. These changes from the original
    class contracts are **GenAI-assisted implementation choices; team review
    pending**. JSON decoding is shared through `Json`; measurement parsing
    lives inside `RecipeImportDraft`. Only two application classes were added:
    `RecipeImportDraft` and `RecipeImportPanel`. No additional library is needed.
    Provider IDs are
    namespaced (`themealdb:ID`), and deterministic recipe UUIDs prevent duplicate
    imports. Unfinished drafts and partial review edits now persist separately
    in format version 2, with backward-compatible version-1 loading. Drafts are
    stored in a `LinkedChain<RecipeImportDraft>` owned by the controller; no
    additional application classes were added for Save for later.
  - Spoonacular was not selected because its full-recipe storage restrictions
    conflict with the offline workflow. Kroger's public API rules prohibit
    cross-retailer comparison. The October 21 scope prioritizes Walmart, Kroger,
    and Food Lion. The Open Prices audit found sparse recent data for these
    chains; useful primary-provider coverage is still unproven. Smaller/local
    chains will not be added just to compensate for missing prices.
    SerpApi Walmart estimates are now implemented after a 15-ingredient audit
    and explicit-store checks. They use reviewed product selections, supported
    package sizes, and visible partial totals. Store 1852 is verified; data-use
    permissions for public distribution still require clarification. See the
    [live Walmart test](WALMART_SERPAPI_FEASIBILITY.md),
    [implementation plan](WALMART_IMPLEMENTATION_PLAN.md), and
    [provider research](PROVIDER_RESEARCH.md).

- **Unfinished work:**

  - Review the implemented Walmart estimate workflow and revised pricing class
    contracts against the course requirements. Walmart-only Broke Mode is the
    agreed scope; Kroger/Food Lion and cross-chain comparisons are deferred.
  - The team still needs to review the recipe-import workflow and class-contract
    changes against the course requirements.
  - Photo recognition and social media import are dropped stretch goals.

## Next Steps

| Order | Work | Team Member | New tests |
| --- | --- | --- | --- |
| 1 | Review completed persistence and its format/recovery behavior. | Zolo | Added `PantryPalDataTest`, `DataStoreTest`, `PantryPalPersistenceTest`, and GUI persistence/recovery tests. |
| 2 | Review completed TheMealDB search/import UI and updated class contracts. | Jamie | `RecipeImporterTest` and `RecipeImportPanelTest`: parsing, review, duplicates, cancellation, failures, save/reopen/compare/cook. Six live requests also passed. |
| 3 | Review implemented Walmart estimates and intended data use. Other chains are deferred. | Momin | `StoreOfferTest`, `PriceFinderTest`, `BrokeModePanelTest`, `JsonTest`: package costs, matching, cache/quota, partial coverage, cancellation, changed pantry state, and provider failures. |
| 4 | Review the completed acceptance results, polish UI, update UML/contracts, and ensure rubric requirements are met. | All three members | `PantryPalAcceptanceTest`: continuous draft/save/reopen/import/price/cook workflow; full suite passes 193 tests. |

**Stretch goals:**
  - **Meal Roulette:** Committed and working.
    - Our existing tests check that it only selects eligible recipes, handles cases where no recipes qualify, supports unit conversions, and does not change pantry inventory.
    - Persistence tests now verify Roulette after reopening and confirm it does
      not rewrite the save. Recipe-provider tests now cover decoding, review,
      cancellation, duplicate imports, and save/reopen/compare/cook behavior.
  - **Social Media Recipe Import:** Dropped to focus on MVP.
  - **Photo Recognition:** Dropped to focus on MVP.
## Attribution

| File | Contributor | How |
| --- | --- | --- |
| `src/Ingredient.java` | Zolo | Written by Zolo; Javadocs, integration, and review by GenAI. |
| `test/IngredientTest.java` | Zolo | Written by GenAI; reviewed by Zolo. |
| `src/Pantry.java` | Zolo | Written by Zolo; Javadocs, integration, and review by GenAI. |
| `test/PantryTest.java` | Zolo | Written by GenAI; reviewed by Zolo. |
| `src/IngredientMatcher.java` | Zolo | Written by Zolo; Javadocs, integration, and review by GenAI. |
| `test/IngredientMatcherTest.java` | Zolo | Written by GenAI; reviewed by Zolo. |
| `src/Unit.java` | Zolo | Written by Zolo; Javadocs, integration, and review by GenAI. |
| `src/PantryPalData.java` | Zolo (planned owner) | Implemented by GenAI; team review pending. |
| `test/PantryPalDataTest.java` | Zolo (planned owner) | Written and executed by GenAI; team review pending. |
| `src/DataStore.java` | Zolo (planned owner) | Implemented by GenAI; team review pending. |
| `test/DataStoreTest.java` | Zolo (planned owner) | Written and executed by GenAI; team review pending. |
| `test/PantryPalPersistenceTest.java` | Zolo / Momin (planned reviewers) | Controller/store integration tests written and executed by GenAI; team review pending. |
| `test/EnvironmentSmokeTest.java` | Zolo | Written by GenAI; reviewed by Zolo. |
| `src/Recipe.java` | Jamie | Written with GenAI assistance; Jamie's contribution to confirm. |
| `test/RecipeTest.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/RecipeBook.java` | Jamie | Written by Jamie; Javadocs, integration, and review by GenAI. |
| `test/RecipeBookTest.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/IngredientMatch.java` | Jamie | Written by Jamie; Javadocs, integration, and review by GenAI. |
| `test/IngredientMatchTest.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/Effort.java` | Jamie | Written by Jamie, reviewed by GenAI. |
| `src/RecipeImporter.java` | Jamie (planned reviewer) | TheMealDB client implemented by GenAI; JSON decoding now shared; client live-tested; team review pending. |
| `src/RecipeImportDraft.java` | Jamie (planned reviewer) | Import draft, partial review snapshots, and conservative measurement parsing implemented by GenAI; team review pending. |
| `test/RecipeImporterTest.java`, `test/RecipeHttpStub.java` | Jamie (planned reviewer) | Written and executed by GenAI; team review pending. |
| `src/RecipeImportPanel.java`, `test/RecipeImportPanelTest.java` | Momin / Jamie (planned reviewers) | Import UI and workflow tests implemented by GenAI; team review pending. |
| `src/StoreOffer.java`, `test/StoreOfferTest.java` | Jamie / Momin (planned reviewers) | Immutable observed-price model and tests implemented by GenAI; team review pending. |
| `test/TestFixtures.java` | Jamie | Written by GenAI; reviewed by Jamie. Persistence fixture helpers added by GenAI; review pending. |
| `src/RecipeMatcher.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. |
| `test/RecipeMatcherTest.java` | Momin | Written by GenAI; reviewed by Momin. |
| `src/RecipeMatchResult.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. |
| `test/RecipeMatchResultTest.java` | Momin | Written by GenAI; reviewed by Momin. |
| `src/PantryPalController.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. Persistence guards, draft search contract, and import duplicate checks added by GenAI; review pending. |
| `test/PantryPalControllerTest.java` | Momin | Written by GenAI; reviewed by Momin. |
| `src/PantryPalGUI.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. Startup/recovery controls and import integration added by GenAI; review pending. |
| `test/PantryPalGUITest.java` | Momin | Written by GenAI; reviewed by Momin. Persistence cases added/executed by GenAI; review pending. |
| `src/PriceFinder.java`, `test/PriceFinderTest.java`, `test/PriceFixtures.java` | Momin (planned reviewer) | SerpApi retrieval, matching, package/cost calculations, cache/quota handling and tests implemented by GenAI; team review pending. |
| `src/BrokeModePanel.java`, `test/BrokeModePanelTest.java` | Momin (planned reviewer) | Asynchronous product review and complete/partial estimate UI implemented by GenAI; team review pending. |
| `src/Json.java`, `test/JsonTest.java` | Jamie / Momin (planned reviewers) | Existing JSON parser extracted for shared use and tested by GenAI; team review pending. |
| `test/PantryPalAcceptanceTest.java`, `ACCEPTANCE_WALKTHROUGH.md` | All three members (planned reviewers) | Continuous Swing acceptance journey, visual checks, and results authored/executed by GenAI; team review pending. |
| `src/LinkedChain.java` | Instructor | Provided by instructor; unchanged. |
| `README.md` | Zolo | Drafted Reflection and Next Steps; later edits assisted by GenAI. |

**GenAI use:** Codex assisted with skeleton classes, logic recommendations,
integration, tests, and documentation.
